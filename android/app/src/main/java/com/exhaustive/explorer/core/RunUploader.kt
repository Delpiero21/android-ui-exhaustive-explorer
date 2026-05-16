package com.exhaustive.explorer.core

import android.util.Log
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileInputStream
import java.net.HttpURLConnection
import java.net.URL
import java.util.zip.ZipEntry
import java.util.zip.ZipOutputStream

/**
 * Run 종료 시 단말의 events.jsonl + summary.json + screens 디렉토리를
 * **자동으로 PC server 에 업로드**.
 *
 * 통신 경로 (이미 셋업됨):
 *   단말 → http://127.0.0.1:8000 → (adb reverse tcp:8000) → PC server
 *
 * 보안 경계:
 *   network_security_config.xml 가 cleartext 를 127.0.0.1 / localhost 만 허용 → 외부 egress 0
 *   사외 IP 시도 시 OS 차단
 *
 * 동작:
 *   1. run 디렉토리 통째로 zip
 *   2. POST /api/runs/upload?run_id=XXX (application/zip body)
 *   3. 성공 시 (선택) 로컬 데이터 삭제 — 기본 false (디버그 위해)
 */
class RunUploader(
    private val baseUrl: String = "http://127.0.0.1:8000",
) {

    /**
     * @param runDir         단말 측 run 폴더 (events.jsonl + summary.json + screens/)
     * @param deleteAfterOk  업로드 성공 시 로컬 폴더 삭제
     * @return true = 200/201/204 응답. false = 실패 (네트워크, server, zip 등)
     */
    suspend fun uploadRun(runDir: File, deleteAfterOk: Boolean = false): Boolean = withContext(Dispatchers.IO) {
        if (!runDir.isDirectory) {
            Log.w(TAG, "uploadRun: runDir not a directory: $runDir")
            return@withContext false
        }
        val runId = runDir.name
        Log.i(TAG, "uploadRun start — runId=$runId path=$runDir")

        // 1) Zip
        val zipFile = File(runDir.parent, "$runId.zip")
        try {
            zipDir(runDir, zipFile)
            val sizeKb = zipFile.length() / 1024
            Log.i(TAG, "uploadRun zipped → ${zipFile.name} ($sizeKb KB)")
        } catch (t: Throwable) {
            Log.e(TAG, "uploadRun zip failed", t)
            zipFile.delete()
            return@withContext false
        }

        // 2) POST
        val ok = try {
            val url = URL("$baseUrl/api/runs/upload?run_id=$runId")
            val conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                doOutput = true
                connectTimeout = 5_000
                readTimeout = 60_000
                setRequestProperty("Content-Type", "application/zip")
                setRequestProperty("X-Run-Id", runId)
                setChunkedStreamingMode(0)  // 큰 파일도 안전하게 stream
            }
            try {
                FileInputStream(zipFile).use { input ->
                    conn.outputStream.use { output ->
                        input.copyTo(output)
                    }
                }
                val code = conn.responseCode
                val body = runCatching {
                    if (code in 200..299) conn.inputStream.bufferedReader().readText()
                    else conn.errorStream?.bufferedReader()?.readText() ?: ""
                }.getOrDefault("")
                Log.i(TAG, "uploadRun http $code — $body")
                code in 200..299
            } finally {
                conn.disconnect()
            }
        } catch (t: Throwable) {
            Log.e(TAG, "uploadRun POST failed: ${t.javaClass.simpleName} ${t.message}", t)
            false
        }

        // 3) Cleanup
        runCatching { zipFile.delete() }
        if (ok && deleteAfterOk) {
            runCatching { runDir.deleteRecursively() }
                .onSuccess { Log.i(TAG, "local run dir deleted: $runDir") }
                .onFailure { Log.w(TAG, "local delete failed: ${it.message}") }
        }

        Log.i(TAG, "uploadRun done — ok=$ok")
        ok
    }

    /** 디렉토리를 재귀적으로 zip (top-level dir 이름 포함). */
    private fun zipDir(srcDir: File, destZip: File) {
        ZipOutputStream(destZip.outputStream().buffered()).use { zos ->
            zipRecursive(srcDir, srcDir.name, zos)
        }
    }

    private fun zipRecursive(file: File, relativePath: String, zos: ZipOutputStream) {
        if (file.isDirectory) {
            // 디렉토리 entry 는 안 만들고 (server 가 mkdir 함) 자식만 재귀
            file.listFiles()?.forEach { child ->
                zipRecursive(child, "$relativePath/${child.name}", zos)
            }
        } else {
            zos.putNextEntry(ZipEntry(relativePath))
            FileInputStream(file).use { input -> input.copyTo(zos) }
            zos.closeEntry()
        }
    }

    companion object {
        private const val TAG = "RunUploader"
    }
}
