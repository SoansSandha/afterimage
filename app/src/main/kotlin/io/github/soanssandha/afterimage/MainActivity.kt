package io.github.soanssandha.afterimage

import android.app.WallpaperManager
import android.content.ActivityNotFoundException
import android.content.ComponentName
import android.content.Intent
import android.os.Bundle
import android.widget.Toast
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.runtime.getValue
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import io.github.soanssandha.afterimage.ui.MainActions
import io.github.soanssandha.afterimage.ui.MainScreen
import io.github.soanssandha.afterimage.ui.MainViewModel
import io.github.soanssandha.afterimage.ui.theme.AfterimageTheme
import io.github.soanssandha.afterimage.wallpaper.OutlineWallpaperService

/** The settings screen. Photos come through the system photo picker, so the app needs no storage permission. */
class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    private val pickPhoto = registerForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        uri?.let(viewModel::choose)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            AfterimageTheme {
                val state by viewModel.state.collectAsStateWithLifecycle()
                MainScreen(state = state, actions = actions)
            }
        }
    }

    private val actions = MainActions(
        choosePhoto = {
            pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
        },
        setWallpaper = ::setWallpaper,
        changeDetail = { viewModel.onDetailChange(it) },
        chooseDetail = { viewModel.onDetailChosen() },
        changeLineColor = { viewModel.onLineColorChange(it) },
    )

    /** Applies the preview. If Afterimage isn't the wallpaper yet, the system picker then sets it; else it's done. */
    private fun setWallpaper() {
        viewModel.setWallpaper { needsPicker ->
            if (needsPicker) {
                openWallpaperPicker()
            } else {
                Toast.makeText(this, R.string.wallpaper_updated, Toast.LENGTH_SHORT).show()
            }
        }
    }

    override fun onResume() {
        super.onResume()
        viewModel.refresh()
    }

    /** Opens the system's live-wallpaper preview for Afterimage, where the user picks lock screen, home or both. */
    private fun openWallpaperPicker() {
        val intent = Intent(WallpaperManager.ACTION_CHANGE_LIVE_WALLPAPER).putExtra(
            WallpaperManager.EXTRA_LIVE_WALLPAPER_COMPONENT,
            ComponentName(this, OutlineWallpaperService::class.java),
        )
        try {
            startActivity(intent)
        } catch (_: ActivityNotFoundException) {
            Toast.makeText(this, R.string.error_no_picker, Toast.LENGTH_LONG).show()
        }
    }
}
