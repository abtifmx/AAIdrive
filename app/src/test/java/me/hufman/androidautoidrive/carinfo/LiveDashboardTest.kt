package me.hufman.androidautoidrive.carinfo

import com.google.gson.JsonParser
import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.CarInformation
import me.hufman.androidautoidrive.carapp.carinfo.CarDetailedInfo
import me.hufman.androidautoidrive.carapp.carinfo.LiveDashboard
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.*
import org.junit.Assert.*
import org.junit.Test

class LiveDashboardTest {
    @Test fun invalidUpdatesClearDisplayedValuesAndPdcFiltersSpecialCodes() = runBlocking {
        suspend fun read(index: Int, property: io.bimmergestalt.idriveconnectkit.CDSProperty,
                         initial: String, update: String? = null): String {
            var callback: CDSEventHandler? = null
            val data = object : CDSData {
                override fun get(key: io.bimmergestalt.idriveconnectkit.CDSProperty) = null
                override fun addEventHandler(key: io.bimmergestalt.idriveconnectkit.CDSProperty, intervalLimit: Int, handler: CDSEventHandler) {
                    callback = handler
                    handler.onPropertyChangedEvent(property, JsonParser.parseString(initial).asJsonObject)
                }
                override fun removeEventHandler(key: io.bimmergestalt.idriveconnectkit.CDSProperty, handler: CDSEventHandler) {}
            }
            val car = object : CarInformation() { override val cdsData = data }
            return withTimeout(1000) {
                LiveDashboard(CDSMetrics(car)).fields[index].drop(1).onEach {
                    if (update != null) callback!!.onPropertyChangedEvent(property, JsonParser.parseString(update).asJsonObject)
                }.take(if (update == null) 1 else 2).toList().last()
            }
        }
        val warm = """{"temperature":{"engine":90}}"""
        assertEquals("Motor: 90 °C", read(0, CDS.ENGINE.TEMPERATURE, warm))
        assertEquals("Motor: --", read(0, CDS.ENGINE.TEMPERATURE, warm, """{"temperature":{"engine":255}}"""))
        assertEquals("Motor: --", read(0, CDS.ENGINE.TEMPERATURE, warm, """{"error":401}"""))
        assertEquals("Tank: 0,0 l", read(4, CDS.SENSORS.FUEL, """{"fuel":{"tanklevel":0}}"""))
        assertEquals("Höhe: --", read(11, CDS.NAVIGATION.GPSEXTENDEDINFO,
            """{"GPSExtendedInfo":{"altitude":120}}""", """{"GPSExtendedInfo":{"altitude":65530}}"""))
    }
    @Test fun parsesMissingMalformedAndNestedValues() {
        fun json(value: String) = JsonParser.parseString(value).asJsonObject
        assertEquals(92.5, LiveDashboard.number(json("""{"temperature":{"oil":92.5}}"""), "temperature", "oil")!!, 0.0)
        assertNull(LiveDashboard.number(json("""{"error":401}"""), "temperature", "oil"))
        assertNull(LiveDashboard.number(json("""{"temperature":null}"""), "temperature", "oil"))
        assertNull(LiveDashboard.number(json("""{"torque":"invalid"}"""), "torque"))
        assertEquals("--", LiveDashboard.format(null, " °C"))
        assertEquals("12,5 l", LiveDashboard.format(12.5, " l", 1))
    }

    @Test fun dashboardIsAvailableWithAndWithoutAdvancedSettings() = runBlocking {
        val info = CarDetailedInfo(emptyMap(), CDSMetrics(CarInformation()))
        val fields = info.basicCategories.getValue(LiveDashboard.TITLE)
        assertSame(fields, info.advancedCategories.getValue(LiveDashboard.TITLE))
        assertEquals(LiveDashboard.TITLE, info.category.value)   // start on the text page, the image pages are heavier for the car
        assertEquals(14, fields.size)
        // Every cell renders immediately even when the car provides no CDS data.
        withTimeout(1000) {
            fields.forEach { assertTrue((it.first() as String).isNotEmpty()) }
        }
    }
}
