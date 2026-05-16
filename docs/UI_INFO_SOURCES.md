# Android UI 정보 source 비교

본 문서는 Android 단말에서 **화면/UI 정보를 얻을 수 있는 모든 주요 방법**을 비교 정리한다.

> **핵심 메시지**: 여러 source 가 같은 화면을 **서로 다른 lens 로** 본다.
> - **a11y live** = framework 가 보여주는 의미 (구조)
> - **dumpsys** = OS 가 보는 사실 (system state)
> - **screenshot** = 사용자가 보는 픽셀 (시각)
> - **ViewDebug** = 실제 view object (raw truth, eng 한정)
>
> 본 도구는 이 **3~4 개 lens 를 동시에 사용해 cross-check** 하는 게 핵심.

용도: 신규 합류자의 개념 학습, Phase 별 source 선택 의사결정, 신규 보강 source 검토 시 비교 기준.

---

## 0. 카테고리 한눈에

```
┌── UI Tree 직접 접근 ───────────────────────────────────────┐
│  ① AccessibilityNodeInfo (live API)        ← 우리 메인       │
│  ② uiautomator dump (XML 직렬화)           ← dump_visualizer │
│  ③ UiAutomator2 (instrumentation 테스트)                    │
│  ④ Layout Inspector (Android Studio)                        │
│  ⑧ Compose Semantics Tree (Compose 특화)                    │
└──────────────────────────────────────────────────────────────┘

┌── 시스템 측 정보 (dumpsys 계열) ──────────────────────────┐
│  ⑤ dumpsys window / activity / SurfaceFlinger / gfxinfo     │
└──────────────────────────────────────────────────────────────┘

┌── 시각 (픽셀) ─────────────────────────────────────────────┐
│  ⑥ adb exec-out screencap / MediaProjection /              │
│     AccessibilityService.takeScreenshot()                   │
└──────────────────────────────────────────────────────────────┘

┌── 내부 / Reflection (eng / system 권한) ──────────────────┐
│  ⑦ ViewDebug / ViewRootImpl reflection                      │
└──────────────────────────────────────────────────────────────┘

┌── 특수 영역 ───────────────────────────────────────────────┐
│  ⑨ WebView debugging bridge                                 │
│  ⑩ logcat 파싱 (앱 자체 로그)                               │
└──────────────────────────────────────────────────────────────┘
```

---

## 1. 종합 비교 표 (핵심 7 가지)

| # | 방법 | 정보 종류 | 권한 | 속도 | 다중 window | 액션 수행 | 본 프로젝트 활용 |
|---|---|---|---|---|---|---|---|
| ① | **AccessibilityNodeInfo (live API)** | UI tree + 모든 a11y 속성 | a11y 토글 | ~50ms | ✅ `getWindows()` | ✅ `performAction()` | ⭐ Tier 1 메인 |
| ② | **uiautomator dump (XML)** | UI tree (직렬화) | adb shell | 1~3s | ❌ focused 만 | ❌ | 검증 도구만 |
| ③ | **UiAutomator2 (test)** | UI tree + 액션 + assertion | adb shell + test APK | ~200ms | 부분 | ✅ | ❌ (preload 부적합) |
| ④ | **Layout Inspector** | UI tree + 시각화 + property | adb + Studio | 느림 | ✅ | ❌ | 개발용 디버깅만 |
| ⑤ | **dumpsys window** | Window stack + focus + IME | adb shell | ~500ms | ✅ 전체 stack | ❌ | Phase 3 보조 |
| ⑥ | **MediaProjection / screencap** | 픽셀 비트맵만 | 권한 / shell | ~200ms | N/A | ❌ | Tier 3/4/5 입력 source |
| ⑦ | **ViewDebug reflection (eng)** | 모든 view + Compose semantics + hidden | eng 빌드 한정 | 빠름 | ✅ | ❌ | Phase 3 도전 |

---

## 2. 상세 — 각 방법

### ① AccessibilityNodeInfo (live API) — ⭐ 본 도구 메인

```kotlin
class ExplorerAccessibilityService : AccessibilityService() {
    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        val windows: List<AccessibilityWindowInfo> = windows        // 모든 활성 window
        val mainRoot: AccessibilityNodeInfo? = rootInActiveWindow
        // → 각 node 에서 performAction(ACTION_CLICK) 등 직접 호출 가능
    }
}
```

**장점**:
- 모든 a11y 속성 노출 (XML 직렬화에서 누락되는 ~30% 추가 메타 — `actionList`, `extras`, `drawingOrder`, `collectionInfo`, `rangeInfo`, `tooltipText`, `paneTitle`, `traversalBefore/After` 등)
- `performAction()` 으로 **좌표 없이** 클릭 가능 (Case 4 같은 동일 resource-id 9 개도 인덱스로 정확)
- AccessibilityEvent push 로 화면 변화 실시간 감지
- 50~100 ms 응답

**단점**:
- 사용자가 단말 설정에서 a11y 토글 ON 필요 (1 회만, `adb shell settings put secure ...` 로 자동화 가능)

**우리 매핑**: `tier1_a11y/MultiWindowCollector.kt` — Phase 1 최우선 모듈.

---

### ② uiautomator dump (XML) — 검증 도구급

```bash
adb shell uiautomator dump /sdcard/window_dump.xml
adb pull /sdcard/window_dump.xml
```

**장점**:
- 단발 호출 + 외부 도구 통합 쉬움 (dump_visualizer 가 사용하는 것)

**단점**:
- 단일 focused window 만 (Case 6/10 사각지대 — [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) §10)
- XML 직렬화 손실 (extras, drawing-order, action-list 등 누락)
- 1~3 초 (DFS 루프에 부적합)

**우리 매핑**: 검증·디버깅 도구로만 사용 (외부 `android-ui-dump-visualizer`).

---

### ③ UiAutomator2 (테스트 프레임워크)

```kotlin
val device = UiDevice.getInstance(InstrumentationRegistry.getInstrumentation())
val btn = device.findObject(UiSelector().text("확인"))
btn.click()
```

**장점**:
- 풍부한 selector + assertion + screenshot 통합
- Google 공식, Espresso 와 paired

**단점**:
- **instrumentation 테스트 컨텍스트** 필요 — 앱 별로 `androidTest` 빌드 또는 테스트 APK 설치
- **preload 앱 (= 우리 타겟) 에 instrumentation 불가**
- ② 와 같은 단일 window 한계

**우리 매핑**: ❌ 사용 안 함.

---

### ④ Layout Inspector (Android Studio)

**용도**:
- Android Studio 에서 GUI 로 view tree 보기
- 각 View 의 모든 property 확인 (debug build 한정으로 picture-perfect)
- Compose semantics 도 보임

**단점**:
- 자동화 불가, 개발자가 수동으로 보는 도구
- USB + Studio 필요

**우리 매핑**: 본 도구 개발 시 디버깅 용도만.

---

### ⑤ dumpsys 계열 — 시스템 측 보강

```bash
adb shell dumpsys window windows | grep mFocus
adb shell dumpsys activity activities
adb shell dumpsys SurfaceFlinger --list
adb shell dumpsys gfxinfo <package>
```

각각 제공하는 정보:

| 명령 | 얻을 수 있는 것 |
|---|---|
| `dumpsys window` | `mCurrentFocus`, `mInputMethodTarget`, 전체 window stack, IME 상태 |
| `dumpsys activity activities` | resumed activity, task stack, configuration |
| `dumpsys SurfaceFlinger --list` | 실제로 화면에 컴포지팅된 surface 들 |
| `dumpsys gfxinfo <pkg>` | frame draw 시간, jank 통계 |

**장점**:
- a11y 가 못 보는 system-level 정보
- 단말 측 ground truth (실제 화면에 뭐 떠 있는지)

**단점**:
- OS 버전마다 포맷 다름 (파서 유지보수 부담)
- shell user 권한 한정
- 노이즈 多

**우리 매핑**: **Phase 3 의 cross-check** — a11y 결과와 ground truth 정합성 검증.

---

### ⑥ MediaProjection / screencap — 시각 source

```bash
# shell
adb exec-out screencap -p > screen.png
```

```kotlin
// a11y service 내에서
service.takeScreenshot(displayId, executor) { screenshot ->
    val bitmap = screenshot.hardwareBuffer.toBitmap()
}

// 또는 MediaProjection (권한 동의 필요)
val mediaProjection = mediaProjectionManager.getMediaProjection(...)
val virtualDisplay = mediaProjection.createVirtualDisplay(...)
```

**장점**:
- a11y 가 못 보는 영역도 픽셀로는 다 보임 (Canvas / OpenGL 모두)
- CV / OCR / VLM 의 입력 source

**단점**:
- 픽셀만 — 구조는 알 수 없음
- 그래서 CV/OCR/VLM 으로 후처리 필요

**우리 매핑**: **Tier 2 (Grid) + Tier 3 (CV) + Tier 4 (Probe) + Tier 5 (VLM) 의 입력**. 핵심.

---

### ⑦ ViewDebug / ViewRootImpl reflection (eng 빌드 특권)

```kotlin
// Hidden API — eng 빌드 + system 권한 + reflection 필요
val wmgClass = Class.forName("android.view.WindowManagerGlobal")
val instance = wmgClass.getMethod("getInstance").invoke(null)
val getViewRoots = wmgClass.getDeclaredMethod("getViewRoots")
val viewRoots = getViewRoots.invoke(instance) as List<*>
// → 각 ViewRoot.view 접근 → 모든 internal property 노출
val debugInfo = ViewDebug.dumpHierarchyWithProperties(viewRoots[0].view)
```

**장점**:
- a11y semantics 가 누락된 Compose / 커스텀 View 도 **실제 view object** 접근
- Compose 의 hidden `SemanticsOwner` 까지 도달
- 모든 internal property 노출 (private field 도 reflection 으로)

**단점**:
- **eng 빌드 + system 권한** 필요 (사내라서 가능)
- API 가 hidden — reflection 으로만 호출 가능 → 버전 호환성 ↓
- 위험 — 시스템 안정성 영향 가능성

**우리 매핑**: **Phase 3 의 도전 옵션**. eng 빌드 특권 활용 차별점.

> 참고: 학계의 **EHBDroid** (ICSE 2017) 가 비슷한 발상 — UI 무시하고 callback 직접 invoke. [`RELATED_WORK.md`](RELATED_WORK.md) §3 참조.

---

### ⑧ Compose Semantics Tree (Compose 특화)

Compose 앱은 일반 View 가 없고 Composable function tree. a11y 노출은 `Modifier.semantics` 통해 해야 함.

**문제**:
- Compose 라이브러리가 `semantics` 미설정인 경우 a11y tree 누락
- One UI 의 일부 신규 화면이 Compose 기반이라 잠재 사각지대

**접근**:
- `androidx.compose.ui.test` 의 `SemanticsNodeInteractionsProvider` — 단, 테스트 컨텍스트
- `androidx.compose.ui.platform.AndroidComposeView.semanticsOwner` — reflection 필요

**우리 매핑**: Phase 3 후보. Samsung Notes / One UI 의 Compose 비율 증가 시 우선순위 상승.

---

### ⑨ WebView Debugging Bridge

```bash
# Chrome 에서 chrome://inspect → device 의 WebView 선택
# → JS 콘솔로 DOM 직접 조회
```

```kotlin
webView.evaluateJavascript("document.querySelectorAll('button').length") { result ->
    // ...
}
```

**장점**:
- WebView 내부 DOM 완전 접근 (a11y 가 root 만 보는 것의 반대)

**단점**:
- WebView debugging 활성화 필요 (`setWebContentsDebuggingEnabled(true)`) — preload 앱은 보통 off
- WebView 만 적용 가능

**우리 매핑**: WebView 사용 앱 만나면 필요. Samsung Notes 는 WebView 거의 없음.

---

### ⑩ logcat 파싱 (앱 자체 로그)

**용도**: 앱이 내뱉는 자체 로그에서 화면 / 이벤트 단서 추출.

**한계**: 앱마다 로그 패턴 다름. 신뢰성 낮음. 보조 신호로만.

**우리 매핑**: ❌ 사용 안 함.

---

## 3. ① vs ② 의 핵심 차이 (헷갈리기 가장 쉬운 페어)

**둘 다 a11y framework 라는 같은 우물에서 물을 푸지만, 양동이가 다릅니다.**

```
┌──────────────────── Android Framework ─────────────────────┐
│                                                             │
│  AccessibilityManager → AccessibilityNodeInfo tree (live)  │
│  └─ 같은 source 에서 두 갈래로 분기 ↓                       │
│                                                             │
│  ┌── 경로 A (Service 내부) ──┐ ┌── 경로 B (외부) ──────┐ │
│  │ AccessibilityService API  │ │ UiAutomation 명령      │ │
│  │ live tree, all windows    │ │ dumpWindowHierarchy()  │ │
│  │ performAction() 가능       │ │ → XML 단일 window     │ │
│  │                            │ │                        │ │
│  │ ① 우리 도구 (Phase 1)      │ │ ② dump_visualizer     │ │
│  └────────────────────────────┘ └────────────────────────┘ │
└─────────────────────────────────────────────────────────────┘
```

### 결정적 차이 5 가지

| 차이 | ① a11y live API | ② uiautomator dump |
|---|---|---|
| **Window 처리** | `getWindows()` 로 모두 | focused 1 개만 |
| **속도** | ~50ms | 1~3s |
| **속성 풍부도** | 90+ 속성 | XML 직렬화 ~30 |
| **액션 수행** | `performAction()` | ❌ (별도 `adb input`) |
| **Live update** | AccessibilityEvent push | dump 마다 polling |

→ Samsung Notes 실측 5 케이스 중 **3 개 (Case 1, 6, 10) 가 이 차이만으로 해결됨**.
   ([`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) 의 메타 발견)

---

## 4. 본 프로젝트 5-Tier × source 매핑

```
Tier 1 — Accessibility (a11y baseline)
  └─ ① AccessibilityNodeInfo live API      ← 메인
  └─ ⑤ dumpsys window 보조 (cross-check)

Tier 2 — Pixel Grid
  └─ ⑥ AccessibilityService.takeScreenshot()  ← 메인
  └─ ⑥ adb exec-out screencap -p (fallback)

Tier 3 — CV + OCR
  └─ ⑥ Screenshot → ML Kit Text Recognition (OCR)
  └─ ⑥ Screenshot → TFLite Object Detection (CV)

Tier 4 — Differential Probe
  └─ ⑥ Screenshot (전 + 후 비교)
  └─ ① a11y tree (전 + 후 비교)

Tier 5 — VLM
  └─ ⑥ Screenshot → On-device VLM (Gemini Nano / Gemma)
  └─ (① a11y 요약을 prompt 에 함께)

Phase 3 도전 (선택)
  └─ ⑤ dumpsys 풀 활용 (system cross-check)
  └─ ⑦ ViewDebug reflection (Compose semantics 누락 보강)
  └─ ⑨ WebView debugging (WebView 깊은 곳)
```

---

## 5. Cross-check 매트릭스 — 한 source 의 누락을 다른 source 가 보강

| 누락 시나리오 | 1차 source | 2차 보강 | 3차 보강 |
|---|---|---|---|
| Canvas 영역 (Case 2) | ① a11y → 단일 View | ⑥ Screenshot + Tier 3/4 | ⑦ ViewDebug (eng) |
| 시스템 popup (Case 6) | ① a11y `getWindows()` | ⑤ `dumpsys window` | ⑥ Screenshot diff |
| 자체 popup (Case 10) | ① a11y `getWindows()` | ⑤ `dumpsys window` | ⑥ Screenshot |
| 색깔 정보 (Case 4) | ⑥ Screenshot + Tier 3 CV | (다른 보강 어려움) | — |
| IME 키보드 (Case 1) | ① a11y `getWindows()` | ⑤ `dumpsys window mInputMethodTarget` | `adb input keyevent` |
| Compose 누락 view | ① a11y | ⑦ ViewDebug.semanticsOwner | ⑥ Screenshot + Tier 3 |
| WebView 내부 DOM | ① a11y root 만 | ⑨ Chrome DevTools bridge | ⑥ Screenshot |

→ **단일 source 만 믿지 말고 cross-check 하는 게 본 도구의 본질**.

---

## 6. 결정적 정리 — "어차피 같은 거 아니냐" 의 답

| 동급 같지만 사실 다른 페어 | 차이의 본질 |
|---|---|
| ① a11y live API ↔ ② uiautomator dump | live API 는 모든 window + 풍부 속성 + 액션 가능. dump 는 단일 window snapshot |
| ② uiautomator dump ↔ ③ UiAutomator2 | 둘 다 같은 framework. UA2 는 instrumentation 필수 (preload 부적합) |
| ⑥ screencap ↔ ① a11y | screencap = 픽셀만 (구조 X). a11y = 구조만 (픽셀 X). **둘 다 필요** |
| ⑤ dumpsys window ↔ ① a11y `getWindows()` | dumpsys = OS 가 보는 진실. a11y = a11y framework 가 노출하는 진실. **불일치 가능** → cross-check |
| ⑦ ViewDebug ↔ ① a11y | ViewDebug = 실제 view object 직접. a11y = 노출된 metadata 만. **a11y 누락 view 도 ViewDebug 는 봄** |

---

## 7. 한 줄 요약

> **여러 source 가 같은 화면을 다른 lens 로 본다.**
> 본 도구의 본질은 **3~4 개 lens 를 동시에 사용해 cross-check** 하는 것.
> 1 단계 source (`AccessibilityNodeInfo live API`) 부터 시작해
> Phase 별로 보강 source 를 추가하는 것이 [`ROADMAP.md`](ROADMAP.md) 의 진행 방향이다.

---

## 8. 본 도구의 실용 결정 (Phase 별)

| Phase | 사용할 source |
|---|---|
| **Phase 1 (현재 다음)** | ① a11y live API 만 (`MultiWindowCollector`) |
| **Phase 1 후반** | ① + ⑥ screencap (Tier 4 Differential Probe) |
| **Phase 2** | ① + ⑥ + 온디바이스 CV/OCR/VLM (ML Kit / TFLite / AICore) |
| **Phase 3** | ① + ⑥ + ⑤ dumpsys cross-check + ⑦ ViewDebug 보강 |

---

## 9. 관련 문서

- [`LIMITATIONS.md`](LIMITATIONS.md) §1, §2 — 각 source 의 사각지대
- [`COUNTERMEASURES.md`](COUNTERMEASURES.md) §1, §2 — 한계별 보완 전략
- [`ARCHITECTURE.md`](ARCHITECTURE.md) §2~3 — Tier 별 책임과 source
- [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) — 5 케이스 실측 (①/② 비교 근거)
- [`RELATED_WORK.md`](RELATED_WORK.md) §6 — 학계가 각 source 를 어떻게 사용했나
