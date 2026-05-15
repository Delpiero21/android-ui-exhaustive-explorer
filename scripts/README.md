# scripts/ — 운영 스크립트

Phase 0 (Bootstrap) 셋업에 필요한 PowerShell 스크립트 모음.

| 스크립트 | 용도 |
|---|---|
| [`dev.ps1`](dev.ps1) | server + web + adb reverse 를 새 창 3개로 동시 기동 |
| [`setup_adb_reverse.ps1`](setup_adb_reverse.ps1) | 단말 → PC server (`tcp:8000`) USB 터널만 설정 |
| [`dump_screen.ps1`](dump_screen.ps1) | 단발 캡처 — 스크린샷 PNG + uiautomator dump XML |

---

## 빠른 시작

### 로컬 개발 (가장 기본)

```powershell
cd C:\GitHub\Delpiero21\android-ui-exhaustive-explorer
.\scripts\dev.ps1
```

새 창 3개가 열리며:
- server → http://127.0.0.1:8000
- web    → http://localhost:5173
- adb    → tcp:8000 reversed

### LAN 모드 (다른 PC 에서 대시보드 보고 싶을 때)

```powershell
.\scripts\dev.ps1 -BindHost 10.10.5.20
```

- server CORS 화이트리스트에 `http://10.10.5.20:5173` 추가
- web dev server 가 `0.0.0.0` 에 바인딩 → LAN 의 다른 PC 에서 접근 가능

### 단말 없이 web 만

```powershell
.\scripts\dev.ps1 -SkipAdbReverse
```

---

## 사전 요구사항

| 도구 | 버전 | 비고 |
|---|---|---|
| PowerShell | 7+ | `pwsh.exe` (Windows PowerShell 5.1 도 동작) |
| Python | 3.11+ | `server/` 의존성 설치용 |
| Node.js | 20+ | `web/` 의존성 설치용 |
| adb | 최신 | platform-tools 안에 포함, PATH 에 있어야 함 |

처음 한 번 실행 전:

```powershell
# server
cd server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
deactivate
cd ..

# web
cd web
npm install
cd ..
```

이후엔 `.\scripts\dev.ps1` 만 실행.

---

## 종료

각 창에서 `Ctrl+C` → 새 창 닫기.
adb reverse 매핑 해제:

```powershell
adb reverse --remove tcp:8000
# 또는 전체 제거
adb reverse --remove-all
```
