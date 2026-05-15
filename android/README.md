# android/ — Kotlin Android 모듈

Phase 0 (Bootstrap) — 빌드 가능한 빈 APK 스캐폴드.
실제 탐색 로직은 Phase 1 부터 각 Tier 폴더에 채워진다.

---

## 첫 빌드 셋업

### 방법 A — Android Studio (권장)

1. **Android Studio Iguana 이상** 실행
2. *File → Open* → `C:\GitHub\Delpiero21\android-ui-exhaustive-explorer\android` 선택
3. 첫 sync 시 Gradle wrapper (`gradle-wrapper.jar`) 와 의존성을 자동 다운로드
4. **Run** → 실 단말 또는 에뮬레이터 선택 → 빈 화면 ("Phase 0 · Bootstrap") 노출 확인

### 방법 B — CLI

먼저 `gradle-wrapper.jar` 가 필요하다 (현재 레포에 미포함).
PC 에 Gradle 8.11+ 가 설치되어 있으면:

```powershell
cd C:\GitHub\Delpiero21\android-ui-exhaustive-explorer\android
gradle wrapper --gradle-version 8.11.1
.\gradlew :app:assembleDebug
adb install -r app\build\outputs\apk\debug\app-debug.apk
```

설치되지 않았으면 Android Studio 한 번만 열어서 sync 한 뒤 위 명령을 쓰면 된다.

---

## 디렉토리 구조

```
android/
├── build.gradle.kts                    # root build (플러그인 선언만)
├── settings.gradle.kts                  # 모듈 + repo 설정
├── gradle.properties                    # JVM/AndroidX 옵션
├── gradle/wrapper/                      # Gradle wrapper 설정
└── app/
    ├── build.gradle.kts                 # 앱 모듈 설정 + Compose/AndroidX 의존성
    ├── proguard-rules.pro
    └── src/main/
        ├── AndroidManifest.xml          # 권한, Activity, AccessibilityService 등록
        ├── java/com/exhaustive/explorer/
        │   ├── core/                    # StateGraph / Fingerprint / ScreenCapture (Phase 1)
        │   ├── engine/                  # ExplorerEngine / PathReplayer (Phase 1)
        │   ├── guard/                   # DialogDismisser / DangerousActionGuard (Phase 1)
        │   ├── input/                   # GestureDispatcher / TextInputSampler (Phase 1)
        │   ├── tier1_a11y/              # ⭐ MultiWindowCollector (Phase 1 최우선)
        │   ├── tier2_grid/              # PixelGridSampler (Phase 2)
        │   ├── tier3_cv/                # CvProposer + OcrLabeler (Phase 2)
        │   ├── tier4_probe/             # DifferentialProbe (Phase 1 후반)
        │   ├── tier5_vlm/               # VlmProposer (Phase 2 후반)
        │   ├── service/
        │   │   └── ExplorerAccessibilityService.kt   # ✅ Phase 0 셋업 완료
        │   └── ui/
        │       └── MainActivity.kt      # ✅ Compose 빈 화면
        └── res/
            ├── values/{strings.xml, themes.xml}
            └── xml/
                ├── accessibility_service_config.xml
                ├── network_security_config.xml      # loopback only
                └── data_extraction_rules.xml         # 백업/이관 차단
```

각 Tier 폴더의 `package-info.kt` 는 향후 들어올 클래스의 책임·근거 문서를 담는다.
근거는 모두 `../docs/SAMSUNG_NOTES_HARD_CASES.md` 의 실측 5 케이스에서 도출.

---

## 서비스 활성화

설치 후 단말에서:

1. 설정 → 접근성 → 설치된 앱 → **UI Exhaustive Explorer**
2. 토글 ON → 로그캣 확인:

```
adb logcat -s ExplorerA11y:V
# I ExplorerA11y: Service connected. Phase 0 — no exploration logic attached yet.
```

이 메시지가 보이면 스캐폴드 동작 검증 완료.

---

## 다음 단계 (Phase 1)

[`../docs/ROADMAP.md`](../docs/ROADMAP.md) §Phase 1 참고. 가장 먼저 작성할 모듈:

1. **`tier1_a11y/MultiWindowCollector.kt`** — `AccessibilityService.getWindows()` 반복 통합
2. **`core/ScreenFingerprint.kt`** — strict + loose 두 단계 해시
3. **`core/StateGraph.kt`** — 노드 + 전이 그래프
4. **`engine/ExplorerEngine.kt`** — 통합 DFS 루프
5. **`tier4_probe/DifferentialProbe.kt`** — 후보 검증

Phase 1 종료 목표: Samsung Notes 단일 앱에서 60 초 예산 내 ≥ 10 화면 발견.
