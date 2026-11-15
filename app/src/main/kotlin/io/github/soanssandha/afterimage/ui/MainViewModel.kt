package io.github.soanssandha.afterimage.ui

import android.app.Application
import android.net.Uri
import android.util.Log
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import io.github.soanssandha.afterimage.data.OutlineMaker
import io.github.soanssandha.afterimage.data.WallpaperStore
import io.github.soanssandha.afterimage.render.OutlineRenderer
import io.github.soanssandha.afterimage.wallpaper.AmbientComponent
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext

/** The settings screen: whether the wallpaper may run on AOD, choosing a photo, and the preview. */
class MainViewModel(application: Application) : AndroidViewModel(application) {

    private val store = WallpaperStore(application)
    private val maker = OutlineMaker(application, store)
    private val mutableState = MutableStateFlow(MainUiState())

    val state: StateFlow<MainUiState> = mutableState.asStateFlow()

    init {
        refresh()
        loadPreview()
    }

    /** Re-reads whether the app is a system app, and syncs the wallpaper service. Called on every resume. */
    fun refresh() {
        mutableState.update { it.copy(systemApp = AmbientComponent.sync(getApplication())) }
    }

    /** Crops [uri] to the screen, makes its outline, stores both and refreshes the preview. */
    fun choose(uri: Uri) {
        if (state.value.busy) return
        mutableState.update { it.copy(busy = true, error = null) }
        viewModelScope.launch {
            try {
                val result = withContext(Dispatchers.Default) { maker.make(uri) }
                mutableState.update { it.copy(litPercent = (result.litFraction * 100).toInt()) }
                loadPreview()
            } catch (cancelled: CancellationException) {
                throw cancelled
            } catch (error: Exception) {
                Log.w(TAG, "Couldn't make an outline", error)
                mutableState.update { it.copy(error = error.message ?: error.javaClass.simpleName) }
            } finally {
                mutableState.update { it.copy(busy = false) }
            }
        }
    }

    private fun loadPreview() {
        viewModelScope.launch {
            val renderer = withContext(Dispatchers.IO) {
                val photo = store.loadPhoto()
                val field = store.loadField()
                if (photo != null && field != null) OutlineRenderer(photo, field) else null
            }
            mutableState.update { it.copy(preview = renderer) }
        }
    }

    private companion object {
        const val TAG = "Afterimage"
    }
}
