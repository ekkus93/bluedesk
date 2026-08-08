package com.augustusmachin.android_bt_kbmouse

/** Explicitly separates a successful empty IME override configuration from a load failure. */
internal data class ImeOverridesLoadState(
    val overrides: Map<String, Boolean>,
    val labels: Map<String, String>,
    val errorMessage: String? = null,
)

/** Result of resolving one package label; stale/missing packages fall back to the stable package id. */
internal data class ImeLabelResolution(
    val label: String,
    val diagnostic: String? = null,
)

internal suspend fun loadImeOverridesState(
    previousOverrides: Map<String, Boolean>,
    previousLabels: Map<String, String>,
    readOverrides: suspend () -> Map<String, Boolean>,
    resolveLabels: suspend (Map<String, Boolean>) -> Map<String, String>,
): ImeOverridesLoadState =
    try {
        val loaded = readOverrides()
        ImeOverridesLoadState(
            overrides = loaded,
            labels = resolveLabels(loaded),
        )
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        ImeOverridesLoadState(
            overrides = previousOverrides,
            labels = previousLabels,
            errorMessage = "Could not load IME overrides: ${e.message ?: e.javaClass.simpleName}",
        )
    }

internal fun resolveImeLabel(
    packageName: String,
    lookup: () -> CharSequence?,
): ImeLabelResolution =
    try {
        ImeLabelResolution(label = lookup()?.toString() ?: packageName)
    } catch (
        @Suppress("TooGenericExceptionCaught") e: Exception,
    ) {
        ImeLabelResolution(
            label = packageName,
            diagnostic = "IME label lookup failed for $packageName: ${e.message ?: e.javaClass.simpleName}",
        )
    }
