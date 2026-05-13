# UI 자동 탐색 도구의 기술적 한계

본 문서는 본 프로젝트(android-ui-exhaustive-explorer)가 의존하는 주요 기술별 사각지대를
**한계 영역 / 왜 못 보나? / 대표 예시** 형식으로 정리한다.
도구 설계 의사결정, 팀장 보고, 신규 기법 도입 검토 시 근거 자료로 사용한다.

> 📎 **각 한계의 극복 방안은** [`COUNTERMEASURES.md`](COUNTERMEASURES.md) **참고. 섹션 번호가 1:1로 대응된다.**

---

## 1. Accessibility (a11y)

→ [극복 방안](COUNTERMEASURES.md#1-accessibility-a11y)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | Canvas 렌더링 | View 하나로 등록되고 내부는 픽셀로만 그려짐 → 자식 노드 없음 | 노트앱 손글씨 영역, 차트, 커스텀 그리기 |
| 2 | OpenGL / GLSurfaceView | GPU가 frame buffer에 직접 그림 → View 계층 밖 | 게임, 3D 뷰어, 카메라 프리뷰 |
| 3 | SurfaceView | 별도 surface에 그려져 a11y tree와 분리 | 비디오 플레이어, 색상 휠 |
| 4 | 커스텀 View (description 누락) | `contentDescription` 미설정 → 노드는 있어도 식별 불가 | 자체 제작 위젯, 아이콘 버튼 |
| 5 | Compose semantics 미지정 | `Modifier.semantics` 없으면 a11y tree 누락 | 구버전·커스텀 Composable |
| 6 | WebView 내부 DOM | WebView root만 노드, 내부 요소는 별도 접근 필요 | 웹 기반 modal, JS 팝업 |
| 7 | 커스텀 Popup / Overlay | `WindowManager.addView` 안 쓰면 window 목록 누락 | 자체 구현 툴팁, 컨텍스트 메뉴 |
| 8 | Toast / Snackbar | 짧은 표시 + 다른 window type → 이벤트 놓치기 쉬움 | 알림 토스트, 일시적 안내 |
| 9 | 동적 콘텐츠 | 노드는 잡혀도 텍스트·상태가 매번 바뀜 → fingerprint 불안정 | 광고, 시계, 실시간 리스트 |
| 10 | 타이밍 의존 | 화면 전환 직후 tree가 비어 있거나 stale 상태 | 애니메이션 중, 로딩 화면 |

---

## 2. UI Automator / View Hierarchy Dump

→ [극복 방안](COUNTERMEASURES.md#2-ui-automator--view-hierarchy-dump)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | Dump 비용 큼 | XML 생성·파싱 무거움 → 초당 1~2회 한계 | 빠른 화면 전환 추적 시 누락 |
| 2 | a11y 한계 그대로 상속 | 내부적으로 a11y tree 사용 | Canvas·OpenGL 동일 누락 |
| 3 | 다중 윈도우 처리 불완전 | 한 번에 한 윈도우만 dump | 다이얼로그 위 다이얼로그 놓침 |
| 4 | 동적 ID 변화 | View ID·hash가 실행마다 다름 | RecyclerView 항목, 광고 배너 |
| 5 | 텍스트 변동 | 시계·카운터 포함 시 dump마다 다름 | 알림 개수, 시간 표시 |

---

## 3. Pixel Grid Sampling

→ [극복 방안](COUNTERMEASURES.md#3-pixel-grid-sampling)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 노이즈 비율 높음 | 대부분의 격자가 빈 영역 → 효율 낮음 | 흰 배경 위 작은 버튼 |
| 2 | 해상도 트레이드오프 | 촘촘하면 느리고, 성기면 누락 | 16px 아이콘, 1mm 토글 |
| 3 | 비격자 UI 약함 | 원형·곡선 영역 미스 | 색상 휠, 원형 슬라이더 |
| 4 | 액션 의미 모름 | 좌표만 알고 무엇인지 모름 | "삭제" 버튼·결제 버튼 무차별 탭 |
| 5 | 드래그·롱프레스 어려움 | 단일 tap 위주 설계 | 그리기, 길게 눌러 메뉴 |
| 6 | 깊이 무시 | 겹친 view 중 상단만 hit | 반투명 overlay 뒤 요소 |

---

## 4. Computer Vision (OpenCV)

→ [극복 방안](COUNTERMEASURES.md#4-computer-vision-opencv)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 추상적 UI 약함 | edge/contour만으로 추론 | 게임 메뉴, 일러스트 버튼 |
| 2 | 텍스트 의미 모름 | OCR 별도 단계 필요 | "확인" vs "취소" 구분 불가 |
| 3 | 테마·디자인 변화 민감 | 색·임계값 기반 룰 | 다크모드 전환 시 깨짐 |
| 4 | False positive 多 | 사각형이면 다 버튼으로 오인 | 카드 UI, 이미지 액자 |
| 5 | 작은 아이콘 누락 | resize·noise로 contour 소실 | 상단바 시스템 아이콘 |
| 6 | 글자·아이콘 구분 어려움 | 둘 다 비슷한 contour | 텍스트 라벨을 버튼으로 오탐 |

---

## 5. VLM (Vision Language Model)

→ [극복 방안](COUNTERMEASURES.md#5-vlm-vision-language-model)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 비결정성 | 동일 입력에도 출력이 흔들림 | 같은 화면에서 매번 다른 후보 제시 |
| 2 | 환각(hallucination) | 없는 버튼을 만들어냄 | "저장 버튼이 우측 상단에 있다"고 잘못 응답 |
| 3 | 좌표 정확도 한계 | 모델이 픽셀 좌표 표현에 약함 | 작은 버튼에서 10~30px 빗나감 |
| 4 | 호출 비용·지연 | API 비용 + 1~3초 응답 | 실시간 DFS 루프에 부담 |
| 5 | 사외 유출 위험 | 화면 캡처 외부 전송 | 사내 데이터 정책 충돌 |
| 6 | 컨텍스트 단절 | 매 호출마다 새 세션 | 이전 화면 기억 없이 단발 판단 |

---

## 6. Differential Probe (탭 전후 화면 비교)

→ [극복 방안](COUNTERMEASURES.md#6-differential-probe-탭-전후-화면-비교)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 비용 큼 | 후보당 2회 캡처 + 대기 | 화면당 수십초 소요 |
| 2 | 동적 변화 오탐 | 시계·광고·애니메이션이 항상 변함 | 빈 영역도 "변화"로 잡힘 |
| 3 | 비가역 액션 위험 | 변화 확인 위해 실제 탭함 | 결제 진행, 메시지 전송 사고 |
| 4 | 비동기 효과 누락 | 변화가 늦게 나타나면 놓침 | 네트워크 응답, 백그라운드 작업 |
| 5 | 미세 변화 미감지 | 임계값 이하 픽셀 차이 무시 | 토글 색만 살짝 바뀌는 경우 |

---

## 7. State Fingerprint (화면 해시)

→ [극복 방안](COUNTERMEASURES.md#7-state-fingerprint-화면-해시)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | Strict 해시 → 무한 신규 | 텍스트·시간 포함 시 매번 fp 다름 | 알림 화면, 채팅 목록 |
| 2 | Loose 해시 → 누락 | 구조만 보면 다른 화면도 동일 fp | 같은 레이아웃의 다른 탭 |
| 3 | 리스트 화면 모호 | 항목 수·내용 매번 변동 | 갤러리, 노트 목록 |
| 4 | 회전·키보드 노출 변동 | 같은 화면도 레이아웃 변경 시 fp 달라짐 | 가로/세로 전환, IME on/off |
| 5 | Modal 중첩 | 상위 화면 + 다이얼로그를 어디까지 한 fp로 볼지 모호 | 권한 다이얼로그가 떠 있는 상태 |

---

## 8. DFS + 백트래킹

→ [극복 방안](COUNTERMEASURES.md#8-dfs--백트래킹)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | BACK이 다른 화면으로 | 앱이 BACK을 가로채거나 종료 | 편집 중 BACK 시 "저장하시겠습니까?" |
| 2 | 깊이 폭발 | 분기 많으면 시간 예산 초과 | 설정 메뉴 수십 단계 |
| 3 | 비가역 분기 | 진행 후 BACK 불가 | 회원가입 완료, 결제 완료 |
| 4 | Replay 의존 | 백트랙 실패 시 Home→재진입→replay 필요 | 깊은 화면 재진입 실패 |
| 5 | 외부 앱 이탈 | 공유·링크 탭으로 다른 앱 진입 | 노트앱에서 공유 → 메시지 앱 |

---

## 9. Replay (경로 재생)

→ [극복 방안](COUNTERMEASURES.md#9-replay-경로-재생)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 비결정성에 취약 | 광고·로그인·시간 의존 | 첫 실행과 두번째 실행 다른 화면 |
| 2 | 좌표 의존 시 깨짐 | 해상도·테마·언어 변경 | S26 → S25 단말 이식 실패 |
| 3 | 타이밍 의존 | wait 부족·과다 모두 실패 | 네트워크 느릴 때 race |
| 4 | 외부 상태 의존 | 로그인·DB·캐시 상태 | 캐시 비운 직후 다른 화면 |
| 5 | 알림·인터럽트 취약 | 재생 중 외부 이벤트 침입 | 전화 수신, 푸시 알림 |

---

## 10. dispatchGesture / Input 자동화

→ [극복 방안](COUNTERMEASURES.md#10-dispatchgesture--input-자동화)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 시스템 UI 차단 | 보안상 일부 영역 입력 거부 | Quick panel, 잠금화면 |
| 2 | 멀티터치 정밀도 | 동시 stroke 동기화 약함 | 핀치 줌, 양손 회전 |
| 3 | 손글씨 정밀도 | 곡선 보간 한계 | 노트앱 자연스러운 필기 |
| 4 | 키 이벤트 한계 | 일부 hardware key 미지원 | 볼륨·전원·Bixby 키 |
| 5 | IME 우회 한계 | 일부 EditText는 SET_TEXT 거부 | 비밀번호 입력란 |

---

## 11. 시스템 정보 (dumpsys / logcat)

→ [극복 방안](COUNTERMEASURES.md#11-시스템-정보-dumpsys--logcat)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | 출력 포맷 불안정 | OS 버전별로 구조 다름 | One UI 8.0 → 8.5 파서 깨짐 |
| 2 | 권한 제한 | eng 빌드 외엔 일부 정보 차단 | user 빌드에서 dumpsys window 제한 |
| 3 | 노이즈 多 | 무관 로그 섞임 | 시스템 서비스 로그 홍수 |
| 4 | 실시간성 떨어짐 | polling 기반 | 짧은 이벤트(toast) 놓침 |
| 5 | 버전 호환성 | Android 메이저 업그레이드마다 변경 | 신규 OS 적용 시 재작성 |

---

## 12. 권한 / 보안 경계

→ [극복 방안](COUNTERMEASURES.md#12-권한--보안-경계)

| # | 한계 영역 | 왜 못 보나? | 대표 예시 |
|---|---|---|---|
| 1 | Accessibility 권한 토글 | 사용자 수동 활성화 필요 | 단말 reset 후 매번 재설정 |
| 2 | 권한 다이얼로그 자동 처리 | 시스템 UI 영역 + 다국어 라벨 | "허용"이 NFC(유니코드 정규화) 차이로 매칭 실패 |
| 3 | 인증·결제·SMS 회피 | 실제 전송 위험 | 결제 진행, SMS 발송 사고 |
| 4 | 앱 외부 이탈 감지 한계 | foreground 변경 감지 지연 | 다른 앱으로 빠지면 그곳까지 탐색 |
| 5 | 시스템 영역 무력 | 잠금화면·복구·Knox 영역 | 진입 자체 불가 |

---

## 한 줄 요약

> **단일 기법으로는 완전탐색 불가능**.
> 각 기법이 서로 다른 사각지대를 가지므로 **계층적 결합 + 상호 검증**이 유일한 현실적 해법.

---

## 본 프로젝트의 대응 매핑

| 한계 영역 | 본 프로젝트 대응 컴포넌트 |
|---|---|
| 1. a11y blind spot | `PixelGridSampler`, (예정) CV / VLM tier |
| 3. Pixel grid 노이즈 | (예정) Differential Probe로 후보 필터링 |
| 4. CV false positive | (예정) Differential Probe + a11y 교차 검증 |
| 5. VLM 비결정성·유출 | 사내 Ollama 한정, stuck 시에만 호출 |
| 7. Fingerprint 불안정 | `ScreenFingerprint` 텍스트 제외 해시 |
| 8. DFS 백트래킹 실패 | `PathReplayer` Home+relaunch+replay |
| 9. Replay 비결정성 | `PathReplayer` resource-id 우선 + 정규화 좌표 + idle-wait |
| 12. 권한 다이얼로그 | `DialogDismisser` 한글 NFC(유니코드 정규화) 처리 |
