"""GET /api/runs — 실제 회수된 run 데이터 노출.

Phase 1 (Dashboard batch) 구현:
- 단말의 RunRecorder 가 events.jsonl + summary.json 을 /sdcard/.../runs/{id}/ 에 저장
- scripts/pull_runs.ps1 가 server/data/runs/{id}/ 로 회수
- 본 라우터가 그 디렉토리를 스캔해서 노출
"""

from __future__ import annotations

import io
import json
import shutil
import zipfile
from datetime import datetime, timezone
from pathlib import Path
from typing import Any

from fastapi import APIRouter, HTTPException, Request
from fastapi.responses import FileResponse
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


@router.post("/runs/upload")
async def upload_run(request: Request, run_id: str) -> dict[str, Any]:
    """단말의 RunRecorder 가 zip 으로 묶어 보낸 run 데이터를 받아 풀어 저장.

    단말 RunUploader 가 호출:
      POST /api/runs/upload?run_id=XXX
      Content-Type: application/zip
      Body: zip stream containing events.jsonl, summary.json, screens/*.png

    zip 안에는 top-level dir ({run_id}/) 가 있고 그 아래 파일들이 있음.
    server/data/runs/{run_id}/ 로 풀어서 저장.
    """
    # run_id sanity check
    if not run_id or "/" in run_id or "\\" in run_id or ".." in run_id:
        raise HTTPException(400, detail="invalid run_id")

    body = await request.body()
    if not body:
        raise HTTPException(400, detail="empty body")

    target_dir = _runs_dir() / run_id
    target_dir.mkdir(parents=True, exist_ok=True)

    extracted = 0
    try:
        with zipfile.ZipFile(io.BytesIO(body), "r") as zf:
            for member in zf.namelist():
                if member.endswith("/"):
                    continue
                # zip 안 경로의 첫 segment (top-level dir) 제거
                parts = member.split("/", 1)
                inner = parts[1] if len(parts) == 2 else parts[0]
                if not inner:
                    continue
                # path traversal 방지
                if ".." in inner.split("/"):
                    continue
                dest = target_dir / inner
                dest.parent.mkdir(parents=True, exist_ok=True)
                with zf.open(member) as src, open(dest, "wb") as out:
                    shutil.copyfileobj(src, out)
                extracted += 1
    except zipfile.BadZipFile as e:
        # 부분 풀린 결과 정리
        raise HTTPException(400, detail=f"bad zip: {e}") from e

    return {
        "ok": True,
        "run_id": run_id,
        "extracted_files": extracted,
        "target_dir": str(target_dir),
    }


@router.get("/runs/{run_id}/screens/{filename}")
async def get_screen_image(run_id: str, filename: str) -> FileResponse:
    """단일 화면 스크린샷 PNG. filename = fp_strict + .png."""
    # path traversal 방지
    if "/" in filename or "\\" in filename or ".." in filename:
        raise HTTPException(400, detail="invalid filename")
    if not filename.endswith(".png"):
        raise HTTPException(400, detail="png only")
    path = _runs_dir() / run_id / "screens" / filename
    if not path.is_file():
        raise HTTPException(404, detail=f"screen not found: {run_id}/{filename}")
    return FileResponse(
        path,
        media_type="image/png",
        headers={"Cache-Control": "public, max-age=3600"},
    )


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
