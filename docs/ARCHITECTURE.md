# 아키텍처

본 문서는 `android-ui-exhaustive-explorer` 의 **5-Tier 계층 결합 아키텍처**와
모듈 책임·의존성·통합 DFS 루프를 정리한다.

핵심 원칙:
1. **계층화 (Layering)** — Tier 1 (a11y) → Tier 5 (VLM) 로 갈수록 광범위·고비용
2. **상호 검증 (Cross-Validation)** — 여러 Tier의 후보를 교차 확인해 false positive 차단
3. **위험 차단 (Guard)** — 비가역 액션은 진입 전 블랙리스트로 사전 차단

상세 한계·극복: [`LIMITATIONS.md`](LIMITATIONS.md) ↔ [`COUNTERMEASURES.md`](COUNTERMEASURES.md)

---

## 1. 전체 그림

```
┌─────────────────── PC ───────────────────┐
│                                          │
│  Web Dashboard (React + Vite, :5173)     │
│  ├─ Run Map / Tree                       │
│  ├─ Coverage Heatmap                     │
│  └─ Roadmap / Report                     │
│                                          │
│  FastAPI Server (:8000)                  │
│  ├─ /api/runs            (탐색 결과)     │
│  ├─ /api/coverage        (히트맵)        │
│  ├─ /api/health                          │
│  └─ vlm/  (사내 Ollama proxy)            │
│                                          │
└───────────────┬──────────────────────────┘
                │ adb reverse tcp:8000
                │ (USB, loopback only)
                ▼
┌─────────── Android Device ───────────────┐
│                                          │
│  ExplorerAccessibilityService            │
│  └─ ExplorerEngine                       │
│     ├─ Tier 1  tier1_a11y/   NodeTraversal
│     ├─ Tier 2  tier2_grid/   PixelGridSampler
│     ├─ Tier 3  tier3_cv/     CvProposer + OCR
│     ├─ Tier 4  tier4_probe/  DifferentialProbe
│     ├─ Tier 5  tier5_vlm/    VlmProposer (Ollama)
│     │                                    │
│     ├─ core/    StateGraph, Fingerprint  │
│     ├─ engine/  DFS, PathReplayer        │
│     ├─ guard/   DangerousActionGuard,    │
│     │          DialogDismisser           │
│     └─ input/   GestureDispatcher,       │
│                TextInputSampler          │
│                                          │
└──────────────────────────────────────────┘
```

---

## 2. 5-Tier 책임

| Tier | 폴더 | 책임 | 입력 | 출력 |
|---|---|---|---|---|
| **1** | `tier1_a11y/` | Accessibility tree 기반 클릭 후보 추출 | a11y window list | `List<Candidate>` (resource-id, bounds, text) |
| **2** | `tier2_grid/` | a11y 미커버 영역 격자 샘플링 (coarse-to-fine) | screen bitmap + tier1 결과 | `List<Candidate>` (좌표만) |
| **3** | `tier3_cv/` | OpenCV로 버튼·아이콘 검출 + OCR로 텍스트 부착 | screen bitmap | `List<Candidate>` (bbox + 추정 텍스트) |
| **4** | `tier4_probe/` | 탭 전후 화면 diff로 hit 검증 (HOT/COLD 분류) | candidates + screen capture | `List<Verified Candidate>` |
| **5** | `tier5_vlm/` | 사내 Ollama 호출로 후보 보조 (stuck 시만) | screenshot + state graph 요약 | `List<Candidate>` (자연어 추론) |

### Tier별 호출 정책

- Tier 1 → **항상 실행** (baseline)
- Tier 2 → 후보 수 < 임계값일 때 (blind screen)
- Tier 3 → 화면 변화 감지되면 매번
- Tier 4 → 모든 후보를 실제 액션 전에 검증
- Tier 5 → 탐색 정체 N step 이상일 때만 (비용 최소화)

---

## 3. 공통 모듈

| 폴더 | 모듈 | 책임 |
|---|---|---|
| `core/` | `StateGraph` | 화면 fingerprint → 노드 + outgoing edges |
| `core/` | `ScreenFingerprint` | a11y tree + 좌표 기반 strict + loose 두 단계 해시 |
| `core/` | `ScreenCapture` | MediaProjection / a11y screenshot 통합 |
| `engine/` | `ExplorerEngine` | DFS 루프 본체. Tier 1~5 조합 + StateGraph 갱신 |
| `engine/` | `PathReplayer` | 백트랙 실패 시 Home → 재시작 → 경로 replay |
| `guard/` | `DialogDismisser` | 권한 다이얼로그·인터럽트 자동 처리 (한글 NFC 정규화) |
| `guard/` | `DangerousActionGuard` | "결제·전송·삭제" 키워드 블랙리스트, 패키지 화이트리스트 |
| `input/` | `GestureDispatcher` | dispatchGesture 합성 터치 (tap / longpress / swipe) |
| `input/` | `TextInputSampler` | `ACTION_SET_TEXT` 직접 주입 (IME 우회) |
| `service/` | `ExplorerAccessibilityService` | a11y 이벤트 진입점, ExplorerEngine 부트스트랩 |

---

## 4. 통합 DFS 루프

```
loop:
    fp_loose, fp_strict = Fingerprint(currentScreen)
    if fp_strict not in visited:
        visited[fp_strict] = list_of_actions(currentScreen)
        stack.push(fp_strict)

        # ===== 후보 수집 (Tier 1~5) =====
        candidates  = Tier1.NodeTraversal(currentScreen)
        if len(candidates) < THRESHOLD:
            candidates += Tier2.PixelGridSampler(currentScreen, mask=tier1_bounds)
        candidates += Tier3.CvProposer(screenshot)
        candidates  = Tier3.OcrLabeler(candidates)            # 텍스트 부착

        if exploration_stuck_count > N:
            candidates += Tier5.VlmProposer(screenshot, state_graph_summary)

        # ===== 안전 필터 =====
        candidates = DangerousActionGuard.filter(candidates)  # blacklisted 제거

        # ===== Tier 4 검증 =====
        for c in priority_order(candidates):
            result = Tier4.DifferentialProbe(c)
            if result.changed:
                visited[fp_strict].append(c)

    if visited[fp_strict] has remaining actions:
        a = visited[fp_strict].pop()
        execute(a)
        wait_idle()
    else:
        BACK()
        wait_idle()
        if fingerprint != expected:
            PathReplayer.recover(stack)

    # ===== 백그라운드 워처 =====
    DialogDismisser.tick()              # 권한·인터럽트 즉시 해소
    ForegroundWatcher.tick()            # 타겟 앱 이탈 감지

until time_budget_exceeded or coverage_satisfied
```

---

## 5. 모듈 의존성 (방향: 사용 → 피사용)

```
service/ExplorerAccessibilityService
    └─> engine/ExplorerEngine
            ├─> tier1_a11y/NodeTraversal       --uses--> core/ScreenFingerprint
            ├─> tier2_grid/PixelGridSampler    --uses--> core/ScreenCapture
            ├─> tier3_cv/CvProposer            --uses--> core/ScreenCapture
            ├─> tier4_probe/DifferentialProbe  --uses--> core/ScreenCapture, input/GestureDispatcher
            ├─> tier5_vlm/VlmProposer          --uses--> core/ScreenCapture (server 경유)
            ├─> core/StateGraph
            ├─> engine/PathReplayer
            ├─> guard/DialogDismisser
            ├─> guard/DangerousActionGuard
            ├─> input/GestureDispatcher
            └─> input/TextInputSampler
```

규칙:
- Tier 모듈은 서로 **직접 참조 금지**. 모두 `engine/ExplorerEngine` 가 조합
- `core/` 는 어디서든 사용 가능, 다른 곳에 의존하지 않음
- `guard/` 는 백그라운드에서 항상 동작, 다른 모듈을 인터럽트할 수 있음

---

## 6. 데이터 흐름 (한 사이클)

```
[Device] AccessibilityEvent
   ↓
ExplorerEngine.onEvent
   ↓
ScreenCapture.snap() ─→ Bitmap
   ↓
ScreenFingerprint.compute() ─→ (fp_strict, fp_loose)
   ↓
[Tier1~5 후보 수집] → candidates
   ↓
DangerousActionGuard.filter() → safe candidates
   ↓
Tier4.DifferentialProbe.verify() → verified candidates
   ↓
GestureDispatcher.execute(chosen) → 새 화면 발생
   ↓
StateGraph.addEdge(prev_fp, action, new_fp)
   ↓
이벤트 + 스크린샷을 events.jsonl / screenshots/ 로 기록
   ↓
[PC] FastAPI Server 가 /api/runs 로 수집
   ↓
[Web] React Dashboard 가 시각화
```

---

## 7. 보안 경계

- APK 의 `network_security_config.xml` 가 cleartext 를 **127.0.0.1 / localhost** 만 허용 → 외부 egress 불가
- VLM 호출은 server 의 화이트리스트 호스트(사내 Ollama) 로만 proxy
- 단말에서 회수한 스크린샷·이벤트는 `.gitignore` 로 commit 차단 (`runs/`, `data/`, `server/data/`)
- 인증·결제·SMS 같은 비가역 액션은 `DangerousActionGuard` 가 진입 전 차단

---

## 8. 한 줄 요약

> **단일 기법으로는 완전탐색 불가능.**
> 5개 Tier 를 **계층화·상호 검증·위험 차단** 세 원칙으로 결합해
> 단일 기법의 사각지대를 메우는 것이 본 도구의 본질.
