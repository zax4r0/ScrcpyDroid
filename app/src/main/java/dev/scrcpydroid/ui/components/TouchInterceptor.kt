package dev.scrcpydroid.ui.components

import android.view.MotionEvent
import androidx.compose.foundation.layout.Box
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.ui.ExperimentalComposeUiApi
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.pointer.pointerInteropFilter

@OptIn(ExperimentalComposeUiApi::class)
@Composable
fun TouchInterceptor(
    modifier: Modifier = Modifier,
    onTouchEvent: (action: Int, x: Float, y: Float, pressure: Float) -> Unit,
    content: @Composable () -> Unit
) {
    val currentOnTouchEvent by rememberUpdatedState(onTouchEvent)

    Box(
        modifier = modifier.pointerInteropFilter { motionEvent ->
            when (motionEvent.actionMasked) {
                MotionEvent.ACTION_DOWN,
                MotionEvent.ACTION_MOVE,
                MotionEvent.ACTION_UP,
                MotionEvent.ACTION_CANCEL -> {
                    currentOnTouchEvent(
                        motionEvent.actionMasked,
                        motionEvent.x,
                        motionEvent.y,
                        motionEvent.pressure
                    )
                    true
                }
                else -> false
            }
        }
    ) {
        content()
    }
}
