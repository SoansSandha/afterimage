package io.github.soanssandha.afterimage.wallpaper

import android.app.WallpaperColors
import android.graphics.Color
import android.hardware.display.DisplayManager
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.SystemClock
import android.service.wallpaper.WallpaperService
import android.util.Log
import android.view.Choreographer
import android.view.Display
import android.view.SurfaceHolder
import io.github.soanssandha.afterimage.core.burnin.BurnInShift
import io.github.soanssandha.afterimage.core.burnin.Offset
import io.github.soanssandha.afterimage.core.engine.AmbientTimeline
import io.github.soanssandha.afterimage.data.WallpaperChanges
import io.github.soanssandha.afterimage.data.WallpaperStore
import io.github.soanssandha.afterimage.render.OutlineRenderer
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

/**
 * The outline wallpaper (docs/plan.md §5.2). Shows the photo; when SystemUI reports the always-on display starting,
 * animates to the photo's glowing outline once, then holds that one frame with no further drawing (ADR 0003); on wake,
 * animates back.
 *
 * Only bound when the app holds `AMBIENT_WALLPAPER`, i.e. it's the copy on the system image ([AmbientComponent]).
 */
class OutlineWallpaperService : WallpaperService() {

    override fun onCreateEngine(): Engine = OutlineEngine()

    /**
     * Engine callbacks arrive on the main thread and only hand events to one render thread, which owns the timeline,
     * the renderer and the surface. Frames are paced by that thread's [Choreographer] and drawn only while a
     * transition runs, or once when the surface needs it.
     */
    private inner class OutlineEngine : Engine() {
        private val thread = HandlerThread("Afterimage render").apply { start() }
        private val handler = Handler(thread.looper)
        private val mainHandler = Handler(Looper.getMainLooper())
        private val store = WallpaperStore(this@OutlineWallpaperService)
        private val displays = getSystemService(DisplayManager::class.java)

        // Render-thread state.
        private val timeline = AmbientTimeline()
        private var choreographer: Choreographer? = null
        private var renderer: OutlineRenderer? = null
        private var surfaceReady = false
        private var frameScheduled = false
        private var ambientCallbackSeen = false
        private var transitionFrames = 0
        private var transitionStartMs = 0L

        @Volatile
        private var colors: WallpaperColors? = null

        private val frameCallback = Choreographer.FrameCallback { drawFrame() }
        private val wallpaperChanged: () -> Unit = { handler.post { reload() } }
        private val displayListener = object : DisplayManager.DisplayListener {
            override fun onDisplayAdded(displayId: Int) = Unit

            override fun onDisplayRemoved(displayId: Int) = Unit

            override fun onDisplayChanged(displayId: Int) {
                if (displayId != Display.DEFAULT_DISPLAY) return
                displays.getDisplay(displayId)?.let { onDisplayState(it.state) }
            }
        }

        override fun onCreate(surfaceHolder: SurfaceHolder) {
            super.onCreate(surfaceHolder)
            setOffsetNotificationsEnabled(false)
            WallpaperChanges.add(wallpaperChanged)
            displays.registerDisplayListener(displayListener, handler)
            Log.i(TAG, "Engine created (preview=$isPreview)")
        }

        override fun onSurfaceCreated(holder: SurfaceHolder) {
            super.onSurfaceCreated(holder)
            handler.post {
                surfaceReady = true
                reload()
            }
        }

        override fun onSurfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            super.onSurfaceChanged(holder, format, width, height)
            handler.post { requestFrame() }
        }

        // The system shows the surface once this returns, so draw before returning.
        override fun onSurfaceRedrawNeeded(holder: SurfaceHolder) {
            super.onSurfaceRedrawNeeded(holder)
            onRenderThreadAndWait { drawFrame() }
        }

        override fun onSurfaceDestroyed(holder: SurfaceHolder) {
            onRenderThreadAndWait {
                surfaceReady = false
                cancelFrame()
            }
            super.onSurfaceDestroyed(holder)
        }

        override fun onVisibilityChanged(visible: Boolean) {
            super.onVisibilityChanged(visible)
            if (visible) handler.post { requestFrame() }
        }

        override fun onComputeColors(): WallpaperColors? = colors

        override fun onDestroy() {
            WallpaperChanges.remove(wallpaperChanged)
            displays.unregisterDisplayListener(displayListener)
            handler.post {
                cancelFrame()
                renderer?.release()
                renderer = null
            }
            thread.quitSafely()
            super.onDestroy()
        }

        /**
         * SystemUI's AOD signal, relayed by system_server. It's `@SystemApi`, missing from the SDK stubs, so this
         * isn't marked `override`: ART dispatches it by name and signature, and `proguard-rules.pro` keeps it.
         */
        @Suppress("unused")
        fun onAmbientModeChanged(inAmbientMode: Boolean, animationDuration: Long) {
            val now = SystemClock.uptimeMillis()
            Log.i(TAG, "onAmbientModeChanged(ambient=$inAmbientMode, duration=$animationDuration ms)")
            handler.post {
                ambientCallbackSeen = true
                setAmbient(inAmbientMode, now, animationDuration)
            }
        }

        /** Logs every display state change, and stands in for the callback if it has never arrived. */
        private fun onDisplayState(state: Int) {
            Log.i(TAG, "Display state ${stateName(state)}")
            if (ambientCallbackSeen) return
            val now = SystemClock.uptimeMillis()
            when (state) {
                Display.STATE_DOZE, Display.STATE_DOZE_SUSPEND -> setAmbient(true, now, 0)
                Display.STATE_ON -> setAmbient(false, now, 0)
            }
        }

        private fun setAmbient(ambient: Boolean, now: Long, requestedMs: Long) {
            if (!timeline.onAmbientChanged(ambient, now, requestedMs)) return
            transitionFrames = 0
            transitionStartMs = now
            Log.i(TAG, "Transition to ${if (ambient) "outline" else "photo"} (AOD entry ${timeline.entries})")
            requestFrame()
        }

        private fun reload() {
            renderer?.release()
            renderer = null
            val photo = store.loadPhoto()
            val field = store.loadField()
            if (photo != null && field != null) {
                renderer = OutlineRenderer(photo, field, store.loadLook().lineColor)
                colors = WallpaperColors.fromBitmap(photo)
                mainHandler.post { notifyColorsChanged() }
            } else {
                Log.i(TAG, "No photo chosen yet; drawing black")
            }
            requestFrame()
        }

        private fun requestFrame() {
            if (frameScheduled || !surfaceReady) return
            frameScheduled = true
            val frames = choreographer ?: Choreographer.getInstance().also { choreographer = it }
            frames.postFrameCallback(frameCallback)
        }

        private fun cancelFrame() {
            if (frameScheduled) choreographer?.removeFrameCallback(frameCallback)
            frameScheduled = false
        }

        private fun drawFrame() {
            frameScheduled = false
            if (!surfaceReady) return
            val now = SystemClock.uptimeMillis()
            val animating = timeline.isAnimating(now)
            val progress = timeline.progress(now)
            val canvas = runCatching { surfaceHolder.lockHardwareCanvas() }
                .onFailure { Log.w(TAG, "No canvas to draw on", it) }
                .getOrNull() ?: return
            try {
                val current = renderer
                if (current == null) {
                    canvas.drawColor(Color.BLACK)
                } else {
                    val offset = if (progress > 0f) BurnInShift.DEFAULT.offsetFor(timeline.entries) else Offset.ZERO
                    canvas.save()
                    canvas.scale(canvas.width.toFloat() / current.width, canvas.height.toFloat() / current.height)
                    current.draw(canvas, progress, offset)
                    canvas.restore()
                }
            } catch (error: RuntimeException) {
                // Never let a drawing problem crash the process: the system would fall back to the default wallpaper.
                Log.w(TAG, "Frame failed", error)
            } finally {
                surfaceHolder.unlockCanvasAndPost(canvas)
            }
            transitionFrames++
            if (animating) {
                requestFrame()
            } else if (transitionStartMs != 0L) {
                Log.i(
                    TAG,
                    "Settled at progress $progress: $transitionFrames frames in ${now - transitionStartMs} ms",
                )
                transitionStartMs = 0L
            }
        }

        /** Runs [block] on the render thread and waits up to half a second for it. */
        private fun onRenderThreadAndWait(block: () -> Unit) {
            val done = CountDownLatch(1)
            val posted = handler.post {
                try {
                    block()
                } finally {
                    done.countDown()
                }
            }
            if (posted) done.await(WAIT_MS, TimeUnit.MILLISECONDS)
        }
    }

    private companion object {
        const val TAG = "Afterimage"
        const val WAIT_MS = 500L

        fun stateName(state: Int): String = when (state) {
            Display.STATE_OFF -> "OFF"
            Display.STATE_ON -> "ON"
            Display.STATE_DOZE -> "DOZE"
            Display.STATE_DOZE_SUSPEND -> "DOZE_SUSPEND"
            Display.STATE_ON_SUSPEND -> "ON_SUSPEND"
            else -> "STATE_$state"
        }
    }
}
