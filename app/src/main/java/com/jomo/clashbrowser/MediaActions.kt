package com.jomo.clashbrowser

import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.BitmapFactory
import android.os.Build
import android.os.Environment
import android.provider.MediaStore
import androidx.core.content.FileProvider
import com.google.zxing.BinaryBitmap
import com.google.zxing.MultiFormatReader
import com.google.zxing.RGBLuminanceSource
import com.google.zxing.common.HybridBinarizer
import okhttp3.OkHttpClient
import okhttp3.Request
import java.io.File
import java.net.InetSocketAddress
import java.net.Proxy
import java.util.concurrent.TimeUnit

/** 长按菜单的媒体操作：拉流（走应用内代理）、存图、下载、分享、二维码解码。 */
object MediaActions {

    private const val UA =
        "Mozilla/5.0 (Linux; Android 14) AppleWebKit/537.36 (KHTML, like Gecko) Chrome/130.0.0.0 Mobile Safari/537.36"

    /** 与 WebView 同路径：代理开启时经 mihomo mixed-port 拉取，保证境外媒资可达。 */
    fun client(): OkHttpClient {
        val b = OkHttpClient.Builder()
            .connectTimeout(15, TimeUnit.SECONDS)
            .readTimeout(30, TimeUnit.SECONDS)
            .followRedirects(true)
        if (ProxyManager.isProxyEnabled()) {
            b.proxy(Proxy(Proxy.Type.HTTP, InetSocketAddress("127.0.0.1", 7890)))
        }
        return b.build()
    }

    fun fetch(url: String): ByteArray? = runCatching {
        val req = Request.Builder().url(url).header("User-Agent", UA).build()
        client().newCall(req).execute().use { resp ->
            if (!resp.isSuccessful) error("HTTP ${resp.code}")
            resp.body?.bytes() ?: error("empty body")
        }
    }.getOrNull()

    fun fileName(url: String, fallbackExt: String): String {
        val seg = url.substringBefore('?').substringAfterLast('/')
        val clean = seg.replace(Regex("[^A-Za-z0-9._-]"), "")
        return if (clean.contains('.') && clean.length <= 80) clean
        else "image_${System.currentTimeMillis()}$fallbackExt"
    }

    fun mimeOf(name: String): String = when {
        name.endsWith(".png") -> "image/png"
        name.endsWith(".gif") -> "image/gif"
        name.endsWith(".webp") -> "image/webp"
        name.endsWith(".bmp") -> "image/bmp"
        name.endsWith(".svg") -> "image/svg+xml"
        else -> "image/jpeg"
    }

    /** 保存图片到相册（Pictures/ClashBrowser）；API<29 存应用外部目录（免权限）。 */
    fun saveToGallery(context: Context, url: String, bytes: ByteArray): String? = runCatching {
        val name = fileName(url, ".jpg")
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Images.Media.DISPLAY_NAME, name)
                put(MediaStore.Images.Media.MIME_TYPE, mimeOf(name))
                put(MediaStore.Images.Media.RELATIVE_PATH, Environment.DIRECTORY_PICTURES + "/ClashBrowser")
            }
            val uri = context.contentResolver.insert(MediaStore.Images.Media.EXTERNAL_CONTENT_URI, cv)
                ?: error("insert failed")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("open failed")
            "Pictures/ClashBrowser/$name"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_PICTURES), "ClashBrowser")
            dir.mkdirs()
            File(dir, name).apply { writeBytes(bytes) }.absolutePath
        }
    }.getOrNull()

    /** 下载图片到 Download/ClashBrowser；API<29 存应用外部目录（免权限）。 */
    fun saveToDownload(context: Context, url: String, bytes: ByteArray): String? = runCatching {
        val name = fileName(url, ".jpg")
        if (Build.VERSION.SDK_INT >= 29) {
            val cv = ContentValues().apply {
                put(MediaStore.Downloads.DISPLAY_NAME, name)
                put(MediaStore.Downloads.MIME_TYPE, mimeOf(name))
                put(MediaStore.Downloads.RELATIVE_PATH, Environment.DIRECTORY_DOWNLOADS + "/ClashBrowser")
            }
            val uri = context.contentResolver.insert(MediaStore.Downloads.EXTERNAL_CONTENT_URI, cv)
                ?: error("insert failed")
            context.contentResolver.openOutputStream(uri)?.use { it.write(bytes) } ?: error("open failed")
            "Download/ClashBrowser/$name"
        } else {
            val dir = File(context.getExternalFilesDir(Environment.DIRECTORY_DOWNLOADS), "ClashBrowser")
            dir.mkdirs()
            File(dir, name).apply { writeBytes(bytes) }.absolutePath
        }
    }.getOrNull()

    /** 分享图片：缓存文件 + FileProvider 授权 URI。 */
    fun shareImage(context: Context, url: String, bytes: ByteArray) {
        val name = fileName(url, ".jpg")
        val dir = File(context.cacheDir, "share")
        dir.mkdirs()
        val f = File(dir, name)
        f.writeBytes(bytes)
        val uri = FileProvider.getUriForFile(context, "${context.packageName}.fileprovider", f)
        val send = Intent(Intent.ACTION_SEND).apply {
            type = mimeOf(name)
            putExtra(Intent.EXTRA_STREAM, uri)
            addFlags(Intent.FLAG_GRANT_READ_URI_PERMISSION)
        }
        context.startActivity(Intent.createChooser(send, "分享图片"))
    }

    /** 解码图片中的二维码/条码，失败返回 null。 */
    fun decodeQr(bytes: ByteArray): String? = runCatching {
        val bmp = BitmapFactory.decodeByteArray(bytes, 0, bytes.size) ?: error("decode fail")
        val w = bmp.width
        val h = bmp.height
        val pixels = IntArray(w * h)
        bmp.getPixels(pixels, 0, w, 0, 0, w, h)
        val source = RGBLuminanceSource(w, h, pixels)
        MultiFormatReader().decodeWithState(BinaryBitmap(HybridBinarizer(source))).text
    }.getOrNull()

    fun decode(bytes: ByteArray, sample: Int): Bitmap? = runCatching {
        val opts = BitmapFactory.Options().apply { inSampleSize = sample }
        BitmapFactory.decodeByteArray(bytes, 0, bytes.size, opts)
    }.getOrNull()
}