package com.hycer.minepulse.ui.theme

import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.SpanStyle
import androidx.compose.ui.text.buildAnnotatedString
import androidx.compose.ui.text.font.FontWeight

val DarkBackground = Color(0xFF0E0E10)
val DarkSurface = Color(0xFF18181B)
val DarkSurfaceVariant = Color(0xFF242429)
val DarkCardBorder = Color(0xFF2E2E36)

val McGreen = Color(0xFF4CAF50)
val McGreenLight = Color(0xFF81C784)
val McGreenDark = Color(0xFF2E7D32)

val TextPrimary = Color(0xFFF4F4F5)
val TextSecondary = Color(0xFFA1A1AA)
val TextTertiary = Color(0xFF71717A)

val WarningYellow = Color(0xFFFFB74D)
val WarningPurple = Color(0xFFBA68C8)
val ErrorRed = Color(0xFFEF5350)
val InfoBlue = Color(0xFF42A5F5)

fun parseMcColorString(input: String): AnnotatedString {
    if (input.isEmpty()) return AnnotatedString("")
    val colorMap = mapOf(
        '0' to Color(0xFF000000), // Black
        '1' to Color(0xFF0000AA), // Dark Blue
        '2' to Color(0xFF00AA00), // Dark Green
        '3' to Color(0xFF00AAAA), // Dark Aqua
        '4' to Color(0xFFAA0000), // Dark Red
        '5' to Color(0xFFAA00AA), // Dark Purple
        '6' to Color(0xFFFFAA00), // Gold
        '7' to Color(0xFFAAAAAA), // Gray
        '8' to Color(0xFF555555), // Dark Gray
        '9' to Color(0xFF5555FF), // Blue
        'a' to Color(0xFF55FF55), // Green
        'b' to Color(0xFF55FFFF), // Aqua
        'c' to Color(0xFFFF5555), // Red
        'd' to Color(0xFFFF55FF), // Light Purple
        'e' to Color(0xFFFFFF55), // Yellow
        'f' to Color(0xFFFFFFFF)  // White
    )

    return buildAnnotatedString {
        var currentColor: Color = TextSecondary
        var isBold = false
        var i = 0
        while (i < input.length) {
            if (input[i] == '§' && i + 1 < input.length) {
                val code = input[i + 1].lowercaseChar()
                if (colorMap.containsKey(code)) {
                    currentColor = colorMap[code]!!
                    isBold = false
                } else if (code == 'l') {
                    isBold = true
                } else if (code == 'r') {
                    currentColor = TextSecondary
                    isBold = false
                }
                i += 2
            } else {
                val start = length
                append(input[i])
                addStyle(
                    SpanStyle(color = currentColor, fontWeight = if (isBold) FontWeight.Bold else FontWeight.Normal),
                    start,
                    length
                )
                i++
            }
        }
    }
}
