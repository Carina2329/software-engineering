param(
    [string]$WeedExe = "D:\seaweedfs\weed.exe",
    [string]$DataDir = "D:\seaweedfs\contest-data"
)

$ErrorActionPreference = "Stop"

if (-not (Test-Path -LiteralPath $WeedExe)) {
    throw "SeaweedFS executable not found: $WeedExe"
}

try {
    $running = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8333/" -TimeoutSec 2
    if ($running.StatusCode -eq 200) {
        Write-Host "SeaweedFS is already running: http://127.0.0.1:8333"
        exit 0
    }
} catch {
    # Not running yet.
}

New-Item -ItemType Directory -Path $DataDir -Force | Out-Null
$stdout = Join-Path (Split-Path $DataDir -Parent) "contest-server.out.log"
$stderr = Join-Path (Split-Path $DataDir -Parent) "contest-server.err.log"

$process = Start-Process `
    -FilePath $WeedExe `
    -ArgumentList @(
        "server",
        "-dir=$DataDir",
        "-master.port=9333",
        "-volume.port=8082",
        "-filer",
        "-filer.port=8888",
        "-s3",
        "-s3.port=8333"
    ) `
    -WorkingDirectory (Split-Path $WeedExe -Parent) `
    -WindowStyle Hidden `
    -RedirectStandardOutput $stdout `
    -RedirectStandardError $stderr `
    -PassThru

$deadline = (Get-Date).AddSeconds(30)
do {
    Start-Sleep -Milliseconds 500
    try {
        $response = Invoke-WebRequest -UseBasicParsing "http://127.0.0.1:8333/" -TimeoutSec 2
        if ($response.StatusCode -eq 200) {
            Write-Host "SeaweedFS started, PID=$($process.Id)"
            Write-Host "S3 API: http://127.0.0.1:8333"
            Write-Host "Filer:  http://127.0.0.1:8888"
            exit 0
        }
    } catch {
        # Keep waiting while SeaweedFS starts.
    }
} until ((Get-Date) -gt $deadline -or $process.HasExited)

Write-Host "SeaweedFS failed to start. Recent log:" -ForegroundColor Red
Get-Content -LiteralPath $stderr -Tail 40 -ErrorAction SilentlyContinue
exit 1
