/**
 * Tier 5 — Vision Language Model fallback.
 *
 * 책임:
 * - 탐색 정체 (stuck) 시 사내 Ollama (Qwen2-VL / MiniCPM-V 계열) 호출
 * - 스크린샷 + state graph 요약을 prompt 로 보내 다음 액션 좌표 후보 추론
 *
 * 핵심 모듈 (Phase 2 후반 예정):
 * - VlmProposer — server 경유 사내 Ollama proxy 호출, 결과 fp 단위 캐싱
 *
 * 호출 정책:
 * - stuck 카운트 N 초과일 때만 fallback
 * - temperature=0 + 2 회 호출 교집합으로 환각 차단
 * - bbox 만 받고 중심점은 자체 계산 (모델 좌표 부정확성 보완)
 */
package com.exhaustive.explorer.tier5_vlm
