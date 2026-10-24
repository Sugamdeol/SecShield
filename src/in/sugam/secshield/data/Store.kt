package `in`.sugam.secshield.data

import android.content.Context
import org.json.JSONArray
import org.json.JSONObject

/** Local only persistence: event log, trusted apps, toggles, score history. No network, ever. */
class Store(ctx: Context) {

    private val sp = ctx.applicationContext.getSharedPreferences(PREFS, Context.MODE_PRIVATE)

    data class Event(val ts: Long, val severity: Int, val title: String, val detail: String)
    data class ScoreRecord(val ts: Long, val score: Int, val band: String)

    fun log(severity: Int, title: String, detail: String) {
        try {
            val arr = readArray(KEY_EVENTS)
            val o = JSONObject()
            o.put("ts", System.currentTimeMillis())
            o.put("sev", severity)
            o.put("t", title)
            o.put("d", detail)
            arr.put(o)
            while (arr.length() > MAX_EVENTS) arr.remove(0)
            sp.edit().putString(KEY_EVENTS, arr.toString()).apply()
        } catch (ignored: Throwable) {
        }
    }

    fun events(): List<Event> {
        val out = ArrayList<Event>()
        try {
            val arr = readArray(KEY_EVENTS)
            for (i in 0 until arr.length()) {
                val o = arr.getJSONObject(i)
                out.add(Event(o.getLong("ts"), o.getInt("sev"), o.optString("t"), o.optString("d")))
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    fun clearEvents() {
        sp.edit().putString(KEY_EVENTS, "[]").apply()
    }

    /** Apps the user declared expected, so they stop being flagged. */
    fun trust(pkg: String) {
        try {
            val arr = readArray(KEY_TRUSTED)
            var found = false
            for (i in 0 until arr.length()) {
                if (pkg == arr.getString(i)) { found = true; break }
            }
            if (!found) {
                arr.put(pkg)
                sp.edit().putString(KEY_TRUSTED, arr.toString()).apply()
            }
        } catch (ignored: Throwable) {
        }
    }

    fun isTrusted(pkg: String): Boolean {
        try {
            val arr = readArray(KEY_TRUSTED)
            for (i in 0 until arr.length()) {
                if (pkg == arr.getString(i)) return true
            }
        } catch (ignored: Throwable) {
        }
        return false
    }

    /** Boolean settings (Guard toggles), stored as one JSON object. */
    fun toggle(key: String, def: Boolean): Boolean {
        try {
            return JSONObject(sp.getString(KEY_TOGGLES, "{}")).optBoolean(key, def)
        } catch (t: Throwable) {
            return def
        }
    }

    fun setToggle(key: String, value: Boolean) {
        try {
            val o = JSONObject(sp.getString(KEY_TOGGLES, "{}"))
            o.put(key, value)
            sp.edit().putString(KEY_TOGGLES, o.toString()).apply()
        } catch (ignored: Throwable) {
        }
    }

    /** Appends a scan to the score history (capped) and remembers it as the last score. */
    fun recordScore(score: Int, band: String) {
        try {
            val arr = readArray(KEY_HISTORY)
            val o = JSONObject()
            o.put("ts", System.currentTimeMillis())
            o.put("s", score)
            o.put("b", band)
            arr.put(o)
            while (arr.length() > MAX_HISTORY) arr.remove(0)
            sp.edit().putString(KEY_HISTORY, arr.toString()).apply()
        } catch (ignored: Throwable) {
        }
    }

    /** Score history, newest first. */
    fun history(): List<ScoreRecord> {
        val out = ArrayList<ScoreRecord>()
        try {
            val arr = readArray(KEY_HISTORY)
            for (i in arr.length() - 1 downTo 0) {
                val o = arr.getJSONObject(i)
                out.add(ScoreRecord(o.getLong("ts"), o.getInt("s"), o.optString("b")))
            }
        } catch (ignored: Throwable) {
        }
        return out
    }

    /** Most recent recorded score, or -1 when no scan has been recorded yet. */
    fun lastScore(): Int {
        val h = history()
        return if (h.isEmpty()) -1 else h[0].score
    }

    /** Most recent recorded band label, or "" when none exists. */
    fun lastBand(): String {
        val h = history()
        return if (h.isEmpty()) "" else h[0].band
    }

    private fun readArray(key: String): JSONArray {
        return JSONArray(sp.getString(key, "[]"))
    }

    companion object {
        private const val PREFS = "secshield"
        private const val KEY_EVENTS = "events"
        private const val KEY_TRUSTED = "trusted"
        private const val KEY_TOGGLES = "toggles"
        private const val KEY_HISTORY = "score_history"
        private const val MAX_EVENTS = 200
        private const val MAX_HISTORY = 30
    }
}
