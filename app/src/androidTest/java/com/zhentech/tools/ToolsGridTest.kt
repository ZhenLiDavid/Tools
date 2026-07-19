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
import org.junit.Assert.assertTrue
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

    @Test
    fun workSimTileIsSecondAndOpensCompleteEditor() {
        var savedSchedule: WorkSimSchedule? = null
        composeRule.setContent {
            var showEditor by remember { mutableStateOf(false) }
            ToolsTheme {
                ToolsGrid(
                    streamingState = StreamingState.Off,
                    onStreamingTileClick = {},
                    workSimState = WorkSimUiState.preview(),
                    onWorkSimTileClick = { showEditor = true },
                    showWorkSimEditor = showEditor,
                    onWorkSimEditorDismiss = { showEditor = false },
                    onWorkSimScheduleSave = {
                        savedSchedule = it
                        showEditor = false
                    },
                )
            }
        }

        val streamingBounds = composeRule.onNodeWithTag(HFP_STREAMING_TILE_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot
        val workSimBounds = composeRule.onNodeWithTag(WORK_SIM_TILE_TEST_TAG)
            .fetchSemanticsNode().boundsInRoot
        assertEquals(streamingBounds.width, workSimBounds.width, 0.1f)
        assertEquals(workSimBounds.width, workSimBounds.height, 0.1f)
        assertEquals(streamingBounds.top, workSimBounds.top, 0.1f)
        assertTrue(workSimBounds.left > streamingBounds.left)

        composeRule.onNodeWithTag(WORK_SIM_TILE_TEST_TAG).performClick()

        composeRule.onNodeWithTag(WORK_SIM_EDITOR_TEST_TAG).assertExists()
        composeRule.onNodeWithText("Work eSIM").assertExists()
        composeRule.onNodeWithText("9:00 AM").assertExists()
        composeRule.onNodeWithText("5:00 PM").assertExists()
        composeRule.onNodeWithTag(WORK_SIM_SAVE_TEST_TAG).performClick()
        composeRule.runOnIdle {
            assertEquals(1, savedSchedule?.slotIndex)
            assertEquals(5, savedSchedule?.days?.size)
        }
    }
}
