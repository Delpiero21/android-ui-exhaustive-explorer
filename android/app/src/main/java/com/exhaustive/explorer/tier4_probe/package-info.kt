/**
 * Tier 4 — Differential Probe.
 *
 * 책임:
 * - 후보 좌표 탭 전후 화면 캡처를 비교해 실제 인터랙티브 영역 판별 (HOT / COLD)
 * - false positive 자동 필터링 — 무의미한 탭 제거
 *
 * 핵심 모듈 (Phase 1 후반 예정):
 * - DifferentialProbe — perceptual hash 기반 영역별 변화 감지
 *
 * 근거:
 * docs/SAMSUNG_NOTES_HARD_CASES.md 의 Case 2 (캔버스).
 * 캔버스는 단일 View 한 개로 압축됨 → 좌표 탭 후 변화로만 인터랙션 여부 판별 가능.
 */
package com.exhaustive.explorer.tier4_probe
