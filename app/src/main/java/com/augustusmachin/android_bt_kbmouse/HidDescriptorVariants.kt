// HID descriptors are documented with an inline comment on each byte argument;
// keep those inline rather than splitting every byte onto its own line.
@file:Suppress("ktlint:standard:discouraged-comment-location")

package com.augustusmachin.android_bt_kbmouse

/**
 * Combined keyboard+mouse HID report descriptors.
 *
 * Both variants use:
 *   Report ID 1 — keyboard (8-byte input: mods + reserved + 6 keys; 1-byte LED output)
 *   Report ID 2 — mouse (3-byte SIMPLE or 5-byte FULL)
 *
 * SIMPLE — no scroll wheels; best Windows compatibility.
 * FULL   — adds vertical scroll (Usage Wheel) and horizontal scroll (Consumer AC Pan).
 */
object HidDescriptorVariants {
    const val REPORT_ID_KEYBOARD: Byte = 1
    const val REPORT_ID_MOUSE: Byte = 2

    // ── SIMPLE: keyboard (ID=1, with LEDs) + mouse (ID=2, buttons+X+Y only) ──────────────────
    val SIMPLE: ByteArray =
        byteArrayOf(
            // ── Keyboard collection ──────────────────────────────────────────────
            0x05, 0x01, // Usage Page (Generic Desktop)
            0x09, 0x06, // Usage (Keyboard)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x85.toByte(), 0x01, //   Report ID (1)
            // Modifier bits (8 × 1-bit: Left Ctrl … Right GUI)
            0x05, 0x07, //   Usage Page (Keyboard)
            0x19.toByte(), 0xE0.toByte(), //   Usage Min (Left Ctrl = 0xE0)
            0x29.toByte(), 0xE7.toByte(), //   Usage Max (Right GUI = 0xE7)
            0x15, 0x00, //   Logical Min (0)
            0x25, 0x01, //   Logical Max (1)
            0x75, 0x01, //   Report Size (1)
            0x95.toByte(), 0x08, //   Report Count (8)
            0x81.toByte(), 0x02, //   Input (Data, Var, Abs)
            // Reserved byte
            0x95.toByte(), 0x01, //   Report Count (1)
            0x75, 0x08, //   Report Size (8)
            0x81.toByte(), 0x01, //   Input (Const)
            // LED output — 5 LEDs (Num/Caps/Scroll/Compose/Kana) + 3-bit padding
            0x95.toByte(), 0x05, //   Report Count (5)
            0x75, 0x01, //   Report Size (1)
            0x05, 0x08, //   Usage Page (LEDs)
            0x19.toByte(), 0x01, //   Usage Min (Num Lock = 1)
            0x29.toByte(), 0x05, //   Usage Max (Kana = 5)
            0x91.toByte(), 0x02, //   Output (Data, Var, Abs)
            0x95.toByte(), 0x01, //   Report Count (1)
            0x75, 0x03, //   Report Size (3)
            0x91.toByte(), 0x01, //   Output (Const) — padding
            // 6-key array
            0x95.toByte(), 0x06, //   Report Count (6)
            0x75, 0x08, //   Report Size (8)
            0x15, 0x00, //   Logical Min (0)
            0x25, 0x65, //   Logical Max (101)
            0x05, 0x07, //   Usage Page (Keyboard)
            0x19.toByte(), 0x00, //   Usage Min (0)
            0x29.toByte(), 0x65, //   Usage Max (101)
            0x81.toByte(), 0x00, //   Input (Data, Array, Abs)
            0xC0.toByte(), // End Collection
            // ── Mouse collection — no scroll ─────────────────────────────────────
            0x05, 0x01, // Usage Page (Generic Desktop)
            0x09, 0x02, // Usage (Mouse)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x85.toByte(), 0x02, //   Report ID (2)
            0x09, 0x01, //   Usage (Pointer)
            0xA1.toByte(), 0x00, //   Collection (Physical)
            // 3 buttons + 5-bit padding
            0x05, 0x09, //     Usage Page (Buttons)
            0x19.toByte(), 0x01, //     Usage Min (Button 1)
            0x29.toByte(), 0x03, //     Usage Max (Button 3)
            0x15, 0x00, //     Logical Min (0)
            0x25, 0x01, //     Logical Max (1)
            0x75, 0x01, //     Report Size (1)
            0x95.toByte(), 0x03, //     Report Count (3)
            0x81.toByte(), 0x02, //     Input (Data, Var, Abs)
            0x75, 0x05, //     Report Size (5)
            0x95.toByte(), 0x01, //     Report Count (1)
            0x81.toByte(), 0x01, //     Input (Const) — padding
            // X and Y axes (relative, 8-bit signed)
            0x05, 0x01, //     Usage Page (Generic Desktop)
            0x09, 0x30, //     Usage (X)
            0x09, 0x31, //     Usage (Y)
            0x15, 0x81.toByte(), //     Logical Min (-127)
            0x25, 0x7F, //     Logical Max (127)
            0x75, 0x08, //     Report Size (8)
            0x95.toByte(), 0x02, //     Report Count (2)
            0x81.toByte(), 0x06, //     Input (Data, Var, Rel)
            0xC0.toByte(), //   End Collection (Physical)
            0xC0.toByte(), // End Collection (Application)
        )

    // ── FULL: keyboard (ID=1, with LEDs) + mouse (ID=2, buttons+X+Y+wheelV+wheelH) ──────────
    val FULL: ByteArray =
        byteArrayOf(
            // ── Keyboard collection — identical to SIMPLE ────────────────────────
            0x05, 0x01,
            0x09, 0x06,
            0xA1.toByte(), 0x01,
            0x85.toByte(), 0x01, //   Report ID (1)
            0x05, 0x07,
            0x19.toByte(), 0xE0.toByte(),
            0x29.toByte(), 0xE7.toByte(),
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x08,
            0x81.toByte(), 0x02,
            0x95.toByte(), 0x01,
            0x75, 0x08,
            0x81.toByte(), 0x01,
            0x95.toByte(), 0x05,
            0x75, 0x01,
            0x05, 0x08,
            0x19.toByte(), 0x01,
            0x29.toByte(), 0x05,
            0x91.toByte(), 0x02,
            0x95.toByte(), 0x01,
            0x75, 0x03,
            0x91.toByte(), 0x01,
            0x95.toByte(), 0x06,
            0x75, 0x08,
            0x15, 0x00,
            0x25, 0x65,
            0x05, 0x07,
            0x19.toByte(), 0x00,
            0x29.toByte(), 0x65,
            0x81.toByte(), 0x00,
            0xC0.toByte(),
            // ── Mouse collection — with vertical + horizontal scroll ──────────────
            0x05, 0x01, // Usage Page (Generic Desktop)
            0x09, 0x02, // Usage (Mouse)
            0xA1.toByte(), 0x01, // Collection (Application)
            0x85.toByte(), 0x02, //   Report ID (2)
            0x09, 0x01, //   Usage (Pointer)
            0xA1.toByte(), 0x00, //   Collection (Physical)
            // 3 buttons + 5-bit padding
            0x05, 0x09,
            0x19.toByte(), 0x01,
            0x29.toByte(), 0x03,
            0x15, 0x00,
            0x25, 0x01,
            0x75, 0x01,
            0x95.toByte(), 0x03,
            0x81.toByte(), 0x02,
            0x75, 0x05,
            0x95.toByte(), 0x01,
            0x81.toByte(), 0x01,
            // X, Y, Wheel (vertical) — 3 × relative 8-bit
            0x05, 0x01,
            0x09, 0x30, //     Usage (X)
            0x09, 0x31, //     Usage (Y)
            0x09, 0x38, //     Usage (Wheel — vertical scroll)
            0x15, 0x81.toByte(),
            0x25, 0x7F,
            0x75, 0x08,
            0x95.toByte(), 0x03, //     Report Count (3)
            0x81.toByte(), 0x06,
            // Horizontal scroll — Consumer AC Pan (usage 0x0238), relative 8-bit
            0x05, 0x0C, //     Usage Page (Consumer)
            0x0A, 0x38, 0x02, //     Usage (AC Pan = 0x0238, 2-byte encoding)
            0x15, 0x81.toByte(),
            0x25, 0x7F,
            0x75, 0x08,
            0x95.toByte(), 0x01, //     Report Count (1)
            0x81.toByte(), 0x06,
            0xC0.toByte(), //   End Collection (Physical)
            0xC0.toByte(), // End Collection (Application)
        )

    fun select(simplified: Boolean): ByteArray = if (simplified) SIMPLE else FULL
}
