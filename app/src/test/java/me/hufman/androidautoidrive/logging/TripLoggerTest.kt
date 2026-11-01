package me.hufman.androidautoidrive.logging

import android.content.Context
import android.content.SharedPreferences
import android.os.SystemClock
import com.google.gson.JsonParser
import io.bimmergestalt.idriveconnectkit.CDS
import me.hufman.androidautoidrive.CarInformation
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder
import org.mockito.Mockito
import org.mockito.kotlin.*
import java.util.concurrent.ExecutorService
import java.util.concurrent.TimeUnit

class TripLoggerTest {
    @get:Rule val temporary = TemporaryFolder()

    @Test fun recordsOnlyWhileConnectedThrottlesUpdatesAndClosesTrips() {
        val directory = temporary.newFolder()
        val preferences = mock<SharedPreferences> {
            on { getBoolean(TripLogger.ENABLED, true) } doReturn true
        }
        val context = mock<Context> {
            on { filesDir } doReturn directory
            on { getSharedPreferences("trip_logging", Context.MODE_PRIVATE) } doReturn preferences
        }
        CarInformation.cdsData.clear()
        var elapsed = 1000L
        Mockito.mockStatic(SystemClock::class.java).use { clock ->
            clock.`when`<Long> { SystemClock.elapsedRealtime() }.thenAnswer { elapsed }
            val logger = TripLogger(context)
            fun speed(value: Int) = CarInformation.cdsData.onPropertyChangedEvent(CDS.DRIVING.SPEEDACTUAL,
                JsonParser.parseString("""{"speedActual":$value}""").asJsonObject)
            speed(5) // disconnected: must not create a trip
            logger.setConnected(true)
            logger.setConnected(true) // duplicate notifications must not split a trip
            speed(10)
            elapsed = 1500; speed(20) // suppressed, another listener may request faster CDS updates
            elapsed = 2000; speed(30)
            logger.setConnected(false)
            speed(40) // disconnected: no sample
            logger.setConnected(true)
            elapsed = 3000; speed(50)
            logger.close()
            val workerField = TripLogger::class.java.getDeclaredField("worker").apply { isAccessible = true }
            assertTrue((workerField.get(logger) as ExecutorService).awaitTermination(5, TimeUnit.SECONDS))
        }
        val store = TripLogger.store(context)
        val trips = store.trips()
        assertEquals(2, trips.size)
        assertEquals(listOf(1L, 2L), trips.map { it.count }.sorted())
        assertTrue(trips.all { it.state == "beendet" })
        val speeds = trips.flatMap { store.page(it.id, 0) }.map {
            it.getAsJsonObject("value").get("speedActual").asInt
        }.sorted()
        assertEquals(listOf(10, 30, 50), speeds)
        assertNull(TripLogger.activeId)
        verify(preferences).unregisterOnSharedPreferenceChangeListener(any())
    }
}
