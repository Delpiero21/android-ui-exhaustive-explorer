import { useEffect, useState } from "react";
import { listRuns, getRun, type RunSummary, type RunDetail } from "../api/runs";

export function RunsPage() {
  const [runs, setRuns] = useState<RunSummary[]>([]);
  const [selected, setSelected] = useState<string | null>(null);
  const [detail, setDetail] = useState<RunDetail | null>(null);
  const [error, setError] = useState<string | null>(null);

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
        단말에서 회수된 자율 탐색 run 결과.{" "}
        <code>scripts/pull_runs.ps1</code> 실행 후 새로고침.
      </p>

      {runs.length === 0 && (
        <div className="empty-card">
          <strong>아직 회수된 run 이 없습니다.</strong>
          <p className="small muted">
            단말에서 자율 탐색 실행 → PC 에서 <code>.\scripts\pull_runs.ps1</code> 실행 → 본 페이지 새로고침.
          </p>
        </div>
      )}

      <div className="runs-layout">
        {/* 리스트 */}
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
              </div>
            </button>
          ))}
        </div>

        {/* 상세 */}
        <div className="run-detail">
          {!selected && (
            <div className="empty-card">왼쪽에서 run 을 선택하세요.</div>
          )}
          {selected && !detail && <div className="muted">로딩 중...</div>}
          {detail && <RunDetailView detail={detail} />}
        </div>
      </div>
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

function RunDetailView({ detail }: { detail: RunDetail }) {
  const summary = detail.summary as Record<string, unknown>;
  const stats = (summary["stats"] || {}) as Record<string, number>;
  const events = detail.events;

  // 이벤트 종류별 카운트
  const typeCount: Record<string, number> = {};
  for (const e of events) typeCount[e.type] = (typeCount[e.type] || 0) + 1;

  // 발견 화면 시계열 (new_screen 이벤트 timestamps)
  const newScreens = events.filter((e) => e.type === "new_screen");

  return (
    <div className="detail-content">
      <h3>{(summary["run_id"] as string) || "?"}</h3>
      <div className="detail-grid">
        <DetailField label="Target" value={(summary["target_pkg"] as string) || "(any)"} />
        <DetailField label="Duration" value={`${((summary["duration_ms"] as number) / 1000).toFixed(1)}s`} />
        <DetailField label="Device" value={`${(summary["device"] as { model?: string })?.model || "?"} (SDK ${(summary["device"] as { sdk?: number })?.sdk || "?"})`} />
      </div>

      <h4>통계</h4>
      <div className="stat-grid">
        <Stat label="발견 화면" value={stats.new_screens || 0} />
        <Stat label="시도 액션" value={stats.actions_executed || 0} />
        <Stat label="엣지" value={stats.edges_recorded || 0} />
        <Stat label="HOT" value={stats.hot_edges || 0} />
        <Stat label="COLD" value={stats.cold_edges || 0} />
        <Stat label="HOT %" value={`${(stats.hot_pct || 0).toFixed(1)}%`} />
        <Stat label="차단된 액션" value={stats.guard_blocks || 0} />
        <Stat label="대피" value={stats.evacuates || 0} />
        <Stat label="dialog 처리" value={stats.dialogs_dismissed || 0} />
        <Stat label="액션/초" value={(stats.actions_per_sec || 0).toFixed(2)} />
      </div>

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

      <h4>발견 화면 ({newScreens.length})</h4>
      <ul className="screens-list">
        {newScreens.slice(0, 30).map((e, i) => (
          <li key={i} className="small">
            <span className="muted">+{(((e.ts as number) - (summary["started_at"] as number)) / 1000).toFixed(1)}s</span>
            {" — "}
            <code>{(e["fp_strict"] as string).slice(0, 8)}</code>
            {" "}
            <span className="muted">{e["pkg"] as string}</span>
            {" "}
            <span className="muted">candidates={e["candidate_count"] as number}</span>
          </li>
        ))}
        {newScreens.length > 30 && (
          <li className="muted small">... {newScreens.length - 30} 개 더</li>
        )}
      </ul>
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
