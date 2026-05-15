/**
 * Input — 합성 입력 (탭 / 스와이프 / 텍스트 주입).
 *
 * 핵심 모듈 (Phase 1 예정):
 * - GestureDispatcher — dispatchGesture 기반 tap / longpress / swipe / multi-stroke
 * - TextInputSampler — `ACTION_SET_TEXT` 직접 주입 (IME 우회)
 *
 * 한계:
 * docs/LIMITATIONS.md §10 dispatchGesture / Input 자동화.
 * 시스템 UI / 멀티터치 정밀도 / 손글씨 곡선 보간 등은 별도 보완 필요.
 */
package com.exhaustive.explorer.input
