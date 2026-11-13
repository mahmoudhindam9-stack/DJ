package com.example

/**
 * Window presets for desktop Android environments such as FydeOS.
 * The OS window is launched at the requested bounds; the Compose frame
 * then uses the matching content width without scaling touch targets.
 */
enum class AppWindowMode(
    val windowWidthDp: Int,
    val windowHeightDp: Int,
    val contentMaxWidthDp: Int
) {
    FULLSCREEN(0, 0, Int.MAX_VALUE),
    PHONE(440, 840, 440),
    COMPACT(360, 640, 360);

    companion object {
        fun fromName(value: String?): AppWindowMode =
            values().firstOrNull { it.name == value } ?: PHONE
    }
}
