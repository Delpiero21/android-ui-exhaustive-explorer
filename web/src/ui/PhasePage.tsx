import { useEffect, useState } from "react";
import { fetchPhaseLog, type PhaseLog, type PhaseEntry } from "../api/phase";

export function PhasePage() {
  const [data, setData] = useState<PhaseLog | null>(null);
  const [error, setError] = useState<string | null>(null);

  useEffect(() => {
    fetchPhaseLog()
      .then(setData)
      .catch((e) => setError(String(e)));
  }, []);

  if (error) return <div className="page-error">에러: {error}</div>;
  if (!data) return <div className="muted">로딩 중...</div>;

  return (
    <div className="phase-page">
      <h2 className="page-title">Phase Progress</h2>
      <p className="muted small">
        본 페이지는 <code>data/phase_log.json</code> (= <code>docs/PHASE_LOG.md</code> mirror) 의
        실시간 렌더. 마지막 갱신: {data.last_updated}
      </p>

      {/* 종합 비교 표 */}
      <h3>한눈에 진화 보기</h3>
      <div className="comparison-table-wrap">
        <table className="comparison-table">
          <thead>
            <tr>
              <th>지표</th>
              <th>P0</th>
              <th>P1 목표</th>
              <th>P1 실측</th>
              <th>P2 목표</th>
              <th>P3 목표</th>
              <th>천장</th>
            </tr>
          </thead>
          <tbody>
            {data.comparison_table.indicators.map((i, idx) => (
              <tr key={idx}>
                <td className="metric-name">{i.metric}</td>
                <td>{i.p0 ?? "—"}</td>
                <td>{i.p1_target ?? "—"}</td>
                <td className={i.p1_actual ? "actual" : "tbd"}>
                  {i.p1_actual ?? <span className="muted">TBD</span>}
                </td>
                <td>{i.p2_target ?? "—"}</td>
                <td>{i.p3_target ?? "—"}</td>
                <td className="muted">{i.ceiling ?? "—"}</td>
              </tr>
            ))}
          </tbody>
        </table>
      </div>

      {/* 각 Phase 카드 */}
      <h3>Phase 별 상세</h3>
      <div className="phase-cards">
        {data.phases.map((p) => (
          <PhaseCard key={p.id} phase={p} />
        ))}
      </div>
    </div>
  );
}

function PhaseCard({ phase }: { phase: PhaseEntry }) {
  const statusColor =
    phase.status === "completed"
      ? "status-completed"
      : phase.status === "in_progress"
        ? "status-progress"
        : "status-planned";

  return (
    <div className={`phase-card ${statusColor}`}>
      <div className="phase-card-head">
        <span className="phase-num">Phase {phase.id}</span>
        <span className="phase-name">{phase.name}</span>
        <span className={`phase-badge ${statusColor}`}>
          {phase.status === "completed" ? "✅ 완료" : phase.status === "in_progress" ? "● 진행 중" : "⏳ 계획"}
        </span>
      </div>

      {phase.io && (
        <div className="io-block">
          <strong>I/O</strong>
          <div className="io-row">
            <span className="io-label">In</span>
            <span>{phase.io.input}</span>
          </div>
          <div className="io-row">
            <span className="io-label">Proc</span>
            <span>{phase.io.processing}</span>
          </div>
          <div className="io-row">
            <span className="io-label">Out</span>
            <span>{phase.io.output}</span>
          </div>
        </div>
      )}

      {phase.coverage && Object.keys(phase.coverage).length > 0 && (
        <div className="coverage-block">
          <strong>Coverage</strong>
          <ul className="small">
            {Object.entries(phase.coverage)
              .filter(([k, v]) => !k.startsWith("_") && v !== undefined)
              .map(([k, v]) => (
                <li key={k}>
                  <code>{k}</code>:{" "}
                  {v === null ? (
                    <span className="muted">TBD</span>
                  ) : Array.isArray(v) ? (
                    <code>{JSON.stringify(v)}</code>
                  ) : (
                    <code>{String(v)}</code>
                  )}
                </li>
              ))}
          </ul>
        </div>
      )}

      {phase.limitations && phase.limitations.length > 0 && (
        <div className="limit-block">
          <strong>실패한 부분 / 못 한 것</strong>
          <ul className="small">
            {phase.limitations.map((l, i) => (
              <li key={i}>{l}</li>
            ))}
          </ul>
        </div>
      )}

      {phase.tech_stack && phase.tech_stack.length > 0 && (
        <div className="tech-block">
          <strong>기술 스택</strong>
          <div className="tech-tags">
            {phase.tech_stack.map((t, i) => (
              <span key={i} className="tech-tag">
                {t}
              </span>
            ))}
          </div>
        </div>
      )}

      {phase.tech_stack_candidates && phase.tech_stack_candidates.length > 0 && (
        <div className="tech-block">
          <strong>기술 스택 (후보)</strong>
          <div className="tech-tags">
            {phase.tech_stack_candidates.map((t, i) => (
              <span key={i} className="tech-tag candidate">
                {t}
              </span>
            ))}
          </div>
        </div>
      )}

      {phase.resolved_cases && phase.resolved_cases.length > 0 && (
        <div className="cases-block">
          <strong>해결된 사각지대</strong>
          <ul className="small">
            {phase.resolved_cases.map((c, i) => (
              <li key={i}>
                <strong>Case {c.case}</strong> {c.title}{" "}
                <span className="muted">← {c.via}</span>
              </li>
            ))}
          </ul>
        </div>
      )}
    </div>
  );
}
