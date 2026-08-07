[CmdletBinding()]
param(
    [string]$WebhookUrl = $env:LIFTLY_DISCORD_WEBHOOK_URL,
    [string[]]$Files = @(),
    [string]$Message = "",
    [string]$ArtifactUrl = "",
    [int]$MaxUploadMb = 0,
    [switch]$DryRun
)

$ErrorActionPreference = "Stop"
$repoRoot = Split-Path -Parent $PSScriptRoot

if ($MaxUploadMb -le 0) {
    $configuredLimit = 0
    if ($env:LIFTLY_DISCORD_MAX_UPLOAD_MB) {
        [void][int]::TryParse($env:LIFTLY_DISCORD_MAX_UPLOAD_MB, [ref]$configuredLimit)
    }
    $MaxUploadMb = if ($configuredLimit -gt 0) { $configuredLimit } else { 10 }
}

if (-not $DryRun -and [string]::IsNullOrWhiteSpace($WebhookUrl)) {
    throw "Defina LIFTLY_DISCORD_WEBHOOK_URL na sessao atual antes de publicar."
}

if (-not [string]::IsNullOrWhiteSpace($WebhookUrl)) {
    $webhookUri = $null
    if (-not [Uri]::TryCreate($WebhookUrl, [UriKind]::Absolute, [ref]$webhookUri) -or $webhookUri.Scheme -ne "https") {
        throw "O webhook precisa ser uma URL HTTPS valida do Discord."
    }
}

$gradleFile = Join-Path $repoRoot "app\build.gradle.kts"
$gradleText = if (Test-Path -LiteralPath $gradleFile) { Get-Content -LiteralPath $gradleFile -Raw } else { "" }
$versionName = ([regex]::Match($gradleText, 'versionName\s*=\s*"([^"]+)"')).Groups[1].Value
$versionCode = ([regex]::Match($gradleText, 'versionCode\s*=\s*(\d+)')).Groups[1].Value
if ([string]::IsNullOrWhiteSpace($versionName)) { $versionName = "desconhecida" }
if ([string]::IsNullOrWhiteSpace($versionCode)) { $versionCode = "?" }

$candidateFiles = New-Object System.Collections.Generic.List[string]
foreach ($file in $Files) {
    if (-not [string]::IsNullOrWhiteSpace($file)) { $candidateFiles.Add($file) }
}
if ($candidateFiles.Count -eq 0) {
    $rootApk = Join-Path $repoRoot "Liftly-v$versionName-debug.apk"
    $builtApk = Join-Path $repoRoot "app\build\outputs\apk\debug\app-debug.apk"
    if (Test-Path -LiteralPath $rootApk) { $candidateFiles.Add($rootApk) }
    elseif (Test-Path -LiteralPath $builtApk) { $candidateFiles.Add($builtApk) }
}

$resolvedFiles = New-Object System.Collections.Generic.List[object]
$skippedFiles = New-Object System.Collections.Generic.List[object]
$maxBytes = [int64]$MaxUploadMb * 1MB
foreach ($file in $candidateFiles) {
    $path = if ([IO.Path]::IsPathRooted($file)) { $file } else { Join-Path $repoRoot $file }
    if (-not (Test-Path -LiteralPath $path -PathType Leaf)) {
        throw "Arquivo nao encontrado: $path"
    }
    $item = Get-Item -LiteralPath $path
    if ($item.Length -gt $maxBytes) { $skippedFiles.Add($item) }
    else { $resolvedFiles.Add($item) }
}

$lines = New-Object System.Collections.Generic.List[string]
$mdCode = [char]96
$lines.Add("**Liftly $versionName (codigo $versionCode)**")
$lines.Add("Build publicado em $(Get-Date -Format 'yyyy-MM-dd HH:mm')")
if (-not [string]::IsNullOrWhiteSpace($Message)) {
    $lines.Add("")
    $lines.Add($Message.Trim())
}
foreach ($item in $resolvedFiles) {
    $hash = (Get-FileHash -LiteralPath $item.FullName -Algorithm SHA256).Hash
    $sizeMb = [math]::Round($item.Length / 1MB, 2)
    $lines.Add("")
    $lines.Add($mdCode + $item.Name + $mdCode + " - $sizeMb MB - SHA-256 " + $mdCode + $hash + $mdCode)
}
foreach ($item in $skippedFiles) {
    $sizeMb = [math]::Round($item.Length / 1MB, 2)
    $lines.Add("")
    $lines.Add("AVISO: " + $mdCode + $item.Name + $mdCode + " ($sizeMb MB) nao foi anexado: limite configurado e ${MaxUploadMb} MB.")
}
if (-not [string]::IsNullOrWhiteSpace($ArtifactUrl)) {
    $lines.Add("")
    $lines.Add("Download externo: $ArtifactUrl")
}
$content = ($lines -join "`n")
if ($content.Length -gt 1900) { $content = $content.Substring(0, 1897) + "..." }

if ($DryRun) {
    Write-Host "[dry-run] Mensagem que seria enviada:"
    Write-Host $content
    Write-Host "[dry-run] Anexos: $($resolvedFiles.Count); ignorados por tamanho: $($skippedFiles.Count); limite: ${MaxUploadMb} MB."
    exit 0
}

Add-Type -AssemblyName System.Net.Http
$client = [System.Net.Http.HttpClient]::new()
$multipart = [System.Net.Http.MultipartFormDataContent]::new()
try {
    $payload = @{
        username = "Liftly Build Bot"
        content = $content
        allowed_mentions = @{ parse = @() }
    } | ConvertTo-Json -Depth 5 -Compress
    $payloadPart = [System.Net.Http.StringContent]::new($payload, [Text.Encoding]::UTF8, "application/json")
    $multipart.Add($payloadPart, "payload_json")

    $partIndex = 0
    foreach ($item in $resolvedFiles) {
        $bytes = [IO.File]::ReadAllBytes($item.FullName)
        $filePart = [System.Net.Http.ByteArrayContent]::new($bytes)
        $filePart.Headers.ContentType = [System.Net.Http.Headers.MediaTypeHeaderValue]::new("application/octet-stream")
        $multipart.Add($filePart, "files[$partIndex]", $item.Name)
        $partIndex++
    }

    $postUrl = if ($WebhookUrl.Contains("?")) { $WebhookUrl + "&wait=true" } else { $WebhookUrl + "?wait=true" }
    $response = $client.PostAsync($postUrl, $multipart).GetAwaiter().GetResult()
    if (-not $response.IsSuccessStatusCode) {
        $status = [int]$response.StatusCode
        throw "Discord recusou a publicacao (HTTP $status). Verifique o webhook e o limite de anexos."
    }
    Write-Host "Publicado no Discord: $($resolvedFiles.Count) anexo(s); $($skippedFiles.Count) ignorado(s) por tamanho."
}
finally {
    $multipart.Dispose()
    $client.Dispose()
}
