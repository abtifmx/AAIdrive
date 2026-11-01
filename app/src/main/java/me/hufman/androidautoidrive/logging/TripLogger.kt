package me.hufman.androidautoidrive.logging

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import android.util.Log
import io.bimmergestalt.idriveconnectkit.CDSProperty
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.cds.CDSEventHandler
import java.io.File
import java.util.concurrent.ArrayBlockingQueue
import java.util.concurrent.ThreadPoolExecutor
import java.util.concurrent.TimeUnit
import java.util.concurrent.RejectedExecutionException

class TripLogger(context: Context) {
    companion object {
        const val ENABLED = "enabled"
        @Volatile var activeId: String? = null
            private set
        @Volatile var error: String? = null
            private set
        fun preferences(context: Context) = context.getSharedPreferences("trip_logging", Context.MODE_PRIVATE)
        fun store(context: Context) = TripLogStore(File(context.filesDir, "trip_logs"))
        // Vehicle telemetry only: no calls, contacts, VIN or GPS coordinates.
        val properties = listOf(1,3,4,5,6,7,8,9,13,15,16,18,20,21,22,23,24,25,26,27,
            28,29,30,31,32,34,35,36,37,38,39,40,41,42,43,44,45,46,47,50,51,52,53,
            61,62,68,70,71,72,73,74,76,77,78,79,81,85,86,94,96,97,98,99,100,104,108,109,110)
            .mapNotNull { CDSProperty.fromIdent(it.toString()) }
    }
    private val preferences = preferences(context)
    private val store by lazy { store(context) }
    private val worker = ThreadPoolExecutor(1, 1, 0, TimeUnit.MILLISECONDS, ArrayBlockingQueue<Runnable>(2048))
    private var connected = false
    private var closed = false
    private class Session(var id: String? = null, @Volatile var failed: Boolean = false)
    private var session: Session? = null
    private var listener: CDSEventHandler? = null
    private val settingListener = SharedPreferences.OnSharedPreferenceChangeListener { _, key ->
        if (key == ENABLED) setConnected(connected)
    }
    init { preferences.registerOnSharedPreferenceChangeListener(settingListener) }

    @Synchronized fun setConnected(value: Boolean) {
        if (closed) return
        connected = value
        if (value && preferences.getBoolean(ENABLED, true)) start() else stop("beendet")
    }

    private fun work(session: Session, task: () -> Unit) {
        if (session.failed) return
        try { worker.execute {
            if (!session.failed) try { task() } catch (e: Exception) {
                session.failed = true
                error = "Aufzeichnung fehlgeschlagen: ${e.localizedMessage}"
                activeId = null
                Log.e("TripLogger", "Unable to persist trip data", e)
            }
        } } catch (e: RejectedExecutionException) {
            session.failed = true
            error = "Aufzeichnung gestoppt: Schreibpuffer voll"
            activeId = null
            Log.e("TripLogger", "Trip writer queue is full", e)
        }
    }

    private fun start() {
        if (session != null) return
        val next = Session()
        session = next
        error = null
        val started = System.currentTimeMillis()
        work(next) { next.id = store.start(started); activeId = next.id }
        val lastSamples = hashMapOf<CDSProperty, Long>()
        var subscribing = true
        val callback = CDSEventHandler { property, value ->
            synchronized(this) {
                val elapsed = SystemClock.elapsedRealtime()
                if (session === next && elapsed - (lastSamples[property] ?: -1000L) >= 1000) {
                    val cached = subscribing
                    if (!cached) lastSamples[property] = elapsed
                    val timestamp = System.currentTimeMillis()
                    val snapshot = value.deepCopy()
                    work(next) { store.sample(next.id!!, timestamp, elapsed, property.propertyName, snapshot, cached) }
                }
            }
        }
        listener = callback
        properties.forEach { CarInformation.cdsData.addEventHandler(it, 1000, callback) }
        subscribing = false
    }

    private fun stop(reason: String) {
        val previous = session ?: return
        val callback = listener
        if (callback != null) properties.forEach { CarInformation.cdsData.removeEventHandler(it, callback) }
        listener = null
        session = null
        val ended = System.currentTimeMillis()
        work(previous) {
            store.finish(previous.id!!, ended, reason)
            if (activeId == previous.id) activeId = null
        }
    }

    @Synchronized fun close() {
        if (closed) return
        stop("beendet")
        closed = true
        preferences.unregisterOnSharedPreferenceChangeListener(settingListener)
        worker.shutdown() // queued writes and the final event are drained
    }
}
