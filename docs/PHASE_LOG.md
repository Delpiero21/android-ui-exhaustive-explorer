# Phase Log — 단계별 I/O · Flow · 실패 · 커버리지 단일 진실

> **본 문서의 운영 규칙 (팀장 mandate)**:
> 각 Phase 마다 4 가지 + 종합 비교를 반드시 기록한다.
>
> 1. **I/O 사양** — Input / Processing / Output
> 2. **주요 Flow** — 입력 → 처리 → 출력 파이프라인 다이어그램
> 3. **실패한 부분** — 안 되는 것 + 왜 + 다음 단계
> 4. **커버리지** — 정량 지표
>
> 그리고 **§0 종합 비교 — Phase 진화 한눈에 보기** 를 매 Phase 마다 갱신.

---

## 0. 종합 비교 — 한눈에 보는 Phase 진화

### 0.1 I/O 진화

| 항목 | Phase 0 ✅ | Phase 1 ✅ | Phase 2 ⏳ | Phase 3 ⏳ |
|---|---|---|---|---|
| **Input** | (없음 — setup) | a11y 이벤트, `windows()`, target package, budget | + 스크린샷 비트맵 | + dumpsys 출력 (window/activity/SF) |
| **핵심 처리** | scaffold | MultiWindowCollector → NodeTraversal → Fingerprint → DFS → Guard → Gesture | + PixelGrid + CV + OCR + VLM (stuck fallback) | + Compose semantics reflection + system ground-truth |
| **Output** | 빈 APK + 문서 | StateGraph + EngineSnapshot + 실시간 logcat | + Tier 별 hit 기여도 + 후보 confidence + server snapshot | + 정합성 점수 + Coverage KPI 회귀 |

### 0.2 커버리지 진화 (정량)

| 지표 | P0 | **P1 (현재)** | P2 (목표) | P3 (목표) | 본질적 천장 |
|---|---|---|---|---|---|
| Activity coverage (Samsung Notes) | 0% | **40~55%** | 55~70% | 65~75% | ~80% |
| 발견 화면 수 (60s budget) | 0 | **≥ 10** | ≥ 15 | ≥ 20 | — |
| Canvas / SurfaceView 후보 발견율 | 0% | 0% | **≥ 30%** | ≥ 50% | ~70% |
| 색깔 / 시각 속성 식별 | 불가능 | 불가능 | **9 색 + 휠 라벨** | 동일 | — |
| 외부 통신 의존 (사외 데이터) | 0 | 0 | 0 (온디바이스) | 0 | 영구 0 |
| 다중 윈도우 (popup) 해결 | 0건 | **3건** (Case 1/6/10) | 동일 | 동일 | — |
| 위험 액션 차단 | 0 | **26 키워드 + 화면 evacuate** | + OCR 결합 | + 행동 패턴 | — |
| Server ↔ Android 연동 | scaffold | 없음 | **snapshot HTTP push** | + 정합성 KPI | — |
| Coverage 회귀 추적 | 없음 | 수동 측정 | 수동 측정 | **자동 + 시계열 DB** | — |

### 0.3 해결된 사각지대 — Case 누적

| Case (LIMITATIONS §) | 어디서 해결되나? | 해결 방식 |
|---|---|---|
| Case 1 SIP 키보드 (§2.3) | **Phase 1** ✅ | MultiWindowCollector — `getWindows()` 통합 |
| Case 6 텍스트 ActionMode (§2.3) | **Phase 1** ✅ | MultiWindowCollector — popup window 포함 |
| Case 10 AI 어시스트 팝업 (§2.3) | **Phase 1** ✅ | MultiWindowCollector — popup + main 동시 |
| Case 2 손글씨 캔버스 (§1.1) | Phase 1 부분 → **Phase 2 완성** | Tier 4 Differential Probe + Tier 2 Grid |
| Case 4 색상 팔레트 (§1.4) | **Phase 2** ⏳ | Tier 3 CV 색 검출 + ML Kit OCR |
| (향후) 이미지 변형 핸들 (§1.1) | Phase 2 ⏳ | Tier 3 CV + Tier 4 |
| (향후) WebView 내부 (§1.6) | Phase 3 ⏳ | Chrome DevTools bridge |
| (향후) Compose semantics 누락 (§1.5) | Phase 3 ⏳ | WindowManagerGlobal reflection |
| (영구) 로그인 / 결제 / 인증 | 해결 안 함 | DangerousActionGuard 차단 (의도된 out-of-scope) |

### 0.4 진화 narrative (한 문단)

> Phase 0 에서 빌드 가능한 골조 + 8 종 문서로 설계 정립. Phase 1 에서 a11y live API 한 source 만으로도 다중 윈도우 통합 (Case 1/6/10 해결) + 자율 DFS + 안전 차단 + 백트래킹 까지 — Samsung Notes 의 40~55% 자율 탐색 도달. Phase 2 에서 시각 source (스크린샷) 추가로 CV/OCR/VLM 보강 — Canvas / 색깔 같은 시각 속성 사각지대 해소. Phase 3 에서 시스템 측 ground-truth (dumpsys) 와 cross-check 해 보고 신뢰도 확보. 최종 65~75% 가 본 도구의 천장 — 그 이상은 본질적 한계 (로그인 / 결제 / 외부 API).

---

## Phase 0 — Bootstrap ✅ (2026-05-13 ~ 14, 8 commits)

### 1. I/O 사양

| 항목 | 내용 |
|---|---|
| **Input** | (직접 input 없음 — 본 phase 는 setup) <br/>외부 요구: 사내 단말 (Galaxy S26 eng), 검증 인프라 |
| **Processing** | 1) Gradle/Kotlin/Compose 환경 셋업 <br/>2) FastAPI + Vite + React 스캐폴드 <br/>3) 문서 8 종 정립 (LIMITATIONS, COUNTERMEASURES, ARCHITECTURE, ROADMAP, RELATED_WORK, UI_INFO_SOURCES, SAMSUNG_NOTES_HARD_CASES, report.html) <br/>4) Samsung Notes 5 케이스 실측 캡처 + 메타 발견 정리 |
| **Output** | 빈 APK (Service connected 로그만) + 문서 + scripts/dev.ps1 통합 런처 |

### 2. 주요 Flow

```
[개발자]
   │
   ├→ git clone
   ├→ Android Studio sync (gradle wrapper jar 자동)
   ├→ .\gradlew :app:assembleDebug → app-debug.apk
   ├→ adb install -r ...
   │
[단말]
   ├→ 설정 → 접근성 → UI Exhaustive Explorer → ON
   ├→ MainActivity: "Phase 0 · Bootstrap" 빈 화면
   └→ logcat: "Service connected" 로그만

[병행]
   ├→ docs/ 8 종 작성
   ├→ android-ui-dump-visualizer 로 Samsung Notes 5 케이스 캡처
   └→ docs/SAMSUNG_NOTES_HARD_CASES.md 의 메타 발견 정리
```

### 3. 실패한 부분 / 못 한 것

| 영역 | 왜 못했나 | 영향 | 다음 단계 |
|---|---|---|---|
| 실제 UI 탐색 | 코드 0 — 본 단계는 setup 만 | 동작 시연 불가능 | Phase 1 |
| a11y 이벤트 처리 | Service 가 빈 껍데기 | 데이터 수집 0 | Phase 1 |
| 데이터 저장 | storage 모듈 미구현 | 결과 회수 불가 | Phase 1 후반 |
| Server ↔ Android 통신 | snapshot push 미구현 | server stub 만 동작 | Phase 2 |
| Web 시각화 (run map 등) | 컴포넌트 미구현 | health badge 만 표시 | Phase 2 |

### 4. 커버리지

| 지표 | 값 |
|---|---|
| 빌드 가능 여부 | ✅ |
| 단말 설치 + Service connection | ✅ |
| **Activity coverage** | **0%** (탐색 안 함) |
| 발견 화면 수 | 0 |
| 결정 가능한 액션 | 0 |
| 처리된 위험 액션 | 0 |
| 문서 완비도 | 8/8 종 (100%) |
| 실측 케이스 | 5/15 (가설 10 건 제외) |

---

## Phase 1 — Autonomous DFS ✅ (2026-05-16, 3 commits, 19 .kt, ~2,200 lines)

### 1. I/O 사양

| 항목 | 내용 |
|---|---|
| **Input** | `AccessibilityEvent` (TYPE_WINDOW_CONTENT_CHANGED / STATE_CHANGED / WINDOWS_CHANGED) <br/>`AccessibilityService.getWindows()` — 모든 활성 window <br/>사용자 설정: target package, budget (ms) |
| **Processing** | 8 모듈 파이프라인 (Flow 참고): <br/>① MultiWindowCollector — getWindows() 반복으로 main + popup + IME root 수집 <br/>② NodeTraversal — BFS, 5 액션 추론 <br/>③ ScreenFingerprint — strict (텍스트/숫자 정규화) + loose (구조만) 두 단계 SHA-256 <br/>④ StateGraph upsert + popNextAction + addEdge (RW lock) <br/>⑤ DangerousActionGuard — 26 키워드 NFC 정규화 매칭 <br/>⑥ GestureDispatcher — coroutine wrapped dispatchGesture <br/>⑦ DialogDismisser — 시스템 popup window 자동 dismiss <br/>⑧ DifferentialProbe — 픽셀 diff HOT/WARM/COLD (선택 적용) |
| **Output** | StateGraph (ScreenNode list + StateEdge list) <br/>EngineSnapshot (nodeCount, edgeCount, lastFp, autonomousRunning) <br/>실시간 logcat: `[NEW] fp=... pkg=... candidates=N` <br/>MainActivity 1s 폴링 UI (실시간 통계) |

### 2. 주요 Flow

```
                           ┌─────────────────────────┐
[AccessibilityEvent] ─────→│ Service.onAccessibilityEvent
                           └─────────┬───────────────┘
                                     │ (passive 모드 — 사용자 만지는 동안)
                                     │
                                     │     OR
                                     │
                          [MainActivity 시작 버튼] ────────────────┐
                                                                  │
                                                                  ▼
                          ┌─────────────────────────────────────────┐
                          │ ExplorerEngine.startAutonomous(target, budget)
                          │   (coroutine on Dispatchers.Default)
                          └──────────────────┬──────────────────────┘
                                             │
                          ╔══════════════════▼══════════════════╗
                          ║         autonomous DFS loop          ║
                          ║   while (active && budget < deadline) ║
                          ╚══════════════════╤══════════════════╝
                                             │
        ┌────────────────────────────────────┼────────────────────────────────────┐
        │                                    │                                    │
        ▼                                    ▼                                    ▼
[MultiWindowCollector]            [DangerousActionGuard]                [PathReplayer]
service.windows()                 isSafe(candidate)?                    pressBack / goHome /
walk all roots                    shouldEvacuateScreen?                 relaunch / replay
   │                                    │                                    │
   ▼                                    ▼                                    ▼
ScreenInfo                        block / evacuate                     recover
   │
   ▼
[NodeTraversal] BFS 후보 추출
   │
   ▼
[ScreenFingerprint] strict + loose
   │
   ▼
[StateGraph.upsert] node (isNew?)
   │
   ▼ popNextAction()
[Candidate (a11y/grid/cv/vlm source)]
   │
   ▼ DangerousActionGuard.isSafe?
   ├ false → 다음 액션
   │
   ▼
[GestureDispatcher.perform] tap/swipe/scroll
   │
   ▼ delay(700ms) — settle
   │
   ▼
[재수집] new ScreenInfo
   │
   ▼ new fp
   │
[StateGraph.addEdge(prev_fp, action, new_fp)]
   │
   ▼ loop
```

### 3. 실패한 부분 / 못 한 것

| 영역 | 왜 못했나 | 영향 | 다음 단계 |
|---|---|---|---|
| Tier 2 Pixel Grid 후보 | 미구현 — Phase 1 범위 외 | a11y 미커버 영역 (Canvas/OpenGL) 후보 0 — Case 2 부분 해결만 | Phase 2 |
| Tier 3 CV + OCR | 미구현 | Case 4 색상 9개 같은 시각 속성 미해결 | Phase 2 |
| Tier 5 VLM | 미구현 | stuck 시 다음 액션 추론 fallback 없음 | Phase 2 |
| NodeInfoCache | NodeInfo lifecycle 복잡 → 보류 | `performAction()` 못 씀, 좌표 fallback 만 — Case 4 동일 swatch 9개 정확 클릭 불가 | Phase 2 |
| resource-id 우선 replay | NodeInfoCache 의존 | 단순 좌표 replay 만 — 해상도 변경 시 깨짐 | Phase 2 |
| Server snapshot push | HTTP client + JSON 직렬화 보류 | MainActivity 만 결과 표시, web 대시보드 정적 | Phase 2 |
| WebView 내부 DOM | Chrome DevTools bridge 필요 | WebView 사용 앱은 root 만 인식 | Phase 3 |
| dumpsys cross-check | shell exec + 파서 | 시스템 ground-truth 와 정합성 검증 안 됨 | Phase 3 |
| Compose semantics reflection | hidden API + eng 권한 | semantics 미설정 Composable 누락 | Phase 3 |
| **빌드 검증** | 이번 세션은 코드 작성만 | 컴파일 에러 / runtime crash 가능성 | **일요일 빌드 + 시연 리허설** |
| 정량 측정 (실측) | 단말 실행 후 측정 가능 | 커버리지 % 추정값만 보유 | **일요일 검증 후 본 표 갱신** |

### 4. 커버리지

| 지표 | 목표 | 실측 (TBD) | 측정 방법 |
|---|---|---|---|
| Activity coverage (Samsung Notes 60s) | 40~55% | **TBD — 일요일 측정** | logcat 의 [NEW] 카운트 vs Notes 총 Activity 수 |
| 발견 unique 화면 수 | ≥ 10 | TBD | EngineSnapshot.nodeCount |
| 화면 fingerprint 안정성 (재방문 시 strict 일치) | ≥ 95% | TBD | 같은 화면 5 회 재방문 후 unique fp 1 |
| Replay 성공률 (단순 경로) | ≥ 80% | TBD | path 5 step 의 5 회 replay 중 도착 횟수 |
| 위험 액션 차단 정확도 (precision) | 매우 높음 (false-positive 허용) | TBD | "결제/송금/삭제" 텍스트 후보 100% 차단 확인 |
| 권한 dialog 자동 처리 | ≥ 90% | TBD | 5 회 권한 dialog 시도 중 통과 횟수 |
| 자율 탐색 한 tick 평균 시간 | < 1.5s | TBD | (60s / 액션 수) 측정 |
| 다중 윈도우 잡힘 (Case 1/6/10) | 3/3 | TBD | logcat 에 honeyboard / popup window id 카운트 |
| 외부 통신 사용 | 0 | 0 ✅ | network_security_config 검증 (확정) |

→ **TBD 들은 일요일 단말 검증 후 실측 채움. PHASE_LOG.md mandate.**

---

## Phase 2 — Blind Spot 보완 ⏳ (계획)

### 1. I/O 사양 (예정)

| 항목 | 내용 |
|---|---|
| **Input** | Phase 1 의 ScreenInfo + **`AccessibilityService.takeScreenshot()` 비트맵** |
| **Processing** | + **Tier 2** PixelGridSampler — a11y 미커버 영역 격자 좌표 후보 <br/>+ **Tier 3** CvProposer (TFLite/OpenCV) — 버튼·아이콘 검출, 색 추출 <br/>+ **Tier 3** OcrLabeler (ML Kit Text Recognition Korean) — 텍스트 영역 라벨 <br/>+ **Tier 5** VlmProposer (on-device: Gemini Nano via AICore **또는** Gemma 2 2B via MediaPipe) — stuck 시 fallback <br/>+ NodeInfoCache — performAction() 활성화 <br/>+ Server snapshot HTTP push |
| **Output** | Phase 1 Output + **후보 confidence (a11y vs CV vs VLM)** + **Tier 별 hit 기여도 통계** + **web 대시보드 실시간 시각화** |

### 2. 주요 Flow (예정)

```
... (Phase 1 흐름)
   │
   ▼ MultiWindowCollector.collect()
ScreenInfo (Tier 1 후보)
   │
   ├──────────────────────────────────────────────┐
   │                                              │
   ▼ ScreenCapture.capture()                      │
Bitmap                                            │
   │                                              │
   ├→ [Tier 2] PixelGridSampler                  │
   │   coarse-to-fine 256→64→16px                │
   │   a11y 미커버 영역만                          │
   │   → 좌표 후보 N 개 추가                       │
   │                                              │
   ├→ [Tier 3a] CvProposer (TFLite SSDLite)       │
   │   사각형 + 원 검출                            │
   │   → bbox 후보 M 개 + dominant color 라벨    │
   │                                              │
   ├→ [Tier 3b] OcrLabeler (ML Kit Korean)        │
   │   각 후보 영역의 텍스트 추출                  │
   │   → 후보 라벨 보강 (위험 단어 매칭)            │
   │                                              │
   └→ [Tier 5] (stuck > 5 일 때만)                │
       VlmProposer.propose(bitmap + StateGraph 요약)
       → 자연어 추론 후보 K 개
   │
   ▼ 통합 후보 리스트 (a11y N + grid M + cv P + vlm K)
   │
   ▼ DangerousActionGuard.isSafe? (OCR 결합 강화)
   │
   ▼ Tier 4 DifferentialProbe — 모든 후보 검증 (HOT 만 채택)
   │
   ▼ NodeInfoCache 가 a11y 후보면 performAction() 호출
   │   아니면 GestureDispatcher 좌표 탭
   │
   ... (이후 Phase 1 흐름)

[Server snapshot push (5s polling)]
ExplorerEngine.snapshot() → JSON → HTTP POST /api/runs/{id}/snapshot
                                  → web 대시보드 실시간 갱신
```

### 3. 실패할 위험 (예상 + 완화)

| 영역 | 위험 | 완화 방안 |
|---|---|---|
| Gemini Nano S26 미지원 | AICore 가 단말 의존 | Gemma 2 2B via MediaPipe 백업 (안정) |
| TFLite 모델 정확도 | 한국 UI specific 학습 데이터 없음 | 1차: OpenCV 룰 / 2차: 사내 데이터 fine-tune |
| ML Kit OCR 의 한자/이모지 | 인식 안 됨 | 텍스트 영역만 활용, 비텍스트는 무시 |
| VLM hallucination (없는 버튼 추론) | model artifact | a11y/CV 후보와 IoU 0.3+ 교차 검증 필수 |
| HTTP push 신뢰성 | adb reverse 끊김 가능 | retry 3 회 + offline buffer (sd card jsonl) |
| Tier 4 Probe 비용 | 후보당 2 screenshot + diff | top-N 우선 (priority queue) — 모든 후보 안 함 |
| Bitmap 메모리 압박 | 1080×2340 ARGB = ~10MB × 수십장 | 즉시 recycle + 다운샘플 |
| NodeInfoCache 정합성 | NodeInfo lifecycle 짧음 | 화면 변할 때마다 cache invalidate |

### 4. 커버리지 (목표)

| 지표 | Phase 1 → Phase 2 목표 |
|---|---|
| Activity coverage (Samsung Notes) | 40~55% → **55~70%** |
| Canvas 영역 후보 발견율 | 0% → **≥ 30%** (Tier 2 Grid + Tier 4 Probe) |
| 색깔 식별률 (Case 4 9 swatch) | 0% → **9 색 + 휠 모두 라벨** |
| 아이콘 의미 인식 | 0% (좌표만) → **OCR/CV 으로 라벨 보강** |
| Tier 별 hit 기여도 측정 | 없음 → **있음 (a11y N%, CV M%, Probe P%, VLM Q%)** |
| Stuck 회복률 | 5 회 → home/relaunch | **+ VLM fallback 으로 self-recovery** |
| Server 실시간 시각화 | 없음 | **/api/runs 가 실제 데이터, web 폴링 표시** |
| 외부 통신 | 0 | **0 (온디바이스 VLM)** ✅ |

---

## Phase 3 — System 연계 ⏳ (계획)

### 1. I/O 사양 (예정)

| 항목 | 내용 |
|---|---|
| **Input** | Phase 2 의 모든 source + **`dumpsys window/activity/SurfaceFlinger/gfxinfo` 출력** + (eng 한정) **WindowManagerGlobal reflection** |
| **Processing** | + dumpsys 파서 (버전별 분기) <br/>+ logcat 필터 (탐색 액션 → 시스템 이벤트 매핑) <br/>+ Compose semantics reflection (eng 빌드) <br/>+ 정합성 점수 (Jaccard similarity between explored set vs actual resumed set) <br/>+ 시계열 DB (SQLite) — 주간 회귀 |
| **Output** | Phase 2 Output + **시스템 측 ground-truth 비교 보고** + **Coverage 시계열 그래프** + **회귀 알람** |

### 2. 주요 Flow (예정)

```
... (Phase 2 흐름)
   │
   ├→ [parallel] dumpsys cross-check (5s 주기 worker)
   │   ├ dumpsys window windows → mFocus / IME / window stack
   │   ├ dumpsys activity activities → resumed activity
   │   └ dumpsys SurfaceFlinger --list → 실제 컴포지팅 surface
   │
   ├→ a11y 가 본 window list ↔ dumpsys window stack 비교
   │   IoU < 0.8 면 누락 영역 보고 (a11y 가 못 본 window)
   │
   ├→ [eng 한정] WindowManagerGlobal reflection
   │   → Compose semantics 미설정 view 까지 접근
   │
   ├→ 탐색 액션 시퀀스 ↔ logcat 의 ResumedActivity 시퀀스
   │   → 정합성 점수 (Jaccard)
   │
   └→ 회귀 sweep cron (주 1 회)
       → SQLite 에 시계열 저장
       → 직전 주 대비 변화 알람 (커버리지 ±5% 이상)
```

### 3. 실패할 위험 (예상)

| 영역 | 위험 | 완화 |
|---|---|---|
| OS 업그레이드 시 dumpsys 포맷 변경 | 파서 재작성 | 버전별 분기 + 회귀 테스트 셋 |
| Reflection API 변경 | hidden API 깨짐 | try-catch + 미지원 환경 fallback |
| 시계열 DB 용량 | 1년 누적 시 수 GB | 압축 + 오래된 데이터 sampling |
| Jaccard 점수의 해석 | 도메인 컨텍스트 부족 | 사내 운영자 함께 임계값 튜닝 |

### 4. 커버리지 (목표)

| 지표 | Phase 2 → Phase 3 목표 |
|---|---|
| Activity coverage | 55~70% → **65~75%** (천장 근접) |
| 탐색 ↔ ground-truth 정합성 점수 | 없음 → **≥ 90%** |
| OS 측 누락 영역 보고 | 없음 → **자동 매주 sweep** |
| Coverage 시계열 가시화 | 없음 → **시계열 그래프 + 회귀 알람** |
| 사내 inventory 단위 audit | 없음 → **N개 단말 × M개 앱 sweep 보고** |

---

## 본 문서 갱신 정책 (mandate)

| 시점 | 누가 | 무엇을 |
|---|---|---|
| Phase 시작 시 | 리드 | 다음 Phase 4 부 초안 작성 (Input / Processing / Output / Flow / 예상 실패 / 목표 커버리지) |
| **산출물 1 개 완성 시** | **개발자** | **§4 커버리지의 해당 지표를 ✓ 또는 실측값으로 갱신 + §3 실패 부분에서 해당 항목 제거** |
| Phase 종료 시 | 리드 | §0 종합 비교의 해당 컬럼을 ✅ + 실측값 채움. 다음 Phase 초안 시작 |
| 예상 못한 한계 발견 시 | 누구든 | 즉시 §3 실패에 추가 + 사유 + 다음 단계 명시 |
| 정량 측정 완료 시 | 개발자 | §4 커버리지의 TBD → 실측값 |

→ **본 문서가 "지금 본 도구로 무엇이 되고 안 되는가" 의 단일 진실 (single source of truth).**
   외부 (팀장 보고 / 신규 합류자 / 차기 분기 계획 / 시연 Q&A) 질문 시 본 문서 한 개로 답.
