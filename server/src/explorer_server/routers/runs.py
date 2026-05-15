"""GET /api/runs — 탐색 실행 결과 목록.

Phase 0 stub: 빈 리스트를 반환.
Phase 1: 단말에서 adb pull 한 events.jsonl 을 파싱해 저장소(storage/)로부터 조회.
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/api", tags=["runs"])


class RunSummary(BaseModel):
    run_id: str
    target_app: str
    started_at: str
    duration_sec: int
    screens_found: int


@router.get("/runs", response_model=list[RunSummary])
async def list_runs() -> list[RunSummary]:
    # TODO Phase 1: storage.list_runs() 로 대체
    return []
