package com.jomo.clashbrowser

import android.content.Context
import androidx.core.content.ContextCompat
import androidx.webkit.ProxyConfig
import androidx.webkit.ProxyController
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import kotlin.coroutines.resume

/**
 * WebView 局部代理绑定（不申请 VPN 权限）。
 * 必须等 setProxyOverride 的成功回调触发后再加载网页，防止首屏直连超时。
 */
object WebViewProxyHelper {

    private const val PROXY_RULE = "http://127.0.0.1:7890"

    /** 返回 true 表示代理规则已生效（回调已触发）。失败时 WebView 保持直连。 */
    suspend fun applyProxy(context: Context): Boolean = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                val config = ProxyConfig.Builder()
                    .addProxyRule(PROXY_RULE)
                    .addBypassRule("<local>")
                    .build()
                ProxyController.getInstance().setProxyOverride(
                    config,
                    ContextCompat.getMainExecutor(context)
                ) {
                    if (cont.isActive) cont.resume(true)
                }
            } catch (t: Throwable) {
                // 当前 WebView 实现不支持 ProxyController 时降级直连
                if (cont.isActive) cont.resume(false)
            }
        }
    }

    suspend fun clearProxy(context: Context): Unit = withContext(Dispatchers.Main) {
        suspendCancellableCoroutine { cont ->
            try {
                ProxyController.getInstance().clearProxyOverride(
                    ContextCompat.getMainExecutor(context)
                ) {
                    if (cont.isActive) cont.resume(Unit)
                }
            } catch (t: Throwable) {
                if (cont.isActive) cont.resume(Unit)
            }
        }
    }
}