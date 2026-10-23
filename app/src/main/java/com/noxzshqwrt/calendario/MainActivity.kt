package com.noxzshqwrt.calendario

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.SystemBarStyle
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.border
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
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
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate
import java.time.YearMonth
import java.time.ZoneOffset

data class AppState(val trackingStart: String, val relapses: Set<String>)

class Store(ctx: Context) {
    private val prefs = ctx.getSharedPreferences("nofap_cal", Context.MODE_PRIVATE)
    fun load(): AppState {
        val start = prefs.getString("trackingStart", LocalDate.now().toString())
            ?: LocalDate.now().toString()
        val relapses = prefs.getStringSet("relapses", emptySet()) ?: emptySet()
        return AppState(start, relapses)
    }
    fun save(s: AppState) {
        prefs.edit()
            .putString("trackingStart", s.trackingStart)
            .putStringSet("relapses", s.relapses)
            .apply()
    }
}

fun todayStr() = LocalDate.now().toString()
fun daysBetween(a: String, b: String): Int =
    (LocalDate.parse(b).toEpochDay() - LocalDate.parse(a).toEpochDay()).toInt()
fun formatBR(s: String): String {
    val d = LocalDate.parse(s)
    return "%02d/%02d/%d".format(d.dayOfMonth, d.monthValue, d.year)
}
fun currentStreak(s: AppState): Int {
    var n = 0; var d = LocalDate.now()
    val start = LocalDate.parse(s.trackingStart)
    while (!d.isBefore(start)) {
        if (d.toString() in s.relapses) break
        n++; d = d.minusDays(1)
    }
    return n
}
fun bestStreak(s: AppState): Int {
    val start = LocalDate.parse(s.trackingStart)
    val today = LocalDate.now()
    var max = 0; var cur = 0; var d = start
    while (!d.isAfter(today)) {
        if (d.toString() in s.relapses) cur = 0
        else { cur++; if (cur > max) max = cur }
        d = d.plusDays(1)
    }
    return max
}
fun totalClean(s: AppState): Int {
    val start = LocalDate.parse(s.trackingStart)
    val today = LocalDate.now()
    var n = 0; var d = start
    while (!d.isAfter(today)) {
        if (d.toString() !in s.relapses) n++
        d = d.plusDays(1)
    }
    return n
}
fun lastRelapse(s: AppState): String? {
    val today = todayStr()
    return s.relapses.filter { it <= today }.maxOrNull()
}

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge(
            statusBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT),
            navigationBarStyle = SystemBarStyle.dark(android.graphics.Color.TRANSPARENT)
        )
        setContent {
            MaterialTheme(colorScheme = darkColorScheme()) {
                Surface(color = Color(0xFF0A0A0F)) { CalendarApp() }
            }
        }
    }
}

@Composable
fun CalendarApp() {
    val ctx = LocalContext.current
    val store = remember { Store(ctx) }
    var state by remember { mutableStateOf(store.load()) }
    var viewMonth by remember { mutableStateOf(YearMonth.now()) }
    var debugExpanded by remember { mutableStateOf(false) }
    val save = { newState: AppState -> state = newState; store.save(newState) }

    val importLauncher = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            try {
                val text = ctx.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }
                if (text != null) {
                    val parsed = JSONObject(text)
                    val d = parsed.optJSONObject("data") ?: parsed
                    val start = d.getString("trackingStart")
                    val arr = d.getJSONArray("relapses")
                    val set = mutableSetOf<String>()
                    for (i in 0 until arr.length()) set.add(arr.getString(i))
                    save(AppState(start, set))
                }
            } catch (_: Exception) {}
        }
    }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("application/json")) { uri ->
        if (uri != null) {
            try {
                val payload = JSONObject().apply {
                    put("app", "nofap-cal")
                    put("version", 1)
                    put("exportedAt", java.time.Instant.now().toString())
                    put("data", JSONObject().apply {
                        put("trackingStart", state.trackingStart)
                        put("relapses", JSONArray(state.relapses.toList()))
                    })
                }
                ctx.contentResolver.openOutputStream(uri)?.bufferedWriter()?.use { it.write(payload.toString(2)) }
            } catch (_: Exception) {}
        }
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .verticalScroll(rememberScrollState())
            .padding(16.dp)
    ) {
        Row(
            modifier = Modifier.fillMaxWidth().padding(bottom = 16.dp),
            horizontalArrangement = Arrangement.SpaceBetween,
            verticalAlignment = Alignment.CenterVertically
        ) {
            Text("Calendário", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Color(0xFFF2F2F7))
            TextButton(onClick = { viewMonth = YearMonth.now() }) {
                Text("hoje", color = Color(0xFFF2F2F7))
            }
        }

        val cs = currentStreak(state)
        val lr = lastRelapse(state)

        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF14141C), RoundedCornerShape(16.dp))
                .padding(22.dp),
            horizontalAlignment = Alignment.CenterHorizontally
        ) {
            Text(
                "$cs", fontSize = 58.sp, fontWeight = FontWeight.ExtraBold,
                color = if (cs == 0) Color(0xFFFF4757) else Color(0xFF2ED573)
            )
            Text(
                if (cs == 1) "dia de streak" else "dias de streak",
                fontSize = 12.sp, color = Color(0xFF7A7A8C), fontWeight = FontWeight.SemiBold
            )
            Spacer(Modifier.height(10.dp))
            Text(
                if (lr == null) "nenhuma recaída registrada" else {
                    val ago = daysBetween(lr, todayStr())
                    val txt = when (ago) { 0 -> "hoje"; 1 -> "ontem"; else -> "há $ago dias" }
                    "última recaída: ${formatBR(lr)} ($txt)"
                },
                fontSize = 12.sp, color = Color(0xFF7A7A8C)
            )
        }

        Spacer(Modifier.height(16.dp))
        CalendarCard(viewMonth, { viewMonth = viewMonth.minusMonths(1) },
            { viewMonth = viewMonth.plusMonths(1) }, state) { day ->
            val newSet = if (day in state.relapses) state.relapses - day else state.relapses + day
            save(state.copy(relapses = newSet))
        }
        Spacer(Modifier.height(16.dp))

        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.Center) {
            LegendDot(Color(0xFF2ED573), "limpo"); Spacer(Modifier.width(18.dp))
            LegendDot(Color(0xFFFF4757), "recaída"); Spacer(Modifier.width(18.dp))
            LegendDot(Color(0xFF7A7A8C), "futuro")
        }
        Spacer(Modifier.height(16.dp))

        Column(
            modifier = Modifier.fillMaxWidth()
                .background(Color(0xFF14141C), RoundedCornerShape(12.dp))
                .padding(14.dp)
        ) {
            Text(
                "debug", color = Color(0xFF7A7A8C), fontSize = 11.sp,
                fontWeight = FontWeight.SemiBold,
                modifier = Modifier.clickable { debugExpanded = !debugExpanded }
            )
            if (debugExpanded) {
                Spacer(Modifier.height(12.dp))
                Text(
                    JSONObject().apply {
                        put("trackingStart", state.trackingStart)
                        put("relapses", JSONArray(state.relapses.toList()))
                        put("currentStreak", cs)
                        put("bestStreak", bestStreak(state))
                        put("totalCleanDays", totalClean(state))
                    }.toString(2),
                    fontSize = 11.sp, color = Color(0xFF7A7A8C), fontFamily = FontFamily.Monospace
                )
                Spacer(Modifier.height(12.dp))
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedButton(onClick = {
                        exportLauncher.launch("nofap-cal-${todayStr().replace("-", "")}.json")
                    }, modifier = Modifier.weight(1f)) {
                        Text("exportar", fontSize = 12.sp, color = Color(0xFFF2F2F7))
                    }
                    OutlinedButton(onClick = {
                        importLauncher.launch(arrayOf("application/json"))
                    }, modifier = Modifier.weight(1f)) {
                        Text("importar", fontSize = 12.sp, color = Color(0xFFF2F2F7))
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = {
                    save(AppState(todayStr(), emptySet()))
                }, modifier = Modifier.fillMaxWidth()) {
                    Text("reset tudo", fontSize = 12.sp, color = Color(0xFFFF4757))
                }
            }
        }
    }
}

@Composable
fun CalendarCard(
    viewMonth: YearMonth,
    onPrev: () -> Unit,
    onNext: () -> Unit,
    state: AppState,
    onToggle: (String) -> Unit,
) {
    val monthNames = listOf("Janeiro","Fevereiro","Março","Abril","Maio","Junho","Julho","Agosto","Setembro","Outubro","Novembro","Dezembro")
    val today = todayStr()
    val firstDay = viewMonth.atDay(1)
    val startWeekday = firstDay.dayOfWeek.value % 7
    val daysInMonth = viewMonth.lengthOfMonth()

    Column(
        modifier = Modifier.fillMaxWidth()
            .background(Color(0xFF14141C), RoundedCornerShape(16.dp))
            .padding(14.dp)
    ) {
        Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
            IconButton(onClick = onPrev) { Text("‹", fontSize = 26.sp, color = Color(0xFFF2F2F7)) }
            Text("${monthNames[viewMonth.monthValue - 1]} ${viewMonth.year}", fontSize = 15.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFFF2F2F7))
            IconButton(onClick = onNext) { Text("›", fontSize = 26.sp, color = Color(0xFFF2F2F7)) }
        }
        Row(Modifier.fillMaxWidth().padding(vertical = 6.dp)) {
            listOf("D","S","T","Q","Q","S","S").forEach { d ->
                Text(d, Modifier.weight(1f), textAlign = TextAlign.Center, fontSize = 11.sp,
                    color = Color(0xFF7A7A8C), fontWeight = FontWeight.SemiBold)
            }
        }

        val cells = mutableListOf<Pair<Int, String?>>()
        for (i in 0 until startWeekday) cells.add(-1 to null)
        for (d in 1..daysInMonth) cells.add(d to viewMonth.atDay(d).toString())

        var idx = 0
        while (idx < cells.size) {
            Row(Modifier.fillMaxWidth()) {
                for (j in 0 until 7) {
                    if (idx >= cells.size) { Spacer(Modifier.weight(1f)) }
                    else {
                        val (dayNum, dayStr) = cells[idx]
                        if (dayNum == -1) Spacer(Modifier.weight(1f))
                        else DayCell(dayNum, dayStr!!, state, dayStr == today, onToggle, Modifier.weight(1f))
                        idx++
                    }
                }
            }
        }
    }
}

@Composable
fun DayCell(day: Int, dayStr: String, state: AppState, isToday: Boolean, onToggle: (String) -> Unit, modifier: Modifier = Modifier) {
    val today = todayStr()
    val isFuture = dayStr > today
    val isBefore = dayStr < state.trackingStart
    val isRelapse = dayStr in state.relapses
    val isClean = !isFuture && !isBefore && !isRelapse

    val bg = when {
        isFuture || isBefore -> Color.Transparent
        isRelapse -> Color(0x38FF4757)
        isClean -> Color(0x292ED573)
        else -> Color.Transparent
    }
    val fg = when {
        isFuture || isBefore -> Color(0xFF7A7A8C).copy(alpha = 0.35f)
        isRelapse -> Color(0xFFFF4757)
        isClean -> Color(0xFF2ED573)
        else -> Color(0xFFF2F2F7)
    }
    Box(
        modifier = modifier
            .aspectRatio(1f)
            .padding(2.dp)
            .background(bg, RoundedCornerShape(10.dp))
            .then(if (isToday) Modifier.border(2.dp, Color(0xFFFFA502), RoundedCornerShape(10.dp)) else Modifier)
            .clickable(enabled = !isFuture && !isBefore) { onToggle(dayStr) },
        contentAlignment = Alignment.Center
    ) {
        Text("$day", color = fg, fontSize = 14.sp,
            fontWeight = if (isRelapse || isClean) FontWeight.Bold else FontWeight.Normal)
    }
}

@Composable
fun LegendDot(color: Color, label: String) {
    Row(verticalAlignment = Alignment.CenterVertically) {
        Box(Modifier.size(11.dp).background(color, RoundedCornerShape(4.dp)))
        Spacer(Modifier.width(6.dp))
        Text(label, fontSize = 12.sp, color = Color(0xFF7A7A8C))
    }
}
