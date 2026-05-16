"""GET /api/phase — PHASE_LOG.md 의 machine-readable mirror 반환.

데이터 source: project_root/data/phase_log.json
이 파일은 PHASE_LOG.md 와 동시 갱신 (mandate v3).
"""

from __future__ import annotations

import json
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException

router = APIRouter(prefix="/api", tags=["phase"])


def _phase_log_path() -> Path:
    # __file__ = server/src/explorer_server/routers/phase.py
    # parents[4] = project root
    return Path(__file__).resolve().parents[4] / "data" / "phase_log.json"


@router.get("/phase")
async def get_phase_log() -> dict[str, Any]:
    """PHASE_LOG 전체 (phases + comparison_table). web Phase Progress 페이지가 사용."""
    p = _phase_log_path()
    if not p.exists():
        raise HTTPException(404, detail=f"phase_log.json not found at {p}")
    return json.loads(p.read_text(encoding="utf-8"))


@router.get("/phase/current")
async def get_current_phase() -> dict[str, Any]:
    """현재 진행 중 또는 가장 최근 완료된 phase. UI 헤더 badge 용."""
    data = await get_phase_log()
    phases = data.get("phases", [])
    in_progress = next((p for p in phases if p.get("status") == "in_progress"), None)
    if in_progress:
        return {"phase": in_progress, "label": "in_progress"}
    completed = [p for p in phases if p.get("status") == "completed"]
    if completed:
        latest = max(completed, key=lambda p: p.get("id", 0))
        return {"phase": latest, "label": "latest_completed"}
    return {"phase": phases[0] if phases else None, "label": "none"}
