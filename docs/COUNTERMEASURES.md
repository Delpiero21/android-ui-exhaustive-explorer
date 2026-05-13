# 기술적 한계의 극복 방안

본 문서는 [`LIMITATIONS.md`](LIMITATIONS.md) 에 정리된 각 기술적 한계에 대해
**한계 영역 / 극복 방안 / 비고** 형식으로 대응 전략을 정리한다.

> 📎 **각 극복 방안의 원인 한계는** [`LIMITATIONS.md`](LIMITATIONS.md) **참고. 섹션 번호가 1:1로 대응된다.**

비고 컬럼의 표기:
- 🟢 **즉시 적용 가능** — 기존 컴포넌트 손질 수준 (수~수십 줄)
- 🟡 **중간 규모** — 신규 모듈 추가, 1~2주 작업
- 🔴 **대규모** — 아키텍처 변경 또는 외부 의존성 도입

---

## 1. Accessibility (a11y)

← [원인 한계](LIMITATIONS.md#1-accessibility-a11y)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | Canvas 렌더링 | Pixel grid + Differential probe로 hit 검증 / CV로 후보 좌표 보완 | 🟢 (기존 `PixelGridSampler` 확장) |
| 2 | OpenGL / GLSurfaceView | 화면 캡처 → CV/VLM 후보 추출 → diff probe로 hit 검증 | 🟡 (CV 모듈 신규) |
| 3 | SurfaceView | 동일 (CV로 원형·곡선 영역 검출) | 🟡 |
| 4 | 커스텀 View description 누락 | OCR로 표시 텍스트 추출 / `viewIdResourceName`을 fallback identifier로 사용 | 🟢 |
| 5 | Compose semantics 미지정 | Reflection으로 `SemanticsOwner` 직접 접근 (eng 빌드 한정) | 🟡 |
| 6 | WebView 내부 DOM | WebView debugging bridge (chrome://inspect) / `evaluateJavascript`로 DOM 직접 조회 | 🟡 |
| 7 | 커스텀 Popup / Overlay | `dumpsys window windows` 와 a11y window list 교차 비교 | 🟢 |
| 8 | Toast / Snackbar | `AccessibilityEvent.TYPE_NOTIFICATION_STATE_CHANGED` 구독 추가 + 주기 캡처 diff | 🟢 |
| 9 | 동적 콘텐츠 | Fingerprint 계산 시 텍스트·시간 제외, 구조만 해싱 | 🟢 (이미 일부 적용) |
| 10 | Tree 타이밍 불안정 | Settle detection (event 끊김 100ms 대기) + 재시도 | 🟢 |

---

## 2. UI Automator / View Hierarchy Dump

← [원인 한계](LIMITATIONS.md#2-ui-automator--view-hierarchy-dump)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | Dump 비용 큼 | a11y service의 push 이벤트 구독으로 대체 (polling → event-driven) | 🟢 (이미 적용) |
| 2 | a11y 한계 상속 | 1번 섹션의 보완책 그대로 적용 | — |
| 3 | 다중 윈도우 처리 불완전 | `AccessibilityService.getWindows()` 반복 + 각 window별 root 수집 | 🟢 |
| 4 | 동적 ID 변화 | `resource-id` 우선, 없으면 (className + bounds + index) 조합 | 🟢 |
| 5 | 텍스트 변동 | fp 계산 시 정규식으로 숫자·시간 마스킹 | 🟢 |

---

## 3. Pixel Grid Sampling

← [원인 한계](LIMITATIONS.md#3-pixel-grid-sampling)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 노이즈 비율 높음 | Differential probe로 hit 좌표만 채택 (cold 좌표 학습으로 다음 라운드 제외) | 🟢 |
| 2 | 해상도 트레이드오프 | Coarse-to-fine 계층적 그리드 (256px → hit 주변만 64px → 16px) | 🟢 |
| 3 | 비격자 UI 약함 | CV로 원/사각형 검출 후 중심 좌표를 그리드에 추가 | 🟡 |
| 4 | 액션 의미 모름 | OCR로 영역 내 텍스트 추출 → "삭제/결제/SMS" 블랙리스트 매칭 | 🟡 |
| 5 | 드래그·롱프레스 어려움 | 그리드 셀 간 4방향 swipe + 같은 좌표 longpress 시퀀스 추가 | 🟢 |
| 6 | 깊이 무시 | Tap 후 변화 없을 시 같은 좌표 재시도(긴 press, 또는 dispatchGesture 추가) | 🟢 |

---

## 4. Computer Vision (OpenCV)

← [원인 한계](LIMITATIONS.md#4-computer-vision-opencv)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 추상적 UI 약함 | VLM fallback (스크린샷 → "버튼 좌표 리스트" 추론) | 🟡 |
| 2 | 텍스트 의미 모름 | OCR 결합 (ML Kit Text Recognition / Tesseract) | 🟡 |
| 3 | 테마·디자인 민감 | HSV 색공간 + adaptive threshold + 다크/라이트 룰셋 분기 | 🟢 |
| 4 | False positive 多 | a11y 후보 + CV 후보 IoU 비교 → 둘 다 잡힌 영역 우선 | 🟢 |
| 5 | 작은 아이콘 누락 | Multi-scale detection (0.5x / 1x / 2x 다중 해상도) | 🟢 |
| 6 | 글자·아이콘 구분 어려움 | OCR로 글자 영역 마스킹 후 나머지를 아이콘으로 분류 | 🟡 |

---

## 5. VLM (Vision Language Model)

← [원인 한계](LIMITATIONS.md#5-vlm-vision-language-model)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 비결정성 | `temperature=0` + 동일 prompt 2회 호출 후 교집합만 채택 | 🟢 |
| 2 | 환각 | a11y/CV 후보 좌표와 IoU 검증 — 매칭 안 되면 폐기 | 🟢 |
| 3 | 좌표 정확도 한계 | bbox만 받고 중심점은 자체 계산 / 그리드 snap | 🟢 |
| 4 | 호출 비용·지연 | Stuck 시에만 fallback + 화면 fp 단위 결과 캐싱 | 🟢 |
| 5 | 사외 유출 위험 | 사내 Ollama 한정 + 캡처 마스킹 (개인정보 영역 blur) + host 화이트리스트 | 🟡 |
| 6 | 컨텍스트 단절 | StateGraph 요약을 prompt에 함께 전달 (방문 화면, 현재 깊이) | 🟢 |

---

## 6. Differential Probe (탭 전후 화면 비교)

← [원인 한계](LIMITATIONS.md#6-differential-probe-탭-전후-화면-비교)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 비용 큼 | Top-N 후보만 probe (a11y/CV 우선순위 점수 기반) | 🟢 |
| 2 | 동적 변화 오탐 | 시계·광고 영역 마스킹 + perceptual hash (pHash/dHash) 사용 | 🟢 |
| 3 | 비가역 액션 위험 | OCR로 버튼 텍스트 확인 → "결제/전송/삭제" 차단 + 샌드박스 계정 사용 | 🟡 |
| 4 | 비동기 효과 누락 | 가변 wait (300ms / 1s / 3s) 3단계로 캡처 후 가장 큰 변화 채택 | 🟢 |
| 5 | 미세 변화 미감지 | pHash 임계값을 영역별 적응형으로 조정 (토글 영역은 민감하게) | 🟢 |

---

## 7. State Fingerprint (화면 해시)

← [원인 한계](LIMITATIONS.md#7-state-fingerprint-화면-해시)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | Strict → 무한 신규 | 텍스트·숫자·시간 정규화(`\d+` → `N`) 후 해싱 | 🟢 |
| 2 | Loose → 누락 | 두 단계 해시 (strict=실제 방문, loose=클러스터링) 병행 운영 | 🟢 |
| 3 | 리스트 화면 모호 | activity + 클릭가능 노드의 (className, resource-id) 만으로 해싱 | 🟢 |
| 4 | 회전·키보드 변동 | orientation / IME 노출 여부를 fp 보조 키로 포함 | 🟢 |
| 5 | Modal 중첩 | window 단위 fp 각자 계산 후 (base_fp, overlay_fp) 튜플로 합성 | 🟢 |

---

## 8. DFS + 백트래킹

← [원인 한계](LIMITATIONS.md#8-dfs--백트래킹)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | BACK이 다른 화면으로 | BACK 후 fp 검증 → 다이얼로그 감지 시 DialogDismisser 호출 | 🟢 (이미 부분 적용) |
| 2 | 깊이 폭발 | 깊이 상한 + 같은 깊이서 "처음 보는 resource-id 우선" 휴리스틱 | 🟢 |
| 3 | 비가역 분기 | DangerousActionGuard로 진행 직전 키워드 차단 (회원가입/결제) | 🟢 |
| 4 | Replay 의존 | 부분 경로 replay + 실패 시점부터 재탐색 모드 | 🟡 |
| 5 | 외부 앱 이탈 | Foreground watcher → 타겟 패키지 벗어나면 즉시 Home + 재진입 | 🟢 |

---

## 9. Replay (경로 재생)

← [원인 한계](LIMITATIONS.md#9-replay-경로-재생)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 비결정성에 취약 | resource-id / 노드 시그니처 우선, 좌표는 fallback | 🟢 |
| 2 | 좌표 깨짐 | 정규화 좌표(0~1) 저장 + 단말 해상도로 동적 변환 | 🟢 |
| 3 | 타이밍 의존 | idle-wait (a11y settle event 기반 동적 wait) | 🟢 |
| 4 | 외부 상태 의존 | replay 전 사전조건 체크 (로그인 상태, DB 초기화) 명시 | 🟡 |
| 5 | 알림·인터럽트 취약 | DND 모드 활성화 + DialogDismisser 상시 가동 | 🟢 |

---

## 10. dispatchGesture / Input 자동화

← [원인 한계](LIMITATIONS.md#10-dispatchgesture--input-자동화)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 시스템 UI 차단 | eng 빌드 권한 활용 + `adb shell input` 폴백 | 🟢 |
| 2 | 멀티터치 정밀도 | `GestureDescription` 다중 stroke 타이밍 명시 + uinput 직접 사용 | 🟡 |
| 3 | 손글씨 정밀도 | Bezier 보간 + 가변 stroke duration | 🟡 |
| 4 | 키 이벤트 한계 | `adb shell input keyevent` 으로 hardware key 보강 | 🟢 |
| 5 | IME 우회 한계 | `ACTION_SET_TEXT` 실패 시 Instrumentation 레벨 setText | 🟡 |

---

## 11. 시스템 정보 (dumpsys / logcat)

← [원인 한계](LIMITATIONS.md#11-시스템-정보-dumpsys--logcat)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | 출력 포맷 불안정 | OS 버전별 파서 분리 + 회귀 테스트 셋 운영 | 🟡 |
| 2 | 권한 제한 | eng 빌드 가정 명시 + user 빌드 시 기능 자동 비활성 | 🟢 |
| 3 | 노이즈 多 | 태그 화이트리스트 + 정규식 필터 | 🟢 |
| 4 | 실시간성 떨어짐 | AccessibilityEvent 와 dumpsys 병행 (a11y가 실시간, dumpsys가 보강) | 🟢 |
| 5 | 버전 호환성 | One UI 8.5 baseline 고정 + 차이 detection 스크립트 | 🟡 |

---

## 12. 권한 / 보안 경계

← [원인 한계](LIMITATIONS.md#12-권한--보안-경계)

| # | 한계 영역 | 극복 방안 | 비고 |
|---|---|---|---|
| 1 | Accessibility 권한 토글 | `adb shell settings put secure enabled_accessibility_services ...` 자동화 | 🟢 (이미 스크립트 존재) |
| 2 | 권한 다이얼로그 자동 처리 | DialogDismisser의 NFC(유니코드 정규화) + 다국어 라벨 사전 | 🟢 (이미 적용) |
| 3 | 인증·결제·SMS 회피 | DangerousActionGuard (텍스트 블랙리스트 + 패키지 블랙리스트) | 🟢 |
| 4 | 앱 외부 이탈 감지 | Foreground watcher 주기 폴링(200ms) + 이탈 시 즉시 Home | 🟢 |
| 5 | 시스템 영역 무력 | 진입 전 차단 (블랙리스트) — 진입 시도 자체를 안 함 | 🟢 |

---

## 통합 극복 전략 — 한 사이클에 끼우는 법

위 개별 극복 방안들을 단일 탐색 루프에 통합한 의사 코드:

```
[per screen]
  candidates = []
  candidates += NodeTraversal()                          # Tier 1 — a11y
  candidates += CvProposer(screenshot)                   # Tier 2 — CV (신규)
  candidates += OcrLabeler(candidates)                   # Tier 2.5 — 의미 부여
  if len(candidates) < threshold:                        # blind 화면
      candidates += PixelGridSampler(coarse_to_fine)     # Tier 3
  if exploration_stuck > N steps:
      candidates += VlmProposer(screenshot, state_graph) # Tier 4 — VLM (사내)

  # Tier 5 — Differential probe로 검증
  candidates = filter(DangerousActionGuard, candidates)  # 위험 액션 제외
  for c in priority_order(candidates):
      if DifferentialProbe(c).changed:
          addEdge(currentFp, c)

  # 보조 — 백그라운드 감시
  WindowStackMonitor.tick()       # 12.4 — 외부 이탈
  ForegroundWatcher.tick()        # 12.4 — 패키지 이탈
  DialogDismisser.tick()          # 12.2 — 권한 다이얼로그
```

---

## 우선순위 추천 (ROI 기준)

다음 순서로 구현하면 **가장 적은 노력으로 가장 큰 커버리지 향상**:

| 순위 | 항목 | 근거 |
|---|---|---|
| 1 | **Differential Probe** (6번 + 3.1) | PixelGridSampler 한 클래스 손질로 노이즈 즉시 감소 |
| 2 | **OCR 결합** (3.4 + 4.2) | 위험 액션 차단 + 의미 부여로 audit 가치 상승 |
| 3 | **두 단계 Fingerprint** (7.2) | 리스트 화면 안정화 — 무한루프 즉시 줄어듦 |
| 4 | **DangerousActionGuard** (8.3 + 12.3) | 안전성 확보, 야간 자동 실행의 전제 |
| 5 | **Coarse-to-fine Grid** (3.2) | 시간 예산 내 더 깊은 탐색 |
| 6 | **CV Proposer** (1.1, 4.x) | 본격적 blind spot 보완 — Notes 앱 손글씨/펜 도구 효과 큼 |
| 7 | **VLM Hybrid** (5.x) | Stuck fallback — 위 6개로도 안 풀리는 화면 |

---

## 한 줄 요약

> **각 한계는 다른 기법으로 메울 수 있고, 단 하나의 기법으로 전부 메우려 하면 실패한다.**
> 핵심은 **계층화(tier)** + **상호 검증(cross-validation)** + **위험 차단(guard)** 세 축.
