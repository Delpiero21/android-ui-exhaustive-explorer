"""GET /health — 서버 동작 확인 + 단말 연결 상태 (Phase 1에서 확장)."""

from __future__ import annotations

from typing import Literal

from fastapi import APIRouter
from pydantic import BaseModel

from explorer_server import __version__

router = APIRouter(prefix="/api", tags=["health"])


class HealthResponse(BaseModel):
    status: Literal["ok"]
    version: str
    phase: str


@router.get("/health", response_model=HealthResponse)
async def health() -> HealthResponse:
    return HealthResponse(status="ok", version=__version__, phase="1-autonomous")
