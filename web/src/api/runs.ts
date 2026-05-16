/** /api/runs + /api/runs/{id}. */

export interface RunStats {
  new_screens: number;
  actions_executed: number;
  edges_recorded: number;
  hot_edges: number;
  cold_edges: number;
  hot_pct: number;
  guard_blocks: number;
  evacuates: number;
  dialogs_dismissed: number;
  actions_per_sec: number;
}

export interface RunSummary {
  run_id: string;
  target_pkg: string;
  started_at: number;
  ended_at: number;
  duration_ms: number;
  duration_sec: number;
  device_model: string | null;
  device_sdk: number | null;
  stats: RunStats;
  has_screenshots: boolean;
}

export interface RunEvent {
  ts: number;
  type: string;
  [k: string]: unknown;
}

export interface RunDetail {
  summary: Record<string, unknown>;
  events: RunEvent[];
  screens: string[];
}

export async function listRuns(): Promise<RunSummary[]> {
  const res = await fetch("/api/runs");
  if (!res.ok) throw new Error(`listRuns failed: ${res.status}`);
  return (await res.json()) as RunSummary[];
}

export async function getRun(runId: string): Promise<RunDetail> {
  const res = await fetch(`/api/runs/${encodeURIComponent(runId)}`);
  if (!res.ok) throw new Error(`getRun(${runId}) failed: ${res.status}`);
  return (await res.json()) as RunDetail;
}
