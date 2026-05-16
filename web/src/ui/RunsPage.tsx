import { useEffect, useMemo, useState } from "react";
import { listRuns, getRun, type RunSummary, type RunDetail, type RunEvent } from "../api/runs";

export function RunsPage() {
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);
  const [zoomFp, setZoomFp] = useState<string | null>(null);

  useEffect(() => {
    listRuns()
      .then(setRuns)
      .catch((e) => setError(String(e)));
  }, []);

  useEffect(() => {
    if (!selected) {
      setDetail(null);
      return;
    }
    setDetail(null);
    getRun(selected)
      .then(setDetail)
      .catch((e) => setError(String(e)));
  }, [selected]);

  if (error) return <div className="page-error">에러: {error}</div>;

  return (
    <div className="runs-page">
      <h2 className="page-title">Runs</h2>
      <p className="muted small">
        단말에서 회수된 자율 탐색 run 결과. <code>scripts/pull_runs.ps1</code> 실행 후 새로고침.
      </p>

      {runs.length === 0 && (
        <div className="empty-card">
          <strong>아직 회수된 run 이 없습니다.</strong>
          <p className="small muted">
            단말에서 자율 탐색 → PC 에서 <code>.\scripts\pull_runs.ps1</code> → 새로고침.
          </p>
        </div>
      )}

      <div className="runs-layout">
        <div className="runs-list">
          {runs.map((r) => (
            <button
              key={r.run_id}
              className={`run-card ${selected === r.run_id ? "selected" : ""}`}
              onClick={() => setSelected(r.run_id)}
            >
              <div className="run-card-head">
                <span className="run-id">{r.run_id}</span>
                <span className="run-duration">{r.duration_sec}s</span>
              </div>
              <div className="run-pkg small muted">{r.target_pkg || "(any)"}</div>
              <div className="run-stats-row">
                <Stat label="화면" value={r.stats.new_screens} />
                <Stat label="액션" value={r.stats.actions_executed} />
                <Stat label="HOT" value={`${r.stats.hot_pct.toFixed(0)}%`} />
                <Stat label="차단" value={r.stats.guard_blocks} />
                {r.has_screenshots && <Stat label="📸" value="✓" />}
              </div>
            </button>
          ))}
        </div>

        <div className="run-detail">
          {!selected && (
            <div className="empty-card">왼쪽에서 run 을 선택하세요.</div>
          )}
          {selected && !detail && <div className="muted">로딩 중...</div>}
          {detail && (
            <RunDetailView
              runId={selected!}
              detail={detail}
              onZoomScreen={(fp) => setZoomFp(fp)}
            />
          )}
        </div>
      </div>

      {zoomFp && selected && (
        <ScreenZoomOverlay
          runId={selected}
          fp={zoomFp}
          onClose={() => setZoomFp(null)}
        />
      )}
    </div>
  );
}

function Stat({ label, value }: { label: string; value: number | string }) {
  return (
    <div className="stat-cell">
      <span className="stat-value">{value}</span>
      <span className="stat-label">{label}</span>
    </div>
  );
}

function RunDetailView({
  runId,
  detail,
  onZoomScreen,
}: {
  runId: string;
  detail: RunDetail;
  onZoomScreen: (fp: string) => void;
}) {
  const summary = detail.summary as Record<string, unknown>;
  const stats = (summary["stats"] || {}) as Record<string, number>;
  const events = detail.events;
  const screensSet = new Set(detail.screens);

  const typeCount: Record<string, number> = {};
  for (const e of events) typeCount[e.type] = (typeCount[e.type] || 0) + 1;

  const newScreens = events.filter((e) => e.type === "new_screen");
  const edges = events.filter((e) => e.type === "edge" && e["changed"] === true);

  // state graph nodes: fp → first new_screen event
  const graphNodes = useMemo(() => {
    return newScreens.map((e, i) => ({
      fp: e["fp_strict"] as string,
      shortFp: (e["fp_strict"] as string).slice(0, 8),
      pkg: e["pkg"] as string,
      candidates: e["candidate_count"] as number,
      offset: ((e.ts as number) - (summary["started_at"] as number)) / 1000,
      index: i,
      hasScreen: screensSet.has(`${(e["fp_strict"] as string)}.png`),
    }));
  }, [newScreens, summary, screensSet]);

  return (
    <div className="detail-content">
      <h3>{(summary["run_id"] as string) || "?"}</h3>
      <div className="detail-grid">
        <DetailField label="Target" value={(summary["target_pkg"] as string) || "(any)"} />
        <DetailField label="Duration" value={`${((summary["duration_ms"] as number) / 1000).toFixed(1)}s`} />
        <DetailField
          label="Device"
          value={`${(summary["device"] as { model?: string })?.model || "?"} (SDK ${(summary["device"] as { sdk?: number })?.sdk || "?"})`}
        />
      </div>

      <h4>통계</h4>
      <div className="stat-grid">
        <Stat label="발견 화면" value={stats.new_screens || 0} />
        <Stat label="시도 액션" value={stats.actions_executed || 0} />
        <Stat label="엣지" value={stats.edges_recorded || 0} />
        <Stat label="HOT" value={stats.hot_edges || 0} />
        <Stat label="COLD" value={stats.cold_edges || 0} />
        <Stat label="HOT %" value={`${(stats.hot_pct || 0).toFixed(1)}%`} />
        <Stat label="차단" value={stats.guard_blocks || 0} />
        <Stat label="대피" value={stats.evacuates || 0} />
        <Stat label="dialog 처리" value={stats.dialogs_dismissed || 0} />
        <Stat label="액션/초" value={(stats.actions_per_sec || 0).toFixed(2)} />
      </div>

      <h4>발견 화면 갤러리 ({graphNodes.length})</h4>
      <div className="screens-gallery">
        {graphNodes.map((n) => (
          <button
            key={n.fp}
            className="screen-thumb"
            onClick={() => n.hasScreen && onZoomScreen(n.fp)}
            disabled={!n.hasScreen}
            title={n.hasScreen ? "클릭으로 확대" : "스크린샷 없음"}
          >
            <div className="screen-thumb-img-wrap">
              {n.hasScreen ? (
                <img
                  src={`/api/runs/${encodeURIComponent(runId)}/screens/${n.fp}.png`}
                  alt={n.shortFp}
                  loading="lazy"
                />
              ) : (
                <div className="thumb-placeholder">no img</div>
              )}
              <span className="thumb-index">{n.index + 1}</span>
            </div>
            <div className="thumb-meta">
              <code>{n.shortFp}</code>
              <span className="muted small">+{n.offset.toFixed(1)}s · {n.candidates}c</span>
            </div>
          </button>
        ))}
      </div>

      <h4>State Graph ({graphNodes.length} 노드 · {edges.length} HOT 엣지)</h4>
      <StateGraphView nodes={graphNodes} edges={edges} runId={runId} onClick={onZoomScreen} />

      <h4>이벤트 분포</h4>
      <div className="bar-list">
        {Object.entries(typeCount)
          .sort((a, b) => b[1] - a[1])
          .map(([type, count]) => (
            <div key={type} className="bar-row">
              <span className="bar-label">{type}</span>
              <div className="bar-track">
                <div
                  className="bar-fill"
                  style={{ width: `${Math.min(100, (count / events.length) * 100)}%` }}
                />
              </div>
              <span className="bar-count">{count}</span>
            </div>
          ))}
      </div>

      <h4>발견 화면 시계열</h4>
      <ul className="screens-list">
        {graphNodes.slice(0, 30).map((n) => (
          <li key={n.fp} className="small">
            <span className="muted">+{n.offset.toFixed(1)}s</span>
            {" — "}
            <code>{n.shortFp}</code>
            {" "}
            <span className="muted">{n.pkg}</span>
            {" "}
            <span className="muted">candidates={n.candidates}</span>
          </li>
        ))}
        {graphNodes.length > 30 && (
          <li className="muted small">... {graphNodes.length - 30} 개 더</li>
        )}
      </ul>
    </div>
  );
}

/**
 * 발견된 화면 + HOT 전이 엣지의 Figma 풍 시각화.
 * 좌→우 시간순으로 노드 배치, 엣지는 베지어 곡선.
 */
function StateGraphView({
  nodes,
  edges,
  runId,
  onClick,
}: {
  nodes: { fp: string; shortFp: string; offset: number; candidates: number; hasScreen: boolean; index: number }[];
  edges: RunEvent[];
  runId: string;
  onClick: (fp: string) => void;
}) {
  // 4-column grid layout. fp → (col, row).
  const COLS = 4;
  const NODE_W = 130;
  const NODE_H = 200;
  const GAP_X = 40;
  const GAP_Y = 60;
  const PAD = 20;

  const positions = useMemo(() => {
    const m = new Map<string, { x: number; y: number; row: number; col: number }>();
    nodes.forEach((n, i) => {
      const row = Math.floor(i / COLS);
      const col = i % COLS;
      m.set(n.fp, {
        x: PAD + col * (NODE_W + GAP_X),
        y: PAD + row * (NODE_H + GAP_Y),
        row,
        col,
      });
    });
    return m;
  }, [nodes]);

  const rows = Math.ceil(nodes.length / COLS);
  const width = COLS * NODE_W + (COLS - 1) * GAP_X + PAD * 2;
  const height = Math.max(1, rows) * NODE_H + Math.max(0, rows - 1) * GAP_Y + PAD * 2;

  if (nodes.length === 0) {
    return <div className="empty-card small">노드 없음</div>;
  }

  return (
    <div className="state-graph-wrap">
      <svg
        viewBox={`0 0 ${width} ${height}`}
        className="state-graph-svg"
        style={{ width: "100%", maxHeight: 720 }}
      >
        {/* edges */}
        <g className="edges">
          {edges.map((e, i) => {
            const from = positions.get(e["from_fp"] as string);
            const to = positions.get(e["to_fp"] as string);
            if (!from || !to) return null;
            const x1 = from.x + NODE_W / 2;
            const y1 = from.y + NODE_H;
            const x2 = to.x + NODE_W / 2;
            const y2 = to.y;
            const mx = (x1 + x2) / 2;
            const my = (y1 + y2) / 2;
            return (
              <g key={i}>
                <path
                  d={`M ${x1},${y1} C ${x1},${my} ${x2},${my} ${x2},${y2}`}
                  fill="none"
                  stroke="#ec7211"
                  strokeWidth={1.5}
                  strokeOpacity={0.5}
                  markerEnd="url(#arrow)"
                />
              </g>
            );
          })}
          <defs>
            <marker
              id="arrow"
              viewBox="0 0 10 10"
              refX="9"
              refY="5"
              markerWidth="6"
              markerHeight="6"
              orient="auto-start-reverse"
            >
              <path d="M 0 0 L 10 5 L 0 10 z" fill="#ec7211" />
            </marker>
          </defs>
        </g>

        {/* nodes */}
        <g className="nodes">
          {nodes.map((n) => {
            const p = positions.get(n.fp)!;
            return (
              <g
                key={n.fp}
                className="graph-node"
                transform={`translate(${p.x}, ${p.y})`}
                style={{ cursor: n.hasScreen ? "pointer" : "default" }}
                onClick={() => n.hasScreen && onClick(n.fp)}
              >
                <rect
                  width={NODE_W}
                  height={NODE_H}
                  rx={6}
                  fill="#fff"
                  stroke="#d5dbdb"
                  strokeWidth={1}
                />
                {n.hasScreen ? (
                  <image
                    href={`/api/runs/${encodeURIComponent(runId)}/screens/${n.fp}.png`}
                    x={4}
                    y={4}
                    width={NODE_W - 8}
                    height={NODE_H - 40}
                    preserveAspectRatio="xMidYMid meet"
                  />
                ) : (
                  <rect
                    x={4}
                    y={4}
                    width={NODE_W - 8}
                    height={NODE_H - 40}
                    fill="#f2f3f3"
                  />
                )}
                {/* label area */}
                <text
                  x={NODE_W / 2}
                  y={NODE_H - 18}
                  textAnchor="middle"
                  fontSize={11}
                  fontWeight={700}
                  fill="#16191f"
                  fontFamily="Consolas, monospace"
                >
                  #{n.index + 1} {n.shortFp}
                </text>
                <text
                  x={NODE_W / 2}
                  y={NODE_H - 6}
                  textAnchor="middle"
                  fontSize={9}
                  fill="#879596"
                >
                  +{n.offset.toFixed(1)}s · {n.candidates}c
                </text>
              </g>
            );
          })}
        </g>
      </svg>
    </div>
  );
}

function ScreenZoomOverlay({
  runId,
  fp,
  onClose,
}: {
  runId: string;
  fp: string;
  onClose: () => void;
}) {
  return (
    <div className="zoom-overlay" onClick={onClose}>
      <div className="zoom-content" onClick={(e) => e.stopPropagation()}>
        <button className="zoom-close" onClick={onClose}>
          ✕
        </button>
        <img
          src={`/api/runs/${encodeURIComponent(runId)}/screens/${fp}.png`}
          alt={fp}
          className="zoom-img"
        />
        <div className="zoom-caption">
          <code>{fp}</code>
        </div>
      </div>
    </div>
  );
}

function DetailField({ label, value }: { label: string; value: string }) {
  return (
    <div className="detail-field">
      <span className="detail-label">{label}</span>
      <span className="detail-value">{value}</span>
    </div>
  );
}
