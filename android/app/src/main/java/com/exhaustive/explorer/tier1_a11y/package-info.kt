/**
 * Tier 1 — Accessibility (a11y) baseline.
 *
 * 책임:
 * - 표준 View 계층에서 클릭 가능 노드 추출
 * - `AccessibilityService.getWindows()` 반복으로 popup / IME window 통합 수집
 *
 * 핵심 모듈 (Phase 1 신규):
 * - MultiWindowCollector — uiautomator dump 의존을 제거하고 모든 window 통합
 * - NodeTraversal — 각 window root 의 a11y tree 순회 + clickable 후보 추출
 *
 * 근거:
 * docs/SAMSUNG_NOTES_HARD_CASES.md 의 Case 1 (SIP 키보드) / 6 (ActionMode) / 10 (AI 팝업).
 * `rootInActiveWindow` 만 사용하면 어떤 popup 은 잡히고 어떤 popup 은 놓치는지 예측 불가.
 */
package com.exhaustive.explorer.tier1_a11y
