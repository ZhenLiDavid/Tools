package com.zhentech.tools

import android.app.TimePickerDialog
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.aspectRatio
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.FilterChip
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Switch
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clip
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.semantics.Role
import androidx.compose.ui.semantics.contentDescription
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.testTag
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import java.time.DayOfWeek
import java.time.LocalTime
import java.time.ZonedDateTime
import java.time.format.DateTimeFormatter
import java.time.format.TextStyle
import java.util.Locale

@Composable
internal fun WorkSimTile(
    state: WorkSimUiState,
    onClick: () -> Unit,
    modifier: Modifier = Modifier,
) {
    val isOn = state.isPoweredOn ?: state.schedule?.isActiveAt(ZonedDateTime.now())
    val (containerColor, contentColor) = workSimTileColors(state, isOn)
    val status = workSimTileStatus(state, isOn)

    Box(
        modifier = modifier
            .fillMaxWidth()
            .aspectRatio(1f)
            .clip(RoundedCornerShape(24.dp))
            .background(containerColor)
            .semantics {
                testTag = WORK_SIM_TILE_TEST_TAG
                contentDescription = "Configure Work SIM. $status"
            }
            .clickable(role = Role.Button, onClick = onClick),
        contentAlignment = Alignment.Center,
    ) {
        Column(
            modifier = Modifier.padding(horizontal = 8.dp),
            horizontalAlignment = Alignment.CenterHorizontally,
            verticalArrangement = Arrangement.spacedBy(2.dp),
        ) {
            Icon(
                painter = painterResource(R.drawable.ic_sim_card),
                contentDescription = null,
                tint = contentColor,
            )
            Text(
                text = "Work SIM",
                color = contentColor,
                style = MaterialTheme.typography.labelLarge,
                fontWeight = FontWeight.SemiBold,
                textAlign = TextAlign.Center,
            )
            Text(
                text = status,
                color = contentColor,
                style = MaterialTheme.typography.labelSmall,
                textAlign = TextAlign.Center,
                maxLines = 2,
            )
        }
    }
}

@Composable
private fun workSimTileColors(
    state: WorkSimUiState,
    isOn: Boolean?,
): Pair<Color, Color> = when {
    state.backendStatus == WorkSimBackendStatus.AdbUnavailable ||
        state.backendStatus == WorkSimBackendStatus.Failed ->
        MaterialTheme.colorScheme.errorContainer to MaterialTheme.colorScheme.onErrorContainer
    isOn == true -> MaterialTheme.colorScheme.primary to MaterialTheme.colorScheme.onPrimary
    state.isConfigured ->
        MaterialTheme.colorScheme.tertiaryContainer to MaterialTheme.colorScheme.onTertiaryContainer
    else ->
        MaterialTheme.colorScheme.secondaryContainer to MaterialTheme.colorScheme.onSecondaryContainer
}

private fun workSimTileStatus(state: WorkSimUiState, isOn: Boolean?): String {
    val schedule = state.schedule
    return when {
        !state.isConfigured -> "Set schedule"
        state.backendStatus == WorkSimBackendStatus.Applying -> "Updating…"
        state.backendStatus == WorkSimBackendStatus.AdbUnavailable -> "Reconnect ADB"
        state.backendStatus == WorkSimBackendStatus.Failed -> "Switch failed"
        schedule?.enabled == false -> "Schedule paused"
        state.nextTransition != null -> {
            val stateLabel = if (isOn == true) "On" else "Off"
            "$stateLabel · ${formatTransition(state.nextTransition)}"
        }
        else -> if (isOn == true) "On" else "Off"
    }
}

private fun formatTransition(transition: WorkSimTransition): String {
    val now = ZonedDateTime.now(transition.at.zone)
    val time = transition.at.format(DateTimeFormatter.ofPattern("h:mm a"))
    return if (transition.isSoonAfter(now)) {
        "until $time"
    } else {
        val day = transition.at.dayOfWeek.getDisplayName(TextStyle.SHORT, Locale.getDefault())
        "$day $time"
    }
}

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
internal fun WorkSimEditorSheet(
    state: WorkSimUiState,
    onDismiss: () -> Unit,
    onSave: (WorkSimSchedule) -> Unit,
) {
    val defaultSim = state.availableSims.firstOrNull(SimDescriptor::isEmbedded)
        ?: state.availableSims.getOrNull(1)
        ?: SimDescriptor(1, "Work eSIM", true)
    var draft by remember(state.schedule, state.availableSims) {
        mutableStateOf(
            state.schedule ?: WorkSimSchedule.Default.copy(
                slotIndex = defaultSim.slotIndex,
                simLabel = defaultSim.label,
                isEmbedded = defaultSim.isEmbedded,
            ),
        )
    }
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        modifier = Modifier.semantics { testTag = WORK_SIM_EDITOR_TEST_TAG },
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(start = 24.dp, end = 24.dp, bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp),
        ) {
            Text(
                text = "Work SIM",
                style = MaterialTheme.typography.headlineSmall,
                fontWeight = FontWeight.SemiBold,
            )

            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .clickable {
                        draft = draft.copy(enabled = !draft.enabled)
                    },
                verticalAlignment = Alignment.CenterVertically,
                horizontalArrangement = Arrangement.SpaceBetween,
            ) {
                Column {
                    Text("Weekly schedule", style = MaterialTheme.typography.titleMedium)
                    Text(
                        if (draft.enabled) "Automatic switching is on" else "Automatic switching is paused",
                        style = MaterialTheme.typography.bodySmall,
                    )
                }
                Switch(
                    checked = draft.enabled,
                    onCheckedChange = { draft = draft.copy(enabled = it) },
                )
            }

            EditorSectionLabel("SIM")
            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp),
            ) {
                state.availableSims.ifEmpty { listOf(defaultSim) }.forEach { sim ->
                    FilterChip(
                        selected = draft.slotIndex == sim.slotIndex,
                        onClick = {
                            draft = draft.copy(
                                slotIndex = sim.slotIndex,
                                simLabel = sim.label,
                                isEmbedded = sim.isEmbedded,
                            )
                        },
                        label = { Text(sim.label) },
                    )
                }
            }

            EditorSectionLabel("Days")
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.spacedBy(4.dp),
            ) {
                DayOfWeek.entries.forEach { day ->
                    FilterChip(
                        selected = day in draft.days,
                        onClick = {
                            val updatedDays = draft.days.toMutableSet().apply {
                                if (!add(day)) remove(day)
                            }
                            draft = draft.copy(days = updatedDays)
                        },
                        label = { Text(DAY_LABELS.getValue(day)) },
                        modifier = Modifier
                            .weight(1f)
                            .semantics {
                                contentDescription =
                                    day.getDisplayName(TextStyle.FULL, Locale.getDefault())
                            },
                    )
                }
            }

            EditorSectionLabel("Hours")
            TimeRangeEditor(
                start = draft.start,
                end = draft.end,
                onStartChanged = { draft = draft.copy(start = it) },
                onEndChanged = { draft = draft.copy(end = it) },
            )

            if (!draft.isValid) {
                Text(
                    text = "Choose at least one day and two different times.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            } else if (!state.preciseSchedulingAvailable) {
                Text(
                    text = "Android will ask for exact-alarm access when you save.",
                    color = MaterialTheme.colorScheme.onSurfaceVariant,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            if (state.backendStatus == WorkSimBackendStatus.AdbUnavailable) {
                Text(
                    text = "ADB Wi-Fi needs to be reconnected before the SIM can switch.",
                    color = MaterialTheme.colorScheme.error,
                    style = MaterialTheme.typography.bodySmall,
                )
            }

            Spacer(Modifier.height(2.dp))
            Button(
                onClick = { onSave(draft) },
                enabled = draft.isValid,
                modifier = Modifier
                    .fillMaxWidth()
                    .semantics { testTag = WORK_SIM_SAVE_TEST_TAG },
            ) {
                Text("Save schedule")
            }
        }
    }
}

@Composable
private fun EditorSectionLabel(text: String) {
    Text(
        text = text,
        style = MaterialTheme.typography.labelLarge,
        color = MaterialTheme.colorScheme.onSurfaceVariant,
    )
}

@Composable
private fun TimeRangeEditor(
    start: LocalTime,
    end: LocalTime,
    onStartChanged: (LocalTime) -> Unit,
    onEndChanged: (LocalTime) -> Unit,
) {
    val context = LocalContext.current
    val formatter = remember { DateTimeFormatter.ofPattern("h:mm a") }
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp),
        verticalAlignment = Alignment.CenterVertically,
    ) {
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onStartChanged(LocalTime.of(hour, minute)) },
                    start.hour,
                    start.minute,
                    false,
                ).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(start.format(formatter))
        }
        Text("to", style = MaterialTheme.typography.bodyMedium)
        OutlinedButton(
            onClick = {
                TimePickerDialog(
                    context,
                    { _, hour, minute -> onEndChanged(LocalTime.of(hour, minute)) },
                    end.hour,
                    end.minute,
                    false,
                ).show()
            },
            modifier = Modifier.weight(1f),
        ) {
            Text(end.format(formatter))
        }
    }
}

private val DAY_LABELS = mapOf(
    DayOfWeek.MONDAY to "M",
    DayOfWeek.TUESDAY to "T",
    DayOfWeek.WEDNESDAY to "W",
    DayOfWeek.THURSDAY to "T",
    DayOfWeek.FRIDAY to "F",
    DayOfWeek.SATURDAY to "S",
    DayOfWeek.SUNDAY to "S",
)

internal const val WORK_SIM_TILE_TEST_TAG = "workSimTile"
internal const val WORK_SIM_EDITOR_TEST_TAG = "workSimEditor"
internal const val WORK_SIM_SAVE_TEST_TAG = "workSimSave"
