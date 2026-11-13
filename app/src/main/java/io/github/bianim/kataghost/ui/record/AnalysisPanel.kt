package io.github.bianim.kataghost.ui.record

import io.github.bianim.kataghost.R
import androidx.compose.ui.platform.LocalResources
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.heightIn
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.HorizontalDivider
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.runtime.Composable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.semantics.semantics
import androidx.compose.ui.semantics.stateDescription
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextOverflow
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import io.github.bianim.kataghost.engine.analysisFor
import io.github.bianim.kataghost.engine.EnginePhase
import io.github.bianim.kataghost.engine.EngineUiState
import io.github.bianim.kataghost.engine.PASS
import io.github.bianim.kataghost.ui.theme.GoColors
import java.util.Locale
import kotlin.math.abs

internal fun Float.oneDecimal() = String.format(Locale.ROOT, "%.1f", this)

@Composable
fun AnalysisPanel(state: RecordUiState, engine: EngineUiState, enabled: Boolean, onRetry: () -> Unit, modifier: Modifier = Modifier, showCandidates: Boolean = true) {
    val resources = LocalResources.current
    val position = state.position
    val analysis = engine.analysisFor(position)
    val preview = state.preview
    val phase = if (!enabled) EnginePhase.OFF else if (engine.positionKey == position.key) engine.phase else EnginePhase.STARTING
    val title = when {
        preview != null -> resources.getString(R.string.preview_title, preview.label, BoardPoint.fromIndex(preview.moves.first())?.label ?: resources.getString(R.string.pass_move))
        analysis == null -> resources.getString(R.string.current_not_analyzed)
        !enabled || phase == EnginePhase.ERROR -> resources.getString(R.string.saved_analysis, position.moves.size)
        analysis.gameFinished -> resources.getString(R.string.final_score, resources.getString(if (analysis.blackLead > 0) R.string.black_wins else if (analysis.blackLead < 0) R.string.white_wins else R.string.draw))
        abs(analysis.blackLead) < 1f -> resources.getString(R.string.position_even)
        else -> resources.getString(R.string.position_advantage, resources.getString(if (analysis.blackLead > 0) R.string.black else R.string.white), resources.getString(if (abs(analysis.blackLead) < 5) R.string.slight_advantage else R.string.advantage))
    }
    val status = when (phase) {
        EnginePhase.OFF -> resources.getString(R.string.engine_paused)
        EnginePhase.STARTING -> resources.getString(R.string.status_starting)
        EnginePhase.ANALYZING -> if (engine.continuousAnalyzing && analysis != null)
            "${engine.config.model.id} · ${analysis.backend} · ${resources.getString(R.string.status_analyzing)}"
            else if (analysis == null) resources.getString(R.string.analyzing) else resources.getString(R.string.status_analyzing)
        EnginePhase.ERROR -> resources.getString(R.string.status_error)
        EnginePhase.SUSPENDED -> resources.getString(R.string.status_suspended)
        EnginePhase.READY -> "${engine.config.model.id} · ${analysis?.backend ?: resources.getString(engine.config.backend.labelRes)}"
    }
    Surface(modifier.testTag("analysis-panel").semantics {
        stateDescription = resources.getString(R.string.analysis_description, position.moves.size, analysis?.let { "${it.visits} visits" } ?: resources.getString(R.string.not_analyzed), status)
    }, shape = RoundedCornerShape(20.dp), color = GoColors.Surface, border = BorderStroke(1.dp, GoColors.Outline)) {
        Column(Modifier.padding(horizontal = 14.dp, vertical = 10.dp)) {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Text(title, fontSize = 10.sp, lineHeight = 16.sp, fontWeight = FontWeight.Medium, color = GoColors.Primary,
                    maxLines = 1, overflow = TextOverflow.Ellipsis, modifier = Modifier.weight(1f).testTag("analysis-title"))
                Text(analysis?.let { "${it.visits} visits" } ?: "", fontSize = 9.sp, color = GoColors.Muted,
                    modifier = Modifier.testTag("analysis-visits"))
            }
            Row(Modifier.fillMaxWidth().padding(top = 2.dp, bottom = 4.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                Metric(resources.getString(R.string.black_win_rate), analysis?.blackWinRate?.oneDecimal() ?: "—", "%", false, "win-rate")
                Metric(if ((analysis?.blackLead ?: 0f) >= 0) resources.getString(R.string.black_lead) else resources.getString(R.string.white_lead), analysis?.blackLead?.let { abs(it).oneDecimal() } ?: "—", resources.getString(R.string.points), true, "score-lead")
            }
            if (phase == EnginePhase.ERROR) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                    Text(if (analysis != null) resources.getString(R.string.analysis_failed_saved) else resources.getString(R.string.analysis_failed_retry), fontSize = 10.sp, color = GoColors.Muted)
                    TextButton(onClick = onRetry, modifier = Modifier.testTag("retry-engine")) { Text(resources.getString(R.string.retry), fontSize = 11.sp) }
                }
            }
            if (preview == null) {
                val pass = analysis?.candidates?.firstOrNull { it.move == PASS }
                if (showCandidates && pass != null) TextButton(onClick = { state.startPreview(Candidate(pass.label, pass.pv)) },
                    modifier = Modifier.testTag("candidate-pass")) { Text(resources.getString(R.string.pass_preview, pass.label), fontSize = 10.sp) }
            } else {
                HorizontalDivider(color = GoColors.Outline)
                Row(Modifier.fillMaxWidth().heightIn(min = 36.dp), verticalAlignment = Alignment.CenterVertically, horizontalArrangement = Arrangement.SpaceBetween) {
                    Text(resources.getString(R.string.branch_letter, preview.label), fontSize = 10.sp, color = GoColors.Primary, fontWeight = FontWeight.Medium)
                    Text(preview.moves.take(state.previewStep).mapIndexed { i, move -> "${i + 1} ${BoardPoint.fromIndex(move)?.label ?: resources.getString(R.string.pass_move)}" }
                        .joinToString(" → ").ifEmpty { resources.getString(R.string.preview_start) },
                        modifier = Modifier.weight(1f).padding(horizontal = 8.dp).testTag("preview-moves"),
                        fontSize = 10.sp, lineHeight = 14.sp, color = GoColors.Muted, maxLines = 2, overflow = TextOverflow.Ellipsis)
                    TextButton(onClick = state::exitPreview, modifier = Modifier.testTag("exit-preview"),
                        contentPadding = androidx.compose.foundation.layout.PaddingValues(horizontal = 6.dp)) { Text(resources.getString(R.string.exit_preview), fontSize = 10.sp) }
                }
            }
        }
    }
}

@Composable
private fun Metric(label: String, value: String, unit: String, green: Boolean, tag: String) {
    Row(Modifier.testTag(tag), horizontalArrangement = Arrangement.spacedBy(5.dp), verticalAlignment = Alignment.Bottom) {
        Text(label, fontSize = 11.sp, lineHeight = 24.sp, color = GoColors.Muted)
        Row(verticalAlignment = Alignment.Bottom) {
            Text(value, fontSize = 25.sp, lineHeight = 34.sp, letterSpacing = (-.8).sp, fontWeight = FontWeight.SemiBold,
                color = if (green) GoColors.Primary else GoColors.Ink, modifier = Modifier.testTag("$tag-value"))
            Text(unit, fontSize = 11.sp, lineHeight = 24.sp, color = if (green) GoColors.Primary else GoColors.Ink)
        }
    }
}
