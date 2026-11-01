package me.hufman.androidautoidrive.logging

import com.google.gson.JsonParser
import org.junit.Assert.*
import org.junit.Rule
import org.junit.Test
import org.junit.rules.TemporaryFolder

class TripLogStoreTest {
    @get:Rule val temporary = TemporaryFolder()
    private fun value(raw: String) = JsonParser.parseString(raw).asJsonObject

    @Test fun persistsTripsAcrossReopenAndPreservesRawTelemetry() {
        val directory = temporary.newFolder()
        val store = TripLogStore(directory)
        val id = store.start(1000)
        store.sample(id, 2000, 500, "engine.temperature", value("""{"temperature":{"engine":90,"oil":85}}"""))
        store.sample(id, 3000, 1500, "driving.drivingStyle", value("""{"accelerate":3,"brake":4,"shift":5}"""))
        store.finish(id, 4000, "beendet")
        val reopened = TripLogStore(directory)
        val trip = reopened.trips().single()
        assertEquals(id, trip.id); assertEquals(2L, trip.count)
        assertEquals(4000L, trip.ended); assertEquals("beendet", trip.state)
        assertEquals(500L, reopened.page(id, 0).first().get("elapsedRealtime").asLong)
        assertEquals(85, reopened.latest(id).getValue("engine.temperature").getAsJsonObject("value")
            .getAsJsonObject("temperature").get("oil").asInt)
    }

    @Test fun handlesInterruptedLastLineAndPagesWithoutMixingTrips() {
        val store = TripLogStore(temporary.newFolder())
        val id = store.start(1000)
        repeat(205) { store.sample(id, 2000L + it, it.toLong(), "driving.speedActual", value("""{"speedActual":$it}""")) }
        store.source(id).appendText("{broken-last-line")
        val other = store.start(9000)
        assertEquals("läuft", store.trips(id).first { it.id == id }.state)
        assertEquals("unterbrochen", store.trips().first { it.id == id }.state)
        assertEquals(205L, store.trips().first { it.id == id }.count)
        assertEquals(100, store.page(id, 0).size)
        assertEquals(100, store.page(id, 100).size)
        assertEquals(5, store.page(id, 200).size)
        assertEquals(0, store.page(other, 0).size)
        store.delete(id)
        assertEquals(other, store.trips().single().id)
    }

    @Test fun rejectsPathTraversal() {
        val store = TripLogStore(temporary.newFolder())
        try { store.source("../outside"); fail("Invalid trip id accepted") }
        catch (expected: IllegalArgumentException) { }
    }
}
