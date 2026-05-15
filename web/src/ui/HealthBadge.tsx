import { useEffect, useState } from "react";
import { fetchHealth, type HealthResponse } from "../api/health";

type Status = "loading" | "ok" | "down";

export function HealthBadge() {
  const [status, setStatus] = useState<Status>("loading");
  const [info, setInfo] = useState<HealthResponse | null>(null);

  useEffect(() => {
    let cancelled = false;
    fetchHealth()
      .then((res) => {
        if (cancelled) return;
        setStatus("ok");
        setInfo(res);
      })
      .catch(() => {
        if (cancelled) return;
        setStatus("down");
      });
    return () => {
      cancelled = true;
    };
  }, []);

  const dotClass = status === "ok" ? "dot dot-ok" : status === "down" ? "dot dot-down" : "dot";
  const label =
    status === "ok"
      ? `server ${info?.version ?? ""} · ${info?.phase ?? ""}`
      : status === "down"
        ? "server down"
        : "checking…";

  return (
    <div className="health-badge" title="GET /api/health">
      <span className={dotClass} />
      <span className="health-label">{label}</span>
    </div>
  );
}
