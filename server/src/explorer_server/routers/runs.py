"""GET /api/runs — 실제 회수된 run 데이터 노출.

Phase 1 (Dashboard batch) 구현:
- 단말의 RunRecorder 가 events.jsonl + summary.json 을 /sdcard/.../runs/{id}/ 에 저장
- scripts/pull_runs.ps1 가 server/data/runs/{id}/ 로 회수
- 본 라우터가 그 디렉토리를 스캔해서 노출
"""

from __future__ import annotations

import json
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException
from pydantic import BaseModel

router = APIRouter(prefix="/api", tags=["runs"])


# server 디렉토리 기준 ../server/data/runs (cwd 무관)
def _runs_dir() -> Path:
    # __file__ = server/src/explorer_server/routers/runs.py
    # 4 단계 위 = server/
    return Path(__file__).resolve().parents[3] / "data" / "runs"


class RunStats(BaseModel):
    new_screens: int = 0
    actions_executed: int = 0
    edges_recorded: int = 0
    hot_edges: int = 0
    cold_edges: int = 0
    hot_pct: float = 0.0
    guard_blocks: int = 0
    evacuates: int = 0
    dialogs_dismissed: int = 0
    actions_per_sec: float = 0.0


class RunSummary(BaseModel):
    run_id: str
    target_pkg: str
    started_at: int  # ms epoch
    ended_at: int
    duration_ms: int
    duration_sec: float
    device_model: str | None = None
    device_sdk: int | None = None
    stats: RunStats
    has_screenshots: bool = False


@router.get("/runs", response_model=list[RunSummary])
async def list_runs() -> list[RunSummary]:
    """단말에서 회수된 모든 run 의 summary 리스트. 시작 시간 내림차순."""
    runs_dir = _runs_dir()
    if not runs_dir.exists():
        return []
    out: list[RunSummary] = []
    for d in sorted(runs_dir.iterdir(), reverse=True):
        if not d.is_dir():
            continue
        summary_file = d / "summary.json"
        if not summary_file.exists():
            continue
        try:
            raw = json.loads(summary_file.read_text(encoding="utf-8"))
            out.append(_to_summary(d.name, raw, has_screenshots=(d / "screens").exists()))
        except (json.JSONDecodeError, KeyError) as e:
            # 부분적으로 손상된 run 도 응답에 포함하지 않고 skip (로깅만)
            print(f"warn: skip {d.name} — {e}")
            continue
    return out


@router.get("/runs/{run_id}", response_model=dict[str, Any])
async def get_run_detail(run_id: str) -> dict[str, Any]:
    """단일 run 의 summary + events 시계열."""
    run_dir = _runs_dir() / run_id
    if not run_dir.is_dir():
        raise HTTPException(404, detail=f"run not found: {run_id}")
    summary_file = run_dir / "summary.json"
    events_file = run_dir / "events.jsonl"
    if not summary_file.exists():
        raise HTTPException(404, detail="summary.json missing")

    summary = json.loads(summary_file.read_text(encoding="utf-8"))
    events: list[dict[str, Any]] = []
    if events_file.exists():
        for line in events_file.read_text(encoding="utf-8").splitlines():
            line = line.strip()
            if not line:
                continue
            try:
                events.append(json.loads(line))
            except json.JSONDecodeError:
                continue

    screens = []
    screens_dir = run_dir / "screens"
    if screens_dir.exists():
        screens = sorted(f.name for f in screens_dir.iterdir() if f.suffix == ".png")

    return {
        "summary": summary,
        "events": events,
        "screens": screens,
    }


def _to_summary(run_id: str, raw: dict[str, Any], has_screenshots: bool) -> RunSummary:
    stats_raw = raw.get("stats", {})
    started_at = int(raw.get("started_at", 0))
    ended_at = int(raw.get("ended_at", 0))
    duration_ms = int(raw.get("duration_ms", 0))
    return RunSummary(
        run_id=raw.get("run_id", run_id),
        target_pkg=raw.get("target_pkg", ""),
        started_at=started_at,
        ended_at=ended_at,
        duration_ms=duration_ms,
        duration_sec=round(duration_ms / 1000.0, 1),
        device_model=raw.get("device", {}).get("model"),
        device_sdk=raw.get("device", {}).get("sdk"),
        stats=RunStats(**stats_raw),
        has_screenshots=has_screenshots,
    )


def _now_iso() -> str:
    return datetime.now(tz=timezone.utc).isoformat()
