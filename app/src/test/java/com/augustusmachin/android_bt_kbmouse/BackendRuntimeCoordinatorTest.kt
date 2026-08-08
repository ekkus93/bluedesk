package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendRuntimeCoordinatorTest {
    @Test
    fun `stopped starts and advances to ready`() {
        val coordinator = BackendRuntimeCoordinator()

        assertTrue(coordinator.beginStart(BackendMode.CLASSIC_HID) is BackendTransitionResult.Applied)
        assertEquals(
            BackendRuntimeState.Starting(BackendMode.CLASSIC_HID, BackendStartupStage.PERMISSION_VALIDATION),
            coordinator.state,
        )

        coordinator.advanceStart(BackendMode.CLASSIC_HID, BackendStartupStage.BINDING_SERVICE)
        assertEquals(
            BackendRuntimeState.Starting(BackendMode.CLASSIC_HID, BackendStartupStage.BINDING_SERVICE),
            coordinator.state,
        )

        assertTrue(coordinator.markReady(BackendMode.CLASSIC_HID) is BackendTransitionResult.Applied)
        assertEquals(
            BackendRuntimeState.Ready(BackendMode.CLASSIC_HID, BackendCapabilitySets.classic),
            coordinator.state,
        )
    }

    @Test
    fun `starting can fail with typed stage information retained by caller`() {
        val coordinator = BackendRuntimeCoordinator()
        coordinator.beginStart(BackendMode.BLE_HOGP)
        coordinator.advanceStart(BackendMode.BLE_HOGP, BackendStartupStage.BINDING_SERVICE)

        coordinator.fail(
            BackendMode.BLE_HOGP,
            BackendFailure(BackendFailureCode.SERVICE_BIND_FAILED, "bind returned false"),
        )

        assertEquals(
            BackendRuntimeState.Failed(
                BackendMode.BLE_HOGP,
                BackendFailure(BackendFailureCode.SERVICE_BIND_FAILED, "bind returned false"),
            ),
            coordinator.state,
        )
    }

    @Test
    fun `ready stops before becoming stopped`() {
        val coordinator = readyClassic()

        coordinator.beginStop()
        assertEquals(BackendRuntimeState.Stopping(BackendMode.CLASSIC_HID), coordinator.state)
        coordinator.markStopped()
        assertEquals(BackendRuntimeState.Stopped, coordinator.state)
    }

    @Test
    fun `service loss from ready becomes failed`() {
        val coordinator = readyClassic()

        coordinator.serviceLost(BackendMode.CLASSIC_HID)

        val failed = coordinator.state as BackendRuntimeState.Failed
        assertEquals(BackendMode.CLASSIC_HID, failed.backend)
        assertEquals(BackendFailureCode.SERVICE_LOST, failed.failure.code)
    }

    @Test
    fun `conflicting second backend start is rejected`() {
        val coordinator = BackendRuntimeCoordinator()
        coordinator.beginStart(BackendMode.CLASSIC_HID)

        val result = coordinator.beginStart(BackendMode.BLE_HOGP)

        assertTrue(result is BackendTransitionResult.Rejected)
        assertEquals(BackendMode.CLASSIC_HID, coordinator.currentLiveBackend)
    }

    @Test
    fun `classic and BLE capabilities differ explicitly`() {
        assertTrue(BackendCapabilitySets.classic.discovery)
        assertTrue(BackendCapabilitySets.classic.verticalScroll)
        assertEquals(false, BackendCapabilitySets.bleHogp.discovery)
        assertEquals(false, BackendCapabilitySets.bleHogp.verticalScroll)
        assertTrue(BackendCapabilitySets.bleHogp.middleClick)
    }

    private fun readyClassic(): BackendRuntimeCoordinator =
        BackendRuntimeCoordinator().apply {
            beginStart(BackendMode.CLASSIC_HID)
            markReady(BackendMode.CLASSIC_HID)
        }
}
