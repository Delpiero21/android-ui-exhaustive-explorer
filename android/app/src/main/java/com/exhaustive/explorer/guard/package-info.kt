/**
 * Guard — 위험 차단 + 인터럽트 처리 (백그라운드 상시 가동).
 *
 * 핵심 모듈 (Phase 1 예정):
 * - DialogDismisser — 권한 다이얼로그·인터럽트 자동 처리, NFC(유니코드 정규화) 기반 라벨 매칭
 * - DangerousActionGuard — "결제·전송·삭제" 키워드 블랙리스트, 패키지 화이트리스트
 *
 * 호출 정책: ExplorerEngine 루프와 무관하게 항상 tick 됨.
 */
package com.exhaustive.explorer.guard
