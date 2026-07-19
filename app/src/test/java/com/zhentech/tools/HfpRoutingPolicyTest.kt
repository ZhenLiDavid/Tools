package com.zhentech.tools

import android.media.AudioManager
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class HfpRoutingPolicyTest {
    @Test
    fun routingStartsOnlyWhenNoCallOrCommunicationSessionOwnsAudio() {
        assertTrue(HfpRoutingPolicy.canStart(AudioManager.MODE_NORMAL))
        assertFalse(HfpRoutingPolicy.canStart(AudioManager.MODE_RINGTONE))
        assertFalse(HfpRoutingPolicy.canStart(AudioManager.MODE_IN_CALL))
        assertFalse(HfpRoutingPolicy.canStart(AudioManager.MODE_IN_COMMUNICATION))
    }

    @Test
    fun activeRouteStopsWheneverItsCommunicationModeIsLost() {
        assertTrue(
            HfpRoutingPolicy.shouldStopForModeChange(
                routeConfirmed = true,
                ownsAudioMode = true,
                mode = AudioManager.MODE_NORMAL,
            ),
        )
        assertFalse(
            HfpRoutingPolicy.shouldStopForModeChange(
                routeConfirmed = true,
                ownsAudioMode = true,
                mode = AudioManager.MODE_IN_COMMUNICATION,
            ),
        )
    }

    @Test
    fun callInterruptionNeverGetsOverwrittenByModeRestoration() {
        assertFalse(
            HfpRoutingPolicy.shouldRestorePreviousMode(
                ownsAudioMode = true,
                callInterrupted = true,
                currentMode = AudioManager.MODE_IN_COMMUNICATION,
            ),
        )
        assertTrue(
            HfpRoutingPolicy.shouldRestorePreviousMode(
                ownsAudioMode = true,
                callInterrupted = false,
                currentMode = AudioManager.MODE_IN_COMMUNICATION,
            ),
        )
    }
}
