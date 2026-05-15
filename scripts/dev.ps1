<#
.SYNOPSIS
    Phase 0 통합 개발 모드 런처.

.DESCRIPTION
    아래 3가지를 한 번에 기동한다:
      1. server/  FastAPI (uvicorn, 127.0.0.1:8000)
      2. web/     Vite dev server (127.0.0.1:5173)
      3. adb reverse tcp:8000 — 단말이 localhost:8000 으로 호스트 PC server 접근

    각 컴포넌트는 새 PowerShell 창에서 실행되며 Ctrl+C 로 개별 종료한다.

.PARAMETER BindHost
    LAN 모드. 0.0.0.0 또는 사내 PC IP (예: 10.10.5.20) 를 주면
    server / web 모두 LAN 바인딩 + CORS 화이트리스트에 추가.
    생략 시 loopback only (가장 안전).

.PARAMETER SkipAdbReverse
    adb reverse 단계를 건너뛴다. 단말 안 꽂혀있거나 web 만 띄울 때 사용.

.EXAMPLE
    .\scripts\dev.ps1
    # 가장 기본 — 로컬 PC 에서만 접근

.EXAMPLE
    .\scripts\dev.ps1 -BindHost 10.10.5.20
    # 사내 LAN 다른 PC 에서 대시보드 보고 싶을 때
#>
[CmdletBinding()]
param(
    [string]$BindHost = "",
    [switch]$SkipAdbReverse
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

Write-Host ""
Write-Host "android-ui-exhaustive-explorer · Phase 0 dev launcher" -ForegroundColor Cyan
Write-Host "root: $root" -ForegroundColor DarkGray
Write-Host ""

# ----- 1. adb reverse -----
if (-not $SkipAdbReverse) {
    Write-Host "[1/3] adb reverse tcp:8000 tcp:8000" -ForegroundColor Yellow
    try {
        & adb reverse tcp:8000 tcp:8000 | Out-Null
        Write-Host "      OK" -ForegroundColor Green
    } catch {
        Write-Host "      SKIP — adb 명령 실패 ($_)" -ForegroundColor DarkYellow
        Write-Host "      (단말 안 꽂혀있어도 server/web 자체는 동작합니다)" -ForegroundColor DarkGray
    }
} else {
    Write-Host "[1/3] adb reverse — skipped (-SkipAdbReverse)" -ForegroundColor DarkGray
}

# ----- 2. server -----
Write-Host ""
Write-Host "[2/3] server (FastAPI / uvicorn :8000)" -ForegroundColor Yellow

$serverDir = Join-Path $root "server"
$serverEnv = @{
    "EXPLORER_HOST" = if ($BindHost) { $BindHost } else { "127.0.0.1" }
    "EXPLORER_PORT" = "8000"
    "EXPLORER_RELOAD" = "1"
}
if ($BindHost) {
    $serverEnv["EXPLORER_ALLOW_LAN"] = "1"
    $serverEnv["EXPLORER_LAN_HOST"] = $BindHost
}

$envSetters = ($serverEnv.GetEnumerator() | ForEach-Object { "`$env:$($_.Key)='$($_.Value)'" }) -join "; "
$serverCmd = "$envSetters; cd '$serverDir'; explorer-server"

Start-Process pwsh -ArgumentList "-NoExit", "-Command", $serverCmd | Out-Null
Write-Host "      started in new window — http://$($serverEnv['EXPLORER_HOST']):8000" -ForegroundColor Green

# ----- 3. web -----
Write-Host ""
Write-Host "[3/3] web (Vite :5173)" -ForegroundColor Yellow

$webDir = Join-Path $root "web"
$webEnv = @{}
if ($BindHost) {
    $webEnv["EXPLORER_ALLOW_LAN"] = "1"
    $webEnv["EXPLORER_LAN_HOST"] = $BindHost
}
$webEnvSetters = ($webEnv.GetEnumerator() | ForEach-Object { "`$env:$($_.Key)='$($_.Value)'" }) -join "; "
$webCmd = if ($webEnvSetters) { "$webEnvSetters; cd '$webDir'; npm run dev" } else { "cd '$webDir'; npm run dev" }

Start-Process pwsh -ArgumentList "-NoExit", "-Command", $webCmd | Out-Null
Write-Host "      started in new window — http://localhost:5173" -ForegroundColor Green

Write-Host ""
Write-Host "✓ all components launched in separate windows" -ForegroundColor Cyan
Write-Host "  • server : http://127.0.0.1:8000   (Ctrl+C in its window to stop)"
Write-Host "  • web    : http://localhost:5173"
if (-not $SkipAdbReverse) {
    Write-Host "  • adb    : tcp:8000 reversed onto device"
}
Write-Host ""
