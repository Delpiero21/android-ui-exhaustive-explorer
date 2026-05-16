<#
.SYNOPSIS
    단말에서 RunRecorder 가 저장한 run 데이터를 PC server/data/runs/ 로 회수.

.DESCRIPTION
    단말 측 경로:
      /sdcard/Android/data/com.exhaustive.explorer.debug/files/runs/

    PC 측 경로:
      server/data/runs/

    회수 방법:
      1. `adb shell ls` 로 단말 내 run 디렉토리 목록 확인
      2. 각 runId 폴더를 `adb pull` 로 server/data/runs/ 에 복사
      3. (선택) 회수 완료한 단말 측 데이터 삭제 (--cleanup 플래그)

.PARAMETER Cleanup
    회수 완료 후 단말의 데이터 삭제.

.PARAMETER PackageName
    APK package name (debug 빌드면 .debug suffix).

.EXAMPLE
    .\scripts\pull_runs.ps1
    .\scripts\pull_runs.ps1 -Cleanup
#>
[CmdletBinding()]
param(
    [switch]$Cleanup,
    [string]$PackageName = "com.exhaustive.explorer.debug"
)

$ErrorActionPreference = "Stop"
$root = Split-Path -Parent $PSScriptRoot
$pcRunsDir = Join-Path $root "server/data/runs"

if (-not (Test-Path $pcRunsDir)) {
    New-Item -ItemType Directory -Path $pcRunsDir -Force | Out-Null
}

$deviceBaseDir = "/sdcard/Android/data/$PackageName/files/runs"

Write-Host ""
Write-Host "Pull runs from device" -ForegroundColor Cyan
Write-Host "  device : $deviceBaseDir" -ForegroundColor DarkGray
Write-Host "  pc     : $pcRunsDir" -ForegroundColor DarkGray
Write-Host ""

# 1. 단말 내 run 폴더 목록
$listOutput = & adb shell "ls $deviceBaseDir 2>/dev/null" 2>$null
if (-not $listOutput) {
    Write-Host "단말에 회수할 run 이 없습니다 ($deviceBaseDir 비어있음)." -ForegroundColor Yellow
    exit 0
}

$runIds = ($listOutput -split "`n" | ForEach-Object { $_.Trim() } | Where-Object { $_ })
Write-Host "발견한 run: $($runIds.Count) 개" -ForegroundColor Green

# 2. 각 runId 별로 pull
$pulled = 0
foreach ($runId in $runIds) {
    $devicePath = "$deviceBaseDir/$runId"
    $pcPath = Join-Path $pcRunsDir $runId

    if (Test-Path $pcPath) {
        Write-Host "  $runId — 이미 PC 에 있음, skip" -ForegroundColor DarkGray
        continue
    }

    Write-Host "  $runId — pulling..." -NoNewline
    & adb pull "$devicePath" "$pcPath" 2>&1 | Out-Null
    if ($LASTEXITCODE -eq 0) {
        Write-Host " ok" -ForegroundColor Green
        $pulled++
    } else {
        Write-Host " failed" -ForegroundColor Red
    }
}

Write-Host ""
Write-Host "총 회수: $pulled / $($runIds.Count)" -ForegroundColor Cyan

# 3. 선택적 cleanup
if ($Cleanup -and $pulled -gt 0) {
    Write-Host ""
    Write-Host "단말 측 데이터 삭제..." -ForegroundColor Yellow
    & adb shell "rm -rf $deviceBaseDir/*" 2>&1 | Out-Null
    Write-Host "OK" -ForegroundColor Green
}

Write-Host ""
Write-Host "이후:" -ForegroundColor Cyan
Write-Host "  server 가 자동으로 새 run 을 /api/runs 에서 노출합니다." -ForegroundColor DarkGray
Write-Host "  http://localhost:5173 (web) 의 Runs 페이지에서 확인하세요." -ForegroundColor DarkGray
