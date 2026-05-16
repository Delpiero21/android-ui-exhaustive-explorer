package com.exhaustive.explorer.ui

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material3.Button
import androidx.compose.material3.Card
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.exhaustive.explorer.engine.ExplorerEngine
import com.exhaustive.explorer.service.ExplorerAccessibilityService
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.asStateFlow

/**
 * 단말 측 UI — 탐색 시작/중지 토글 + 라이브 상태 요약.
 *
 * Phase 1 의 최소 기능:
 * - [버튼] 접근성 설정 열기 (서비스 활성화)
 * - [입력] target package
 * - [버튼] 자율 탐색 시작 / 중지
 * - [표시] 발견 화면 / 엣지 / 마지막 fp / 자율 모드 동작 여부 (1초 폴링)
 */
class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme {
                Surface(
                    modifier = Modifier.fillMaxSize(),
                    color = MaterialTheme.colorScheme.background,
                ) {
                    ControlPanel(
                        onOpenA11ySettings = {
                            startActivity(
                                Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)
                                    .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK),
                            )
                        },
                        // 시작 후 우리 앱을 즉시 background 로 — target 앱이 자연 노출됨
                        onAfterStart = { moveTaskToBack(true) },
                    )
                }
            }
        }
    }
}

@Composable
private fun ControlPanel(
    onOpenA11ySettings: () -> Unit,
    onAfterStart: () -> Unit,
) {
    var targetPackage by remember { mutableStateOf("com.samsung.android.app.notes") }
    var budgetSec by remember { mutableStateOf("300") }  // 기본 5 분

    // 1초 주기로 service snapshot 폴링 — 가벼움
    val snapshotFlow = remember { MutableStateFlow<ExplorerEngine.EngineSnapshot?>(null) }
    LaunchedEffect(Unit) {
        while (true) {
            snapshotFlow.value = ExplorerAccessibilityService.INSTANCE?.snapshot()
            delay(1000L)
        }
    }
    val snapshot by snapshotFlow.asStateFlow().collectAsState()

    Column(
        modifier = Modifier
            .fillMaxSize()
            .padding(PaddingValues(20.dp)),
        verticalArrangement = Arrangement.spacedBy(16.dp),
    ) {
        Text(
            text = "android-ui-exhaustive-explorer",
            style = MaterialTheme.typography.titleLarge,
            fontWeight = FontWeight.SemiBold,
        )
        Text(
            text = "Phase 1 · Passive + Autonomous",
            style = MaterialTheme.typography.titleMedium,
            color = MaterialTheme.colorScheme.primary,
        )

        // ─── 서비스 상태 ───
        val serviceReady = snapshot != null
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("AccessibilityService", fontWeight = FontWeight.SemiBold)
                Text(
                    text = if (serviceReady) "● 활성화됨" else "○ 비활성화 — 아래 버튼으로 설정",
                    color = if (serviceReady) MaterialTheme.colorScheme.primary
                    else MaterialTheme.colorScheme.error,
                )
                if (!serviceReady) {
                    OutlinedButton(onClick = onOpenA11ySettings) {
                        Text("설정 → 접근성 열기")
                    }
                }
            }
        }

        // ─── target package + budget ───
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("자율 탐색 설정", fontWeight = FontWeight.SemiBold)
                OutlinedTextField(
                    value = targetPackage,
                    onValueChange = { targetPackage = it },
                    label = { Text("target package") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
                OutlinedTextField(
                    value = budgetSec,
                    onValueChange = { v -> budgetSec = v.filter { it.isDigit() }.take(5) },
                    label = { Text("budget (seconds) — 0 = 무제한 (frontier 소진 시 자동 종료)") },
                    modifier = Modifier.fillMaxWidth(),
                    singleLine = true,
                )
            }
        }

        // ─── 시작/중지 버튼 ───
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(12.dp),
        ) {
            val running = snapshot?.autonomousRunning == true
            Button(
                onClick = {
                    // budget=0 → unlimited (engine 내부에서 처리)
                    val budgetMs = (budgetSec.toLongOrNull() ?: 300L) * 1000L
                    ExplorerAccessibilityService.INSTANCE?.startAutonomous(
                        targetPackage = targetPackage.takeIf { it.isNotBlank() },
                        budgetMs = budgetMs,
                    )
                    // 즉시 우리 앱을 background 로 — target 앱이 자연 노출됨
                    onAfterStart()
                },
                enabled = serviceReady && !running,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { Text("시작") }
            OutlinedButton(
                onClick = { ExplorerAccessibilityService.INSTANCE?.stopAutonomous() },
                enabled = running,
                modifier = Modifier.fillMaxWidth().weight(1f),
            ) { Text("중지") }
        }

        OutlinedButton(
            onClick = { ExplorerAccessibilityService.INSTANCE?.reset() },
            enabled = serviceReady,
            modifier = Modifier.fillMaxWidth(),
        ) { Text("StateGraph 초기화") }

        // ─── 실시간 통계 ───
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(Modifier.padding(16.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                Text("탐색 통계 (1s polling)", fontWeight = FontWeight.SemiBold)
                val snap = snapshot
                if (snap == null) {
                    Text("아직 데이터 없음", style = MaterialTheme.typography.bodySmall)
                } else {
                    Text("발견 화면 (unique fingerprint): ${snap.nodeCount}")
                    Text("전이 엣지: ${snap.edgeCount}")
                    Text("자율 탐색: ${if (snap.autonomousRunning) "● 진행 중" else "○ 정지"}")
                    snap.lastFp?.let {
                        Text("마지막 fp.strict: ${it.strict.take(12)}…")
                        Text("마지막 fp.loose : ${it.loose.take(12)}…")
                    }
                }
            }
        }

        Spacer(Modifier.height(4.dp))
        Text(
            text = "logcat: adb logcat -s ExplorerA11y:V ExplorerEngine:I",
            style = MaterialTheme.typography.bodySmall,
            color = MaterialTheme.colorScheme.onSurfaceVariant,
        )
    }
}
