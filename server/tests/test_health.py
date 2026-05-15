"""GET /api/health smoke test."""

from __future__ import annotations

from fastapi.testclient import TestClient

from explorer_server.main import app


def test_health_returns_ok() -> None:
    client = TestClient(app)
    resp = client.get("/api/health")
    assert resp.status_code == 200
    body = resp.json()
    assert body["status"] == "ok"
    assert body["phase"] == "0-bootstrap"
    assert body["version"]


def test_root_endpoint() -> None:
    client = TestClient(app)
    resp = client.get("/")
    assert resp.status_code == 200
    body = resp.json()
    assert "android-ui-exhaustive-explorer" in body["name"]
