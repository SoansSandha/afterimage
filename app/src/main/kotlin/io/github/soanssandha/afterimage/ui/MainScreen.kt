package io.github.soanssandha.afterimage.ui

import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.safeDrawingPadding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.FilledTonalButton
import androidx.compose.material3.LinearProgressIndicator
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Slider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.draw.drawBehind
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.unit.dp
import io.github.soanssandha.afterimage.R
import io.github.soanssandha.afterimage.core.burnin.Offset
import io.github.soanssandha.afterimage.render.OutlineRenderer

/** The one settings screen: status, choosing a photo, setting the wallpaper, and a preview of the transition. */
@Composable
fun MainScreen(state: MainUiState, onChoosePhoto: () -> Unit, onSetWallpaper: () -> Unit) {
    Surface(modifier = Modifier.fillMaxSize(), color = MaterialTheme.colorScheme.background) {
        Column(
            modifier = Modifier
                .safeDrawingPadding()
                .verticalScroll(rememberScrollState())
                .padding(16.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(stringResource(R.string.app_name), style = MaterialTheme.typography.headlineMedium)
            StatusCard(state)
            Row(horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                Button(onClick = onChoosePhoto, enabled = !state.busy) {
                    Text(stringResource(R.string.action_choose_photo))
                }
                FilledTonalButton(
                    onClick = onSetWallpaper,
                    enabled = state.systemApp && state.preview != null && !state.busy,
                ) {
                    Text(stringResource(R.string.action_set_wallpaper))
                }
            }
            if (state.busy) {
                Text(stringResource(R.string.working))
                LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
            }
            state.error?.let { message ->
                Text(stringResource(R.string.error_photo, message), color = MaterialTheme.colorScheme.error)
            }
            state.preview?.let { Preview(it) }
        }
    }
}

@Composable
private fun StatusCard(state: MainUiState) {
    Card(modifier = Modifier.fillMaxWidth()) {
        Column(modifier = Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
            Text(stringResource(R.string.status_title), style = MaterialTheme.typography.titleMedium)
            Text(
                stringResource(
                    if (state.systemApp) R.string.status_system_app_ok else R.string.status_system_app_missing,
                ),
            )
            Text(
                when {
                    state.litPercent != null -> stringResource(R.string.status_photo_ready, state.litPercent)
                    state.preview == null -> stringResource(R.string.status_photo_missing)
                    else -> ""
                },
            )
        }
    }
}

/** The wallpaper's own renderer, scaled into a phone-shaped box, with a slider for the transition's progress. */
@Composable
private fun Preview(renderer: OutlineRenderer) {
    var progress by remember { mutableFloatStateOf(1f) }
    Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
        Text(stringResource(R.string.preview_title), style = MaterialTheme.typography.titleMedium)
        Box(modifier = Modifier.fillMaxWidth(), contentAlignment = Alignment.Center) {
            Box(
                modifier = Modifier
                    .fillMaxWidth(PREVIEW_WIDTH)
                    .aspectRatio(renderer.width.toFloat() / renderer.height)
                    .clip(RoundedCornerShape(16.dp))
                    .drawBehind {
                        drawIntoCanvas { canvas ->
                            val native = canvas.nativeCanvas
                            native.save()
                            native.scale(size.width / renderer.width, size.height / renderer.height)
                            renderer.draw(native, progress, Offset.ZERO)
                            native.restore()
                        }
                    },
            )
        }
        Text(stringResource(R.string.preview_progress), style = MaterialTheme.typography.labelLarge)
        Slider(value = progress, onValueChange = { progress = it })
    }
}

private const val PREVIEW_WIDTH = 0.6f
