package com.exhaustive.explorer.core

import android.content.Context
import android.graphics.Bitmap
import android.os.Build
import android.util.Log
import org.json.JSONArray
import org.json.JSONObject
import java.io.File
import java.io.FileOutputStream
import java.io.PrintWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import java.util.concurrent.atomic.AtomicInteger

/**
 * 한 번의 자율 탐색 run 의 모든 이벤트 + 요약 + (선택) 스크린샷을 저장.
 *
 * 저장 위치 (단말 내부 — backup 차단):
 * ```
 * /sdcard/Android/data/com.exhaustive.explorer.debug/files/runs/{runId}/
 *   ├ events.jsonl    한 줄당 한 이벤트 (실시간 append, run 중)
 *   ├ summary.json    run 종료 시 한 번 쓰기
 *   └ screens/        새 화면마다 .png (선택 — bitmap null 이면 skip)
 * ```
 *
 * 회수: adb pull 또는 server HTTP 송출 (Phase 2 예정).
 *
 * Thread safety:
 * - autonomous loop 가 single coroutine 에서 호출하므로 lock 없음
 * - PrintWriter 의 println 은 OS 가 atomic line write 보장
 *
 * 파일 포맷:
 * - events.jsonl 한 줄 예시:
 *   {"ts": 1747432800123, "type": "new_screen", "fp_strict": "a3f5...", ...}
 * - summary.json: 사람과 PC 측 dashboard 모두 읽기 쉬운 한 객체.
 */
class RunRecorder(
    context: Context,
    private val runId: String = makeRunId(),
) {
    private val baseDir: File = File(context.getExternalFilesDir(null), "runs/$runId").apply {
        mkdirs()
    }
    private val eventsFile: File = File(baseDir, "events.jsonl")
    private val summaryFile: File = File(baseDir, "summary.json")
    private val screensDir: File = File(baseDir, "screens").apply { mkdirs() }

    private val eventsWriter: PrintWriter = PrintWriter(eventsFile.outputStream().buffered(), true)

    // ───── counters (summary.json 채울 때 사용) ─────
    private val newScreenCount = AtomicInteger(0)
    private val actionCount = AtomicInteger(0)
    private val edgeCount = AtomicInteger(0)
    private val hotEdgeCount = AtomicInteger(0)
    private val coldEdgeCount = AtomicInteger(0)
    private val blockCount = AtomicInteger(0)
    private val dialogCount = AtomicInteger(0)
    private val evacuateCount = AtomicInteger(0)
    private var startedAt: Long = System.currentTimeMillis()
    private var endedAt: Long = 0L
    private var targetPackage: String? = null

    // ───── lifecycle ─────

    fun recordRunStart(target: String?, budgetMs: Long) {
        targetPackage = target
        startedAt = System.currentTimeMillis()
        write(
            JSONObject()
                .put("type", "run_start")
                .put("ts", startedAt)
                .put("run_id", runId)
                .put("target_pkg", target ?: "")
                .put("budget_ms", budgetMs)
                .put("device_sdk", Build.VERSION.SDK_INT)
                .put("device_model", Build.MODEL),
        )
    }

    fun recordRunEnd() {
        endedAt = System.currentTimeMillis()
        write(
            JSONObject()
                .put("type", "run_end")
                .put("ts", endedAt)
                .put("duration_ms", endedAt - startedAt),
        )
        writeSummary()
        runCatching { eventsWriter.flush(); eventsWriter.close() }
        Log.i(TAG, "run $runId end. summary at ${summaryFile.absolutePath}")
    }

    // ───── events ─────

    fun recordNewScreen(fp: ScreenFingerprint.Composite, screen: ScreenInfo) {
        newScreenCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "new_screen")
                .put("ts", screen.timestamp)
                .put("fp_strict", fp.strict)
                .put("fp_loose", fp.loose)
                .put("pkg", screen.foregroundPackage ?: "")
                .put("activity_hint", screen.foregroundActivity ?: "")
                .put("candidate_count", screen.candidates.size)
                .put("window_count", screen.windows.size)
                .put("ime_visible", screen.imeVisible)
                .put("windows", windowsToJson(screen.windows)),
        )
    }

    fun recordAction(candidate: Candidate, fromFp: String) {
        actionCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "action")
                .put("ts", System.currentTimeMillis())
                .put("from_fp", fromFp)
                .put("label", candidate.shortLabel())
                .put("resource_id", candidate.resourceId ?: "")
                .put("content_desc", candidate.contentDesc ?: "")
                .put("text", candidate.text ?: "")
                .put("x", candidate.centerX)
                .put("y", candidate.centerY)
                .put("class", candidate.className ?: "")
                .put("source", candidate.source.name)
                .put("action_type", candidate.actions.firstOrNull()?.name ?: "?"),
        )
    }

    fun recordEdge(fromFp: String, toFp: String, candidate: Candidate, changed: Boolean) {
        edgeCount.incrementAndGet()
        if (changed) hotEdgeCount.incrementAndGet() else coldEdgeCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "edge")
                .put("ts", System.currentTimeMillis())
                .put("from_fp", fromFp)
                .put("to_fp", toFp)
                .put("changed", changed)
                .put("action_label", candidate.shortLabel()),
        )
    }

    fun recordBlock(reason: String, label: String) {
        blockCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "guard_block")
                .put("ts", System.currentTimeMillis())
                .put("reason", reason)
                .put("label", label),
        )
    }

    fun recordEvacuate(reason: String) {
        evacuateCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "evacuate")
                .put("ts", System.currentTimeMillis())
                .put("reason", reason),
        )
    }

    fun recordDialogDismiss(buttonLabel: String, windowPkg: String?) {
        dialogCount.incrementAndGet()
        write(
            JSONObject()
                .put("type", "dialog_dismiss")
                .put("ts", System.currentTimeMillis())
                .put("button", buttonLabel)
                .put("window_pkg", windowPkg ?: ""),
        )
    }

    /** 새 화면 스크린샷 저장 (선택). bitmap null 이면 skip. */
    fun saveScreenshot(fpStrict: String, bitmap: Bitmap?) {
        if (bitmap == null) return
        val file = File(screensDir, "$fpStrict.png")
        runCatching {
            FileOutputStream(file).use { out ->
                bitmap.compress(Bitmap.CompressFormat.PNG, 85, out)
            }
        }.onFailure { Log.w(TAG, "screenshot save failed", it) }
    }

    // ───── helpers ─────

    private fun write(json: JSONObject) {
        eventsWriter.println(json.toString())
    }

    private fun writeSummary() {
        val durationMs = (endedAt - startedAt).coerceAtLeast(1L)
        val hotPct = if (edgeCount.get() > 0) {
            (hotEdgeCount.get() * 100.0 / edgeCount.get())
        } else 0.0

        val summary = JSONObject()
            .put("run_id", runId)
            .put("target_pkg", targetPackage ?: "")
            .put("started_at", startedAt)
            .put("ended_at", endedAt)
            .put("duration_ms", durationMs)
            .put("device", JSONObject()
                .put("model", Build.MODEL)
                .put("sdk", Build.VERSION.SDK_INT)
                .put("manufacturer", Build.MANUFACTURER))
            .put("stats", JSONObject()
                .put("new_screens", newScreenCount.get())
                .put("actions_executed", actionCount.get())
                .put("edges_recorded", edgeCount.get())
                .put("hot_edges", hotEdgeCount.get())
                .put("cold_edges", coldEdgeCount.get())
                .put("hot_pct", "%.2f".format(hotPct).toDouble())
                .put("guard_blocks", blockCount.get())
                .put("evacuates", evacuateCount.get())
                .put("dialogs_dismissed", dialogCount.get())
                .put("actions_per_sec", "%.3f".format(actionCount.get() * 1000.0 / durationMs).toDouble()))
            .put("output_dir", baseDir.absolutePath)

        summaryFile.writeText(summary.toString(2))
    }

    private fun windowsToJson(windows: List<WindowInfo>): JSONArray {
        val arr = JSONArray()
        for (w in windows) {
            arr.put(
                JSONObject()
                    .put("id", w.id)
                    .put("type", w.type)
                    .put("layer", w.layer)
                    .put("pkg", w.packageName ?: "")
                    .put("nodes", w.nodeCount)
                    .put("active", w.isActive)
                    .put("focused", w.isFocused),
            )
        }
        return arr
    }

    val outputDir: File get() = baseDir
    val id: String get() = runId

    companion object {
        private const val TAG = "RunRecorder"
        private val TS_FORMAT = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US)

        fun makeRunId(): String = TS_FORMAT.format(Date())
    }
}
