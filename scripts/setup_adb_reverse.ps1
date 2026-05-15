<#
.SYNOPSIS
    단말이 localhost:8000 으로 호스트 PC server 에 접근할 수 있도록 adb reverse 설정.

.DESCRIPTION
    APK 의 network_security_config 가 cleartext 를 127.0.0.1 / localhost 만 허용하기 때문에
    PC 의 server 에 접근하려면 USB 터널로 단말 측 localhost:8000 을 PC 8000 으로 reverse 해야 한다.

.EXAMPLE
    .\scripts\setup_adb_reverse.ps1
#>
[CmdletBinding()]
param()

$ErrorActionPreference = "Stop"

Write-Host "adb reverse tcp:8000 tcp:8000" -ForegroundColor Yellow
& adb reverse tcp:8000 tcp:8000
Write-Host "OK — 단말 측 http://127.0.0.1:8000 → PC :8000 으로 연결됨" -ForegroundColor Green

Write-Host ""
Write-Host "현재 reverse 매핑 확인:"
& adb reverse --list
