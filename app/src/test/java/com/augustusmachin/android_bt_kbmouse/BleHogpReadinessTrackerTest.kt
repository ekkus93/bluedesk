package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BleHogpReadinessTrackerTest {
    @Test
    fun `full registration and advertising success reaches ready`() {
        val tracker = BleHogpReadinessTracker()
        tracker.beginGattRegistration(listOf("hid", "battery", "device-info"))

        assertEquals("hid", tracker.nextServiceToRegister())
        assertTrue(tracker.onAddServiceImmediate("hid", true))
        assertFalse(tracker.onServiceAdded("hid", true))
        assertEquals("battery", tracker.nextServiceToRegister())
        assertTrue(tracker.onAddServiceImmediate("battery", true))
        assertFalse(tracker.onServiceAdded("battery", true))
        assertEquals("device-info", tracker.nextServiceToRegister())
        assertTrue(tracker.onAddServiceImmediate("device-info", true))
        assertTrue(tracker.onServiceAdded("device-info", true))

        tracker.beginAdvertising()
        assertEquals(
            BleHogpStartupState.Starting(BleHogpStartupStage.STARTING_ADVERTISING),
            tracker.state,
        )
        tracker.advertisingSucceeded()
        assertEquals(BleHogpStartupState.Ready, tracker.state)
    }

    @Test
    fun `immediate addService rejection fails closed`() {
        val tracker = BleHogpReadinessTracker()
        tracker.beginGattRegistration(listOf("hid"))
        tracker.nextServiceToRegister()

        assertFalse(tracker.onAddServiceImmediate("hid", false))
        assertTrue(tracker.state is BleHogpStartupState.Failed)
    }

    @Test
    fun `service callback failure fails closed`() {
        val tracker = BleHogpReadinessTracker()
        tracker.beginGattRegistration(listOf("hid"))
        tracker.nextServiceToRegister()
        tracker.onAddServiceImmediate("hid", true)

        assertFalse(tracker.onServiceAdded("hid", false))
        assertTrue(tracker.state is BleHogpStartupState.Failed)
    }

    @Test
    fun `advertising cannot begin before service callbacks complete`() {
        val tracker = BleHogpReadinessTracker()
        tracker.beginGattRegistration(listOf("hid"))
        tracker.nextServiceToRegister()
        tracker.onAddServiceImmediate("hid", true)

        tracker.beginAdvertising()

        assertTrue(tracker.state is BleHogpStartupState.Failed)
    }

    @Test
    fun `failure remains durable for late observer query`() {
        val tracker = BleHogpReadinessTracker()
        tracker.fail("advertiser unavailable")

        assertEquals(BleHogpStartupState.Failed("advertiser unavailable"), tracker.state)
        tracker.advance(BleHogpStartupStage.OPENING_GATT_SERVER)
        assertEquals(BleHogpStartupState.Failed("advertiser unavailable"), tracker.state)
    }

    @Test
    fun `advertising success without advertising stage fails`() {
        val tracker = BleHogpReadinessTracker()

        tracker.advertisingSucceeded()

        assertTrue(tracker.state is BleHogpStartupState.Failed)
    }
}
