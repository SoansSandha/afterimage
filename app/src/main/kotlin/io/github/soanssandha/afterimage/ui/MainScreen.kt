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
import androidx.compose.material3.Switch
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
import io.github.soanssandha.afterimage.core.config.LineColor
import io.github.soanssandha.afterimage.render.OutlineRenderer
import io.github.soanssandha.afterimage.ui.MainUiState.Work

/**
 * The one settings screen: status, choosing a photo, setting the wallpaper, and a preview with the Detail slider and
 * the transition. Choosing a photo and changing the detail only change the preview; Set as wallpaper applies it.
 */
@Composable
fun MainScreen(state: MainUiState, actions: MainActions) {
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
                Button(onClick = actions.choosePhoto, enabled = state.work == null) {
                    Text(stringResource(R.string.action_choose_photo))
                }
                FilledTonalButton(onClick = actions.setWallpaper, enabled = state.canSetWallpaper) {
                    Text(stringResource(R.string.action_set_wallpaper))
                }
            }
            if (state.work == Work.CHOOSING) Working(R.string.working_photo)
            state.error?.let { message ->
                Text(stringResource(R.string.error_outline, message), color = MaterialTheme.colorScheme.error)
            }
            state.preview?.let { Preview(it, state, actions) }
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
                stringResource(
                    when {
                        state.preview == null -> R.string.status_photo_missing
                        state.pending -> R.string.status_pending
                        state.active -> R.string.status_active
                        else -> R.string.status_not_set
                    },
                ),
            )
            state.litPercent?.let { Text(stringResource(R.string.status_lit, it)) }
        }
    }
}

/**
 * The wallpaper's own renderer, scaled into a phone-shaped box. Under it, the Detail slider (remakes the outline when
 * let go), the line colour switch, and the transition's progress (drawing only).
 */
@Composable
private fun Preview(renderer: OutlineRenderer, state: MainUiState, actions: MainActions) {
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

        Text(stringResource(R.string.detail_title), style = MaterialTheme.typography.labelLarge)
        Slider(
            value = state.detail,
            onValueChange = actions.changeDetail,
            onValueChangeFinished = actions.chooseDetail,
            enabled = state.work == null,
            steps = DETAIL_SLIDER_STEPS,
        )
        Row(modifier = Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween) {
            Text(stringResource(R.string.detail_fewer), style = MaterialTheme.typography.bodySmall)
            Text(stringResource(R.string.detail_more), style = MaterialTheme.typography.bodySmall)
        }
        if (state.work == Work.DETAIL) Working(R.string.working_detail)

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(16.dp),
            verticalAlignment = Alignment.CenterVertically,
        ) {
            Column(modifier = Modifier.weight(1f)) {
                Text(stringResource(R.string.line_color_title), style = MaterialTheme.typography.labelLarge)
                Text(
                    stringResource(
                        when (state.lineColor) {
                            LineColor.PHOTO -> R.string.line_color_photo
                            LineColor.WHITE -> R.string.line_color_white
                        },
                    ),
                    style = MaterialTheme.typography.bodySmall,
                )
            }
            Switch(
                checked = state.lineColor == LineColor.PHOTO,
                onCheckedChange = { colored ->
                    actions.changeLineColor(if (colored) LineColor.PHOTO else LineColor.WHITE)
                },
                enabled = state.work == null,
            )
        }

        Text(stringResource(R.string.preview_progress), style = MaterialTheme.typography.labelLarge)
        Slider(value = progress, onValueChange = { progress = it })
    }
}

@Composable
private fun Working(label: Int) {
    Column(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        Text(stringResource(label))
        LinearProgressIndicator(modifier = Modifier.fillMaxWidth())
    }
}

private const val PREVIEW_WIDTH = 0.6f

// 19 stops between the ends: every 1/20, matching MainViewModel's snapping.
private const val DETAIL_SLIDER_STEPS = 19
