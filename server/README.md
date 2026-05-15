# server/ — FastAPI 백엔드

Phase 0 (Bootstrap) — `/api/health` 만 동작하는 빈 서버.
실제 데이터 수집/조회 라우터는 Phase 1 부터 채워진다.

---

## 빠른 시작

### 1. 가상 환경 + 의존성

```powershell
cd C:\GitHub\Delpiero21\android-ui-exhaustive-explorer\server
python -m venv .venv
.\.venv\Scripts\Activate.ps1
pip install -e ".[dev]"
```

### 2. 서버 기동

```powershell
explorer-server
# 또는 reload 모드
$env:EXPLORER_RELOAD = "1"; explorer-server
```

기본: `http://127.0.0.1:8000`

### 3. 헬스 체크

```powershell
curl http://127.0.0.1:8000/api/health
# → {"status":"ok","version":"0.1.0","phase":"0-bootstrap"}
```

### 4. 테스트

```powershell
pytest
```

---

## 환경 변수

| 변수 | 기본값 | 설명 |
|---|---|---|
| `EXPLORER_HOST` | `127.0.0.1` | bind host |
| `EXPLORER_PORT` | `8000` | bind port |
| `EXPLORER_RELOAD` | `0` | `1` 이면 hot reload |
| `EXPLORER_ALLOW_LAN` | (off) | `1` 이면 LAN 모드 CORS 허용 |
| `EXPLORER_LAN_HOST` | (off) | LAN 모드에서 허용할 호스트 (예: `10.10.5.20`) |
| `EXPLORER_OFFLINE` | (off) | `1` 이면 외부 API (Ollama 등) 호출 일체 차단 |
| `EXPLORER_OLLAMA_HOSTS` | (off) | 사내 Ollama 화이트리스트 |

---

## 디렉토리 구조

```
server/
├── pyproject.toml                    # Hatchling build, deps, console script
├── README.md                         # 본 문서
├── src/explorer_server/
│   ├── __init__.py
│   ├── main.py                       # FastAPI 진입점 + CORS + 라우터 결합
│   ├── routers/
│   │   ├── health.py                 # ✅ 동작
│   │   ├── runs.py                   # 🔜 Phase 1
│   │   └── coverage.py               # 🔜 Phase 2
│   ├── services/                     # 🔜 Phase 1 — 비즈니스 로직
│   ├── storage/                      # 🔜 Phase 1 — 파일시스템 저장소
│   └── vlm/                          # 🔜 Phase 2 — Ollama proxy
└── tests/
    └── test_health.py
```

---

## API (현재 / 예정)

| 메서드 | 경로 | 상태 | 설명 |
|---|---|---|---|
| GET | `/` | ✅ | 서버 메타 |
| GET | `/api/health` | ✅ | 헬스 체크 |
| GET | `/api/runs` | 🔶 stub | 탐색 실행 결과 목록 |
| GET | `/api/coverage` | 🔶 stub | Tier 별 hit 기여도 |
| POST | `/api/runs/{run_id}/events` | 🔜 Phase 1 | 단말에서 회수한 events.jsonl 업로드 |
| POST | `/api/vlm/propose` | 🔜 Phase 2 | VLM 후보 추론 (사내 Ollama proxy) |

---

## 보안 경계

- 기본 bind: `127.0.0.1` (loopback only)
- CORS 기본 허용: `localhost:5173` / `127.0.0.1:5173`
- LAN 모드는 `EXPLORER_ALLOW_LAN=1` + `EXPLORER_LAN_HOST` 명시한 호스트만 추가 허용
- Ollama 호출은 `EXPLORER_OLLAMA_HOSTS` 화이트리스트만, `EXPLORER_OFFLINE=1` 이면 일체 차단

---

## 다음 단계 (Phase 1)

1. `services/run_ingest.py` — 단말 events.jsonl 파싱
2. `storage/file_store.py` — `data/{run_id}/` 디렉토리 관리
3. `routers/runs.py` 의 stub 을 실제 storage 호출로 대체
