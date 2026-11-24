package com.jomo.clashbrowser

import android.webkit.WebView
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.Composable
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.luminance
import androidx.webkit.WebSettingsCompat
import androidx.webkit.WebViewFeature

/** 按当前主题返回对应颜色（浅色值 / 深色值），保持浅色外观与原设计完全一致。 */
@Composable
fun themeColor(light: Color, dark: Color): Color =
    if (MaterialTheme.colorScheme.surface.luminance() > 0.5f) light else dark

/**
 * 网页深浅色同步：
 * - 新版 WebView：ALGORITHMIC_DARKENING，按 WebView 上下文主题自动暗化网页，
 *   支持 prefers-color-scheme 的网站会收到深色偏好；
 * - 旧版 WebView：回退 FORCE_DARK。
 * 均不支持时静默跳过（网页保持原样）。
 */
object WebViewThemeHelper {

    @Suppress("DEPRECATION")
    fun apply(webView: WebView, dark: Boolean) {
        runCatching {
            when {
                WebViewFeature.isFeatureSupported(WebViewFeature.ALGORITHMIC_DARKENING) ->
                    WebSettingsCompat.setAlgorithmicDarkeningAllowed(webView.settings, dark)
                WebViewFeature.isFeatureSupported(WebViewFeature.FORCE_DARK) ->
                    WebSettingsCompat.setForceDark(
                        webView.settings,
                        if (dark) WebSettingsCompat.FORCE_DARK_ON else WebSettingsCompat.FORCE_DARK_OFF
                    )
            }
        }
    }
}