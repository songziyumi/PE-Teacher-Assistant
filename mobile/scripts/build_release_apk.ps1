param(
    [string]$ApiBaseUrl = "",
    [switch]$SplitPerAbi
)

$ErrorActionPreference = "Stop"

$mobileRoot = (Resolve-Path (Join-Path $PSScriptRoot "..")).Path
$keyPropertiesPath = Join-Path $mobileRoot "android\\key.properties"
if (!(Test-Path $keyPropertiesPath)) {
    throw "android/key.properties not found. Run scripts/generate_keystore.ps1 first."
}

Push-Location $mobileRoot
try {
    $args = @("build", "apk", "--release")
    if ($SplitPerAbi) {
        $args += "--split-per-abi"
    }
    if (-not [string]::IsNullOrWhiteSpace($ApiBaseUrl)) {
        $args += "--dart-define=API_BASE_URL=$ApiBaseUrl"
    }

    & flutter @args
} finally {
    Pop-Location
}
