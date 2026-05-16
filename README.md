# android-ui-exhaustive-explorer

> **모바일 단말 preload 앱의 UI 표면을 자동으로 완전탐색**하는 도구.
> Accessibility · Pixel Grid · Computer Vision · Differential Probe · VLM 다섯 계층을 결합해
> 단일 기법으로는 닿지 못하는 사각지대까지 audit-가능한 형태로 보고한다.

[![status](https://img.shields.io/badge/status-phase%201%20complete%20·%20autonomous%20DFS-green)]()
[![phase](https://img.shields.io/badge/phase-1%20%E2%9C%93-blue)]()
[![target](https://img.shields.io/badge/target-One%20UI%208.5%20%2F%20S26-black)]()

---

## 한 줄 요약

> **단일 기법으로는 완전탐색 불가능.**
> **계층 + 검증 + 차단** 세 축이 유일한 현실적 해법.

---

## 디렉토리 구조

```
android-ui-exhaustive-explorer/
├── docs/             # 설계 문서 (LIMITATIONS, COUNTERMEASURES, ARCHITECTURE, ROADMAP, report.html)
├── android/          # Kotlin Android APK (com.exhaustive.explorer)
│   └── app/src/main/java/com/exhaustive/explorer/
│       ├── tier1_a11y/   # Accessibility (a11y) — baseline
│       ├── tier2_grid/   # Pixel Grid Sampling
│       ├── tier3_cv/     # Computer Vision + OCR
│       ├── tier4_probe/  # Differential Probe (verify)
│       ├── tier5_vlm/    # Vision Language Model (fallback)
│       ├── engine/       # ExplorerEngine, DFS, PathReplayer
│       ├── core/         # StateGraph, ScreenFingerprint, ScreenCapture
│       ├── guard/        # DangerousActionGuard, DialogDismisser
│       ├── input/        # GestureDispatcher, TextInputSampler
│       ├── service/      # ExplorerAccessibilityService 진입점
│       └── ui/           # Jetpack Compose UI
├── server/           # Python FastAPI 백엔드
├── web/              # React + Vite + TypeScript 대시보드
└── scripts/          # 운영 / 셋업 스크립트
```

---

## 빠른 시작 (더블클릭만 하면 됨)

```
1. setup.bat  더블클릭   ← 최초 1회 (conda env + pip + npm)
2. dev.bat    더블클릭   ← 이후 매번 (server + web + adb reverse)
3. 브라우저: http://localhost:5173
```

Android APK 는 Android Studio 에서 `android/` 폴더 열고 Run.

PowerShell 직접 사용하려면:
```powershell
.\scripts\dev.ps1                       # default conda env "explorer"
.\scripts\dev.ps1 -CondaEnv none        # system Python
.\scripts\dev.ps1 -BindHost 10.10.5.20  # LAN 모드
```

자세한 셋업·환경 변수: [`scripts/README.md`](scripts/README.md), [`android/README.md`](android/README.md), [`server/README.md`](server/README.md), [`web/README.md`](web/README.md).

---

## 핵심 문서

| 문서 | 목적 |
|---|---|
| [`docs/ARCHITECTURE.md`](docs/ARCHITECTURE.md) | 5-Tier 아키텍처 상세 |
| [`docs/LIMITATIONS.md`](docs/LIMITATIONS.md) | 12개 기술 영역의 사각지대 정리 |
| [`docs/COUNTERMEASURES.md`](docs/COUNTERMEASURES.md) | 한계별 극복 방안 + 우선순위 |
| [`docs/ROADMAP.md`](docs/ROADMAP.md) | Phase 계획 |
| [`docs/report.html`](docs/report.html) | 한눈에 보는 대시보드 (보고용) |

---

## 보안 경계

- APK: `network_security_config.xml` 로 cleartext 를 `127.0.0.1 / localhost` 만 허용 → 외부 egress 불가
- 서버: VLM 호출은 **사내 Ollama 화이트리스트 호스트** 한정
- 단말에서 회수한 스크린샷/이벤트는 `.gitignore` 로 commit 차단 (`runs/`, `data/`, `server/data/`)
- 키스토어 (`*.jks`, `*.keystore`) commit 차단

---

## 라이선스

내부 검토용 (사내). 외부 배포 전 별도 검토 필요.
