package dev.scrcpydroid.ui.mirror

import android.app.Activity
import android.app.ActivityManager
import android.content.Context
import android.os.Build
import androidx.activity.compose.BackHandler
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.animation.fadeIn
import androidx.compose.animation.fadeOut
import androidx.compose.animation.slideInVertically
import androidx.compose.animation.slideOutVertically
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.horizontalScroll
import androidx.compose.foundation.interaction.MutableInteractionSource
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.BoxWithConstraints
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.statusBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.automirrored.filled.ArrowBack
import androidx.compose.material.icons.filled.Apps
import androidx.compose.material.icons.filled.Home
import androidx.compose.material.icons.filled.LockOpen
import androidx.compose.material.icons.filled.Notifications
import androidx.compose.material.icons.filled.PowerSettingsNew
import androidx.compose.material.icons.filled.PushPin
import androidx.compose.material.icons.filled.ScreenRotation
import androidx.compose.material.icons.filled.Tune
import androidx.compose.material.icons.filled.Visibility
import androidx.compose.material.icons.filled.VisibilityOff
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.FilledIconButton
import androidx.compose.material3.FilledTonalIconButton
import androidx.compose.material3.Icon
import androidx.compose.material3.IconButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.platform.LocalView
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.hilt.navigation.compose.hiltViewModel
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import dev.scrcpydroid.ui.components.TouchInterceptor
import dev.scrcpydroid.ui.components.VideoSurface
import kotlinx.coroutines.delay

@Composable
fun MirrorScreen(
    onNavigateBack: () -> Unit,
    viewModel: MirrorViewModel = hiltViewModel()
) {
    val uiState by viewModel.uiState.collectAsStateWithLifecycle()
    var showDisconnectDialog by remember { mutableStateOf(false) }

    // Forward Android back gesture directly to the controlled remote device
    BackHandler(enabled = true) {
        viewModel.pressBack()
    }

    val menuScrollState = rememberScrollState()

    // Auto-hide controls after 4.5 seconds of inactivity (pause timer while scrolling)
    LaunchedEffect(uiState.showControls, menuScrollState.isScrollInProgress) {
        if (uiState.showControls && !menuScrollState.isScrollInProgress) {
            delay(4500)
            viewModel.setShowControls(false)
        }
    }

    val context = LocalContext.current
    val view = LocalView.current
    val activity = context as? Activity
    val activityManager = remember(context) {
        context.getSystemService(Context.ACTIVITY_SERVICE) as? ActivityManager
    }

    var isPinned by remember {
        mutableStateOf(
            activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
        )
    }

    // Keep pinning state updated when resumed
    val lifecycleOwner = LocalLifecycleOwner.current
    DisposableEffect(lifecycleOwner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_RESUME) {
                isPinned = activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
                viewModel.onAppResumed()
            }
        }
        lifecycleOwner.lifecycle.addObserver(observer)
        onDispose {
            lifecycleOwner.lifecycle.removeObserver(observer)
        }
    }

    // Hide system bars for immersive mirroring
    DisposableEffect(view) {
        val window = activity?.window
        if (window != null) {
            val insetsController = WindowCompat.getInsetsController(window, view)
            insetsController.hide(WindowInsetsCompat.Type.systemBars())
            insetsController.systemBarsBehavior = WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
        }
        onDispose {
            try {
                activity?.stopLockTask()
            } catch (e: Exception) {
                // Ignore
            }

            val window = activity?.window
            if (window != null) {
                val insetsController = WindowCompat.getInsetsController(window, view)
                insetsController.show(WindowInsetsCompat.Type.systemBars())
            }
        }
    }

    Box(
        modifier = Modifier
            .fillMaxSize()
            .background(Color.Black),
        contentAlignment = Alignment.Center
    ) {
        val density = LocalDensity.current
        val remoteWidth = uiState.deviceInfo?.width ?: 1080
        val remoteHeight = uiState.deviceInfo?.height ?: 1920

        BoxWithConstraints(
            modifier = Modifier.fillMaxSize(),
            contentAlignment = Alignment.Center
        ) {
            val containerWidthPx = with(density) { maxWidth.toPx() }
            val containerHeightPx = with(density) { maxHeight.toPx() }

            val remoteAspect = remoteWidth.toFloat() / remoteHeight.toFloat()
            val containerAspect = containerWidthPx / containerHeightPx

            val (videoWidthPx, videoHeightPx) = if (remoteAspect > containerAspect) {
                containerWidthPx to (containerWidthPx / remoteAspect)
            } else {
                (containerHeightPx * remoteAspect) to containerHeightPx
            }

            val videoWidthDp = with(density) { videoWidthPx.toDp() }
            val videoHeightDp = with(density) { videoHeightPx.toDp() }

            val offsetX = (containerWidthPx - videoWidthPx) / 2f
            val offsetY = (containerHeightPx - videoHeightPx) / 2f

            var dragStartY by remember { mutableStateOf<Float?>(null) }
            var notificationTriggered by remember { mutableStateOf(false) }

            TouchInterceptor(
                modifier = Modifier.fillMaxSize(),
                onTouchEvent = { action, localX, localY, pressure ->
                    // Map container coordinates to video surface coordinates
                    val clampedX = (localX - offsetX).coerceIn(0f, videoWidthPx)
                    val clampedY = (localY - offsetY).coerceIn(0f, videoHeightPx)
                    val scaledX = (clampedX / videoWidthPx) * remoteWidth
                    val scaledY = (clampedY / videoHeightPx) * remoteHeight

                    // Top drag detection for remote notification expansion
                    when (action) {
                        0 -> { // ACTION_DOWN
                            dragStartY = localY
                            notificationTriggered = false
                        }
                        2 -> { // ACTION_MOVE
                            val start = dragStartY
                            if (start != null && start < (containerHeightPx * 0.15f) && !notificationTriggered) {
                                if (localY - start > 50f) {
                                    viewModel.expandNotificationPanel()
                                    notificationTriggered = true
                                }
                            }
                        }
                        1, 3 -> { // ACTION_UP, ACTION_CANCEL
                            dragStartY = null
                            notificationTriggered = false
                        }
                    }

                    viewModel.onTouchEvent(
                        action = action,
                        x = scaledX,
                        y = scaledY,
                        pressure = pressure
                    )
                }
            ) {
                Box(
                    modifier = Modifier.fillMaxSize(),
                    contentAlignment = Alignment.Center
                ) {
                    VideoSurface(
                        modifier = Modifier.size(videoWidthDp, videoHeightDp),
                        onSurfaceAvailable = { surface ->
                            viewModel.startDecoding(surface)
                        },
                        onSurfaceDestroyed = {
                            viewModel.onSurfaceDestroyed()
                        }
                    )
                }
            }
        }

        // Tap trigger to show controls when hidden
        if (!uiState.showControls) {
            FilledTonalIconButton(
                onClick = { viewModel.setShowControls(true) },
                modifier = Modifier
                    .align(Alignment.BottomEnd)
                    .navigationBarsPadding()
                    .padding(20.dp)
                    .size(46.dp),
                shape = CircleShape,
                colors = IconButtonDefaults.filledTonalIconButtonColors(
                    containerColor = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.88f),
                    contentColor = MaterialTheme.colorScheme.onSurface
                )
            ) {
                Icon(
                    imageVector = Icons.Default.Tune,
                    contentDescription = "Show Controls",
                    modifier = Modifier.size(22.dp)
                )
            }
        }

        // Top Status Bar (Google Floating Pill)
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn() + slideInVertically { -it },
            exit = fadeOut() + slideOutVertically { -it },
            modifier = Modifier.align(Alignment.TopCenter)
        ) {
            Surface(
                modifier = Modifier
                    .statusBarsPadding()
                    .padding(top = 12.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(24.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.90f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 16.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically,
                    horizontalArrangement = Arrangement.spacedBy(16.dp)
                ) {
                    Text(
                        text = uiState.deviceInfo?.name?.ifBlank { "Android Device" } ?: "Android Device",
                        style = MaterialTheme.typography.titleSmall,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Bold
                    )

                    Row(
                        verticalAlignment = Alignment.CenterVertically,
                        horizontalArrangement = Arrangement.spacedBy(10.dp)
                    ) {
                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.surfaceContainer
                        ) {
                            Text(
                                text = "${remoteWidth}x${remoteHeight}",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onSurfaceVariant,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }

                        Surface(
                            shape = RoundedCornerShape(8.dp),
                            color = MaterialTheme.colorScheme.primaryContainer
                        ) {
                            Text(
                                text = "${uiState.fps} FPS",
                                style = MaterialTheme.typography.labelSmall,
                                color = MaterialTheme.colorScheme.onPrimaryContainer,
                                fontWeight = FontWeight.Bold,
                                modifier = Modifier.padding(horizontal = 6.dp, vertical = 2.dp)
                            )
                        }
                    }
                }
            }
        }

        // Bottom Floating Action Buttons (Google Camera / Media Pill Style)
        AnimatedVisibility(
            visible = uiState.showControls,
            enter = fadeIn() + slideInVertically { it },
            exit = fadeOut() + slideOutVertically { it },
            modifier = Modifier.align(Alignment.BottomCenter)
        ) {
            Surface(
                modifier = Modifier
                    .navigationBarsPadding()
                    .padding(bottom = 24.dp, start = 16.dp, end = 16.dp),
                shape = RoundedCornerShape(32.dp),
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.92f),
                tonalElevation = 8.dp
            ) {
                Row(
                    modifier = Modifier
                        .horizontalScroll(menuScrollState)
                        .padding(horizontal = 14.dp, vertical = 10.dp),
                    horizontalArrangement = Arrangement.spacedBy(10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    // Back
                    FilledIconButton(
                        onClick = { viewModel.pressBack() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.AutoMirrored.Filled.ArrowBack,
                            contentDescription = "Back",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Home
                    FilledIconButton(
                        onClick = { viewModel.pressHome() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Home,
                            contentDescription = "Home",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // App Switch
                    FilledIconButton(
                        onClick = { viewModel.pressAppSwitch() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Apps,
                            contentDescription = "App Switch",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Notifications
                    FilledIconButton(
                        onClick = { viewModel.expandNotificationPanel() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.Notifications,
                            contentDescription = "Notifications",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Pin / Unpin
                    FilledIconButton(
                        onClick = {
                            val currentlyPinned = activityManager?.lockTaskModeState != ActivityManager.LOCK_TASK_MODE_NONE
                            if (currentlyPinned) {
                                try {
                                    activity?.stopLockTask()
                                    isPinned = false
                                } catch (e: Exception) {
                                    android.util.Log.w("MirrorScreen", "stopLockTask failed", e)
                                }
                            } else {
                                try {
                                    activity?.startLockTask()
                                    isPinned = true
                                } catch (e: Exception) {
                                    android.util.Log.w("MirrorScreen", "startLockTask failed", e)
                                }
                            }
                        },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (isPinned) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (isPinned) MaterialTheme.colorScheme.onPrimaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PushPin,
                            contentDescription = if (isPinned) "Unpin App" else "Pin App",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Remote Screen On/Off (Display Power)
                    FilledIconButton(
                        onClick = { viewModel.toggleDisplayPower() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = if (!uiState.isDisplayPowerOn) MaterialTheme.colorScheme.tertiaryContainer else MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = if (!uiState.isDisplayPowerOn) MaterialTheme.colorScheme.onTertiaryContainer else MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = if (uiState.isDisplayPowerOn) Icons.Default.Visibility else Icons.Default.VisibilityOff,
                            contentDescription = if (uiState.isDisplayPowerOn) "Turn Remote Screen Off" else "Turn Remote Screen On",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Rotate
                    FilledIconButton(
                        onClick = { viewModel.rotateDevice() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.ScreenRotation,
                            contentDescription = "Rotate",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Unlock Device
                    FilledIconButton(
                        onClick = { viewModel.unlockDevice() },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.surfaceContainer,
                            contentColor = MaterialTheme.colorScheme.onSurface
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.LockOpen,
                            contentDescription = "Unlock Device",
                            modifier = Modifier.size(20.dp)
                        )
                    }

                    // Disconnect
                    FilledIconButton(
                        onClick = { showDisconnectDialog = true },
                        modifier = Modifier.size(44.dp),
                        colors = IconButtonDefaults.filledIconButtonColors(
                            containerColor = MaterialTheme.colorScheme.errorContainer,
                            contentColor = MaterialTheme.colorScheme.onErrorContainer
                        )
                    ) {
                        Icon(
                            imageVector = Icons.Default.PowerSettingsNew,
                            contentDescription = "Disconnect",
                            modifier = Modifier.size(20.dp)
                        )
                    }
                }
            }
        }

        // Reconnection banner overlay when disconnected or error occurs
        if (!uiState.isConnected || uiState.errorMessage != null) {
            Surface(
                modifier = Modifier
                    .align(Alignment.TopCenter)
                    .statusBarsPadding()
                    .padding(top = 16.dp)
                    .clickable { viewModel.autoReconnect() },
                shape = CircleShape,
                color = MaterialTheme.colorScheme.surfaceContainerHighest.copy(alpha = 0.95f),
                tonalElevation = 6.dp
            ) {
                Row(
                    modifier = Modifier.padding(horizontal = 18.dp, vertical = 10.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    CircularProgressIndicator(
                        modifier = Modifier.size(16.dp),
                        strokeWidth = 2.dp,
                        color = MaterialTheme.colorScheme.primary
                    )
                    Spacer(modifier = Modifier.width(10.dp))
                    Text(
                        text = uiState.errorMessage ?: "Reconnecting...",
                        style = MaterialTheme.typography.bodyMedium,
                        color = MaterialTheme.colorScheme.onSurface,
                        fontWeight = FontWeight.Medium
                    )
                }
            }
        }
    }

    if (showDisconnectDialog) {
        AlertDialog(
            onDismissRequest = { showDisconnectDialog = false },
            shape = RoundedCornerShape(28.dp),
            containerColor = MaterialTheme.colorScheme.surfaceContainerHigh,
            title = {
                Text(
                    text = "Disconnect Session",
                    fontWeight = FontWeight.Bold,
                    style = MaterialTheme.typography.titleLarge
                )
            },
            text = {
                Text(
                    text = "Are you sure you want to disconnect from this device?",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            },
            confirmButton = {
                Button(
                    onClick = {
                        showDisconnectDialog = false
                        try {
                            activity?.stopLockTask()
                        } catch (e: Exception) {
                            // Ignore
                        }
                        viewModel.disconnect()
                        onNavigateBack()
                    },
                    shape = RoundedCornerShape(20.dp),
                    colors = androidx.compose.material3.ButtonDefaults.buttonColors(
                        containerColor = MaterialTheme.colorScheme.error,
                        contentColor = MaterialTheme.colorScheme.onError
                    )
                ) {
                    Text(
                        text = "Disconnect",
                        fontWeight = FontWeight.Bold
                    )
                }
            },
            dismissButton = {
                TextButton(onClick = { showDisconnectDialog = false }) {
                    Text(text = "Cancel")
                }
            }
        )
    }
}
