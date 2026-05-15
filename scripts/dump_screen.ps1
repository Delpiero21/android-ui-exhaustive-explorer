<#
.SYNOPSIS
    단말 현재 화면을 캡처 (스크린샷 PNG + uiautomator dump XML).

.DESCRIPTION
    docs/SAMSUNG_NOTES_HARD_CASES.md 의 실측 5 케이스 캡처에 사용된 절차와 동일.
    별도 도구 (android-ui-dump-visualizer) 없이 단발 캡처가 필요할 때 사용.

.PARAMETER OutDir
    캡처 저장 디렉토리. 기본: runs/<timestamp>/

.EXAMPLE
    .\scripts\dump_screen.ps1
    .\scripts\dump_screen.ps1 -OutDir runs/case-XX
#>
[CmdletBinding()]
param(
    [string]$OutDir = ""
)

$ErrorActionPreference = "Stop"

if (-not $OutDir) {
    $ts = Get-Date -Format "yyyyMMdd_HHmmss"
    $OutDir = "runs/$ts"
}
New-Item -ItemType Directory -Force -Path $OutDir | Out-Null

Write-Host "Capturing to $OutDir ..." -ForegroundColor Yellow

# 1. screenshot
& adb exec-out screencap -p > "$OutDir/screen.png"
Write-Host "  screen.png : $((Get-Item "$OutDir/screen.png").Length) bytes" -ForegroundColor Green

# 2. ui hierarchy
& adb shell uiautomator dump /sdcard/_dump.xml | Out-Null
& adb pull /sdcard/_dump.xml "$OutDir/dump.xml" | Out-Null
& adb shell rm /sdcard/_dump.xml | Out-Null
Write-Host "  dump.xml   : $((Get-Item "$OutDir/dump.xml").Length) bytes" -ForegroundColor Green

Write-Host ""
Write-Host "Done. Output: $OutDir" -ForegroundColor Cyan
