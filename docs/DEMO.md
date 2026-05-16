# 월요일 데모 가이드

본 문서는 **시연 진행자**의 절차서 + **청중에게 설명할 기반 기술 흐름**.
실제 데모 ~15 분 + 기반 기술 설명 ~15 분 = 총 30 분 분량.

---

## 0. 사전 준비 (시연 30분 전)

### 단말 준비
- [ ] Galaxy S26 (또는 보유 단말) USB 연결
- [ ] `adb devices` 로 인식 확인
- [ ] 단말 화면 켜짐, 화면잠금 해제

### PC 측 준비
- [ ] Android Studio 에서 `android/` 폴더 열고 **첫 sync 완료**
- [ ] APK 빌드: Android Studio `Run` 또는 `gradlew :app:assembleDebug`
- [ ] 설치: `adb install -r app/build/outputs/apk/debug/app-debug.apk`
- [ ] logcat 윈도우 띄우기:
      ```powershell
      adb logcat -s ExplorerA11y:V ExplorerEngine:I MultiWindowCollector:W DangerousActionGuard:W DialogDismisser:I
      ```
- [ ] 브라우저로 [`docs/report.html`](report.html) 열어두기 (대시보드)

### 단말 측 a11y 활성화 (1 회만)
1. 단말 → **설정 → 접근성**
2. **설치된 앱** 또는 **다운로드된 앱** → **UI Exhaustive Explorer**
3. 토글 **ON** → 시스템 경고 dialog 에서 **허용**
4. 확인: logcat 에 `Service connected. Phase 1 — passive + autonomous available.` 로그

> 한 번 활성화하면 단말 재부팅 전엔 유지됨.

---

## 1. 데모 시나리오 (15 분)

### Scenario A — Passive 모드 (3 분)

**메시지**: *"먼저 사용자가 단말을 만지는 동안 우리 도구가 무엇을 보는지 확인합니다."*

1. 단말에서 우리 앱 (UI Exhaustive Explorer) 실행
2. *"AccessibilityService 활성화됨"* 확인 (▶ 시작 버튼 활성화)
3. 단말에서 Samsung Notes 직접 실행 → 새 노트 → 텍스트 입력 → 펜 모드 → 그리기
4. **logcat 에 실시간 출력**:
   ```
   [NEW] fp=a3f5... pkg=com.samsung.android.app.notes candidates=28 windows=2
   [NEW] fp=b7d1... pkg=com.samsung.android.honeyboard candidates=74 windows=3
   ```
5. 앱 화면으로 돌아와서 **발견 화면 수 / 엣지 수** 가 증가하는 것 확인

**강조 포인트**:
- *"키보드(`honeyboard`)도 별도 window 로 잡힘 — 이게 `getWindows()` 통합의 효과"*
- *"`uiautomator dump` 만 쓰면 키보드가 통째 사라집니다. 우리 도구는 다릅니다."*
- 우리 [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) Case 1 의 실측 결과 인용

### Scenario B — Autonomous 자율 탐색 (7 분) ⭐ 핵심

**메시지**: *"이제 사람이 안 만지고 도구 혼자 Samsung Notes 를 탐색합니다."*

1. 우리 앱으로 돌아옴
2. `target package` = `com.samsung.android.app.notes`
3. `budget` = `60`
4. **▶ 시작** 클릭
5. *(즉시 발생)*:
   - Notes 자동 실행
   - 후보 노드 1 개씩 자동 탭
   - 화면 변하면 새 fingerprint 등록
   - 발견 화면 수 증가 (target ≥ 10)
6. *(중간에 일어나는 일)*:
   - 권한 다이얼로그 떠도 자동 허용 (DialogDismisser)
   - "전체 삭제" 같은 위험 버튼은 차단 (DangerousActionGuard)
   - 외부 앱으로 이탈하면 자동 홈 + 재진입
7. *(60 초 후)* 자동 종료
8. **최종 통계** 확인:
   - 발견 화면 ~10~20 (Samsung Notes 의 메뉴 + 설정 + 편집 화면 등)
   - 엣지 ~30~50

**강조 포인트**:
- *"실제 동작은 100% 가 아닙니다. 65~75% 가 현실적 천장 — 학계 벤치마크와 동일."* ([`RELATED_WORK.md`](RELATED_WORK.md) §2)
- *"못 본 영역은 [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) 의 5 케이스로 정리해뒀습니다."*
- *"이게 본 도구의 핵심 deliverable — 본 것 + 못 본 것 + 못 본 이유."*

### Scenario C — 안전성 시연 (2 분)

**메시지**: *"위험 액션은 어떻게 차단되는지 보여드립니다."*

1. logcat 으로:
   ```
   adb logcat -s DangerousActionGuard:W DialogDismisser:I
   ```
2. Scenario B 다시 실행 중 logcat 에 출력되는 차단 사례 보여주기:
   ```
   W DangerousActionGuard: BLOCKED [결제]: 결제하기@[540,1200]
   I DialogDismisser: permission dialog detected → allow
   ```
3. **결제 / 전송 / 삭제 / 인증 / 로그아웃** 키워드 패턴 보여주기 (코드의 `DANGEROUS_KEYWORDS` 인용)

### Scenario D — Audit 보고 (3 분)

**메시지**: *"이게 팀장님이 요청하신 audit 자료입니다."*

1. 브라우저로 [`docs/report.html`](report.html) 열기 (이미 띄워둔 것)
2. 한 페이지로 보여주는 것:
   - 5-Tier 아키텍처
   - 12 한계 영역 (LIMITATIONS)
   - 우선순위 매트릭스
   - 실측 5 케이스
3. *"이게 검증 한 번 더 돌릴 때마다 자동 생성될 수 있는 보고서 형식."*

---

## 2. 기반 기술 설명 (15 분)

### Step 1 (2 분) — 문제 정의

**슬라이드/말씀할 것**:
- *"`uiautomator dump` 는 단일 window snapshot 입니다."*
- *"실측해보니 어떤 popup 은 잡히고 어떤 popup 은 통째 사라집니다."*
- 보여주기: [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) Case 10 vs Case 6 의 정량 비교 표

### Step 2 (3 분) — 5-Tier 결합

**보여주기**: [`docs/report.html`](report.html) 의 5-Tier 시각화 / [`ARCHITECTURE.md`](ARCHITECTURE.md) §1 그림

**말씀할 것**:
- *"단일 기법은 사각지대가 생깁니다. 5 개 lens 를 결합합니다."*
  - Tier 1 — a11y (구조)
  - Tier 2 — Pixel Grid (a11y 미커버)
  - Tier 3 — CV + OCR (시각 속성)
  - Tier 4 — Differential Probe (검증)
  - Tier 5 — VLM (정체 시 fallback, 사내 / 온디바이스)
- *"현재 Phase 1 은 Tier 1 + 4 기초만. Tier 2/3/5 는 Phase 2 예정."*

### Step 3 (3 분) — UI 정보 source 비교

**보여주기**: [`UI_INFO_SOURCES.md`](UI_INFO_SOURCES.md) §1 비교 표

**말씀할 것**:
- *"같은 a11y framework 에서 두 갈래로 갈립니다."*
- *"② `uiautomator dump` 는 단일 window 1~3 초 snapshot. 학계 도구들이 주로 사용."*
- *"① `AccessibilityService` live API 는 모든 window 50ms + `performAction()` 으로 좌표 무관 클릭."*
- *"본 도구 Phase 1 의 첫 모듈 `MultiWindowCollector` 가 정공법."*

### Step 4 (3 분) — 학계 비교

**보여주기**: [`RELATED_WORK.md`](RELATED_WORK.md) §2 + §7

**말씀할 것**:
- *"DroidBot (ICSE 2017) 이 본 도구의 가장 가까운 학술 부모."*
- *"ByteDance 의 Fastbot 이 TikTok CI 에 5 년 운영 — 산업 실증."*
- *"본 도구의 차별점은 새 알고리즘이 아니라 5-Tier 결합 + audit 단위 보고."*
- 직접 인용: *"단일 기법으로는 완전탐색이 불가능 — 학계 합의."*

### Step 5 (2 분) — 한계와 솔직함

**보여주기**: [`LIMITATIONS.md`](LIMITATIONS.md) §1, §6, §7

**말씀할 것**:
- *"100% 자동 탐색은 본질적으로 불가능 (Choudhary et al., ASE 2015)."*
- *"우리 목표는 65~75% — Fastbot 수준."*
- *"못 본 것 (로그인 / 결제 / 게임 / WebView 내부) 은 명시적으로 out-of-scope 선언."*
- *"이게 audit 의 본질 — 본 것 + 못 본 것 + 못 본 이유."*

### Step 6 (2 분) — 다음 단계

**보여주기**: [`ROADMAP.md`](ROADMAP.md)

**말씀할 것**:
- *"Phase 1 (지금) — passive + autonomous DFS. Samsung Notes 데모 동작."*
- *"Phase 2 — Tier 3 (온디바이스 CV/OCR — ML Kit) + Tier 5 (Gemini Nano)."*
- *"Phase 3 — 시스템 측 (`dumpsys` cross-check) + 자동 회귀 KPI."*

---

## 3. Q&A 대비 — 예상 질문 + 답

### Q1. "DroidBot 그냥 쓰면 되는 거 아닌가?"
- A: *"DroidBot 은 Tier 1 만. 본 도구는 Tier 1~5. 한국어 / Samsung 자사 앱 a11y 비일관성 (Case 4) 같은 사내 특화 케이스도 다룸."* + RELATED_WORK §2.1 표 인용.

### Q2. "왜 100% 자동 탐색이 안 되나?"
- A: *"학계 합의입니다."* + LIMITATIONS.md §9 (Replay) 인용. *"로그인 / 동적 데이터 / 시스템 인증 영역은 본질적으로 자동화 불가능."* + Choudhary et al. ASE 2015 인용.

### Q3. "Tier 5 (VLM) 외부 API 쓰는 거 아닌가? 보안은?"
- A: *"현 설계는 사내 Ollama. 갱신 계획: 100% 온디바이스 (Gemini Nano via AICore on S26)."* + UI_INFO_SOURCES.md §4 매핑 인용.

### Q4. "ByteDance Fastbot 도 결국 못 잡는 영역 있을 텐데?"
- A: *"맞습니다. Fastbot 도 단일 Tier (a11y + native input). Canvas / 시각 속성 (Case 4 색상) 같은 영역은 미해결. 본 도구가 Tier 3 (CV) 로 보완 — Phase 2 예정."*

### Q5. "성능 — 60초 budget 으로 얼마나 탐색되나?"
- A: *"학계 벤치마크 기준 ~50% Activity coverage. 본 도구 Phase 1 동일 수준 목표. Phase 2 추가 Tier 로 ~65-70% 까지."* + ROADMAP.md §정량 목표 인용.

### Q6. "Samsung Notes 가 a11y 잘 되어 있다면 우리 도구 필요한가?"
- A: *"같은 패널 안에서도 비일관."* + Case 4 (펜 5 종은 라벨 OK, 색상 9 개는 라벨 빔) 인용. *"Samsung 도 element 별로 들쭉날쭉 — 가정 위험."*

### Q7. "결제 / 인증 차단은 어떻게 보장?"
- A: *"DangerousActionGuard 가 NFC 정규화 + 한국어/영어 키워드. + 화면 단위 evacuate. + 외부 앱 이탈 즉시 home 복귀."* + 코드 보여주기.

---

## 4. 시연 중 사고 대비

| 사고 | 대응 |
|---|---|
| 단말이 다른 앱으로 빠짐 | `target_package` 가 명시되어 있으면 자동 복귀. 안 되면 stop → 다시 start. |
| 시연 중 권한 dialog 가 사람 손이 가야 함 | DialogDismisser 가 못 잡으면 수동으로 허용. 사후 logcat 분석해서 패턴 추가. |
| budget 60 초가 너무 빠르거나 늦음 | 즉시 budget 수정 후 재시작. UI 에서 입력만 변경. |
| 빌드 실패 | Android Studio sync 안 됐을 가능성. `gradle/wrapper/gradle-wrapper.jar` 있는지 확인. |
| autonomous 가 frontier=0 에서 멈춤 | 정상 동작 (BACK 시도). 5 회 이상 no-progress 면 home+relaunch 자동. |
| 단말 자체가 안 됨 | 미리 emulator 띄워두기 (S26 이 안 되면 다른 단말로 시연 가능) |

---

## 5. 데모 후 follow-up 자료

질문 받은 사람에게 보내줄 링크들:

- 전체 코드: <https://github.com/Delpiero21/android-ui-exhaustive-explorer>
- 한 페이지 보고서: [`docs/report.html`](report.html)
- 실측 케이스: [`docs/SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md)
- 한계 + 극복: [`docs/LIMITATIONS.md`](LIMITATIONS.md) + [`docs/COUNTERMEASURES.md`](COUNTERMEASURES.md)
- 학계 비교: [`docs/RELATED_WORK.md`](RELATED_WORK.md)
- UI 정보 source 비교: [`docs/UI_INFO_SOURCES.md`](UI_INFO_SOURCES.md)
- 다음 단계: [`docs/ROADMAP.md`](ROADMAP.md)

---

## 6. 한 줄 요약 (시연 마무리용)

> *"단일 기법으로는 안 됩니다. 5 개 lens 를 결합하고, 못 본 것을 명시적으로 보고합니다.*
> *Phase 1 으로 Samsung Notes 자율 탐색 + 안전 차단 + audit 보고가 동작합니다.*
> *Phase 2 / 3 으로 시각 정보 + 시스템 정합성까지 확장합니다."*
