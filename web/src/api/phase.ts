/** /api/phase + /api/phase/current. */

export interface PhaseCoverage {
  activity_coverage_pct?: number | null;
  activity_coverage_target_min?: number;
  activity_coverage_target_max?: number;
  screens_found?: number | null;
  screens_target_min?: number;
  external_egress?: number;
  multi_window_resolved_cases?: string[];
  [k: string]: unknown;
}

export interface PhaseEntry {
  id: number;
  name: string;
  status: "completed" | "in_progress" | "planned";
  started_at?: string;
  ended_at?: string;
  commits?: number;
  io?: { input: string; processing: string; output: string };
  coverage?: PhaseCoverage;
  limitations?: string[];
  tech_stack?: string[];
  tech_stack_candidates?: string[];
  modules_built?: string[];
  resolved_cases?: Array<{ case: number; title: string; via: string }>;
  resolved_cases_planned?: Array<{ case: number; title: string; via: string }>;
}

export interface ComparisonIndicator {
  metric: string;
  p0?: string;
  p1_target?: string;
  p1_actual?: string | null;
  p2_target?: string;
  p3_target?: string;
  ceiling?: string;
}

export interface PhaseLog {
  schema_version: number;
  last_updated: string;
  phases: PhaseEntry[];
  comparison_table: { indicators: ComparisonIndicator[] };
}

export async function fetchPhaseLog(): Promise<PhaseLog> {
  const res = await fetch("/api/phase");
  if (!res.ok) throw new Error(`fetchPhaseLog failed: ${res.status}`);
  return (await res.json()) as PhaseLog;
}
