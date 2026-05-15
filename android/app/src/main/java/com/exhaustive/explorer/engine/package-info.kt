/**
 * Engine — DFS 루프 본체 + 백트래킹 + 경로 재생.
 *
 * 핵심 모듈 (Phase 1 예정):
 * - ExplorerEngine — 통합 DFS 루프, Tier 1~5 조합, StateGraph 갱신
 * - PathReplayer — 백트랙 실패 시 Home + relaunch + replay
 *
 * 통합 DFS 루프 의사 코드: docs/ARCHITECTURE.md §4
 */
package com.exhaustive.explorer.engine
