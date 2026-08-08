package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class BackendLifecycleControllerTest {
    @Test
    fun `service start failure rolls back without bind`() {
        val ops = FakeOps(startResult = LifecycleOperationResult.Failure("start failed"))
        val fixture = Fixture(ops)

        assertFalse(fixture.controller.start(BackendMode.CLASSIC_HID))

        assertEquals(listOf("start:CLASSIC_HID", "clear:CLASSIC_HID", "stop:CLASSIC_HID", "reset"), ops.events)
        assertEquals(BackendFailureCode.SERVICE_START_FAILED, fixture.failedState().failure.code)
    }

    @Test
    fun `bind failure after successful start always stops service`() {
        val ops = FakeOps(bindResult = LifecycleOperationResult.Failure("bind returned false"))
        val fixture = Fixture(ops)

        assertFalse(fixture.controller.start(BackendMode.CLASSIC_HID))

        assertEquals(
            listOf(
                "start:CLASSIC_HID",
                "bind:CLASSIC_HID",
                "clear:CLASSIC_HID",
                "unbind:CLASSIC_HID",
                "stop:CLASSIC_HID",
                "reset",
            ),
            ops.events,
        )
        assertEquals(BackendFailureCode.SERVICE_BIND_FAILED, fixture.failedState().failure.code)
    }

    @Test
    fun `successful bind is not ready until initialization completes`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)

        assertTrue(fixture.controller.start(BackendMode.CLASSIC_HID))
        assertEquals(
            BackendRuntimeState.Starting(BackendMode.CLASSIC_HID, BackendStartupStage.BINDING_SERVICE),
            fixture.coordinator.state,
        )

        assertTrue(fixture.controller.beginListenerInstallation(BackendMode.CLASSIC_HID))
        assertTrue(fixture.controller.listenerInstalled(BackendMode.CLASSIC_HID))
        assertTrue(fixture.controller.senderInstalled(BackendMode.CLASSIC_HID))
        assertTrue(fixture.controller.markReady(BackendMode.CLASSIC_HID))
        assertTrue(fixture.coordinator.state is BackendRuntimeState.Ready)
    }

    @Test
    fun `backend init failure after bind performs full rollback`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)
        fixture.controller.start(BackendMode.BLE_HOGP)
        fixture.controller.beginListenerInstallation(BackendMode.BLE_HOGP)

        fixture.controller.failInitialization(BackendMode.BLE_HOGP, "advertiser unavailable")

        assertTrue(ops.events.contains("unbind:BLE_HOGP"))
        assertTrue(ops.events.contains("stop:BLE_HOGP"))
        assertEquals(BackendFailureCode.BACKEND_INIT_FAILED, fixture.failedState().failure.code)
    }

    @Test
    fun `switch stops source completely before target starts`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)
        ready(fixture, BackendMode.CLASSIC_HID)
        ops.events.clear()

        assertTrue(fixture.controller.switchTo(BackendMode.BLE_HOGP))

        val stopIndex = ops.events.indexOf("stop:CLASSIC_HID")
        val targetStartIndex = ops.events.indexOf("start:BLE_HOGP")
        assertTrue(stopIndex >= 0)
        assertTrue(targetStartIndex > stopIndex)
        assertFalse(ops.liveServices.contains(BackendMode.CLASSIC_HID))
        assertTrue(ops.liveServices.contains(BackendMode.BLE_HOGP))
    }

    @Test
    fun `switch target failure leaves no backend live`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)
        ready(fixture, BackendMode.CLASSIC_HID)
        ops.failNextStart = true

        assertFalse(fixture.controller.switchTo(BackendMode.BLE_HOGP))

        assertTrue(ops.liveServices.isEmpty())
        assertTrue(fixture.coordinator.state is BackendRuntimeState.Failed)
    }

    @Test
    fun `repeated target switch while starting does not start duplicate service`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)

        assertTrue(fixture.controller.switchTo(BackendMode.BLE_HOGP))
        assertTrue(fixture.controller.switchTo(BackendMode.BLE_HOGP))

        assertEquals(1, ops.events.count { it == "start:BLE_HOGP" })
        assertEquals(setOf(BackendMode.BLE_HOGP), ops.liveServices)
    }

    @Test
    fun `unexpected service loss invalidates runtime and local state`() {
        val ops = FakeOps()
        val fixture = Fixture(ops)
        ready(fixture, BackendMode.CLASSIC_HID)
        ops.events.clear()

        fixture.controller.unexpectedServiceLoss(BackendMode.CLASSIC_HID)

        assertEquals(listOf("clear:CLASSIC_HID", "reset"), ops.events)
        assertEquals(BackendFailureCode.SERVICE_LOST, fixture.failedState().failure.code)
    }

    private fun ready(fixture: Fixture, mode: BackendMode) {
        fixture.controller.start(mode)
        fixture.controller.beginListenerInstallation(mode)
        fixture.controller.listenerInstalled(mode)
        fixture.controller.senderInstalled(mode)
        fixture.controller.markReady(mode)
    }

    private class Fixture(val ops: FakeOps) {
        val coordinator = BackendRuntimeCoordinator()
        val failures = mutableListOf<String>()
        val published = mutableListOf<BackendRuntimeState>()
        val controller =
            BackendLifecycleController(
                coordinator,
                ops,
                publish = { published += it },
                surfaceFailure = { failures += it },
            )

        fun failedState(): BackendRuntimeState.Failed = coordinator.state as BackendRuntimeState.Failed
    }

    private class FakeOps(
        private val startResult: LifecycleOperationResult = LifecycleOperationResult.Success,
        private val bindResult: LifecycleOperationResult = LifecycleOperationResult.Success,
    ) : BackendLifecycleOperations {
        val events = mutableListOf<String>()
        val liveServices = mutableSetOf<BackendMode>()
        var failNextStart = false

        override fun startService(mode: BackendMode): LifecycleOperationResult {
            events += "start:$mode"
            if (failNextStart) {
                failNextStart = false
                return LifecycleOperationResult.Failure("target start failed")
            }
            if (startResult is LifecycleOperationResult.Success) liveServices += mode
            return startResult
        }

        override fun bindService(mode: BackendMode): LifecycleOperationResult {
            events += "bind:$mode"
            return bindResult
        }

        override fun releaseHeldInput(mode: BackendMode) {
            events += "release:$mode"
        }

        override fun clearSenderAndListener(mode: BackendMode) {
            events += "clear:$mode"
        }

        override fun unbindService(mode: BackendMode) {
            events += "unbind:$mode"
        }

        override fun stopService(mode: BackendMode) {
            events += "stop:$mode"
            liveServices -= mode
        }

        override fun resetLocalState() {
            events += "reset"
        }
    }
}
