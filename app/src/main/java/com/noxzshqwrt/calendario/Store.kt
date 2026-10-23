package com.noxzshqwrt.calendario

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.time.LocalDate

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
    fun toJson(s: AppState): String = JSONObject().apply {
        put("app", "nofap-cal")
        put("version", 1)
        put("exportedAt", java.time.Instant.now().toString())
        put("data", JSONObject().apply {
            put("trackingStart", s.trackingStart)
            put("relapses", JSONArray(s.relapses.toList()))
        })
    }.toString(2)
    fun fromJson(text: String): AppState? {
        return try {
            val parsed = JSONObject(text)
            val d = parsed.optJSONObject("data") ?: parsed
            val start = d.getString("trackingStart")
            val arr = d.getJSONArray("relapses")
            val set = mutableSetOf<String>()
            for (i in 0 until arr.length()) set.add(arr.getString(i))
            val ymdRegex = Regex("\\d{4}-\\d{2}-\\d{2}")
            if (!start.matches(ymdRegex)) return null
            if (set.any { !it.matches(ymdRegex) }) return null
            AppState(start, set)
        } catch (_: Exception) { null }
    }
}

fun todayStr(): String = LocalDate.now().toString()
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
