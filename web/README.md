# web/ — React + Vite + TypeScript 대시보드

Phase 0 (Bootstrap) — `/api/health` 만 호출하는 빈 페이지.
실제 데이터 시각화 (run map / coverage heatmap / roadmap) 는 Phase 1 부터 채워진다.

---

## 빠른 시작

### 1. 의존성

```powershell
cd C:\GitHub\Delpiero21\android-ui-exhaustive-explorer\web
npm install
```

### 2. 개발 모드

서버 (`server/`) 가 8000 포트에서 동작 중이어야 한다.

```powershell
npm run dev
# → http://localhost:5173
```

화면 상단에서 헬스 badge 확인:
- 🟢 `server 0.1.0 · 0-bootstrap` — 정상
- 🔴 `server down` — server 가 안 떴거나 다른 포트

### 3. 빌드

```powershell
npm run build
npm run preview        # 빌드 결과 미리보기
```

---

## 환경 변수

빌드/실행 시점에 vite.config.ts 가 읽음 (브라우저 코드에 노출 안 됨):

| 변수 | 기본값 | 설명 |
|---|---|---|
| `EXPLORER_API_TARGET` | `http://127.0.0.1:8000` | `/api/*` proxy 대상 |
| `EXPLORER_ALLOW_LAN` | (off) | `1` 이면 vite dev server 가 `0.0.0.0` 바인딩 |
| `EXPLORER_LAN_HOST` | (off) | LAN 모드 시 허용할 호스트 (예: `10.10.5.20`) |

---

## 디렉토리 구조

```
web/
├── package.json
├── vite.config.ts                     # /api/* → server proxy 설정
├── tsconfig.json + tsconfig.app.json + tsconfig.node.json
├── index.html                          # Pretendard CDN + root mount
├── public/                             # 정적 파일
└── src/
    ├── main.tsx                        # createRoot
    ├── vite-env.d.ts
    ├── ui/
    │   ├── App.tsx                     # ✅ 빈 헤더 + 히어로 + health badge
    │   └── HealthBadge.tsx             # ✅ /api/health 폴링
    ├── api/
    │   └── health.ts                   # fetch wrapper
    └── styles/
        └── global.css                  # AWS Console 스타일 베이스
```

---

## 다음 단계 (Phase 1)

1. `ui/pages/RunMap.tsx` — state graph 시각화 (탐색 경로 트리)
2. `ui/pages/Coverage.tsx` — Tier 별 hit 기여도 히트맵
3. `api/runs.ts` — `GET /api/runs` wrapper
4. `api/coverage.ts` — `GET /api/coverage` wrapper

스타일 가이드: `docs/report.html` 의 톤 (AWS Console + Pretendard) 유지.
