# Phase Log — 단계별 커버리지 / 한계 / 기술 기록

> **본 문서의 운영 규칙**:
> 각 Phase 마다 다음 **3 가지를 반드시 기록**한다 (팀장 mandate).
> 1. **커버리지 (Coverage)** — 본 단계가 동작 가능하게 한 것
> 2. **한계 (Limitations)** — 본 단계로는 안 되는 것 + 왜 (다음 단계로 미루는 명시적 이유)
> 3. **기술 스택 (Technology)** — 사용한 API / 라이브러리 / 알고리즘 / 학술 근거
>
> Phase 시작 시 초안 작성, Phase 완료 시 실제 결과로 갱신. 한 Phase 도 누락 금지.

---

## 0. 색인 (한눈에)

| Phase | 상태 | 핵심 산출물 | 커버리지 한 줄 | 가장 큰 한계 |
|---|---|---|---|---|
| 0 — Bootstrap | ✅ | 빈 APK + 문서 8 종 + scaffolds | "빌드 / 설치 / 동작 가능" | 실제 탐색 X |
| 1 — Autonomous DFS | ✅ | 19 Kotlin 파일 + autonomous 모드 + UI 토글 | "Samsung Notes 자율 탐색 + 안전 차단" | Tier 2/3/5 미구현 |
| 2 — Blind Spot 보완 | ⏳ 계획 | Tier 2/3/5 + 온디바이스 ML | "Canvas/시각 속성 보강" | — |
| 3 — System 연계 | ⏳ 계획 | dumpsys / logcat cross-check | "탐색 ↔ 추적 정합성" | — |

---

## Phase 0 — Bootstrap ✅

**기간**: 2026-05-13 ~ 14 (8 commits)
**산출물**: 레포 셋업 + 문서 8 종 + 4 컴포넌트 scaffold + dev launcher

### 커버리지 (이 단계로 가능해진 것)

| 영역 | 동작 |
|---|---|
| **Android APK** | `gradlew assembleDebug` 빌드 성공. 빈 화면 + AccessibilityService 등록만 |
| **FastAPI Server** | `/api/health` 200 OK. `/api/runs` / `/api/coverage` stub |
| **Web Dashboard** | Vite 5 + React 18. `/api/health` 폴링 health badge |
| **Scripts** | `dev.ps1` 한 줄로 server + web + adb reverse 동시 기동 |
| **문서** | 8 종 (LIMITATIONS / COUNTERMEASURES / ARCHITECTURE / ROADMAP / SAMSUNG_NOTES_HARD_CASES / RELATED_WORK / UI_INFO_SOURCES / report.html) |
| **실측 캡처** | Samsung Notes 5 케이스 + 메타 발견 3 가지 |

### 한계 (이 단계로 안 되는 것 + 이유)

| 안 되는 것 | 이유 | 다음 단계 |
|---|---|---|
| 실제 UI 탐색 | 탐색 알고리즘 코드 0 | Phase 1 |
| a11y 이벤트 처리 | Service 가 빈 껍데기 | Phase 1 |
| 데이터 수집 / 저장 | storage 모듈 미구현 | Phase 1 후반 |
| Server ↔ Android 통신 | snapshot push 미구현 | Phase 2 |
| 시각화 (run map / coverage heatmap) | web 컴포넌트 미구현 | Phase 2 |

### 기술 스택

| 카테고리 | 사용 기술 | 비고 |
|---|---|---|
| Build | Gradle 8.11.1 + Kotlin 2.0.21 + AGP 8.7.3 | Wrapper jar 포함 |
| Android baseline | minSdk=31 (Android 12) / targetSdk=35 / compileSdk=35 | SM-S947U1 / Android 16 검증 환경 |
| UI | Jetpack Compose BOM 2024.12.01 + Material3 | activity-compose:1.9.3 |
| Server | Python 3.11+ / FastAPI 0.115+ / Pydantic 2.9+ / Hatchling | uvicorn + httpx |
| Web | Vite 5.4 + React 18.3 + TypeScript 5.7 | strict mode, Bundler resolution |
| 보안 | network_security_config.xml (loopback only) + data_extraction_rules.xml | 외부 egress 차단 |
| 한글 폰트 | Pretendard CDN | report.html / web 공통 |
| 학술 근거 (문서) | DroidBot / APE / Fastbot / TimeMachine / Mobile-Agent | RELATED_WORK.md 정리 |

### 출력 산출 위치
- 코드: `android/`, `server/`, `web/`, `scripts/`
- 문서: `docs/`
- 실측 캡처: `docs/images/case-{01,02,04,06,10}-{screen,dump}.{png,xml}`

---

## Phase 1 — Autonomous DFS ✅

**기간**: 2026-05-16 (3 commits, 19 Kotlin 파일, ~2,200 lines)
**산출물**: 자율 탐색 가능 APK + MainActivity UI 토글 + DEMO.md

### 커버리지 (이 단계로 가능해진 것)

#### 1. UI 정보 수집
| 모듈 | 동작 |
|---|---|
| `tier1_a11y/MultiWindowCollector` ⭐ | `AccessibilityService.getWindows()` 반복 → main + popup + IME 통합. `uiautomator dump` 의존 제거 |
| `tier1_a11y/NodeTraversal` | BFS, MAX_NODES=5000, 5 액션 추론 (click/longclick/scroll/focus/edit) |
| `core/ScreenInfo` + `WindowInfo` | 단일 스냅샷 통합 표현. orientation / IME 가시성 추출 |
| `core/ScreenCapture` | `takeScreenshot()` (API 30+) — Tier 2/3/4/5 입력 source |

#### 2. State 식별 + 그래프
| 모듈 | 동작 |
|---|---|
| `core/ScreenFingerprint` | strict + loose 두 단계 해시. 텍스트/숫자/시간 정규화, 좌표 8px 양자화 |
| `core/StateGraph` | thread-safe RW lock. upsert / popNextAction / addEdge / snapshot |

#### 3. 액션 수행
| 모듈 | 동작 |
|---|---|
| `input/GestureDispatcher` | tap / longpress / swipe / scroll. coroutine wrap. global action (BACK/HOME/RECENTS) |
| `input/TextInputSampler` | `ACTION_SET_TEXT` IME 우회 텍스트 주입 (NodeInfo direct 만; cache 도입 후 candidate 버전) |

#### 4. 안전 + 자동 처리
| 모듈 | 동작 |
|---|---|
| `guard/DangerousActionGuard` | 결제/송금/인증/SMS/삭제/로그아웃/외부앱 26 키워드. NFC 정규화. 화면 단위 evacuate |
| `guard/DialogDismisser` | 시스템 popup 자동 dismiss (permission / 광고 / 알림). 한국어/영어 양쪽 |
| `tier4_probe/DifferentialProbe` | bitmap pixel diff (8px 샘플링, ARGB threshold). HOT/WARM/COLD 분류 |

#### 5. 백트래킹
| 모듈 | 동작 |
|---|---|
| `engine/PathReplayer` | BACK N회 → Home → relaunch → 좌표 replay ladder |
| `engine/ExplorerEngine.autonomous` | DFS 루프 + budget(60s 기본) + max-no-progress 5회 → home/relaunch |

#### 6. 진입점 + UI
| 모듈 | 동작 |
|---|---|
| `service/ExplorerAccessibilityService` | INSTANCE singleton + start/stop/snapshot. passive + autonomous |
| `ui/MainActivity` | Compose 토글 + 1s 폴링 통계. target package / budget 입력 |

#### 7. 시연 가능한 시나리오 (DEMO.md)
- A. **Passive** — 사용자 만지는 동안 fp/StateGraph 누적
- B. **Autonomous** — Samsung Notes 60s 자율 DFS, 화면 ≥10 발견 목표
- C. **Safety** — 위험 액션 차단 + 권한 dialog 자동 허용
- D. **Audit** — report.html 한 페이지 보고

### 한계 (이 단계로 안 되는 것 + 이유)

#### 미구현 Tier
| Tier | 미구현 항목 | 영향 |
|---|---|---|
| **Tier 2** Pixel Grid | a11y 미커버 영역 격자 좌표 자동 후보 생성 | OpenGL / Canvas 영역의 후보 0 |
| **Tier 3** CV + OCR | 색상·아이콘 시각 속성 검출 / 텍스트 영역 OCR | Case 4 (색상 9 swatch) 같은 시각 속성 사각지대 미해결 |
| **Tier 5** VLM | stuck 시 fallback inference | 정체 화면에서 다음 액션 추론 없음 |

#### 미구현 핵심 기능
| 항목 | 이유 | 다음 단계 |
|---|---|---|
| `NodeInfoCache` | AccessibilityNodeInfo 객체 lifecycle 관리 복잡 | Phase 2 |
| `performAction()` 기반 클릭 | NodeInfoCache 의존 | Phase 2 (좌표 fallback 으로 운영 중) |
| resource-id 우선 replay | NodeInfoCache 의존 | Phase 2 |
| Server snapshot push | HTTP client + JSON 직렬화 | Phase 2 |
| Web dashboard 실시간 시각화 | server push 의존 | Phase 2 |
| WebView 내부 DOM 접근 | Chrome DevTools bridge | Phase 3 |
| dumpsys cross-check | shell 명령 호출 + 파서 | Phase 3 |
| Compose semantics reflection | hidden API + eng 권한 | Phase 3 |
| 학습 / 모델 기반 우선순위 | 학습 데이터 / runtime ML | Phase 4 (선택) |

#### 정량 한계
| 항목 | 현재 능력 | 천장 |
|---|---|---|
| Activity coverage (Samsung Notes 1 앱) | 40~55% (추정) | 65~75% (Phase 3 까지 누적) |
| 화면 fingerprint 안정성 | 정적 화면 ≥ 95% / 동적 (리스트) ≥ 80% | — |
| Replay 성공률 | 단순 경로 ≥ 80% / 인증 후 경로 0% | 본질적 |
| 위험 액션 차단 | 26 키워드 매칭 + 화면 단위 evacuate | OCR 결합 시 향상 |

#### 명시적 Out-of-Scope (audit 보고용)
- 로그인이 필요한 화면 → 자동 인증 불가능
- 결제 / SMS 전송 → DangerousActionGuard 차단
- OpenGL 게임 UI → a11y 사각지대 + CV/VLM 도 비용 과다
- WebView 내부 DOM → 별도 도메인 (Chrome DevTools)
- S Pen hover preview → 짧은 표시, 캐치 비용 ↑
- 외부 앱 이탈 후 → Foreground watcher 가 즉시 home

### 기술 스택

#### Android API
| API | 용도 | Phase 0 → 1 추가 |
|---|---|---|
| `AccessibilityService.getWindows()` | 모든 활성 window 접근 ⭐ | NEW |
| `AccessibilityService.takeScreenshot(displayId, executor, cb)` | API 30+ 스크린샷 | NEW |
| `AccessibilityService.dispatchGesture(gesture, cb, handler)` | 합성 터치 | NEW |
| `AccessibilityService.performGlobalAction(GLOBAL_ACTION_*)` | BACK/HOME/RECENTS | NEW |
| `AccessibilityNodeInfo.performAction(ACTION_SET_TEXT)` | IME 우회 텍스트 입력 | NEW |
| `AccessibilityEvent.TYPE_WINDOW_*_CHANGED` | 화면 변화 이벤트 | NEW |
| `GestureDescription` + `StrokeDescription` + `Path` | 터치 경로 합성 | NEW |
| `Bitmap.wrapHardwareBuffer()` + `HardwareBuffer.close()` | 스크린샷 GPU 메모리 처리 | NEW |

#### Kotlin / JVM
| 기술 | 용도 |
|---|---|
| `kotlinx.coroutines` (1.9.0) | 비동기 + structured concurrency. `suspendCancellableCoroutine` 으로 callback API wrap |
| `kotlin.concurrent.read/write` extension | RW lock idiom |
| `java.util.concurrent.locks.ReentrantReadWriteLock` | StateGraph thread safety |
| `java.security.MessageDigest("SHA-256")` | 두 단계 fingerprint |
| `java.text.Normalizer.Form.NFC` | 한글 자모 결합 정규화 (한글 "허용" 매칭 사고 방지) |
| `kotlin.collections.ArrayDeque` | pendingActions queue (DFS frontier) |
| sealed class / data class | NodeRef / TickResult / ProbeResult.Verdict 타입 안전 분기 |

#### 알고리즘
| 알고리즘 | 모듈 | 출처 |
|---|---|---|
| BFS tree walk (MAX_NODES guard) | NodeTraversal | 표준 |
| Two-level fingerprint hash (strict + loose) | ScreenFingerprint | DroidBot 의 strict + APE 의 model refinement 의 절충 |
| DFS with frontier + backtrack ladder | ExplorerEngine | A³E (OOPSLA 2013) 직계 |
| Pixel diff with regional masking | DifferentialProbe | 자체 (perceptual hash 는 Phase 2) |
| NFC unicode normalization 기반 라벨 매칭 | DangerousActionGuard, DialogDismisser | 자체 (한글 사고 사례) |

#### Compose UI
| 컴포넌트 | 용도 |
|---|---|
| `Surface + Column + Card` | 패널 레이아웃 |
| `OutlinedTextField` | target package / budget 입력 |
| `Button + OutlinedButton` | 시작/중지/초기화 |
| `LaunchedEffect + delay(1000L)` | 1초 폴링 |
| `MutableStateFlow.collectAsState()` | snapshot 반응형 갱신 |
| `Modifier.fillMaxWidth().weight(1f)` | 좌우 균등 분할 |

#### 학술 근거 (코드 → 논문 매핑)
| 본 코드 | 학술 참조 | 차별점 |
|---|---|---|
| `MultiWindowCollector.getWindows()` | DroidBot 도 사용 — Li et al. ICSE 2017 | 본 도구는 명시적 module 화 + Case 1/6/10 실측 입증 |
| `ScreenFingerprint` 두 단계 | DroidBot (strict) / APE (refinement) — Gu et al. ICSE 2019 | 본 도구는 둘 다 동시 산출 |
| `ExplorerEngine.DFS` | A³E — Azim & Neamtiu OOPSLA 2013 | 본 도구는 budget + max-no-progress + evacuate 보강 |
| `PathReplayer.relaunch` | TimeMachine 의 정공법 — Dong et al. ICSE 2020 (snapshot 까진 아직) | snapshot 보다 가벼움 (Phase 3 검토) |
| `DangerousActionGuard` | (학계 거의 부재) | 본 도구 자체 발명 |
| `DifferentialProbe` | (학계 일부 시도, 명시적 module 화 드묾) | 본 도구가 Tier 격상 |
| autonomous DFS 운영 | Fastbot (ByteDance ESEC/FSE 2020) | 본 도구는 결정적, Fastbot 은 RL |

#### 의존성 (build.gradle.kts)
- `androidx.core:core-ktx:1.15.0`
- `androidx.lifecycle:lifecycle-runtime-ktx:2.8.7`
- `androidx.activity:activity-compose:1.9.3`
- Compose BOM 2024.12.01 (ui + material3 + tooling-preview)
- `org.jetbrains.kotlinx:kotlinx-coroutines-android:1.9.0` (NEW)

### 출력 산출 위치
- 코드: `android/app/src/main/java/com/exhaustive/explorer/` (19 .kt)
- 문서 갱신: `docs/ROADMAP.md` (11/11 ✅), `docs/report.html` (Phase 1 표시)
- 신규 문서: `docs/DEMO.md` (시연 가이드)

---

## Phase 2 — Blind Spot 보완 ⏳ (계획)

**예정 산출물**: Tier 2 (Pixel Grid) + Tier 3 (CV + OCR) + Tier 5 (VLM on-device) + server snapshot push

### 커버리지 (예정)
- a11y 미커버 영역 (Canvas / OpenGL) 좌표 후보 자동 생성 — Case 2 보완
- 시각 속성 라벨링 (색상 / 아이콘) — Case 4 보완
- stuck 시 VLM fallback (Gemini Nano on-device via AICore, 또는 Gemma 2 2B via MediaPipe)
- NodeInfoCache 로 `performAction()` 활용 — 좌표 의존 제거
- Server snapshot push (HTTP) + web 실시간 시각화

### 한계 (예정 — Phase 2 종료 시점에도 안 되는 것)
- WebView 내부 DOM — Phase 3
- dumpsys cross-check — Phase 3
- Compose semantics reflection — Phase 3
- 학습 기반 우선순위 (RL/imitation) — Phase 4 검토만

### 기술 스택 (예정)
| 영역 | 후보 기술 |
|---|---|
| Pixel Grid | TFLite + 좌표 후보 생성 / coarse-to-fine 256→64→16px |
| CV Detection | OpenCV (사각형/원 검출) 또는 TFLite SSDLite |
| OCR | ML Kit Text Recognition (Korean) — 30MB 모델, <100ms |
| VLM on-device | Google AICore (Gemini Nano) **또는** MediaPipe LLM Inference (Gemma 2 2B) |
| HTTP push | OkHttp (Square) 또는 Android 내장 HttpURLConnection + JSONObject |
| JSON | kotlinx.serialization 또는 Gson |

**오픈 의사결정**:
1. CV — OpenCV (full) vs TFLite (작음, NPU 가속)
2. VLM — Gemini Nano (S26 호환성 확인 필요) vs Gemma MediaPipe (안정, 다국어)
3. JSON — kotlinx (Kotlin 친화) vs Gson (성숙)

---

## Phase 3 — System 연계 ⏳ (계획)

**예정 산출물**: dumpsys 파서 + Compose semantics reflection + Ground-truth 정합성 검증 + Coverage 회귀 KPI

### 커버리지 (예정)
- `dumpsys window` / `activity` / `SurfaceFlinger` / `gfxinfo` cross-check
- `WindowManagerGlobal` reflection (eng 빌드) — Compose semantics 누락 보강
- 탐색 결과 ↔ logcat 의 system event 정합성 측정
- 단말 / 앱 inventory 단위 coverage KPI

### 한계 (Phase 3 후에도)
- 로그인 / 인증 / 결제 — 영구 out-of-scope
- 게임 UI (실시간 OpenGL) — Tier 5 VLM 도 비용 한계
- OS 메이저 업그레이드 시 dumpsys 포맷 변경 — 파서 재작성

### 기술 스택 (예정)
| 영역 | 후보 |
|---|---|
| Shell exec | Kotlin `Runtime.getRuntime().exec("dumpsys ...")` |
| Reflection | `Class.forName("android.view.WindowManagerGlobal")` + private method invoke |
| 정합성 점수 | Jaccard similarity between (탐색이 본 component set) ↔ (실제 logcat 의 ResumedActivity set) |
| 회귀 추적 | 시계열 DB (SQLite) + 주간 자동 sweep cron |

---

## 본 문서 갱신 정책

| 시점 | 누가 | 무엇을 |
|---|---|---|
| Phase 시작 시 | 리드 | 다음 Phase 섹션 초안 작성 (커버리지/한계/기술 예정) |
| 산출물 1 개 완성 시 | 개발자 | 해당 Phase 의 커버리지 항목에 ✓ 표시 |
| Phase 종료 시 | 리드 | 실제 결과로 한계 / 기술 갱신. 다음 Phase 초안 시작 |
| 예상 못한 한계 발견 시 | 누구든 | 즉시 한계 섹션에 추가 + 그 사유 명시 |

→ **본 문서는 "본 도구가 무엇을 할 수 있는가" 의 단일 진실 (single source of truth).**
   외부 (팀장 보고 / 신규 합류자 / 다음 분기 계획) 질문 시 이 문서 한 개만 보면 답이 나와야 한다.
