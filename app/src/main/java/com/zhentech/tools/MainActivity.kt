package com.zhentech.tools

import android.Manifest
import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.calculateEndPadding
import androidx.compose.foundation.layout.calculateStartPadding
import androidx.compose.foundation.layout.consumeWindowInsets
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.lazy.grid.GridCells
import androidx.compose.foundation.lazy.grid.LazyVerticalGrid
import androidx.compose.foundation.lazy.grid.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.Scaffold
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalLayoutDirection
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import com.zhentech.tools.ui.theme.ToolsTheme

class MainActivity : ComponentActivity() {
    private lateinit var streamingController: HfpStreamingController
    private var startRequestInProgress = false

    private val permissionLauncher = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { grants ->
        if (grants.values.all { it }) launchMediaProjectionRequest()
    }

    private val mediaProjectionLauncher = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult(),
    ) { result ->
        startRequestInProgress = false
        val projectionData = result.data
        if (result.resultCode == Activity.RESULT_OK && projectionData != null) {
            streamingController.start(result.resultCode, projectionData)
        }
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        streamingController = HfpStreamingController(AndroidHfpStreamingGateway(applicationContext))
        setContent {
            ToolsTheme {
                val state by streamingController.state.collectAsState()
                ToolsGrid(
                    streamingState = state,
                    onStreamingTileClick = ::onStreamingTileClick,
                )
            }
        }
    }

    private fun onStreamingTileClick() {
        if (streamingController.state.value == StreamingState.On) {
            streamingController.stop()
            return
        }
        if (startRequestInProgress || !streamingController.hasAvailableHfpDevice()) return
        if (hasRequiredPermissions()) {
            launchMediaProjectionRequest()
        } else {
            permissionLauncher.launch(REQUIRED_PERMISSIONS)
        }
    }

    private fun hasRequiredPermissions(): Boolean = REQUIRED_PERMISSIONS.all {
        checkSelfPermission(it) == PackageManager.PERMISSION_GRANTED
    }

    private fun launchMediaProjectionRequest() {
        if (startRequestInProgress || !streamingController.hasAvailableHfpDevice()) return
        startRequestInProgress = true
        val projectionManager = getSystemService(MediaProjectionManager::class.java)
        mediaProjectionLauncher.launch(projectionManager.createScreenCaptureIntent())
    }

    companion object {
        private val REQUIRED_PERMISSIONS = arrayOf(
            Manifest.permission.BLUETOOTH_CONNECT,
            Manifest.permission.RECORD_AUDIO,
        )
    }
}

@Composable
internal fun ToolsGrid(
    streamingState: StreamingState,
    onStreamingTileClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val layoutDirection = LocalLayoutDirection.current
    Scaffold(
        modifier = modifier.fillMaxSize(),
    ) { innerPadding ->
        LazyVerticalGrid(
            columns = GridCells.Fixed(3),
            modifier = Modifier
                .fillMaxSize()
                .consumeWindowInsets(innerPadding)
                .semantics { testTag = GRID_TEST_TAG },
            contentPadding = PaddingValues(
                start = innerPadding.calculateStartPadding(layoutDirection) + GRID_PADDING,
                top = innerPadding.calculateTopPadding() + GRID_PADDING,
                end = innerPadding.calculateEndPadding(layoutDirection) + GRID_PADDING,
                bottom = innerPadding.calculateBottomPadding() + GRID_PADDING,
            ),
        ) {
            items(items = listOf(streamingState), key = { "hfp-streamer" }) { state ->
                HfpStreamingTile(state = state, onClick = onStreamingTileClick)
            }
        }
    }
}

@Composable
private fun HfpStreamingTile(
    state: StreamingState,
    onClick: () -> Unit,
) {
    val isStreaming = state == StreamingState.On
    val containerColor = if (isStreaming) {
        MaterialTheme.colorScheme.primary
    } else {
        MaterialTheme.colorScheme.secondaryContainer
    }
    val contentColor = if (isStreaming) {
        MaterialTheme.colorScheme.onPrimary
    } else {
        MaterialTheme.colorScheme.onSecondaryContainer
    }
    val label = if (isStreaming) "Streaming on" else "Streaming"

    androidx.compose.foundation.layout.Box(
        modifier = Modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .semantics {
                testTag = HFP_STREAMING_TILE_TEST_TAG
                contentDescription = if (isStreaming) "Stop streaming" else "Start streaming"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        androidx.compose.foundation.layout.Column(
            horizontalAlignment = Alignment.CenterHorizontally,
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_bluetooth),
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = label,
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.Medium,
                textAlign = TextAlign.Center,
            )
        }
    }
}

internal const val GRID_TEST_TAG = "toolsGrid"
internal const val HFP_STREAMING_TILE_TEST_TAG = "hfpStreamingTile"
private val GRID_PADDING = 16.dp

@Preview(showBackground = true)
@Composable
private fun ToolsGridPreview() {
    ToolsTheme(dynamicColor = false) {
        ToolsGrid(streamingState = StreamingState.Off, onStreamingTileClick = {})
    }
}
