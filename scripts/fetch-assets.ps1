#Requires -Version 7
# 自动下载 mihomo 内核 + GeoIP/GeoSite 数据库（已存在则跳过）
# 用法: pwsh -File scripts/fetch-assets.ps1 [-ProjectRoot <工程根目录>]
param([string]$ProjectRoot = (Split-Path -Parent $PSScriptRoot))
$ErrorActionPreference = 'Stop'
$ProgressPreference = 'SilentlyContinue'

$jniDir      = Join-Path $ProjectRoot 'app/src/main/jniLibs/arm64-v8a'
$assetsDir   = Join-Path $ProjectRoot 'app/src/main/assets'
New-Item -ItemType Directory -Force -Path $jniDir, $assetsDir | Out-Null

$soPath      = Join-Path $jniDir 'libmihomo.so'
$mmdbPath    = Join-Path $assetsDir 'Country.mmdb'
$geositePath = Join-Path $assetsDir 'geosite.dat'
$tmpDir      = Join-Path ([IO.Path]::GetTempPath()) 'clashbrowser-dl'
New-Item -ItemType Directory -Force -Path $tmpDir | Out-Null

function Get-RemoteFile {
    param([string]$Url, [string]$OutFile)
    foreach ($u in @($Url, "https://ghfast.top/$Url")) {
        try {
            Write-Host "  尝试下载: $u"
            Invoke-WebRequest -Uri $u -OutFile $OutFile -TimeoutSec 180 -MaximumRedirection 8 -Headers @{ 'User-Agent' = 'ClashBrowser/1.0' }
            if ((Test-Path $OutFile) -and (Get-Item $OutFile).Length -gt 1024) { return $true }
        } catch { Write-Warning "  失败: $($_.Exception.Message)" }
    }
    return $false
}

# ---------- 1. mihomo 内核 ----------
if ((Test-Path $soPath) -and (Get-Item $soPath).Length -gt 1MB) {
    Write-Host '[跳过] libmihomo.so 已存在'
} else {
    Write-Host '[1/3] 获取 mihomo android-arm64-v8a 内核...'
    $assetUrl = $null
    try {
        $api = Invoke-RestMethod -Uri 'https://api.github.com/repos/MetaCubeX/mihomo/releases/latest' -TimeoutSec 30 -Headers @{ 'User-Agent' = 'ClashBrowser/1.0'; 'Accept' = 'application/vnd.github+json' }
        $asset = $api.assets | Where-Object { $_.name -match '^mihomo-android-arm64-v8a?-v[\d\.]+\.gz$' } | Select-Object -First 1
        if ($asset) { $assetUrl = $asset.browser_download_url }
    } catch { Write-Warning "GitHub API 不可用: $($_.Exception.Message)" }

    if (-not $assetUrl) {
        Write-Host '  API 失败，通过镜像解析最新 tag...'
        $resp = Invoke-WebRequest -Uri 'https://ghfast.top/https://github.com/MetaCubeX/mihomo/releases/latest' -TimeoutSec 60 -MaximumRedirection 8 -Headers @{ 'User-Agent' = 'ClashBrowser/1.0' }
        $tag = $null
        $finalUrl = $resp.BaseResponse.RequestMessage.RequestUri.AbsoluteUri
        if ($finalUrl -match '/tag/(v[\d\.]+)') { $tag = $Matches[1] }
        if (-not $tag -and $resp.Content -match 'releases/tag/(v[\d\.]+)') { $tag = $Matches[1] }
        if ($tag) { $assetUrl = "https://github.com/MetaCubeX/mihomo/releases/download/$tag/mihomo-android-arm64-v8-$tag.gz" }
    }
    if (-not $assetUrl) { throw '无法定位 mihomo android-arm64-v8a 下载地址' }
    Write-Host "  内核地址: $assetUrl"

    $gz = Join-Path $tmpDir 'mihomo.gz'
    if (-not (Get-RemoteFile $assetUrl $gz)) { throw '内核下载失败（直连与镜像均不可用）' }
    $inStream  = [IO.File]::OpenRead($gz)
    $gzStream  = [IO.Compression.GzipStream]::new($inStream, [IO.Compression.CompressionMode]::Decompress)
    $outStream = [IO.File]::Create($soPath)
    $gzStream.CopyTo($outStream)
    $outStream.Dispose(); $gzStream.Dispose(); $inStream.Dispose()
    Write-Host "[完成] $soPath"
}

# ---------- 2. Country.mmdb ----------
if ((Test-Path $mmdbPath) -and (Get-Item $mmdbPath).Length -gt 10KB) {
    Write-Host '[跳过] Country.mmdb 已存在'
} else {
    Write-Host '[2/3] 下载轻量版 Country.mmdb...'
    if (-not (Get-RemoteFile 'https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/country-lite.mmdb' $mmdbPath)) { throw 'Country.mmdb 下载失败' }
    Write-Host "[完成] $mmdbPath"
}

# ---------- 3. geosite.dat（兼容订阅中的 GEOSITE 规则，防止内核启动失败） ----------
if ((Test-Path $geositePath) -and (Get-Item $geositePath).Length -gt 10KB) {
    Write-Host '[跳过] geosite.dat 已存在'
} else {
    Write-Host '[3/3] 下载 geosite.dat...'
    if (-not (Get-RemoteFile 'https://github.com/MetaCubeX/meta-rules-dat/releases/latest/download/geosite.dat' $geositePath)) {
        Write-Warning 'geosite.dat 下载失败（非致命，仅影响含 GEOSITE 规则的订阅）'
    } else { Write-Host "[完成] $geositePath" }
}

Write-Host ''
Write-Host '===== 资源就位情况 ====='
foreach ($f in @($soPath, $mmdbPath, $geositePath)) {
    if (Test-Path $f) {
        $size = [math]::Round((Get-Item $f).Length / 1MB, 2)
        Write-Host ("  OK  {0}  ({1} MB)" -f $f, $size)
    } else {
        Write-Host "  MISSING  $f"
    }
}