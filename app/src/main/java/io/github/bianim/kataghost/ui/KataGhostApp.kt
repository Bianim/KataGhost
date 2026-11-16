package io.github.bianim.kataghost.ui

import androidx.compose.ui.platform.LocalResources
import io.github.bianim.kataghost.R
import androidx.activity.compose.BackHandler
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import io.github.bianim.kataghost.engine.analysisFor
import androidx.compose.runtime.*
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.unit.dp
import androidx.lifecycle.Lifecycle
import androidx.lifecycle.LifecycleEventObserver
import androidx.lifecycle.compose.LocalLifecycleOwner
import androidx.lifecycle.viewmodel.compose.viewModel
import io.github.bianim.kataghost.DiagnosticsScreen
import io.github.bianim.kataghost.engine.BoardSnapshot
import io.github.bianim.kataghost.engine.EnginePhase
import io.github.bianim.kataghost.engine.KataGoViewModel
import io.github.bianim.kataghost.ui.record.RecordScreen
import io.github.bianim.kataghost.ui.record.RecordViewModel
import io.github.bianim.kataghost.ui.record.TreeViewportState
import io.github.bianim.kataghost.ui.theme.GoColors
import kotlinx.coroutines.delay

@Composable
fun KataGhostApp() {
    val resources = LocalResources.current
    val document: RecordViewModel = viewModel()
    val record = document.record
    val audioContext = androidx.compose.ui.platform.LocalContext.current
    DisposableEffect(audioContext, record) {
        val pool = android.media.SoundPool.Builder().setMaxStreams(1)
            .setAudioAttributes(android.media.AudioAttributes.Builder()
                .setUsage(android.media.AudioAttributes.USAGE_GAME)
                .setContentType(android.media.AudioAttributes.CONTENT_TYPE_SONIFICATION)
                .build())
            .build()
        pool.setOnLoadCompleteListener { _, sample, status ->
            if (status == 0) {
                record.onStonePlaced = { pool.play(sample, 1f, 1f, 0, 0, 1f) }
            }
        }
        pool.load(audioContext, io.github.bianim.kataghost.R.raw.stone_placement, 1)
        onDispose {
            record.onStonePlaced = null
            pool.setOnLoadCompleteListener(null)
            pool.release()
        }
    }
    val settings = document.preferences
    val viewport = rememberSaveable(saver = TreeViewportState.Saver) { TreeViewportState() }
    var page by rememberSaveable { mutableIntStateOf(0) }
    var diagnosticsReturnPage by rememberSaveable { mutableIntStateOf(1) }
    var pickerOpen by rememberSaveable { mutableStateOf(false) }
    val engine: KataGoViewModel = viewModel()
    val engineState by engine.controller.state.collectAsState()
    val benchmark by engine.controller.benchmark.collectAsState()
    val owner = LocalLifecycleOwner.current
    var foreground by remember(owner) { mutableStateOf(owner.lifecycle.currentState.isAtLeast(Lifecycle.State.STARTED)) }
    val latestSettings by rememberUpdatedState(settings)
    val position = record.position
    val visible = foreground && page == 0 && !document.busy && !pickerOpen && document.importChoices.isEmpty()
    var territory by remember { mutableStateOf(false) }
    BackHandler(territory) { territory = false }
    val autoTurn = !territory && settings.hasAutomaticPlayer && settings.automatic(position.nextPlayer) && record.autoPlayArmed && record.preview == null
    val recordNodes = record.nodes
    val recordHistory = remember(recordNodes, record.rules, record.komi) {
        recordNodes.map { record.positionFor(it.id) }.distinctBy { it.key }
    }
    val opponent = settings.effectiveOpponentSearch.takeIf { autoTurn }
    val placement = record.lastPlacement
    val deltaBefore = record.node.parentId?.takeIf { placement != null && position == placement.position && record.preview == null }
        ?.let(record::positionFor)
    val before = deltaBefore?.let { engineState.analysisFor(it) }
    val after = engineState.analysisFor(position)
    var deltaFinished by remember { mutableStateOf<io.github.bianim.kataghost.ui.record.RecordUiState.Placement?>(null) }
    val waitForPass = placement?.automaticPass == true && position == placement.position && deltaFinished !== placement
    var settlement by remember { mutableStateOf<io.github.bianim.kataghost.engine.AnalysisPosition?>(null) }
    val settling = settlement == position
    val waitForDelta = settings.waitForWinRateChange && deltaBefore != null
    val history = if (autoTurn || record.preview != null) emptyList() else recordHistory
    val snackbar = remember { SnackbarHostState() }

    val importFile = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        pickerOpen = false
        if (uri != null) { document.importSgf(uri); viewport.initialized = false; page = 0 }
    }
    val exportFile = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/x-go-sgf")) { uri ->
        pickerOpen = false
        if (uri != null) document.exportSgf(uri) else document.cancelExport()
    }
    fun suspendForPicker() {
        record.pauseAutomatic(); pickerOpen = true
        engine.controller.setTarget(record.position, settings.engineEnabled, visible = false, maxVisits = settings.maxVisits, config = settings.engineConfig)
    }
    val onImport: () -> Unit = {
        if (!document.busy) {
            suspendForPicker()
            importFile.launch(arrayOf("*/*"))
        }
    }
    val onExport: () -> Unit = {
        if (!document.busy) {
            suspendForPicker(); document.prepareExport()
            exportFile.launch("KataGhost.sgf")
        }
    }

    LaunchedEffect(position, history, settings.engineEnabled, settings.maxVisits, settings.engineConfig, visible, page, foreground, opponent, territory, settling, waitForDelta, waitForPass, deltaBefore) {
        engine.controller.setTarget(position, settings.engineEnabled || territory || settling, visible, maxVisits = settings.maxVisits, config = settings.engineConfig,
            history = history, continuous = !autoTurn, opponent = opponent.takeUnless { waitForPass }, territory = territory || settling, deltaBefore = deltaBefore.takeIf { waitForDelta || waitForPass })
    }
    LaunchedEffect(autoTurn, visible, settings.engineEnabled, settings.maxVisits, settings.engineConfig, position, engineState.phase, engineState.opponentResult, opponent, waitForDelta, waitForPass, deltaFinished, before != null && after != null) {
        val result = engineState.opponentResult?.takeIf { it.first == opponent }?.second
        if (!waitForPass && (!waitForDelta || deltaFinished === placement || before == null || after == null) && autoTurn && visible && settings.engineEnabled && engineState.phase == EnginePhase.READY &&
            engineState.config == settings.engineConfig && engineState.maxVisits == settings.maxVisits && result?.position == position && !result.gameFinished) {
            // Give navigation, stop and a newly selected position time to cancel this turn.
            delay(180)
            if (!territory && record.position == position && record.autoPlayArmed && latestSettings.engineEnabled && latestSettings.engineConfig == engineState.config &&
                latestSettings.automatic(position.nextPlayer) && record.preview == null &&
                latestSettings.effectiveOpponentSearch == opponent && engine.controller.state.value.opponentResult === engineState.opponentResult) {
                val move = opponent?.selectMove(result, BoardSnapshot.from(position)::isLegal)
                if (move == null || !record.play(move, automatic = true)) record.automaticFailed(R.string.auto_move_failed)
            }
        }
    }
    LaunchedEffect(placement, deltaFinished, position) {
        if (placement?.automaticPass == true && deltaFinished === placement &&
            position == placement.position && BoardSnapshot.from(position).finished) {
            settlement = position
        }
    }
    if (settling) {
        io.github.bianim.kataghost.ui.record.SettlementDialog(
            position, record.node.color, engineState, onClose = { settlement = null },
        )
    }
    LaunchedEffect(document.message) {
        document.message?.let { snackbar.showSnackbar(it); document.message = null }
    }
    DisposableEffect(owner) {
        val observer = LifecycleEventObserver { _, event ->
            if (event == Lifecycle.Event.ON_START) foreground = true
            if (event == Lifecycle.Event.ON_STOP) {
                foreground = false
                engine.controller.setTarget(record.position, latestSettings.engineEnabled, visible = false, maxVisits = latestSettings.maxVisits, config = latestSettings.engineConfig)
            }
        }
        owner.lifecycle.addObserver(observer)
        onDispose { owner.lifecycle.removeObserver(observer) }
    }
    fun goBack() {
        when (page) { 3 -> page = 1; 2 -> page = diagnosticsReturnPage; 1 -> page = 0; else -> record.exitPreview() }
    }
    BackHandler(page != 0 || record.preview != null, onBack = ::goBack)

    Box(Modifier.fillMaxSize().background(GoColors.Background)
        .windowInsetsPadding(WindowInsets.safeDrawing.only(WindowInsetsSides.Top + WindowInsetsSides.Horizontal))) {
        when (page) {
            1 -> SettingsScreen(settings, record.rules, record.komi, document.busy,
                onApply = document::applySettings,
                onBack = ::goBack, onDiagnostics = { diagnosticsReturnPage = 1; page = 2 }, onEngines = { page = 3 })
            2 -> DiagnosticsScreen(onBack = ::goBack, model = settings.model)
            3 -> EngineScreen(settings.engineConfig, benchmark,
                onConfig = { config ->
                    document.updatePreferences(settings.copy(model = config.model, backend = config.backend))
                    engine.controller.setTarget(position, settings.engineEnabled, visible = false, maxVisits = settings.maxVisits, config = config)
                }, onStart = { engine.controller.startBenchmark(settings.engineConfig, it) }, onCancel = engine.controller::cancelBenchmark,
                onDiagnostics = { diagnosticsReturnPage = 3; page = 2 }, onBack = ::goBack)
            else -> RecordScreen(record, viewport, settings, engineState,
                onDeltaFinished = { deltaFinished = it },
                territory = territory, onTerritory = { territory = !territory },
                onCandidates = { document.updatePreferences(settings.copy(candidates = it)) },
                onPass = {
                    record.play(io.github.bianim.kataghost.engine.PASS).also { played ->
                        if (played && BoardSnapshot.from(record.position).finished) settlement = record.position
                    }
                },
                manualInput = !waitForPass && !(autoTurn && settings.engineEnabled) && !document.busy,
                onEngineEnabled = {
                    document.updatePreferences(settings.copy(engineEnabled = it))
                    engine.controller.setTarget(record.position, it, visible, maxVisits = settings.maxVisits, config = settings.engineConfig,
                        history = history, continuous = !autoTurn, opponent = opponent)
                },
                onRetry = { engine.controller.setTarget(record.position, settings.engineEnabled, visible, retry = true, maxVisits = settings.maxVisits,
                    config = settings.engineConfig, history = history, continuous = !autoTurn, opponent = opponent) },
                onSettings = { page = 1 }, onImport = onImport, onExport = onExport,
                onNewGame = { record.newGame(settings.whiteHandicap, settings.komi); viewport.initialized = false },
                onResumeAutomatic = { record.resumeAutomatic(); document.updatePreferences(settings.copy(engineEnabled = true)) })
        }
        SnackbarHost(snackbar, Modifier.align(Alignment.BottomCenter).padding(bottom = if (page == 1) 96.dp else 0.dp).navigationBarsPadding())
        if (document.busy) LinearProgressIndicator(Modifier.fillMaxWidth().align(Alignment.TopCenter).testTag("document-progress"))
    }
    if (document.importChoices.isNotEmpty()) {
        AlertDialog(onDismissRequest = document::cancelImportChoice, title = { Text(resources.getString(R.string.choose_game)) },
            text = {
                Column(Modifier.heightIn(max = 400.dp).verticalScroll(rememberScrollState())) {
                    document.importChoices.forEachIndexed { index, root ->
                        val props = root.properties
                        val name = props["GN"]?.firstOrNull() ?: "${props["PB"]?.firstOrNull() ?: resources.getString(R.string.black)} / ${props["PW"]?.firstOrNull() ?: resources.getString(R.string.white)}"
                        TextButton(onClick = { document.chooseImport(index) }, modifier = Modifier.fillMaxWidth()) { Text("${index + 1}. $name") }
                    }
                }
            }, confirmButton = { TextButton(onClick = document::cancelImportChoice) { Text(resources.getString(R.string.cancel)) } })
    }
}
