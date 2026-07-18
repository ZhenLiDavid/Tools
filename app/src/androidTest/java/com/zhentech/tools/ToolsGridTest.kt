package com.zhentech.tools

import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.test.onNodeWithTag
import androidx.compose.ui.test.onNodeWithText
import androidx.compose.ui.test.performClick
import androidx.compose.ui.test.junit4.v2.createComposeRule
import com.zhentech.tools.ui.theme.ToolsTheme
import org.junit.Assert.assertEquals
import org.junit.Rule
import org.junit.Test

class ToolsGridTest {
    @get:Rule
    val composeRule = createComposeRule()

    @Test
    fun tileIsSquareAndTogglesItsVisibleState() {
        composeRule.setContent {
            var state by remember { mutableStateOf(StreamingState.Off) }
            ToolsTheme {
                ToolsGrid(
                    streamingState = state,
                    onStreamingTileClick = { state = StreamingState.On },
                )
            }
        }

        val tileSize = composeRule.onNodeWithTag(HFP_STREAMING_TILE_TEST_TAG)
            .fetchSemanticsNode()
            .size
        assertEquals(tileSize.width, tileSize.height)
        composeRule.onNodeWithText("Streaming").assertExists()

        composeRule.onNodeWithTag(HFP_STREAMING_TILE_TEST_TAG).performClick()

        composeRule.onNodeWithText("Streaming on").assertExists()
    }
}
