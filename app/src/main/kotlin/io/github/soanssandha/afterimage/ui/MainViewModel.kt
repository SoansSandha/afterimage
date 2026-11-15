package io.github.soanssandha.afterimage.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.soanssandha.afterimage.core.config.LineColor
import io.github.soanssandha.afterimage.data.OutlineMaker
import io.github.soanssandha.afterimage.data.WallpaperEditor
import io.github.soanssandha.afterimage.data.WallpaperStore
import io.github.soanssandha.afterimage.render.OutlineRenderer
import io.github.soanssandha.afterimage.ui.MainUiState.Work
import io.github.soanssandha.afterimage.wallpaper.ActiveWallpaper
import io.github.soanssandha.afterimage.wallpaper.AmbientComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.roundToInt

/**
 * The settings screen. Choosing a photo or moving the Detail slider only changes the preview (the store's draft);
 * [setWallpaper] is what changes the wallpaper. Work queues rather than being dropped (a photo picked while the screen
 * is still loading is kept), and the editing thread takes it in order.
 */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val editor = WallpaperEditor(WallpaperStore(application), OutlineMaker(application))

    // The editor takes one call at a time, and each call builds on the last.
    private val editing = Dispatchers.Default.limitedParallelism(1)
    private val mutableState = MutableStateFlow(MainUiState())

    /** The detail level of the outline in the preview. */
    private var shownDetail = mutableState.value.detail

    /** Work started and not yet finished. Main thread only. */
    private var unfinished = 0

    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        refresh()
        edit(Work.LOADING) { editor.load() }
    }

    /** Re-reads whether the app is a system app and the lock screen's wallpaper; syncs the service. On every resume. */
    fun refresh() {
        val app = getApplication<Application>()
        val systemApp = AmbientComponent.sync(app)
        mutableState.update {
            it.copy(systemApp = systemApp, active = systemApp && ActiveWallpaper.isOnLockScreen(app))
        }
    }

    /** Makes [uri]'s outline at the shown detail level, for the preview. The wallpaper doesn't change. */
    fun choose(uri: Uri) {
        edit(Work.CHOOSING) { editor.choose(uri) }
    }

    /** The Detail slider moved; snapped to [DETAIL_STEPS]. Nothing is remade until [onDetailChosen]. */
    fun onDetailChange(value: Float) {
        mutableState.update { it.copy(detail = (value * DETAIL_STEPS).roundToInt() / DETAIL_STEPS.toFloat()) }
    }

    /** The Detail slider was let go: remake the preview's outline at that level. */
    fun onDetailChosen() {
        val detail = state.value.detail
        if (detail == shownDetail) return
        edit(Work.DETAIL) { editor.changeDetail(detail) }
    }

    /** The line colour switch flipped: the preview shows it at once; the wallpaper only after [setWallpaper]. */
    fun onLineColorChange(color: LineColor) {
        if (color == state.value.lineColor) return
        mutableState.update { it.copy(lineColor = color) }
        edit(Work.LOOK) { editor.changeLineColor(color) }
    }

    /**
     * Applies the preview to the wallpaper, then calls [onDone]: with true if Afterimage isn't on the lock screen yet,
     * so the system picker must be opened to put it there.
     */
    fun setWallpaper(onDone: (needsPicker: Boolean) -> Unit) {
        val needsPicker = !state.value.active
        start(Work.SETTING) {
            val applied = attempt { withContext(editing) { editor.apply() } } != null
            if (applied) {
                mutableState.update { it.copy(pending = false) }
                onDone(needsPicker)
            }
        }
    }

    /** Runs [block] on the editing thread and shows what it returns; after a failure, shows what's stored. */
    private fun edit(work: Work, block: () -> WallpaperEditor.Shown?) {
        start(work) {
            val done = attempt { display(withContext(editing) { block()?.withRenderer() }) }
            if (done == null) attempt { display(withContext(editing) { editor.load()?.withRenderer() }) }
        }
    }

    /** Marks [work] as running until it and everything queued before it has finished. Main thread only. */
    private fun start(work: Work, body: suspend () -> Unit) {
        unfinished++
        mutableState.update { it.copy(work = work, error = null) }
        viewModelScope.launch {
            try {
                body()
            } finally {
                unfinished--
                if (unfinished == 0) mutableState.update { it.copy(work = null) }
            }
        }
    }

    /** [body]'s result, or null after showing its error. */
    private suspend fun <T : Any> attempt(body: suspend () -> T): T? = try {
        body()
    } catch (cancelled: CancellationException) {
        throw cancelled
    } catch (error: Exception) {
        Log.w(TAG, "Edit failed", error)
        mutableState.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
        null
    }

    // Built on the editing thread: the renderer decodes the field into a bitmap.
    private fun WallpaperEditor.Shown.withRenderer() = this to OutlineRenderer(photo, field, look.lineColor)

    private fun display(shown: Pair<WallpaperEditor.Shown, OutlineRenderer>?) {
        val (outline, renderer) = shown ?: return mutableState.update { it.copy(preview = null, pending = false) }
        shownDetail = outline.look.detail
        mutableState.update {
            it.copy(
                preview = renderer,
                pending = outline.pending,
                detail = outline.look.detail,
                lineColor = outline.look.lineColor,
                litPercent = outline.litFraction?.let { lit -> (lit * 100).roundToInt() },
            )
        }
    }

    private companion object {
        const val TAG = "Afterimage"

        /** The Detail slider stops at every 1/20, so a level can be named and found again. */
        const val DETAIL_STEPS = 20
    }
}
