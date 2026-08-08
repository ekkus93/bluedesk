package com.augustusmachin.android_bt_kbmouse

import kotlinx.coroutines.test.runTest
import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ImeOverridesLoadStateTest {
    @Test
    fun `successful empty configuration is distinct from failure`() =
        runTest {
            val state =
                loadImeOverridesState(
                    previousOverrides = mapOf("old.pkg" to true),
                    previousLabels = mapOf("old.pkg" to "Old IME"),
                    readOverrides = { emptyMap() },
                    resolveLabels = { emptyMap() },
                )

            assertTrue(state.overrides.isEmpty())
            assertTrue(state.labels.isEmpty())
            assertNull(state.errorMessage)
        }

    @Test
    fun `successful populated configuration returns resolved labels`() =
        runTest {
            val state =
                loadImeOverridesState(
                    previousOverrides = emptyMap(),
                    previousLabels = emptyMap(),
                    readOverrides = { mapOf("pkg.a" to true, "pkg.b" to false) },
                    resolveLabels = { mapOf("pkg.a" to "IME A", "pkg.b" to "IME B") },
                )

            assertEquals(mapOf("pkg.a" to true, "pkg.b" to false), state.overrides)
            assertEquals(mapOf("pkg.a" to "IME A", "pkg.b" to "IME B"), state.labels)
            assertNull(state.errorMessage)
        }

    @Test
    fun `storage failure preserves last known good state and exposes cause`() =
        runTest {
            val previousOverrides = mapOf("known.pkg" to true)
            val previousLabels = mapOf("known.pkg" to "Known IME")
            val state =
                loadImeOverridesState(
                    previousOverrides = previousOverrides,
                    previousLabels = previousLabels,
                    readOverrides = { error("corrupt preferences") },
                    resolveLabels = { error("must not resolve after read failure") },
                )

            assertEquals(previousOverrides, state.overrides)
            assertEquals(previousLabels, state.labels)
            assertTrue(state.errorMessage!!.contains("corrupt preferences"))
        }

    @Test
    fun `package label lookup failure falls back to stable package id with diagnostic`() {
        val resolution =
            resolveImeLabel("missing.ime") {
                error("package removed")
            }

        assertEquals("missing.ime", resolution.label)
        assertTrue(resolution.diagnostic!!.contains("package removed"))
    }
}
