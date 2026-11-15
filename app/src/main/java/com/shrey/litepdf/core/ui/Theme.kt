package com.shrey.litepdf.core.ui

import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.darkColorScheme
import androidx.compose.material3.dynamicDarkColorScheme
import androidx.compose.material3.dynamicLightColorScheme
import androidx.compose.material3.lightColorScheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext

private val Light = lightColorScheme(primary = Color(0xFFB3261E), secondary = Color(0xFF5D5F66))
private val Dark = darkColorScheme(primary = Color(0xFFFFB4AB), secondary = Color(0xFFC6C6CE))

@Composable
fun LitePdfTheme(content: @Composable () -> Unit) {
    val dark = isSystemInDarkTheme()
    val ctx = LocalContext.current
    // minSdk 31 always has Material You colours; fall back only if the device returns none.
    val scheme = runCatching { if (dark) dynamicDarkColorScheme(ctx) else dynamicLightColorScheme(ctx) }
        .getOrElse { if (dark) Dark else Light }
    MaterialTheme(colorScheme = scheme, content = content)
}
