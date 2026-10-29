package app.farwalker.data

import android.util.AtomicFile
import app.farwalker.domain.Continent
import app.farwalker.domain.Journey
import app.farwalker.domain.Leg
import app.farwalker.domain.Mode
import app.farwalker.domain.News
import app.farwalker.domain.Night
import app.farwalker.domain.Note
import app.farwalker.domain.Ride
import app.farwalker.domain.Stamp
import app.farwalker.domain.Visit
import org.json.JSONArray
import org.json.JSONObject
import java.io.File

/** The journey as one JSON file, written whole and atomically: it is small, and never half-saved. */
object Store {
    fun read(file: File): Journey {
        val f = AtomicFile(file)
        if (!file.exists()) return Journey()
        return runCatching { decode(JSONObject(String(f.readFully(), Charsets.UTF_8))) }.getOrElse { Journey() }
    }

    fun write(file: File, j: Journey) {
        val f = AtomicFile(file)
        val out = f.startWrite()
        try {
            out.write(encode(j).toString().toByteArray(Charsets.UTF_8))
            f.finishWrite(out)
        } catch (e: Exception) {
            f.failWrite(out)
            throw e
        }
    }

    fun encode(j: Journey): JSONObject = JSONObject().apply {
        put("home", j.home); put("town", j.town); put("dest", j.dest); put("at", j.at); put("iso", j.iso)
        put("coins", j.coins); put("walked", j.walked); put("rode", j.rode); put("sailed", j.sailed)
        put("start", j.start); put("lastValue", j.lastValue); put("lastAt", j.lastAt); put("lastDay", j.lastDay)
        put("legs", JSONArray().apply { j.legs.forEach { put(if (it.forward) it.edge else -it.edge - 1) } })
        j.ride?.let { r -> put("ride", JSONObject().put("mode", r.mode.name).put("legs", r.legs).put("km", r.km).put("start", r.start).put("done", r.done)) }
        put("days", JSONObject().apply { j.days.forEach { (d, n) -> put(d.toString(), n) } })
        put("dayKm", JSONObject().apply { j.dayKm.forEach { (d, k) -> put(d.toString(), k) } })
        put("stamps", JSONArray().apply { j.stamps.values.forEach { put(JSONArray().put(it.iso).put(it.day).put(it.town).put(it.entries)) } })
        put("continents", JSONObject().apply { j.continents.forEach { (c, d) -> put(c.name, d) } })
        put("capes", JSONObject().apply { j.capes.forEach { (t, d) -> put(t.toString(), d) } })
        put("trail", JSONArray().apply { j.trail.forEach { put(JSONArray().put(it.town).put(it.mode.ordinal).put(it.day)) } })
        put("nights", JSONArray().apply { j.nights.forEach { put(JSONArray().put(it.day).put(it.town).put(it.iso).put(it.km).put(it.mode.ordinal).put(it.next).put(it.at)) } })
        put("notes", JSONArray().apply { j.notes.forEach { put(JSONArray().put(it.day).put(it.news.name).put(it.ref).put(it.town)) } })
    }

    fun decode(o: JSONObject): Journey = Journey().apply {
        home = o.optInt("home", -1); town = o.optInt("town", -1); dest = o.optInt("dest", -1)
        at = o.optDouble("at", 0.0); iso = o.optString("iso")
        coins = o.optDouble("coins", 0.0); walked = o.optDouble("walked", 0.0)
        rode = o.optDouble("rode", 0.0); sailed = o.optDouble("sailed", 0.0)
        start = o.optLong("start", -1); lastValue = o.optLong("lastValue", -1); lastAt = o.optLong("lastAt", 0)
        lastDay = o.optLong("lastDay", -1)
        o.optJSONArray("legs")?.let { a -> for (i in 0 until a.length()) { val v = a.getInt(i); legs += if (v >= 0) Leg(v, true) else Leg(-v - 1, false) } }
        o.optJSONObject("ride")?.let { r ->
            ride = Ride(Mode.valueOf(r.getString("mode")), r.getInt("legs"), r.getDouble("km"), r.getLong("start"), r.optDouble("done", 0.0))
        }
        o.optJSONObject("days")?.let { d -> d.keys().forEach { days[it.toLong()] = d.getInt(it) } }
        o.optJSONObject("dayKm")?.let { d -> d.keys().forEach { dayKm[it.toLong()] = d.getDouble(it) } }
        o.optJSONArray("stamps")?.let { a -> for (i in 0 until a.length()) { val s = a.getJSONArray(i); stamps[s.getString(0)] = Stamp(s.getString(0), s.getLong(1), s.getInt(2), s.getInt(3)) } }
        o.optJSONObject("continents")?.let { d -> d.keys().forEach { k -> Continent.entries.firstOrNull { it.name == k }?.let { continents[it] = d.getLong(k) } } }
        o.optJSONObject("capes")?.let { d -> d.keys().forEach { capes[it.toInt()] = d.getLong(it) } }
        o.optJSONArray("trail")?.let { a -> for (i in 0 until a.length()) { val v = a.getJSONArray(i); trail += Visit(v.getInt(0), Mode.entries[v.getInt(1)], v.getLong(2)) } }
        o.optJSONArray("nights")?.let { a -> for (i in 0 until a.length()) { val v = a.getJSONArray(i); nights += Night(v.getLong(0), v.getInt(1), v.getString(2), v.getDouble(3), Mode.entries[v.getInt(4)], v.optInt(5, -1), v.optDouble(6, 0.0)) } }
        o.optJSONArray("notes")?.let { a ->
            for (i in 0 until a.length()) {
                val v = a.getJSONArray(i)
                News.entries.firstOrNull { it.name == v.getString(1) }?.let { notes += Note(v.getLong(0), it, v.getString(2), v.getInt(3)) }
            }
        }
    }
}
