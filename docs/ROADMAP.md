# 로드맵

본 문서는 `android-ui-exhaustive-explorer` 의 Phase 별 산출물과 완료 기준을 정리한다.

핵심 원칙:
- 각 Phase 는 **검증 가능한 완료 기준**을 명시한다 ("동작한다"가 아닌 "X 가 Y 이상"의 정량값).
- Phase 종료 시 `LIMITATIONS.md` / `COUNTERMEASURES.md` / `report.html` 동기화한다.
- Phase 간 의존성: 0 → 1 → 2 → 3 순서. 단, **Phase 2 의 4 개 Tier 는 병렬 가능**.

---

## Phase 0 · Bootstrap (현재 진행 중)

> 레포 셋업과 설계 문서 정립. 코드는 스캐폴드만.

| # | 산출물 | 완료 기준 | 상태 |
|---|---|---|---|
| 0-1 | `.gitignore` + `.editorconfig` + `README.md` | 첫 커밋 푸시 | ✅ |
| 0-2 | `docs/` 5개 문서 (LIMITATIONS, COUNTERMEASURES, ARCHITECTURE, ROADMAP, report.html) | 모두 main 에 존재 | 🔄 |
| 0-3 | `android/` Gradle 스캐폴드 | `./gradlew :app:assembleDebug` 성공 | ⏳ |
| 0-4 | `server/` FastAPI 스캐폴드 | `GET /health` 가 200 OK | ⏳ |
| 0-5 | `web/` Vite + React 스캐폴드 | `npm run dev` 가 빈 페이지 띄움 | ⏳ |
| 0-6 | `scripts/dev.ps1` (server + web + adb reverse 한 줄 런처) | 명령 1 회로 3 컴포넌트 기동 | ⏳ |

**완료 시점 목표**: 5월 중

---

## Phase 1 · Baseline Explorer

> Tier 1 (a11y) + Tier 4 (Differential Probe) + 기본 DFS 엔진.
> 표준 UI 만 탐색해도 동작은 한다.

| # | 산출물 | 완료 기준 | 상태 |
|---|---|---|---|
| 1-A | **`core/` 공통 데이터 클래스** (Candidate, NodeRef, ScreenInfo, WindowInfo, CandidateAction, CandidateSource) | 모든 Tier 가 공유하는 데이터 모양 정의 | ✅ |
| **1-0** | **⭐ `tier1_a11y/MultiWindowCollector` — `AccessibilityService.getWindows()` 반복** | **`uiautomator dump` 의존 제거, popup·IME 통합 dump (Case 1·6·10 해결)** | ✅ |
| 1-1 | `tier1_a11y/NodeTraversal` | a11y tree 노드 → Candidate 리스트 | ✅ |
| 1-2 | `core/ScreenFingerprint` 두 단계 해시 (strict + loose) | 같은 화면 재방문 시 fp_strict 일치율 ≥ 95% | ✅ |
| 1-3 | `core/StateGraph` | 노드 추가 + outgoing edges + thread-safe RW lock | ✅ |
| 1-4 | `engine/ExplorerEngine` DFS 루프 (skeleton) | 화면 변화 시 fingerprint 계산 + StateGraph 누적 (passive observation) | 🟢 skeleton |
| 1-10a | `service/ExplorerAccessibilityService` engine 연결 | a11y 이벤트 → ExplorerEngine.onEvent | ✅ |
| 1-5 | `engine/PathReplayer` Home + relaunch + replay | 백트랙 실패 시 ≥ 80% 재진입 성공 | ⏳ |
| 1-6 | `guard/DialogDismisser` 한글 NFC 정규화 | Notes 권한 다이얼로그 자동 해소 | ⏳ |
| 1-7 | `guard/DangerousActionGuard` 블랙리스트 | "전체 삭제·결제" 키워드 차단 | ⏳ |
| 1-8 | `tier4_probe/DifferentialProbe` | 후보 → HOT/COLD 분류, false positive ≤ 20% | ⏳ |
| 1-9 | `input/GestureDispatcher` + `input/TextInputSampler` | tap, longpress, swipe, SET_TEXT 모두 동작 | ⏳ |
| 1-10b | `service/` ↔ engine 양방향 (액션 수행) | 자율 탐색 모드 활성화 | ⏳ |

> 1-0 은 [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) 의 메타 발견으로 **Phase 1 최우선 항목**으로 격상됨.
> 단일 dump 로는 어떤 popup 이 잡히고 어떤 popup 이 누락되는지 예측 불가능.

**Phase 1 종료 목표 (Samsung Notes 1개 앱)**:
- 발견 화면 ≥ 10
- 클릭 후보 ≥ 30
- 60 초 예산 내 종료
- `events.jsonl` 회수 가능

---

## Phase 2 · Blind Spot 보완

> 표준 a11y 가 놓치는 Canvas / OpenGL / Custom popup 보강.
> 4 개 Tier 모듈을 병렬로 진행할 수 있다.

| # | 산출물 | 완료 기준 |
|---|---|---|
| 2-1 | `tier2_grid/PixelGridSampler` Coarse-to-fine | a11y 미커버 영역에서 hit 후보 발견율 ≥ 50% |
| 2-2 | `tier3_cv/CvProposer` (OpenCV 사각형/원 검출) | 색상 휠·아이콘 버튼 좌표 추출 |
| 2-3 | `tier3_cv/OcrLabeler` (ML Kit Text Recognition) | 후보당 텍스트 부착, 정확도 ≥ 80% |
| 2-4 | `tier5_vlm/VlmProposer` (사내 Ollama proxy) | stuck 시 fallback 으로 좌표 후보 받음 |
| 2-5 | `guard/DangerousActionGuard` OCR 결합 강화 | OCR 텍스트로 위험 액션 추가 차단 |
| 2-6 | `web/` Coverage Heatmap 시각화 | 각 Tier 의 hit 영역을 화면에 오버레이 |

**Phase 2 종료 목표 (Samsung Notes 1개 앱)**:
- 손글씨 캔버스 / 색상 휠 영역 탐색 가능
- Tier 별 hit 기여도 정량화 (예: a11y 60%, CV 25%, grid 10%, VLM 5%)
- VLM 호출 횟수 ≤ 화면 수 × 5%

---

## Phase 3 · System 연계 및 정합성

> 탐색 결과를 시스템 측 정보와 교차 검증.
> 단순 UI 탐색을 넘어 실제 영향까지 추적.

| # | 산출물 | 완료 기준 |
|---|---|---|
| 3-1 | `server/` `/api/system-dump` (dumpsys window / activity / SurfaceFlinger) | 단말 측 사실과 탐색 결과 cross-check |
| 3-2 | logcat 필터 + 태그 화이트리스트 | 탐색 액션 → 시스템 이벤트 매핑 |
| 3-3 | Ground-truth 정합성 리포트 | 탐색이 깨운 component 와 실제 실행 component 일치율 ≥ 90% |
| 3-4 | Coverage 회귀 추적 | OS / 앱 업데이트 시 커버리지 변동 자동 측정 |
| 3-5 | 운영용 KPI 대시보드 | 주간 자동 회귀 + 알람 |

---

## Phase 4 (도전) · 확장

다음은 천장 측정 후 ROI 보고 진입.

- 학계 비교 도구 (DroidBot, APE) 와 동등 조건 벤치마크
- 동영상 기반 캡처 (액션 ↔ 화면 변화 영상 자동 인덱싱)
- 다국어 / 다단말 inventory 자동 sweep

---

## 기록 정책

- Phase 진입/종료 시 `ROADMAP.md` 의 상태 갱신
- 진입 시 `report.html` 의 roadmap 섹션 동기화
- 종료 시 `LIMITATIONS.md` / `COUNTERMEASURES.md` 에서 해당 Tier 항목의 비고 (🟢/🟡/🔴) 재평가
- 각 산출물 완료 커밋 메시지에 `Phase N-M` 태그 (예: `feat(tier1): node traversal [Phase 1-1]`)

---

## 한 줄 요약

> **Phase 0 (셋업) → 1 (a11y + Probe) → 2 (CV + Grid + VLM) → 3 (시스템 연계).**
> 각 Phase 는 검증 가능한 정량 완료 기준을 가진다.
