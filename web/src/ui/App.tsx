import { HealthBadge } from "./HealthBadge";

export function App() {
  return (
    <div className="page">
      <header className="header">
        <div className="brand">
          <span className="brand-mark">EX</span>
          <span className="brand-name">UI Exhaustive Explorer</span>
        </div>
        <HealthBadge />
      </header>

      <main className="hero">
        <div className="hero-inner">
          <div className="phase-pill">Phase 0 · Bootstrap</div>
          <h1>android-ui-exhaustive-explorer</h1>
          <p className="lead">
            모바일 단말 preload 앱의 UI 표면을 자동으로 완전탐색하는 도구.
            Accessibility · Pixel Grid · CV + OCR · Differential Probe · VLM
            다섯 계층을 결합해 단일 기법으로는 닿지 못하는 사각지대까지 audit-가능한
            형태로 보고한다.
          </p>
          <p className="muted">
            실제 탐색 데이터는 Phase 1 부터 본 대시보드에 노출됩니다.
            현재 화면은 셋업 검증용입니다.
          </p>
        </div>
      </main>
    </div>
  );
}
