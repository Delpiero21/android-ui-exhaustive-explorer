/**
 * Core — 모든 Tier 가 의존하는 공통 모듈.
 *
 * 핵심 모듈 (Phase 1 예정):
 * - StateGraph — 화면 fingerprint → 노드 + outgoing edges
 * - ScreenFingerprint — strict + loose 두 단계 해시
 * - ScreenCapture — MediaProjection / a11y screenshot 통합
 *
 * 규칙:
 * - core 는 어디서든 사용 가능, 다른 곳에 의존하지 않는다.
 * - Tier 모듈끼리는 직접 참조 금지 — 모두 engine 이 조합.
 */
package com.exhaustive.explorer.core
