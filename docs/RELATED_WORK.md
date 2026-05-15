# Related Work — 학계 / 산업계 비교

본 프로젝트의 위치를 학계 · 오픈소스 도구 대비 명확히 한다.
팀장 · 매니저 보고 시 "왜 이 도구가 필요한가" 를 차별화 포인트로 설명하기 위한 근거 자료.

핵심 메시지:

> 본 프로젝트는 **새 알고리즘이 아니라 5-Tier 결합 전략 + audit 단위 보고**가 차별점.
> Tier 1 (a11y) / Tier 4 (Probe) 같은 개별 기법은 학계에서 이미 다뤄졌다.
> **차별성은 통합과 audit 가능성**에 있다.

---

## 0. 한눈에 보는 시기 카테고리

```
2003 - 2013   ▶ GUI Ripping 시대 (기반 개념 정립)
2013 - 2016   ▶ 초기 DFS 도구 (A³E, Dynodroid)
2016 - 2019   ▶ ⭐ 핵심 도구급 논문 (Sapienz, Stoat, DroidBot, APE)
2019 - 2022   ▶ 머신러닝 시대 (Humanoid, Q-testing, TimeMachine, ComboDroid)
2023 - now    ▶ LLM / VLM 시대 (GPTDroid, AutoDroid, Mobile-Agent, DroidRun)
```

본 프로젝트는 **2016–2019 도구급 논문 (DroidBot / APE)** 에 가장 가깝고,
**2023–now 의 LLM 접근** 을 Tier 5 fallback 으로 흡수.

---

## 1. 시작점 (2003–2016)

| 논문 | 저자 | 발표지 | 핵심 |
|---|---|---|---|
| GUI Ripping | Memon et al. | WCRE 2003 | 데스크탑 GUI 자동 탐색 — 안드로이드 이전 원조. "screen state" 그래프 개념 |
| A³E | Azim & Neamtiu | OOPSLA 2013 | 안드로이드 DFS 탐색의 시초. "screen identifier" 로 중복 화면 제거 |
| Dynodroid | Machiry et al. | FSE 2013 | random + observe-select-execute 사이클. UI / system 이벤트 둘 다 |
| GUI Ripping for Android | Amalfitano et al. | ASE 2012 | Memon 의 GUI Ripping 을 Android 에 적용. Activity / View 단위 그래프 |

→ 본 프로젝트의 `core/StateGraph` 는 이 시대의 "화면 단위 노드" 개념을 그대로 사용.

---

## 2. 핵심 도구급 (2016–2019) — 본 프로젝트의 직계 조상

### 2.1 DroidBot (Li et al., ICSE 2017) ⭐ 가장 가까운 비교 대상

- **저자**: Yuanchun Li, Ziyue Yang, Yao Guo, Xiangqun Chen (Peking Univ.)
- **GitHub**: https://github.com/honeynet/droidbot
- **핵심 기법**: a11y tree → 화면 fingerprint → state model → DFS / BFS / random 정책 선택
- **장점**: 결정적, lightweight, 인스트루먼테이션 불필요 → preload 앱에도 적용 가능
- **본 프로젝트와의 관계**:
  - `tier1_a11y` + `core/StateGraph` + `engine/ExplorerEngine` = 사실상 DroidBot 의 확장
  - 차이점: DroidBot 은 단일 Tier (a11y). 본 도구는 Tier 2–5 추가로 사각지대 보완

### 2.2 APE (Gu et al., ICSE 2019) ⭐ active refinement

- **저자**: Tianxiao Gu, Chengnian Sun, Xiaoxing Ma, et al. (Nanjing Univ.)
- **핵심 기법**: state model 을 **runtime active learning** 으로 동적 refinement
  - 처음엔 거친 모델 → 새 화면 발견 시 split → 너무 다른 화면이 같이 묶이는 일 방지
- **본 프로젝트와의 관계**: 두 단계 fingerprint (strict + loose) 가 같은 문제 해결. APE 의 active refinement 는 더 정교하지만 복잡 → Phase 2 로 보류

### 2.3 Stoat (Su et al., FSE 2017)

- **저자**: Ting Su, Guozhu Meng, et al. (East China Normal Univ. + NTU)
- **핵심 기법**: Markov chain + GA 로 사용자 행동 모델링 후 sampling
- **본 프로젝트와의 관계**: 동일 audit 효과를 DFS 로 단순 달성 가능 → **채택 X**. 확률 모델 학습 비용 부담

### 2.4 Sapienz (Mao et al., ISSTA 2016)

- **저자**: Ke Mao, Mark Harman, Yue Jia (UCL → Facebook 인수)
- **핵심 기법**: multi-objective GA — 코드 커버리지 + crash 수 + 시퀀스 길이 동시 최적화
- **본 프로젝트와의 관계**: 소스 인스트루먼테이션 의존 → **preload 앱처럼 소스 접근 불가한 환경엔 부적합**. 채택 X

### 핵심 도구급 정리

| 도구 | 정책 | 인스트루먼테이션 | preload 앱 적용 | 본 프로젝트 채택 |
|---|---|---|---|---|
| DroidBot | DFS/BFS/random | ❌ 불필요 | ✅ 가능 | ✅ 직접 영향 |
| APE | active refinement | ❌ 불필요 | ✅ 가능 | 🔶 Phase 2 보류 |
| Stoat | Markov + GA | ❌ 불필요 | ✅ 가능 | ❌ |
| Sapienz | multi-obj GA | **✅ 필요** | ❌ 불가 | ❌ |

→ **DroidBot 논문 한 편만 정독하면 본 프로젝트 Phase 1 의 80% 가 이해됨.**

---

## 3. 머신러닝 시대 (2019–2022)

| 논문 | 발표지 | 핵심 | 본 프로젝트 입장 |
|---|---|---|---|
| Humanoid | Li et al., ASE 2019 | 인간 트레이스 imitation learning | 학습 데이터 비용 ↑. 보류 |
| Q-testing | Pan et al., ISSTA 2020 | RL + 호기심 기반 reward | RL 운영 부담 ↑. 보류 |
| **TimeMachine** | Dong et al., ICSE 2020 | state checkpointing + 시간 되돌리기 (snapshot) | ⭐ **Replay 의 정공법** — Phase 1 의 PathReplayer 를 보완할 후보 |
| ComboDroid | Wang et al., ICSE 2020 | use case 조합 기반 시퀀스 합성 | 가이드 모드 없는 본 프로젝트엔 부적합 |

→ ML 계열은 **audit 가능성 / 재현성** 측면에서 사내 검증 도구로 약함. Phase 1 에서는 결정적 알고리즘만 사용하고 ML 은 Tier 5 (VLM) 에 집중.

---

## 4. LLM / VLM 시대 (2023–현재) — Tier 5 의 학술 기반

| 논문 / 도구 | 발표 | 핵심 | 본 프로젝트 입장 |
|---|---|---|---|
| **GPTDroid** | Liu et al., 2023 (arXiv → ICSE-Industry) | GPT-3 zero-shot UI testing | Tier 5 prompt 설계 참고 |
| **AutoDroid** | Wen et al., MobiCom 2024 | LLM + offline knowledge mining | offline knowledge 부분이 흥미 — Phase 3 (시스템 연계) 검토 |
| **Mobile-Agent** | Wang et al., 2024 | VLM (screenshot + a11y) 기반 multi-modal agent | 본 프로젝트의 Tier 1 + Tier 5 결합 패턴과 일치 |
| DroidRun | 2024 오픈소스 | LLM 자연어 task 실행 | https://droidrun.ai/ — 데모용 |

→ 본 프로젝트의 **Tier 5 (VLM fallback)** 가 정확히 이 흐름.
   단, 우리는 *"stuck 시에만 fallback + 사내 Ollama 한정"* 으로 제한해 비용 · 환각 · 사외 유출 위험 차단.

---

## 5. 산업 / 오픈소스 도구

| 도구 | 제공 | 카테고리 | 본 프로젝트 관계 |
|---|---|---|---|
| **adb shell monkey** | Google (Android SDK 내장) | random | 비교 baseline, stress test 용 |
| **UI Automator** | Google | a11y tree dump | 본 도구도 일부 사용 (단발 dump 검증 시) |
| **Espresso** | Google | instrumentation (white-box) | 소스 필요 → preload 부적합 |
| **Appium** | OSS (Selenium 계열) | cross-platform | webdriver 프로토콜. 본 도구와 직교 |
| **Firebase Robo Test** | Google Cloud | crawler-based | 클라우드 전용. 사내 단말 사용 불가 |
| **DroidBot** | OSS (학술) | 학술 결과 그대로 | 본 도구의 출발점 |

---

## 6. ⭐ LIMITATIONS × Related Work 매핑

본 프로젝트의 12 한계 영역 ([`LIMITATIONS.md`](LIMITATIONS.md)) 을 가장 잘 다룬 학술 / 도구.

| § | 한계 영역 | 가장 가까운 학술 시도 | 본 프로젝트의 접근 | 차별성 |
|---|---|---|---|---|
| 1.1 | Canvas 렌더링 | (정공법 거의 없음 — 대부분 skip) | Tier 4 Differential Probe | **명시적 위치 부여** |
| 1.2 | OpenGL / GLSurfaceView | (없음) | Tier 2 Grid + Tier 4 Probe | 동일 |
| 1.7 | 커스텀 Popup | DroidBot 이 `getWindows()` 사용 | `tier1_a11y/MultiWindowCollector` (Phase 1 최우선) | DroidBot 보다 명시적 |
| 1.9 | 동적 콘텐츠 (fingerprint 불안정) | **APE** 의 active refinement | 두 단계 해시 (strict + loose) | APE 가 더 정교 — Phase 2 도입 검토 |
| 2.3 | 다중 윈도우 처리 | DroidBot · Mobile-Agent 가 부분 처리 | `MultiWindowCollector` 로 `getWindows()` 통합 | Case 6 ↔ 10 비일관성 실측 후 정공법 |
| 4 | CV false positive | (학술 거의 없음) | Tier 3 + Tier 1 IoU 교차 검증 | 본 프로젝트 자체 발명 |
| 5 | VLM 비결정성·환각 | Mobile-Agent 가 환각 문제 일부 다룸 | `temperature=0` + 2 회 호출 교집합 + a11y/CV IoU 검증 | 보수적 보호 장치 다중 |
| 6 | Differential Probe | (정확한 학술 명칭은 드묾, 일부 ML 평가에서 비슷한 시도) | Tier 4 명시 모듈화 | Phase 1 핵심 산출물 |
| 7 | State Fingerprint | DroidBot (strict) / APE (refinement) | 두 단계 해시 | APE 와 DroidBot 의 중간 |
| 8 | DFS 백트래킹 | A³E / DroidBot | `PathReplayer` (Home + relaunch + replay) | 백트랙 실패 시 명시 복구 경로 |
| 9 | Replay 비결정성 | **TimeMachine (ICSE 2020)** snapshot | resource-id 우선 + 정규화 좌표 + idle-wait | snapshot 보다 가벼움, 단 결정성 ↓ |
| 12.2 | 권한 다이얼로그 | (학계 거의 부재) | `DialogDismisser` + 한글 NFC 정규화 | 사내 운영 특화 |

**해석**:
- §1.1, §1.2 (Canvas/OpenGL) 와 §6 (Differential Probe) 는 학계에서도 정공법이 드물어 본 프로젝트가 **개척 영역**
- §1.9, §7 (fingerprint) 는 APE 가 가장 강력. 채택은 안 했지만 알고리즘 의식적으로 본받음
- §9 (Replay) 는 **TimeMachine 도입을 Phase 3 후보**로 검토

---

## 7. 본 프로젝트의 위치

### 7.1 새 알고리즘인가?

❌ — 알려진 기법의 결합이 본질.

### 7.2 그럼 무엇이 차별점인가?

1. **5-Tier 통합** — 학계는 보통 한두 Tier (DroidBot=a11y, Sapienz=GA + 인스트루먼테이션).
   본 도구는 a11y + Grid + CV + Probe + VLM 을 단일 DFS 루프에 결합
2. **Inventory-as-denominator audit** — *"단말 inventory N 개 중 explored M / shallow K / failed J / excluded P"*.
   학계는 단일 AUT 가정이 일반적이라 이 개념 자체가 없음
3. **Differential Probe 의 명시적 위치 부여** — 학계에선 "샘플링 보완" 정도로만 다뤘던 것을 Tier 4 라는 정식 단계로 격상
4. **사내 운영 특화** — 한국어 권한 라벨 (NFC 정규화), Samsung permission controller, `com.samsung.*` / `com.sec.*` prefix 필터
5. **실측 케이스 기반 설계** — [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) 의 5 케이스 실측이 모듈 우선순위 결정

### 7.3 한 줄 요약

> 오픈소스 도구가 *"얼마나 깊이 들어갔나"* (state graph) 를 측정한다면,
> 본 프로젝트는 ***"단말의 전체 UI 표면에서 무엇을 빠뜨렸나"*** (inventory × screen × candidate) 를
> audit 가능한 형태로 보고한다.

---

## 8. 추천 reading list (시간 순서)

본 프로젝트 합류자가 학술 배경 학습 시:

1. **DroidBot** (ICSE 2017) — 30 분 — 본 프로젝트 Phase 1 의 직계 부모
2. **APE** (ICSE 2019) — 1 시간 — state model refinement 의 정수
3. **TimeMachine** (ICSE 2020) — 30 분 — Replay 의 정공법
4. **AutoDroid** (MobiCom 2024) — 1 시간 — Tier 5 VLM 의 설계 참고
5. **Choudhary et al., ASE 2015** — 30 분 — Monkey/Dynodroid/A³E 비교 벤치마크 (오래됐지만 비교 기준 명확)

총 ~3.5 시간으로 도메인 이해.

---

## 9. 더 찾아보는 법

| 채널 | 키워드 |
|---|---|
| **DBLP** | "Android GUI testing" / "Android UI exploration" / "automated mobile testing" |
| **Google Scholar** | 위 + 인용 그래프 따라가기 (DroidBot citing papers ~ 1500+) |
| **arXiv (cs.SE)** | 최신 LLM 기반 — 2024 년 이후 매월 신규 등장 |
| **학회** | ICSE · ESEC/FSE · ASE · ISSTA · MobiSys · MobiCom — 매년 1~2 편 새 논문 |

주요 survey:
- "A Comprehensive Survey of Android UI Testing Tools" (Mendez-Porras et al., 2015) — 약간 오래됨
- "Automated Test Input Generation for Android: Are We There Yet?" (Choudhary et al., ASE 2015) — 비교 벤치마크
- "GUI Testing for Mobile Applications" (Linares-Vásquez et al., IEEE Access 2017) — 종합 정리

---

## 10. 본 문서 갱신 정책

- 신규 학술 / 도구 발견 시 §1–5 추가
- 본 도구 Phase 변화 시 §6 매핑 + §7 차별성 갱신
- 매 분기 1 회 ICSE / FSE / ASE / ISSTA proceedings 훑기 권장
