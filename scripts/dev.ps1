<#
.SYNOPSIS
    통합 개발 모드 런처 (server + web + adb reverse).

.DESCRIPTION
    아래 3가지를 한 번에 기동한다:
      1. server/  FastAPI (uvicorn, 127.0.0.1:8000)
      2. web/     Vite dev server (127.0.0.1:5173)
      3. adb reverse tcp:8000 — 단말이 localhost:8000 으로 호스트 PC server 접근

    각 컴포넌트는 새 PowerShell 창에서 실행되며 Ctrl+C 로 개별 종료한다.

    conda env 자동 활성화 — 기본값 "explorer".

.PARAMETER BindHost
    LAN 모드. 0.0.0.0 또는 사내 PC IP (예: 10.10.5.20) 를 주면
    server / web 모두 LAN 바인딩 + CORS 화이트리스트에 추가.

.PARAMETER SkipAdbReverse
    adb reverse 단계를 건너뛴다.

.PARAMETER CondaEnv
    server 가 사용할 conda env 이름. 기본 "explorer".
    "none" 또는 빈 문자열이면 conda 활성화 안 함 (system Python 사용).

.EXAMPLE
    .\scripts\dev.ps1
    # 기본 — conda explorer env, loopback only

.EXAMPLE
    .\scripts\dev.ps1 -BindHost 10.10.5.20
    # 사내 LAN 다른 PC 에서 대시보드 보고 싶을 때

.EXAMPLE
    .\scripts\dev.ps1 -CondaEnv none
    # conda 없이 system Python 으로
#>
[CmdletBinding()]
param(
    [string]$BindHost = "",
    [switch]$SkipAdbReverse,
    [string]$CondaEnv = "explorer"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot

# PowerShell 종류 감지 — pwsh (7+) 우선, 없으면 windows powershell 5.x
$shellExe = if (Get-Command pwsh -ErrorAction SilentlyContinue) { "pwsh" } else { "powershell" }

Write-Host ""
Write-Host "android-ui-exhaustive-explorer · dev launcher" -ForegroundColor Cyan
Write-Host "root      : $root" -ForegroundColor DarkGray
Write-Host "shell     : $shellExe" -ForegroundColor DarkGray
$useConda = $CondaEnv -and $CondaEnv -ne "none"
if ($useConda) {
    Write-Host "conda env : $CondaEnv" -ForegroundColor DarkGray
}
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

if ($useConda) {
    # conda activate 는 새 shell 에서 직접 호출하려면 conda 가 PowerShell profile 에 init 되어 있어야 함.
    # 가장 확실한 방법: conda env 의 Scripts/ 디렉토리에서 explorer-server.exe 를 직접 실행.
    $condaBase = & conda info --base 2>$null
    if ($LASTEXITCODE -ne 0 -or -not $condaBase) {
        Write-Host "      WARN: conda 명령 실패 — system Python 으로 fallback" -ForegroundColor DarkYellow
        $serverExe = "explorer-server"
    } else {
        $condaBase = $condaBase.Trim()
        $serverExe = Join-Path $condaBase "envs\$CondaEnv\Scripts\explorer-server.exe"
        if (-not (Test-Path $serverExe)) {
            Write-Host "      WARN: $serverExe 없음 — system explorer-server 로 fallback" -ForegroundColor DarkYellow
            $serverExe = "explorer-server"
        } else {
            Write-Host "      conda env: $CondaEnv ($serverExe)" -ForegroundColor DarkGray
        }
    }
    $serverCmd = "$envSetters; cd '$serverDir'; & '$serverExe'"
} else {
    $serverCmd = "$envSetters; cd '$serverDir'; explorer-server"
}

Start-Process $shellExe -ArgumentList "-NoExit", "-Command", $serverCmd | Out-Null
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

Start-Process $shellExe -ArgumentList "-NoExit", "-Command", $webCmd | Out-Null
Write-Host "      started in new window — http://localhost:5173" -ForegroundColor Green

Write-Host ""
Write-Host "✓ all components launched in separate windows" -ForegroundColor Cyan
Write-Host "  • server : http://127.0.0.1:8000   (Ctrl+C in its window to stop)"
Write-Host "  • web    : http://localhost:5173"
if (-not $SkipAdbReverse) {
    Write-Host "  • adb    : tcp:8000 reversed onto device"
}
Write-Host ""
