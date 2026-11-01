package me.hufman.androidautoidrive.logging

import com.google.gson.Gson
import com.google.gson.JsonObject
import com.google.gson.JsonParser
import java.io.File
import java.util.UUID

/** Append-only, flushed events survive process death; no network or external storage. */
class TripLogStore(private val directory: File) {
    data class Trip(val id: String, val started: Long, val ended: Long?, val state: String,
                    val count: Long, val bytes: Long, val lastTimestamp: Long = ended ?: started) {
        fun durationMillis(now: Long = System.currentTimeMillis()): Long =
            maxOf(0L, (if (state == "läuft") now else ended ?: lastTimestamp) - started)
    }
    private val gson = Gson()
    init { require(directory.isDirectory || directory.mkdirs()) }

    private fun file(id: String): File {
        require(id.matches(Regex("[0-9a-f-]+")))
        return File(directory, "$id.jsonl")
    }

    @Synchronized fun start(time: Long): String {
        val id = UUID.randomUUID().toString()
        append(id, JsonObject().apply {
            addProperty("type", "start"); addProperty("timestamp", time)
            addProperty("formatVersion", 1)
        })
        return id
    }

    @Synchronized fun sample(id: String, time: Long, elapsed: Long, property: String, value: JsonObject, cached: Boolean = false) {
        append(id, JsonObject().apply {
            addProperty("type", "sample"); addProperty("timestamp", time)
            addProperty("elapsedRealtime", elapsed); addProperty("property", property)
            addProperty("cached", cached)
            add("value", value.deepCopy())
        })
    }

    @Synchronized fun finish(id: String, time: Long, reason: String) {
        append(id, JsonObject().apply {
            addProperty("type", "end"); addProperty("timestamp", time); addProperty("reason", reason)
        })
    }

    private fun append(id: String, value: JsonObject) {
        // Closing each append flushes Java buffers, at most once per CDS update.
        file(id).appendText(gson.toJson(value) + "\n", Charsets.UTF_8)
    }

    fun source(id: String): File = file(id)

    fun events(id: String): Sequence<JsonObject> = sequence {
        file(id).bufferedReader().use { reader ->
            while (true) {
                val line = reader.readLine() ?: break
                // An interrupted final write must not make the rest of a trip unreadable.
                val event = runCatching { JsonParser.parseString(line).asJsonObject }.getOrNull()
                if (event != null) yield(event)
            }
        }
    }

    fun trips(activeId: String? = null): List<Trip> = directory.listFiles().orEmpty()
        .filter { it.extension == "jsonl" }.mapNotNull { source ->
            runCatching {
                var start = 0L; var end: Long? = null; var count = 0L; var reason = "unterbrochen"; var last = 0L
                events(source.nameWithoutExtension).forEach {
                    last = maxOf(last, it.get("timestamp")?.asLong ?: 0L)
                    when (it.get("type")?.asString) {
                        "start" -> start = it.get("timestamp").asLong
                        "sample" -> count++
                        "end" -> { end = it.get("timestamp").asLong; reason = it.get("reason").asString }
                    }
                }
                require(start > 0) { "Unvollständiger Fahrtbeginn" }
                Trip(source.nameWithoutExtension, start, end,
                    if (source.nameWithoutExtension == activeId) "läuft" else reason, count, source.length(), last)
            }.getOrNull()
        }.sortedByDescending { it.started }

    fun page(id: String, offset: Int, limit: Int = 100): List<JsonObject> {
        require(offset >= 0 && limit in 1..1000)
        val result = arrayListOf<JsonObject>()
        var index = 0
        // Always consume the sequence so its reader closes, including on short pages.
        events(id).forEach {
            if (it.get("type")?.asString == "sample") {
                if (index >= offset && result.size < limit) result.add(it)
                index++
            }
        }
        return result
    }

    fun latest(id: String): Map<String, JsonObject> {
        val result = linkedMapOf<String, JsonObject>()
        events(id).filter { it.get("type")?.asString == "sample" }.forEach {
            result[it.get("property").asString] = it
        }
        return result
    }

    @Synchronized fun delete(id: String) { check(file(id).delete()) { "Fahrt konnte nicht gelöscht werden" } }
}
