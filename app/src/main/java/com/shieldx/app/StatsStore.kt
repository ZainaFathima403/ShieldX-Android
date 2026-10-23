package com.shieldx.app

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject
import java.util.UUID

data class ActivityEntry(val id: String, val label: String, val level: String, val time: String)

data class Stats(var threats: Int = 0, var scans: Int = 0, var warn: Int = 0, var score: Int = 100)

/** Simple SharedPreferences-backed store so dashboard stats survive an app restart. */
object StatsStore {
    private const val PREFS = "shieldx_stats"
    private const val KEY_STATS = "stats"
    private const val KEY_ACTIVITY = "activity"
    private const val MAX_ACTIVITY = 10

    fun getStats(context: Context): Stats {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_STATS, null) ?: return Stats()
        return try {
            val j = JSONObject(raw)
            Stats(j.optInt("threats", 0), j.optInt("scans", 0), j.optInt("warn", 0), j.optInt("score", 100))
        } catch (e: Exception) {
            Stats()
        }
    }

    fun getActivity(context: Context): List<ActivityEntry> {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val raw = prefs.getString(KEY_ACTIVITY, null) ?: return emptyList()
        return try {
            val arr = JSONArray(raw)
            (0 until arr.length()).map {
                val o = arr.getJSONObject(it)
                ActivityEntry(
                    o.optString("id", UUID.randomUUID().toString()),
                    o.getString("label"),
                    o.getString("level"),
                    o.getString("time")
                )
            }
        } catch (e: Exception) {
            emptyList()
        }
    }

    private fun saveActivity(context: Context, activity: List<ActivityEntry>) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val activityJson = JSONArray().apply {
            activity.forEach {
                put(JSONObject().put("id", it.id).put("label", it.label).put("level", it.level).put("time", it.time))
            }
        }
        prefs.edit().putString(KEY_ACTIVITY, activityJson.toString()).apply()
    }

    /** Records a scan result, updating stats and prepending to recent activity. */
    fun recordScan(context: Context, level: RiskLevel, label: String) {
        val prefs = context.getSharedPreferences(PREFS, Context.MODE_PRIVATE)
        val stats = getStats(context)
        stats.scans += 1
        when (level) {
            RiskLevel.DANGER -> { stats.threats += 1; stats.warn += 1; stats.score = maxOf(10, stats.score - 8) }
            RiskLevel.WARN -> { stats.warn += 1; stats.score = maxOf(30, stats.score - 3) }
            else -> { stats.score = minOf(100, stats.score + 1) }
        }

        val activity = getActivity(context).toMutableList()
        activity.add(0, ActivityEntry(
            UUID.randomUUID().toString(),
            label,
            level.name.lowercase(),
            java.text.SimpleDateFormat("hh:mm a").format(java.util.Date())
        ))
        while (activity.size > MAX_ACTIVITY) activity.removeAt(activity.size - 1)

        val statsJson = JSONObject().apply {
            put("threats", stats.threats); put("scans", stats.scans); put("warn", stats.warn); put("score", stats.score)
        }
        prefs.edit().putString(KEY_STATS, statsJson.toString()).apply()
        saveActivity(context, activity)
    }

    /** Removes a single activity entry by id. Stat counters (threats blocked, safety score, etc.)
     *  are left untouched — clearing the log is just tidying the list, not rewriting history. */
    fun removeActivityEntry(context: Context, id: String) {
        val activity = getActivity(context).filterNot { it.id == id }
        saveActivity(context, activity)
    }

    /** Clears the entire recent-activity log. Stat counters are left untouched. */
    fun clearActivity(context: Context) {
        saveActivity(context, emptyList())
    }
}
