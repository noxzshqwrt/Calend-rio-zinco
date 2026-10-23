package com.noxzshqwrt.calendario

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.CircleShape
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.CalendarMonth
import androidx.compose.material.icons.filled.Delete
import androidx.compose.material.icons.filled.FileDownload
import androidx.compose.material.icons.filled.FileUpload
import androidx.compose.material.icons.filled.KeyboardArrowLeft
import androidx.compose.material.icons.filled.KeyboardArrowRight
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Today
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlinx.coroutines.launch
import java.time.Instant
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        initReminder(this)
        enableEdgeToEdge()
        setContent {
            CalendarioTheme { CalendarScreen() }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun CalendarScreen() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var state by remember { mutableStateOf(store.load()) }
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var showDebug by remember { mutableStateOf(false) }
    var showDatePicker by remember { mutableStateOf(false) }
    var confirmReset by remember { mutableStateOf(false) }
    var pendingImport by remember { mutableStateOf<AppState?>(null) }

    val snackbarHost = remember { SnackbarHostState() }
    val scope = rememberCoroutineScope()

    fun save(newState: AppState) {
        state = newState
        store.save(newState)
    }

    fun onDayTap(dayStr: String) {
        val oldState = state
        val wasRelapse = dayStr in oldState.relapses
        val newState = oldState.copy(
            relapses = if (wasRelapse) oldState.relapses - dayStr else oldState.relapses + dayStr
        )
        save(newState)
        scope.launch {
            val msg = if (wasRelapse) "Recaída de ${formatBR(dayStr)} removida"
                      else "Recaída em ${formatBR(dayStr)} marcada"
            val result = snackbarHost.showSnackbar(
                message = msg,
                actionLabel = "Desfazer",
                duration = SnackbarDuration.Short
            )
            if (result == SnackbarResult.ActionPerformed) save(oldState)
        }
    }

    val exportLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.CreateDocument("application/json")
    ) { uri ->
        if (uri != null) {
            try {
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use {
                    it.write(store.toJson(state))
                }
                scope.launch { snackbarHost.showSnackbar("Backup exportado") }
            } catch (e: Exception) {
                scope.launch { snackbarHost.showSnackbar("Erro: ${e.message}") }
            }
        }
    }

    val importLauncher = rememberLauncherForActivityResult(
        ActivityResultContracts.OpenDocument()
    ) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                val parsed = text?.let { store.fromJson(it) }
                if (parsed == null) {
                    scope.launch { snackbarHost.showSnackbar("Arquivo JSON inválido") }
                } else pendingImport = parsed
            } catch (e: Exception) {
                scope.launch { snackbarHost.showSnackbar("Erro: ${e.message}") }
            }
        }
    }

    Scaffold(
        topBar = {
            CenterAlignedTopAppBar(
                title = { Text("Calendário", fontWeight = FontWeight.Bold) },
                actions = {
                    IconButton(onClick = { viewMonth = YearMonth.now() }) {
                        Icon(Icons.Default.Today, contentDescription = "Hoje")
                    }
                    IconButton(onClick = { showDebug = true }) {
                        Icon(Icons.Default.Settings, contentDescription = "Ajustes")
                    }
                }
            )
        },
        snackbarHost = { SnackbarHost(snackbarHost) }
    ) { padding ->
        Column(
            modifier = Modifier
                .fillMaxSize()
                .padding(padding)
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 16.dp, vertical = 8.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            StreakCard(state)
            StatsRow(state)
            CalendarCard(
                viewMonth = viewMonth,
                state = state,
                onPrev = { viewMonth = viewMonth.minusMonths(1) },
                onNext = { viewMonth = viewMonth.plusMonths(1) },
                onDayTap = ::onDayTap
            )
            Legend()
            Spacer(Modifier.height(8.dp))
        }
    }

    if (showDebug) {
        DebugSheet(
            state = state,
            onDismiss = { showDebug = false },
            onExport = { exportLauncher.launch("nofap-cal-${todayStr().replace("-", "")}.json") },
            onImport = { importLauncher.launch(arrayOf("application/json")) },
            onChangeStart = { showDatePicker = true },
            onReset = { confirmReset = true }
        )
    }

    if (showDatePicker) {
        val datePickerState = rememberDatePickerState(
            initialSelectedDateMillis = LocalDate.parse(state.trackingStart)
                .atStartOfDay(ZoneOffset.UTC).toInstant().toEpochMilli()
        )
        DatePickerDialog(
            onDismissRequest = { showDatePicker = false },
            confirmButton = {
                TextButton(onClick = {
                    datePickerState.selectedDateMillis?.let { millis ->
                        val date = Instant.ofEpochMilli(millis).atZone(ZoneOffset.UTC).toLocalDate()
                        if (date.isAfter(LocalDate.now())) {
                            scope.launch { snackbarHost.showSnackbar("Data não pode ser no futuro") }
                        } else {
                            save(state.copy(trackingStart = date.toString()))
                        }
                    }
                    showDatePicker = false
                }) { Text("Salvar") }
            },
            dismissButton = {
                TextButton(onClick = { showDatePicker = false }) { Text("Cancelar") }
            }
        ) {
            DatePicker(state = datePickerState)
        }
    }

    if (confirmReset) {
        AlertDialog(
            onDismissRequest = { confirmReset = false },
            title = { Text("Apagar todos os dados?") },
            text = { Text("Isso não pode ser desfeito. Considere exportar um backup antes.") },
            confirmButton = {
                TextButton(
                    onClick = {
                        save(AppState(todayStr(), emptySet()))
                        confirmReset = false
                        scope.launch { snackbarHost.showSnackbar("Dados apagados") }
                    },
                    colors = ButtonDefaults.textButtonColors(
                        contentColor = MaterialTheme.colorScheme.error
                    )
                ) { Text("Apagar") }
            },
            dismissButton = {
                TextButton(onClick = { confirmReset = false }) { Text("Cancelar") }
            }
        )
    }

    pendingImport?.let { imported ->
        AlertDialog(
            onDismissRequest = { pendingImport = null },
            title = { Text("Importar backup?") },
            text = {
                Text(
                    "Dados atuais serão substituídos.\n\n" +
                    "Atual: início ${formatBR(state.trackingStart)}, ${state.relapses.size} recaída(s)\n" +
                    "Novo: início ${formatBR(imported.trackingStart)}, ${imported.relapses.size} recaída(s)"
                )
            },
            confirmButton = {
                TextButton(onClick = {
                    save(imported)
                    pendingImport = null
                    scope.launch { snackbarHost.showSnackbar("Backup importado") }
                }) { Text("Substituir") }
            },
            dismissButton = {
                TextButton(onClick = { pendingImport = null }) { Text("Cancelar") }
            }
        )
    }
}

@Composable
fun StreakCard(state: AppState) {
    val streak = currentStreak(state)
    val lr = lastRelapse(state)
    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.primaryContainer
        ),
        shape = RoundedCornerShape(28.dp)
    ) {
        Column(
            modifier = Modifier.fillMaxWidth().padding(horizontal = 24.dp, vertical = 28.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$streak",
                style = MaterialTheme.typography.displayLarge.copy(
                    fontSize = 96.sp,
                    fontWeight = FontWeight.Black,
                    lineHeight = 96.sp
                ),
                color = MaterialTheme.colorScheme.onPrimaryContainer
            )
            Text(
                if (streak == 1) "dia de streak" else "dias de streak",
                style = MaterialTheme.typography.titleMedium,
                color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.85f)
            )
            Spacer(Modifier.height(12.dp))
            if (lr != null) {
                val ago = daysBetween(lr, todayStr())
                val txt = when (ago) { 0 -> "hoje"; 1 -> "ontem"; else -> "há $ago dias" }
                Surface(
                    color = MaterialTheme.colorScheme.surface.copy(alpha = 0.5f),
                    shape = RoundedCornerShape(20.dp)
                ) {
                    Text(
                        "Última recaída: ${formatBR(lr)} ($txt)",
                        modifier = Modifier.padding(horizontal = 14.dp, vertical = 8.dp),
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onPrimaryContainer
                    )
                }
            } else {
                Text(
                    "Nenhuma recaída registrada 🎯",
                    style = MaterialTheme.typography.bodyMedium,
                    color = MaterialTheme.colorScheme.onPrimaryContainer.copy(alpha = 0.7f)
                )
            }
        }
    }
}

@Composable
fun StatsRow(state: AppState) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        StatCard("Recorde", bestStreak(state), Modifier.weight(1f))
        StatCard("Dias limpos", totalClean(state), Modifier.weight(1f))
        StatCard("Tentativas", state.relapses.size + 1, Modifier.weight(1f))
    }
}

@Composable
fun StatCard(label: String, value: Int, modifier: Modifier = Modifier) {
    OutlinedCard(modifier = modifier) {
        Column(Modifier.padding(14.dp)) {
            Text(
                "$value",
                style = MaterialTheme.typography.headlineMedium,
                fontWeight = FontWeight.Bold
            )
            Text(
                label,
                style = MaterialTheme.typography.labelMedium,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
        }
    }
}

@Composable
fun CalendarCard(
    viewMonth: YearMonth,
    state: AppState,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    onDayTap: (String) -> Unit
) {
    val monthNames = listOf("Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro")
    val today = todayStr()
    val startWeekday = viewMonth.atDay(1).dayOfWeek.value % 7
    val daysInMonth = viewMonth.lengthOfMonth()

    Card(
        modifier = Modifier.fillMaxWidth(),
        colors = CardDefaults.cardColors(
            containerColor = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f)
        ),
        shape = RoundedCornerShape(24.dp)
    ) {
        Column(Modifier.padding(16.dp)) {
            Row(
                modifier = Modifier.fillMaxWidth(),
                horizontalArrangement = Arrangement.SpaceBetween,
                verticalAlignment = Alignment.CenterVertically
            ) {
                FilledTonalIconButton(onClick = onPrev) {
                    Icon(Icons.Default.KeyboardArrowLeft, contentDescription = "Mês anterior")
                }
                Text(
                    "${monthNames[viewMonth.monthValue - 1]} ${viewMonth.year}",
                    style = MaterialTheme.typography.titleMedium,
                    fontWeight = FontWeight.SemiBold
                )
                FilledTonalIconButton(onClick = onNext) {
                    Icon(Icons.Default.KeyboardArrowRight, contentDescription = "Próximo mês")
                }
            }
            Spacer(Modifier.height(12.dp))
            Row(Modifier.fillMaxWidth()) {
                listOf("D","S","T","Q","Q","S","S").forEach { d ->
                    Text(
                        d,
                        Modifier.weight(1f),
                        textAlign = TextAlign.Center,
                        style = MaterialTheme.typography.labelMedium,
                        color = MaterialTheme.colorScheme.onSurfaceVariant,
                        fontWeight = FontWeight.SemiBold
                    )
                }
            }
            Spacer(Modifier.height(8.dp))
            val totalCells = startWeekday + daysInMonth
            val weeks = (totalCells + 6) / 7
            for (w in 0 until weeks) {
                Row(Modifier.fillMaxWidth()) {
                    for (d in 0 until 7) {
                        val idx = w * 7 + d
                        if (idx < startWeekday || idx >= startWeekday + daysInMonth) {
                            Spacer(Modifier.weight(1f))
                        } else {
                            val day = idx - startWeekday + 1
                            val dayStr = viewMonth.atDay(day).toString()
                            DayCell(
                                day = day,
                                dayStr = dayStr,
                                state = state,
                                isToday = dayStr == today,
                                onTap = { onDayTap(dayStr) },
                                modifier = Modifier.weight(1f)
                            )
                        }
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(
    day: Int,
    dayStr: String,
    state: AppState,
    isToday: Boolean,
    onTap: () -> Unit,
    modifier: Modifier = Modifier
) {
    val today = todayStr()
    val isFuture = dayStr > today
    val isBefore = dayStr < state.trackingStart
    val isRelapse = dayStr in state.relapses
    val isClean = !isFuture && !isBefore && !isRelapse
    val interactive = !isFuture && !isBefore

    val containerColor = when {
        isFuture || isBefore -> Color.Transparent
        isRelapse -> MaterialTheme.colorScheme.errorContainer
        isClean -> MaterialTheme.colorScheme.tertiaryContainer
        else -> Color.Transparent
    }
    val contentColor = when {
        isFuture || isBefore -> MaterialTheme.colorScheme.onSurfaceVariant.copy(alpha = 0.35f)
        isRelapse -> MaterialTheme.colorScheme.onErrorContainer
        isClean -> MaterialTheme.colorScheme.onTertiaryContainer
        else -> MaterialTheme.colorScheme.onSurface
    }

    Box(
        modifier = modifier.aspectRatio(1f).padding(2.dp),
        contentAlignment = Alignment.Center
    ) {
        Surface(
            onClick = onTap,
            enabled = interactive,
            shape = CircleShape,
            color = containerColor,
            contentColor = contentColor,
            border = if (isToday) BorderStroke(2.dp, MaterialTheme.colorScheme.primary) else null,
            modifier = Modifier.fillMaxSize().padding(2.dp)
        ) {
            Box(contentAlignment = Alignment.Center) {
                Text(
                    "$day",
                    style = MaterialTheme.typography.bodyMedium,
                    fontWeight = if (isRelapse || isClean) FontWeight.Bold else FontWeight.Normal
                )
            }
        }
    }
}

@Composable
fun Legend() {
    Row(
        modifier = Modifier.fillMaxWidth(),
        horizontalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterHorizontally)
    ) {
        LegendChip("Limpo", MaterialTheme.colorScheme.tertiaryContainer)
        LegendChip("Recaída", MaterialTheme.colorScheme.errorContainer)
        LegendChip("Futuro", MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.4f))
    }
}

@Composable
fun LegendChip(label: String, color: Color) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(14.dp).background(color, CircleShape))
        Spacer(Modifier.width(6.dp))
        Text(
            label,
            style = MaterialTheme.typography.labelMedium,
            color = MaterialTheme.colorScheme.onSurfaceVariant
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun DebugSheet(
    state: AppState,
    onDismiss: () -> Unit,
    onExport: () -> Unit,
    onImport: () -> Unit,
    onChangeStart: () -> Unit,
    onReset: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    ModalBottomSheet(onDismissRequest = onDismiss, sheetState = sheetState) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .verticalScroll(rememberScrollState())
                .padding(horizontal = 24.dp)
                .padding(bottom = 32.dp),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            Text("Debug", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)

            OutlinedCard(onClick = onChangeStart, modifier = Modifier.fillMaxWidth()) {
                Row(
                    Modifier.fillMaxWidth().padding(16.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Icon(Icons.Default.CalendarMonth, contentDescription = null)
                    Spacer(Modifier.width(16.dp))
                    Column(Modifier.weight(1f)) {
                        Text("Início do tracking", style = MaterialTheme.typography.titleSmall)
                        Text(
                            formatBR(state.trackingStart),
                            style = MaterialTheme.typography.bodyMedium,
                            color = MaterialTheme.colorScheme.onSurfaceVariant
                        )
                    }
                }
            }

            Text(
                "Estado atual",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Surface(
                color = MaterialTheme.colorScheme.surfaceVariant.copy(alpha = 0.5f),
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier.fillMaxWidth()
            ) {
                Text(
                    """
                    currentStreak: ${currentStreak(state)}
                    bestStreak: ${bestStreak(state)}
                    totalCleanDays: ${totalClean(state)}
                    relapses: ${state.relapses.size}
                    """.trimIndent(),
                    modifier = Modifier.padding(16.dp),
                    fontFamily = FontFamily.Monospace,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.onSurfaceVariant
                )
            }

            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                OutlinedButton(onClick = onExport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileDownload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Exportar")
                }
                OutlinedButton(onClick = onImport, modifier = Modifier.weight(1f)) {
                    Icon(Icons.Default.FileUpload, contentDescription = null, modifier = Modifier.size(18.dp))
                    Spacer(Modifier.width(8.dp))
                    Text("Importar")
                }
            }

            Button(
                onClick = onReset,
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(
                    containerColor = MaterialTheme.colorScheme.errorContainer,
                    contentColor = MaterialTheme.colorScheme.onErrorContainer
                )
            ) {
                Icon(Icons.Default.Delete, contentDescription = null, modifier = Modifier.size(18.dp))
                Spacer(Modifier.width(8.dp))
                Text("Apagar todos os dados")
            }
        }
    }
}
