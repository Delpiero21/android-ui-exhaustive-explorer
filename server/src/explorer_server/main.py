"""FastAPI 진입점.

실행:
    uvicorn explorer_server.main:app --reload --port 8000

또는 pyproject.toml 의 console script:
    explorer-server
"""

from __future__ import annotations

import logging
import os

from fastapi import FastAPI
from fastapi.middleware.cors import CORSMiddleware

from explorer_server import __version__
from explorer_server.routers import coverage, health, runs

logger = logging.getLogger("explorer_server")
logging.basicConfig(level=logging.INFO, format="%(asctime)s %(levelname)s %(name)s %(message)s")


def create_app() -> FastAPI:
    """Build the FastAPI application.

    CORS:
      Default = loopback only (127.0.0.1 / localhost).
      LAN mode 가 필요하면 EXPLORER_ALLOW_LAN=1 + EXPLORER_LAN_HOST 환경 변수 설정.
    """
    app = FastAPI(
        title="android-ui-exhaustive-explorer · server",
        version=__version__,
        description=(
            "Phase 0 — Bootstrap. /health 외에는 stub. "
            "실제 라우터는 Phase 1 부터 채워짐."
        ),
    )

    # ----- CORS -----
    allowed_origins: list[str] = [
        "http://localhost:5173",
        "http://127.0.0.1:5173",
    ]
    if os.environ.get("EXPLORER_ALLOW_LAN") == "1":
        lan_host = os.environ.get("EXPLORER_LAN_HOST")
        if lan_host:
            allowed_origins.append(f"http://{lan_host}:5173")
            logger.info("LAN mode enabled — allowed origin: http://%s:5173", lan_host)

    app.add_middleware(
        CORSMiddleware,
        allow_origins=allowed_origins,
        allow_credentials=True,
        allow_methods=["GET", "POST", "PUT", "DELETE"],
        allow_headers=["*"],
    )

    # ----- Routers -----
    app.include_router(health.router)
    app.include_router(runs.router)
    app.include_router(coverage.router)

    @app.get("/")
    async def root() -> dict[str, str]:
        return {
            "name": "android-ui-exhaustive-explorer · server",
            "version": __version__,
            "phase": "0-bootstrap",
        }

    logger.info("Server initialized. version=%s", __version__)
    return app


app = create_app()


def run() -> None:
    """`explorer-server` console script."""
    import uvicorn

    host = os.environ.get("EXPLORER_HOST", "127.0.0.1")
    port = int(os.environ.get("EXPLORER_PORT", "8000"))
    reload = os.environ.get("EXPLORER_RELOAD", "0") == "1"
    uvicorn.run("explorer_server.main:app", host=host, port=port, reload=reload)


if __name__ == "__main__":
    run()
