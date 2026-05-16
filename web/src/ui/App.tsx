import { useState } from "react";
import { HealthBadge } from "./HealthBadge";
import { PhasePage } from "./PhasePage";
import { RunsPage } from "./RunsPage";

type Tab = "home" | "runs" | "phase";

export function App() {
  const [tab, setTab] = useState<Tab>("home");

  return (
    <div className="page">
      <header className="header">
        <div className="brand">
          <span className="brand-mark">EX</span>
          <span className="brand-name">UI Exhaustive Explorer</span>
        </div>
        <nav className="nav-tabs">
          <NavButton label="Home" active={tab === "home"} onClick={() => setTab("home")} />
          <NavButton label="Runs" active={tab === "runs"} onClick={() => setTab("runs")} />
          <NavButton label="Phase" active={tab === "phase"} onClick={() => setTab("phase")} />
        </nav>
        <HealthBadge />
      </header>

      {tab === "home" && <HomeContent />}
      {tab === "runs" && (
        <main className="content">
          <RunsPage />
        </main>
      )}
      {tab === "phase" && (
        <main className="content">
          <PhasePage />
        </main>
      )}
    </div>
  );
}

function NavButton({
  label,
  active,
  onClick,
}: {
  label: string;
  active: boolean;
  onClick: () => void;
}) {
  return (
    <button className={`nav-tab ${active ? "active" : ""}`} onClick={onClick}>
      {label}
    </button>
  );
}

function HomeContent() {
  return (
    <main className="hero">
      <div className="hero-inner">
        <div className="phase-pill">Phase 1 · Autonomous DFS</div>
        <h1>android-ui-exhaustive-explorer</h1>
        <p className="lead">
          모바일 단말 preload 앱의 UI 표면을 자동으로 완전탐색하는 도구.
          Accessibility · Pixel Grid · CV + OCR · Differential Probe · VLM
          다섯 계층을 결합해 단일 기법으로는 닿지 못하는 사각지대까지 audit-가능한
          형태로 보고한다.
        </p>
        <p className="muted">
          <strong>Runs</strong> 탭 — 단말에서 회수된 자율 탐색 결과.
          <br />
          <strong>Phase</strong> 탭 — 본 도구가 지금 무엇이 되고 안 되는지 (PHASE_LOG mirror).
        </p>
      </div>
    </main>
  );
}
