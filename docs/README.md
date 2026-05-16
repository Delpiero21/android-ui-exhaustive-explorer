# docs/ — 문서 인덱스

본 디렉토리의 문서는 **목적·청중·갱신 주기** 별로 분리되어 있다.
용도에 맞는 문서로 바로 진입할 수 있도록 인덱스로 정리한다.

---

## 핵심 문서

| 문서 | 목적 | 청중 | 갱신 주기 |
|---|---|---|---|
| [`ARCHITECTURE.md`](ARCHITECTURE.md) | 5-Tier 아키텍처 + 모듈 의존성 + 통합 DFS 루프 | 개발자, 신규 합류자 | 주요 설계 변경 시 |
| [`LIMITATIONS.md`](LIMITATIONS.md) | 기술별 사각지대 정리 (한계 영역 / 왜 못 보나 / 대표 예시) | 팀장 보고, QA 매니저 | 반기 (OS 변경 시) |
| [`COUNTERMEASURES.md`](COUNTERMEASURES.md) | 한계별 극복 방안 + 우선순위 (한계 영역 / 극복 방안 / 비고) | 개발자, 로드맵 의사결정 | 월~분기 |
| [`ROADMAP.md`](ROADMAP.md) | Phase 0~3 단계별 산출물·완료 기준 | 팀, 보고 | Phase 진입/종료 시 |
| [`PHASE_LOG.md`](PHASE_LOG.md) ⭐ | **각 Phase 별 커버리지 / 한계 / 기술스택 — 단일 진실** | 팀장 보고, 신규 합류자, 차기 계획 | **Phase 마다 자동 갱신 (mandate)** |
| [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) | Samsung Notes 실측 검증 (5 케이스, 가설은 신규 캡처 시 추가) | 개발자, 보고 | 신규 캡처 시 |
| [`RELATED_WORK.md`](RELATED_WORK.md) | 학계 / 산업계 비교 + 본 프로젝트의 위치 + LIMITATIONS × 학술 매핑 | 팀장 보고, 신규 합류자 | 분기 1 회 |
| [`UI_INFO_SOURCES.md`](UI_INFO_SOURCES.md) | Android UI 정보 source 비교 (a11y / dumpsys / screencap / ViewDebug 등 10 종) + cross-check 매트릭스 | 개발자, 신규 합류자 | 신규 source 검토 시 |
| [`DEMO.md`](DEMO.md) | 시연 절차 + 기반 기술 설명 흐름 + Q&A 대비 + 사고 대응 | 시연 진행자, 본인 | 시연 전 |
| [`report.html`](report.html) | 한눈에 보는 대시보드 (보고용) | 팀장·매니저 발표 | LIMITATIONS/COUNTERMEASURES 갱신 시 |

---

## LIMITATIONS ↔ COUNTERMEASURES 매핑

두 문서는 **섹션 번호 1:1 대응** 한 쌍이다.
"문제 정의 ↔ 해결 방안" 으로 함께 인용하면 보고 효과가 가장 크다.

| # | 기술 영역 | 한계 | 극복 |
|---|---|---|---|
| 1 | Accessibility (a11y) | [LIMITATIONS §1](LIMITATIONS.md#1-accessibility-a11y) | [COUNTERMEASURES §1](COUNTERMEASURES.md#1-accessibility-a11y) |
| 2 | UI Automator / View Dump | [LIMITATIONS §2](LIMITATIONS.md#2-ui-automator--view-hierarchy-dump) | [COUNTERMEASURES §2](COUNTERMEASURES.md#2-ui-automator--view-hierarchy-dump) |
| 3 | Pixel Grid Sampling | [LIMITATIONS §3](LIMITATIONS.md#3-pixel-grid-sampling) | [COUNTERMEASURES §3](COUNTERMEASURES.md#3-pixel-grid-sampling) |
| 4 | Computer Vision (OpenCV) | [LIMITATIONS §4](LIMITATIONS.md#4-computer-vision-opencv) | [COUNTERMEASURES §4](COUNTERMEASURES.md#4-computer-vision-opencv) |
| 5 | VLM (Vision Language Model) | [LIMITATIONS §5](LIMITATIONS.md#5-vlm-vision-language-model) | [COUNTERMEASURES §5](COUNTERMEASURES.md#5-vlm-vision-language-model) |
| 6 | Differential Probe | [LIMITATIONS §6](LIMITATIONS.md#6-differential-probe-탭-전후-화면-비교) | [COUNTERMEASURES §6](COUNTERMEASURES.md#6-differential-probe-탭-전후-화면-비교) |
| 7 | State Fingerprint | [LIMITATIONS §7](LIMITATIONS.md#7-state-fingerprint-화면-해시) | [COUNTERMEASURES §7](COUNTERMEASURES.md#7-state-fingerprint-화면-해시) |
| 8 | DFS + 백트래킹 | [LIMITATIONS §8](LIMITATIONS.md#8-dfs--백트래킹) | [COUNTERMEASURES §8](COUNTERMEASURES.md#8-dfs--백트래킹) |
| 9 | Replay | [LIMITATIONS §9](LIMITATIONS.md#9-replay-경로-재생) | [COUNTERMEASURES §9](COUNTERMEASURES.md#9-replay-경로-재생) |
| 10 | dispatchGesture / Input | [LIMITATIONS §10](LIMITATIONS.md#10-dispatchgesture--input-자동화) | [COUNTERMEASURES §10](COUNTERMEASURES.md#10-dispatchgesture--input-자동화) |
| 11 | 시스템 정보 (dumpsys/logcat) | [LIMITATIONS §11](LIMITATIONS.md#11-시스템-정보-dumpsys--logcat) | [COUNTERMEASURES §11](COUNTERMEASURES.md#11-시스템-정보-dumpsys--logcat) |
| 12 | 권한 / 보안 경계 | [LIMITATIONS §12](LIMITATIONS.md#12-권한--보안-경계) | [COUNTERMEASURES §12](COUNTERMEASURES.md#12-권한--보안-경계) |

---

## 시나리오별 진입점

### 🆕 처음 합류한 사람
1. [`../README.md`](../README.md) — 프로젝트 한 줄 + 디렉토리 구조
2. [`PHASE_LOG.md`](PHASE_LOG.md) — **"지금 무엇이 되는가 / 안 되는가" 단일 진실** (가장 빠른 도착)
3. [`ARCHITECTURE.md`](ARCHITECTURE.md) — 5-Tier 가 무엇이고 왜 그렇게 쌓았는가
4. [`UI_INFO_SOURCES.md`](UI_INFO_SOURCES.md) — Android UI 정보 source 가 어떤 종류 있고 차이가 뭔지
5. [`LIMITATIONS.md`](LIMITATIONS.md) → [`COUNTERMEASURES.md`](COUNTERMEASURES.md) — 왜 이렇게 만들었나
6. [`SAMSUNG_NOTES_HARD_CASES.md`](SAMSUNG_NOTES_HARD_CASES.md) — 실측 5 케이스로 어떤 사각지대가 있는지 확인
7. [`RELATED_WORK.md`](RELATED_WORK.md) — 학계 배경. §8 reading list 로 ~3.5 시간에 도메인 이해
8. [`ROADMAP.md`](ROADMAP.md) — 어디까지 했고 어디로 가는가

### 📊 팀장·매니저 보고 준비
1. [`PHASE_LOG.md`](PHASE_LOG.md) ⭐ — "현재 무엇이 되는가 / 안 되는가" 한 페이지 답
2. [`report.html`](report.html) — 한 페이지 대시보드 (브라우저로 발표)
3. [`LIMITATIONS.md`](LIMITATIONS.md) — 문제 정의 깊이 설명 시
4. [`COUNTERMEASURES.md`](COUNTERMEASURES.md) §우선순위 — 다음 분기 계획 인용
5. [`RELATED_WORK.md`](RELATED_WORK.md) §7 — 학계 대비 차별성 ("왜 이게 필요한가" 설득)

### 🛠 새 기능 추가 의사결정
1. [`LIMITATIONS.md`](LIMITATIONS.md) 에서 해당 영역 사각지대 확인
2. [`COUNTERMEASURES.md`](COUNTERMEASURES.md) 에서 비고(🟢/🟡/🔴) 확인 — 작업량 가늠
3. [`ARCHITECTURE.md`](ARCHITECTURE.md) 에서 어느 Tier 폴더에 들어갈지 식별
4. [`ROADMAP.md`](ROADMAP.md) 의 Phase 매핑 확인

---

## 갱신 정책

| 문서 | 누가 갱신 | 언제 |
|---|---|---|
| ARCHITECTURE | 리드/개발자 | 주요 모듈 추가·변경 시 |
| LIMITATIONS | 개발자 | OS 업그레이드 / 신규 사각지대 발견 시 |
| COUNTERMEASURES | 개발자 | 신규 기법 도입 검토 시 / Phase 완료 시 |
| ROADMAP | 리드 | Phase 진입·완료 시 |
| **PHASE_LOG** ⭐ | **개발자 + 리드** | **Phase 시작·종료·산출물 완성 시 무조건 동시 갱신 (mandate)** |
| RELATED_WORK | 리드 | 분기 1 회 + 본 도구 Phase 전환 시 §6 매핑 갱신 |
| UI_INFO_SOURCES | 리드/개발자 | 신규 source 검토 시 / OS 업그레이드로 API 변경 시 |
| SAMSUNG_NOTES_HARD_CASES | 개발자 | 신규 케이스 캡처 시 |
| report.html | 위 갱신될 때마다 동기화 |
