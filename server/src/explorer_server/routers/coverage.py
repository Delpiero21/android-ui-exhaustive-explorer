"""GET /api/coverage — Tier 별 hit 기여도 + UI 영역별 커버리지 히트맵.

Phase 0 stub: 빈 응답.
Phase 2 (Tier 분리 후) 부터 본격적으로 데이터 채워짐.
"""

from __future__ import annotations

from fastapi import APIRouter
from pydantic import BaseModel

router = APIRouter(prefix="/api", tags=["coverage"])


class TierContribution(BaseModel):
    tier: int  # 1..5
    label: str  # "a11y" / "grid" / "cv" / "probe" / "vlm"
    hits: int
    pct: float


class CoverageResponse(BaseModel):
    total_screens: int
    total_candidates: int
    tier_contributions: list[TierContribution]


@router.get("/coverage", response_model=CoverageResponse)
async def coverage() -> CoverageResponse:
    # TODO Phase 2: storage.aggregate_coverage() 로 대체
    return CoverageResponse(
        total_screens=0,
        total_candidates=0,
        tier_contributions=[],
    )
