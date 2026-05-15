/**
 * Tier 3 — Computer Vision + OCR.
 *
 * 책임:
 * - 스크린샷에서 버튼 / 아이콘 후보 검출 (OpenCV)
 * - 영역별 dominant color / 텍스트 라벨 추출 (ML Kit Text Recognition)
 * - 시각 속성 라벨이 누락된 노드에 의미 부여
 *
 * 핵심 모듈 (Phase 2 예정):
 * - CvProposer — 사각형 / 원 검출, 색상 추출
 * - OcrLabeler — 후보 영역에 텍스트 부착, 위험 단어 매칭
 *
 * 근거:
 * docs/SAMSUNG_NOTES_HARD_CASES.md 의 Case 4 (색상 팔레트).
 * a11y 가 노드는 잡았지만 content-desc 가 빈 문자열 → 색깔 자체가 사각지대.
 */
package com.exhaustive.explorer.tier3_cv
