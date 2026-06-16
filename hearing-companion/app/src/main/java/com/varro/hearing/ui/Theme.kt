package com.varro.hearing.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color

private val Green = Color(0xFF1A9B50)
private val GreenDark = Color(0xFF0A3D1F)
private val Gold = Color(0xFFF59E0B)

private val Dark = darkColorScheme(
    primary = Color(0xFF4ADE80), onPrimary = Color(0xFF052010),
    secondary = Gold, background = Color(0xFF052010), surface = Color(0xFF0A3D1F),
)
private val Light = lightColorScheme(
    primary = Green, onPrimary = Color.White, secondary = Gold,
)

@Composable
fun HearingTheme(content: @Composable () -> Unit) {
    MaterialTheme(
        colorScheme = if (isSystemInDarkTheme()) Dark else Light,
        content = content
    )
}
