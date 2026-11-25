import java.net.HttpURLConnection
import java.security.MessageDigest
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
        versionCode = 25
        versionName = "1.16.1"
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
            isMinifyEnabled = true
            isDebuggable = false
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
// 首次编译前自动下载 mihomo 内核（锁定版本 + SHA-256 校验），挂载到 preBuild；
// Geo 数据库固化于仓库 assets，构建期仅做 SHA-256 校验（供应链锁定）
// ---------------------------------------------------------------------------
private val MIHOMO_TAG = "v1.19.34"
private val MIHOMO_GZ_SHA256 = "48a71fc6a68e2909587176e0ab7185d8f2fac298fa96ccdacf30ea86ab2359b5"
private val GEO_MMDB_SHA256 = "4ffd955e56281782c40949809011ec3d06a3eedd131993792365f397f5757647"
private val GEO_SITE_SHA256 = "de5bdc1f196e68cd1fad180becb593a0d3cdd41c372de7742dfacd0b1c8b5230"

private fun sha256(bytes: ByteArray): String =
    MessageDigest.getInstance("SHA-256").digest(bytes)
        .joinToString("") { "%02x".format(it) }

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

        // 1. mihomo 内核：锁定版本下载 + SHA-256 校验（不匹配即构建失败）
        if (soFile.asFile.length() <= 1024 * 1024) {
            logger.lifecycle("[fetchCoreAssets] 下载 mihomo $MIHOMO_TAG android-arm64-v8a 内核...")
            val resolvedUrl =
                "https://github.com/MetaCubeX/mihomo/releases/download/$MIHOMO_TAG/mihomo-android-arm64-v8-$MIHOMO_TAG.gz"
            logger.lifecycle("  内核地址: $resolvedUrl")
            val gzBytes = httpGet(resolvedUrl)
            val got = sha256(gzBytes)
            if (got != MIHOMO_GZ_SHA256) {
                throw GradleException("mihomo 内核 SHA-256 校验失败（期望 $MIHOMO_GZ_SHA256，实际 $got）")
            }
            soFile.asFile.parentFile.mkdirs()
            GZIPInputStream(gzBytes.inputStream()).use { gz ->
                soFile.asFile.outputStream().use { out -> gz.copyTo(out) }
            }
            logger.lifecycle("[fetchCoreAssets] 内核已就位: ${soFile.asFile} (${soFile.asFile.length() / 1024 / 1024} MB)")
        }

        // 2/3. Geo 数据库已固化进仓库 assets：构建期校验 SHA-256，杜绝上游滚动更新投毒
        fun verifyGeo(f: java.io.File, expect: String, name: String) {
            if (!f.exists()) throw GradleException("缺少固化的 $name")
            val got = sha256(f.readBytes())
            if (got != expect) throw GradleException("$name SHA-256 校验失败（期望 $expect，实际 $got）")
            logger.lifecycle("[fetchCoreAssets] $name 校验通过 (${f.length() / 1024} KB)")
        }
        verifyGeo(mmdbFile.asFile, GEO_MMDB_SHA256, "Country.mmdb")
        verifyGeo(geositeFile.asFile, GEO_SITE_SHA256, "geosite.dat")
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