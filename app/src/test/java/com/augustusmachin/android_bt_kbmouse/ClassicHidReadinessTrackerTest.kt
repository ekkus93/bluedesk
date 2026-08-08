package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class ClassicHidReadinessTrackerTest {
    @Test
    fun `accepted request waits for callback`() {
        val published = mutableListOf<ClassicHidStartupState>()
        val tracker = ClassicHidReadinessTracker { published += it }

        tracker.registrationRequestAccepted()

        assertEquals(ClassicHidStartupState.WaitingForRegistrationCallback, tracker.state)
        assertEquals(ClassicHidStartupState.WaitingForRegistrationCallback, published.last())
    }

    @Test
    fun `accepted request and success callback reaches ready`() {
        val tracker = ClassicHidReadinessTracker { }
        tracker.registrationRequestAccepted()

        tracker.registrationCallback(true)

        assertEquals(ClassicHidStartupState.Ready, tracker.state)
    }

    @Test
    fun `accepted request and false callback fails`() {
        val tracker = ClassicHidReadinessTracker { }
        tracker.registrationRequestAccepted()

        tracker.registrationCallback(false)

        assertTrue(tracker.state is ClassicHidStartupState.Failed)
    }

    @Test
    fun `immediate request failure never becomes ready`() {
        val tracker = ClassicHidReadinessTracker { }

        tracker.registrationRequestFailed("registerApp returned false")

        assertEquals(ClassicHidStartupState.Failed("registerApp returned false"), tracker.state)
    }

    @Test
    fun `success callback without accepted request fails closed`() {
        val tracker = ClassicHidReadinessTracker { }

        tracker.registrationCallback(true)

        assertTrue(tracker.state is ClassicHidStartupState.Failed)
    }
}
