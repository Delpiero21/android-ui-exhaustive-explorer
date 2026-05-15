/**
 * Tier 2 — Pixel Grid Sampling.
 *
 * 책임:
 * - Tier 1 (a11y) 가 못 잡는 영역에 대해 격자 좌표를 후보로 등록
 * - Coarse-to-fine 계층 그리드 (256px → 64px → 16px) 로 효율화
 *
 * 핵심 모듈 (Phase 2 예정):
 * - PixelGridSampler — a11y 미커버 영역만 추출 + 격자 후보 생성
 *
 * 근거:
 * Canvas / OpenGL 영역은 a11y tree 가 자식 0 개라 좌표만으로 찔러봐야 함.
 * Tier 4 Differential Probe 와 결합해 hit 좌표만 채택.
 */
package com.exhaustive.explorer.tier2_grid
