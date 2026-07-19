package com.zhentech.tools

import androidx.test.ext.junit.runners.AndroidJUnit4
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith

@RunWith(AndroidJUnit4::class)
class CommunicationRouteAnchorInstrumentedTest {
    @Test
    fun silentAnchorProvidesActivePlaybackForBackgroundModeOwnership() {
        val anchor = CommunicationRouteAnchor.create()

        try {
            anchor.start()

            assertTrue(anchor.isPlaying)
        } finally {
            anchor.close()
        }
    }
}
