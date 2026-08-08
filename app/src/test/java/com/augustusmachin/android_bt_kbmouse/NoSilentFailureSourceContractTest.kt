package com.augustusmachin.android_bt_kbmouse

import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test
import java.io.File

/**
 * Guardrails for the exact silent-failure patterns removed by the post-Fix3 hardening pass.
 * These are intentionally narrow source contracts, not a general Kotlin linter.
 */
class NoSilentFailureSourceContractTest {
    private val criticalSources =
        listOf(
            "com/augustusmachin/android_bt_kbmouse/MainActivity.kt",
            "com/augustusmachin/android_bt_kbmouse/BluetoothService.kt",
            "com/augustusmachin/android_bt_kbmouse/BleHogpService.kt",
            "com/augustusmachin/android_bt_kbmouse/BluetoothHidTransport.kt",
            "com/augustusmachin/android_bt_kbmouse/store/Middleware.kt",
            "com/augustusmachin/android_bt_kbmouse/store/CommandContract.kt",
        )

    @Test
    fun `critical runtime has no empty generic exception catches`() {
        val emptyGenericCatch = Regex("catch\\s*\\(\\s*_\\s*:\\s*Exception\\s*\\)\\s*\\{\\s*}")
        criticalSources.forEach { path ->
            val text = source(path)
            assertFalse("Empty generic catch in $path", emptyGenericCatch.containsMatchIn(text))
        }
    }

    @Test
    fun `critical runtime does not use runCatchingLogged to swallow state changes`() {
        criticalSources.forEach { path ->
            assertFalse(
                "runCatchingLogged is prohibited in critical runtime: $path",
                source(path).contains("runCatchingLogged("),
            )
        }
    }

    @Test
    fun `critical runtime has no nullable sender dispatch`() {
        criticalSources.forEach { path ->
            assertFalse("Nullable sender dispatch is prohibited in $path", source(path).contains("sender?."))
        }
    }

    @Test
    fun `production input runtime contains no blocking sleeps`() {
        val productionRoot = locateProductionRoot()
        val offenders =
            productionRoot.walkTopDown()
                .filter { it.isFile && it.extension == "kt" }
                .filter { it.readText().contains("Thread.sleep(") }
                .map { it.relativeTo(productionRoot).path }
                .toList()
        assertTrue("Production Thread.sleep calls: $offenders", offenders.isEmpty())
    }

    @Test
    fun `KeySender contract contains no default no-op operation bodies`() {
        val contract = source("com/augustusmachin/android_bt_kbmouse/store/CommandContract.kt")
        assertFalse(contract.contains("fun execute(command: KeyCommand): CommandResult ="))
        assertTrue(contract.contains("fun execute(command: KeyCommand): CommandResult"))
    }

    private fun source(relative: String): String {
        val root = locateProductionRoot()
        val file = File(root, relative)
        assertTrue("Source file missing: ${file.path}", file.isFile)
        return file.readText()
    }

    private fun locateProductionRoot(): File {
        val candidates = listOf(File("src/main/java"), File("app/src/main/java"))
        return candidates.firstOrNull { it.isDirectory }
            ?: error("Could not locate production source root from ${File(".").absolutePath}")
    }
}
