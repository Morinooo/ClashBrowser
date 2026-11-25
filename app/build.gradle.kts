import java.net.HttpURLConnection
import java.net.URI
import java.util.zip.GZIPInputStream

plugins {
    id("com.android.application")
    id("org.jetbrains.kotlin.android")
}

android {
    namespace = "com.jomo.clashbrowser"
    compileSdk = 34

    defaultConfig {
        applicationId = "com.jomo.clashbrowser"
        minSdk = 26
        targetSdk = 34
        versionCode = 24
        versionName = "1.16.0"
        ndk { abiFilters += listOf("arm64-v8a") }
    }

    // 存量问题记入基线，新增 lint 错误直接失败（CI 门禁）
    lint {
        abortOnError = true
        baseline = file("lint-baseline.xml")
    }

    // Optional release signing. The release workflow injects these environment
    // variables from encrypted GitHub Actions secrets; local builds without them
    // still work and simply produce an unsigned release APK.
    signingConfigs {
        val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
        if (!keystorePath.isNullOrBlank()) {
            create("release") {
                storeFile = file(keystorePath)
                storePassword = System.getenv("RELEASE_KEYSTORE_PASSWORD")
                keyAlias = System.getenv("RELEASE_KEY_ALIAS")
                keyPassword = System.getenv("RELEASE_KEY_PASSWORD")
            }
        }
    }

    buildTypes {
        release {
            isMinifyEnabled = false
            proguardFiles(getDefaultProguardFile("proguard-android-optimize.txt"), "proguard-rules.pro")
            val keystorePath = System.getenv("RELEASE_KEYSTORE_PATH")
            if (!keystorePath.isNullOrBlank()) {
                signingConfig = signingConfigs.getByName("release")
            }
        }
    }

    compileOptions {
        sourceCompatibility = JavaVersion.VERSION_17
        targetCompatibility = JavaVersion.VERSION_17
    }
    kotlinOptions { jvmTarget = "17" }

    buildFeatures { compose = true }
    composeOptions { kotlinCompilerExtensionVersion = "1.5.14" }

    // 等价于 manifest 的 extractNativeLibs=true：安装时把 libmihomo.so 解压到磁盘以便执行
    packaging { jniLibs.useLegacyPackaging = true }
}

// ---------------------------------------------------------------------------
// 首次编译前自动下载 mihomo 内核与 Geo 数据库（已存在则跳过），挂载到 preBuild
// ---------------------------------------------------------------------------
val fetchCoreAssets by tasks.registering {
    val soFile = layout.projectDirectory.file("src/main/jniLibs/arm64-v8a/libmihomo.so")
    val mmdbFile = layout.projectDirectory.file("src/main/assets/Country.mmdb")
    val geositeFile = layout.projectDirectory.file("src/main/assets/geosite.dat")
    outputs.files(soFile, mmdbFile, geositeFile)
    outputs.upToDateWhen {
        soFile.asFile.length() > 1024 * 1024 && mmdbFile.asFile.length() > 10 * 1024
    }
    doLast {
        fun httpGet(url: String): ByteArray {
            var lastError: Exception? = null
            for (u in listOf(url, "https://ghfast.top/$url")) {
                try {
                    logger.lifecycle("  尝试下载: $u")
                    val conn = URI(u).toURL().openConnection() as HttpURLConnection
                    conn.connectTimeout = 20_000
                    conn.readTimeout = 180_000
                    conn.instanceFollowRedirects = true
                    conn.setRequestProperty("User-Agent", "ClashBrowserBuild/1.0")
                    val code = conn.responseCode
                    if (code in 200..299) return conn.inputStream.use { it.readBytes() }
                    lastError = RuntimeException("HTTP $code")
                } catch (e: Exception) {
                    lastError = e
                    logger.warn("  失败: ${e.message}")
                }
            }
            throw lastError ?: RuntimeException("下载失败: $url")
        }

        // 1. mihomo 内核
        if (soFile.asFile.length() <= 1024 * 1024) {
            logger.lifecycle("[fetchCoreAssets] 下载 mihomo android-arm64-v8a 内核...")
            var assetUrl: String? = null
            try {
                val apiJson = String(httpGet("https://api.github.com/repos/MetaCubeX/mihomo/releases/latest"))
                assetUrl = Regex("\"browser_download_url\"\\s*:\\s*\"([^\"]*mihomo-android-arm64-v8a?-v[\\d.]+\\.gz)\"")
                    .find(apiJson)?.groupValues?.get(1)
            } catch (e: Exception) {
                logger.warn("GitHub API 不可用: ${e.message}")
            }
            if (assetUrl == null) {
                val html = String(httpGet("https://github.com/MetaCubeX/mihomo/releases/latest"))
                val tag = Regex("releases/tag/(v[\\d.]+)").find(html)?.groupValues?.get(1)
                    ?: throw GradleException("无法解析 mihomo 最新版本号")
                assetUrl = "https://github.com/MetaCubeX/mihomo/releases/download/$tag/mihomo-android-arm64-v8-$tag.gz"
            }
            val resolvedUrl = assetUrl ?: throw GradleException("无法定位 mihomo 内核下载地址")
            logger.lifecycle("  内核地址: $resolvedUrl")
            val gzBytes = httpGet(resolvedUrl)
            soFile.asFile.parentFile.mkdirs()
            GZIPInputStream(gzBytes.inputStream()).use { gz ->
                soFile.asFile.outputStream().use { out -> gz.copyTo(out) }
            }
            logger.lifecycle("[fetchCoreAssets] 内核已就位: ${soFile.asFile} (${soFile.asFile.length() / 1024 / 1024} MB)")
        }

        // 2. Country.mmdb
        if (mmdbFile.asFile.length() <= 10 * 1024) {
            logger.lifecycle("[fetchCoreAssets] 下载 country-lite.mmdb...")
            val bytes = httpGet("https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/country-lite.mmdb")
            mmdbFile.asFile.parentFile.mkdirs()
            mmdbFile.asFile.writeBytes(bytes)
        }

        // 3. geosite.dat（兼容含 GEOSITE 规则的订阅）
        if (geositeFile.asFile.length() <= 10 * 1024) {
            logger.lifecycle("[fetchCoreAssets] 下载 geosite.dat...")
            try {
                val bytes = httpGet("https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/geosite.dat")
                geositeFile.asFile.writeBytes(bytes)
            } catch (e: Exception) {
                logger.warn("geosite.dat 下载失败（非致命）: ${e.message}")
            }
        }
    }
}

tasks.named("preBuild") { dependsOn(fetchCoreAssets) }

dependencies {
    val composeBom = platform("androidx.compose:compose-bom:2024.06.00")
    implementation(composeBom)
    implementation("androidx.compose.ui:ui")
    implementation("androidx.compose.material3:material3")

    implementation("androidx.core:core-ktx:1.13.1")
    implementation("androidx.activity:activity-compose:1.9.0")
    implementation("androidx.lifecycle:lifecycle-runtime-ktx:2.8.3")
    implementation("androidx.lifecycle:lifecycle-process:2.8.3")

    implementation("androidx.webkit:webkit:1.11.0")
    implementation("com.squareup.okhttp3:okhttp:4.12.0")
    implementation("org.jetbrains.kotlinx:kotlinx-coroutines-android:1.8.1")
    implementation("org.yaml:snakeyaml:2.2")
    implementation("com.google.zxing:core:3.5.3")
    testImplementation("junit:junit:4.13.2")
    // 单测用真实 org.json（android.jar 中为空桩），供 Suggest 解析单测
    testImplementation("org.json:json:20240303")
}