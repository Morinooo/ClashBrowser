package com.jomo.clashbrowser

import android.content.Context
import android.text.InputType
import android.view.inputmethod.EditorInfo
import android.view.inputmethod.InputConnection
import android.view.inputmethod.InputMethodManager
import android.webkit.JavascriptInterface
import android.webkit.WebView

/**
 * 密码输入安全键盘（v1.4.3 加固）：
 * 1. WebView 密码框默认上报 TYPE_TEXT_VARIATION_WEB_PASSWORD（网页密码类型），
 *    vivo/OPPO/小米等厂商框架只识别原生密码类型。onCreateInputConnection 里
 *    改写为原生 TYPE_TEXT_VARIATION_PASSWORD。
 * 2. 仅靠改写不稳定：键盘已显示时点密码框可能复用旧输入连接，且厂商框架
 *    只在键盘"弹出瞬间"判定安全键盘，键盘显示中更新类型不会触发切换。
 *    因此注入 focusin 监听（事件委托，覆盖动态插入的密码框），密码框一获焦
 *    就自动执行"收起键盘 → 重建输入连接 → 重新弹出"，强制框架按原生密码
 *    类型重新判定，一次点击即稳定调起安全键盘。
 * 3. IME_FLAG_NO_PERSONALIZED_LEARNING + privateImeOptions 禁止输入法学习，
 *    作为不支持安全键盘切换的输入法的兜底保护。
 */
class SecureWebView(context: Context) : WebView(context) {

    init {
        addJavascriptInterface(PasswordFocusWatch(), "__cbPwdWatch")
    }

    override fun onCreateInputConnection(outAttrs: EditorInfo): InputConnection? {
        val ic = super.onCreateInputConnection(outAttrs)
        if (ic != null) {
            val variation = outAttrs.inputType and InputType.TYPE_MASK_VARIATION
            when (variation) {
                InputType.TYPE_TEXT_VARIATION_WEB_PASSWORD -> {
                    // 改写为原生密码类型，触发厂商系统安全键盘
                    outAttrs.inputType =
                        (outAttrs.inputType and InputType.TYPE_MASK_VARIATION.inv()) or
                            InputType.TYPE_TEXT_VARIATION_PASSWORD
                    markSecure(outAttrs)
                }
                InputType.TYPE_TEXT_VARIATION_PASSWORD,
                InputType.TYPE_NUMBER_VARIATION_PASSWORD,
                InputType.TYPE_TEXT_VARIATION_VISIBLE_PASSWORD -> markSecure(outAttrs)
            }
        }
        return ic
    }

    private fun markSecure(outAttrs: EditorInfo) {
        outAttrs.imeOptions =
            outAttrs.imeOptions or EditorInfo.IME_FLAG_NO_PERSONALIZED_LEARNING
        outAttrs.privateImeOptions = "nm"
    }

    /** 每次页面加载完成后注入密码框焦点监听（同一文档内防重复安装）。 */
    fun installPasswordWatch() {
        evaluateJavascript(JS_PWD_WATCH, null)
    }

    private inner class PasswordFocusWatch {
        @JavascriptInterface
        fun onPasswordFocus() {
            postDelayed({
                val imm = context.getSystemService(Context.INPUT_METHOD_SERVICE)
                    as InputMethodManager
                // 先收起当前键盘，再重建输入连接（此时已改写为原生密码类型）并重新弹出，
                // 强制厂商框架在"弹出瞬间"按新类型判定安全键盘
                imm.hideSoftInputFromWindow(windowToken, 0)
                postDelayed({
                    imm.restartInput(this@SecureWebView)
                    imm.showSoftInput(this@SecureWebView, 0)
                }, 100)
            }, 50)
        }
    }

    companion object {
        private const val JS_PWD_WATCH = """
(function(){
  if (window.__cbPwdInstalled) return;
  window.__cbPwdInstalled = true;
  document.addEventListener('focusin', function(e){
    try {
      var t = e.target;
      if (t && t.tagName === 'INPUT' && String(t.type).toLowerCase() === 'password' && window.__cbPwdWatch) {
        window.__cbPwdWatch.onPasswordFocus();
      }
    } catch (x) {}
  }, true);
})();
"""
    }
}