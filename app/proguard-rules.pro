# WebView JS 接口：保留 @JavascriptInterface 注解方法（R8 混淆下反射调用必需）
-keepclassmembers class * { @android.webkit.JavascriptInterface <methods>; }
-keepattributes *Annotation*

# SnakeYAML 内部反射
-keep class org.yaml.snakeyaml.** { *; }
-dontwarn org.yaml.snakeyaml.**
-dontwarn java.beans.**

# OkHttp / Okio 平台探测反射
-dontwarn okhttp3.internal.platform.**
-dontwarn org.conscrypt.**
-dontwarn org.bouncycastle.**
-dontwarn org.openjsse.**