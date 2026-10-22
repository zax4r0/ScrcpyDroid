package dev.scrcpydroid.ui.components

import android.view.Surface
import android.view.SurfaceHolder
import android.view.SurfaceView
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.Modifier
import androidx.compose.ui.viewinterop.AndroidView

@Composable
fun VideoSurface(
    modifier: Modifier = Modifier,
    onSurfaceAvailable: (Surface) -> Unit,
    onSurfaceDestroyed: () -> Unit
) {
    val currentOnSurfaceAvailable by rememberUpdatedState(onSurfaceAvailable)
    val currentOnSurfaceDestroyed by rememberUpdatedState(onSurfaceDestroyed)

    AndroidView(
        modifier = modifier,
        factory = { context ->
            SurfaceView(context).apply {
                holder.addCallback(object : SurfaceHolder.Callback {
                    override fun surfaceCreated(holder: SurfaceHolder) {
                        currentOnSurfaceAvailable(holder.surface)
                    }

                    override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
                    }

                    override fun surfaceDestroyed(holder: SurfaceHolder) {
                        currentOnSurfaceDestroyed()
                    }
                })

                if (holder.surface.isValid) {
                    currentOnSurfaceAvailable(holder.surface)
                }
            }
        }
    )
}
