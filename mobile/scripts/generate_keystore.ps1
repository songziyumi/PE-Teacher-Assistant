param(
    [string]$KeystorePath = "android/app/upload-keystore.jks",
    [string]$KeyAlias = "upload",
    [string]$StorePass = "",
    [string]$KeyPass = "",
    [string]$Dname = "CN=PE Teacher Assistant, OU=Mobile, O=PE Teacher Assistant, L=Shanghai, ST=Shanghai, C=CN",
    [int]$ValidityDays = 36500
)

$ErrorActionPreference = "Stop"

if ([string]::IsNullOrWhiteSpace($StorePass)) {
    throw "StorePass is required. Example: -StorePass 'your_store_password'"
}
if ([string]::IsNullOrWhiteSpace($KeyPass)) {
    throw "KeyPass is required. Example: -KeyPass 'your_key_password'"
}

$mobileRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$keystoreAbs = Join-Path $mobileRoot $KeystorePath
$keystoreDir = Split-Path $keystoreAbs -Parent
New-Item -ItemType Directory -Force -Path $keystoreDir | Out-Null

$keytool = if ($env:JAVA_HOME -and (Test-Path (Join-Path $env:JAVA_HOME "bin\\keytool.exe"))) {
    Join-Path $env:JAVA_HOME "bin\\keytool.exe"
} else {
    "keytool"
}

$aliasExists = $false
if (Test-Path $keystoreAbs) {
    & $keytool `
        -list `
        -keystore $keystoreAbs `
        -alias $KeyAlias `
        -storepass $StorePass | Out-Null
    if ($LASTEXITCODE -eq 0) {
        $aliasExists = $true
        Write-Host "Alias '$KeyAlias' already exists in keystore, skip keypair generation."
    }
}

if (-not $aliasExists) {
    & $keytool `
        -genkeypair `
        -v `
        -keystore $keystoreAbs `
        -storetype JKS `
        -keyalg RSA `
        -keysize 2048 `
        -validity $ValidityDays `
        -alias $KeyAlias `
        -storepass $StorePass `
        -keypass $KeyPass `
        -dname $Dname
    if ($LASTEXITCODE -ne 0) {
        throw "keytool failed with exit code $LASTEXITCODE"
    }
}

$keyPropertiesPath = Join-Path $mobileRoot "android\\key.properties"
$androidRoot = Join-Path $mobileRoot "android"
$androidRootFull = [System.IO.Path]::GetFullPath($androidRoot)
$keystoreAbsFull = [System.IO.Path]::GetFullPath($keystoreAbs)
if ($keystoreAbsFull.StartsWith($androidRootFull, [System.StringComparison]::OrdinalIgnoreCase)) {
    $storeFileValue = $keystoreAbsFull.Substring($androidRootFull.Length).TrimStart("\", "/") -replace "\\", "/"
} else {
    $storeFileValue = $keystoreAbsFull -replace "\\", "/"
}
$content = @(
    "storePassword=$StorePass",
    "keyPassword=$KeyPass",
    "keyAlias=$KeyAlias",
    "storeFile=$storeFileValue"
)
Set-Content -Path $keyPropertiesPath -Value $content -Encoding Ascii

Write-Host "Keystore ready: $keystoreAbs"
Write-Host "key.properties written: $keyPropertiesPath"
