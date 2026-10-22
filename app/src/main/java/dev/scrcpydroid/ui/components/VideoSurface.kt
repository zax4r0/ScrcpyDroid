package dev.scrcpydroid.ui.components

import android.graphics.SurfaceTexture
import android.view.Surface
import android.view.TextureView
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
            TextureView(context).apply {
                surfaceTextureListener = object : TextureView.SurfaceTextureListener {
                    private var currentSurface: Surface? = null

                    override fun onSurfaceTextureAvailable(st: SurfaceTexture, width: Int, height: Int) {
                        currentSurface?.release()
                        val surface = Surface(st)
                        currentSurface = surface
                        currentOnSurfaceAvailable(surface)
                    }

                    override fun onSurfaceTextureSizeChanged(st: SurfaceTexture, width: Int, height: Int) {
                    }

                    override fun onSurfaceTextureDestroyed(st: SurfaceTexture): Boolean {
                        currentOnSurfaceDestroyed()
                        currentSurface?.release()
                        currentSurface = null
                        return true
                    }

                    override fun onSurfaceTextureUpdated(st: SurfaceTexture) {
                    }
                }

                if (isAvailable && surfaceTexture != null) {
                    val surface = Surface(surfaceTexture)
                    currentOnSurfaceAvailable(surface)
                }
            }
        }
    )
}
