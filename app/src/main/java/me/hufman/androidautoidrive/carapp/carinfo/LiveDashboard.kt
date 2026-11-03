package me.hufman.androidautoidrive.carapp.carinfo

import com.google.gson.JsonObject
import io.bimmergestalt.idriveconnectkit.CDS
import io.bimmergestalt.idriveconnectkit.CDSProperty
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import java.util.Locale

/** Live-only CDS values; PDC distances are displayed in centimetres. */
class LiveDashboard(metrics: CDSMetrics) {
    companion object {
        const val TITLE = "430d Live"

        internal fun number(json: JsonObject, vararg path: String): Double? = runCatching {
            var value: com.google.gson.JsonElement = json
            for (key in path) value = value.asJsonObject.get(key) ?: return null
            value.asDouble.takeIf { it.isFinite() }
        }.getOrNull()

        internal fun format(value: Double?, unit: String, decimals: Int = 0): String =
            value?.let { String.format(Locale.GERMAN, "%.${decimals}f%s", it, unit) } ?: "--"
    }

    private val data = metrics.carInfo.cdsData

    private fun field(property: CDSProperty, label: String, unit: String = "", decimals: Int = 0,
                      valid: (Double) -> Boolean = { true }, vararg path: String): Flow<String> =
        data.flow[property].map { json ->
            "$label: ${format(number(json, *path)?.takeIf(valid), unit, decimals)}"
        }.onStart { emit("$label: --") }.distinctUntilChanged()

    // The existing iDrive view renders pairs as a scrollable two-column table.
    // Explicit units avoid waiting for the vehicle.units subscription.
    val fields: List<Flow<String>> = listOf(
        field(CDS.ENGINE.TEMPERATURE, "Motor", " °C", valid = { it in -50.0..254.0 }, path = arrayOf("temperature", "engine")),
        field(CDS.ENGINE.TEMPERATURE, "Öl", " °C", valid = { it in -50.0..254.0 }, path = arrayOf("temperature", "oil")),
        field(CDS.ENGINE.TORQUE, "Drehmoment", " Nm", valid = { it in 0.0..2000.0 }, path = arrayOf("torque")),
        metrics.drivingGearName.map { "Gang: $it" }.onStart { emit("Gang: --") },
        field(CDS.SENSORS.FUEL, "Tank", " l", 1, { it in 0.0..200.0 }, "fuel", "tanklevel"),
        field(CDS.SENSORS.FUEL, "Reichweite", " km", valid = { it in 0.0..5000.0 }, path = arrayOf("fuel", "range")),
        field(CDS.DRIVING.SPEEDACTUAL, "Tempo Ist", " km/h", valid = { it in 0.0..400.0 }, path = arrayOf("speedActual")),
        field(CDS.DRIVING.ACCELERATION, "Längs", " m/s²", 2, { it in -100.0..100.0 }, "acceleration", "longitudinal"),
        field(CDS.DRIVING.ACCELERATION, "Quer", " m/s²", 2, { it in -100.0..100.0 }, "acceleration", "lateral"),
        metrics.parkingBrakeSet.map { "Parkbremse: ${if (it) "angezogen" else "gelöst"}" }.onStart { emit("Parkbremse: --") },
        field(CDS.CONTROLS.CRUISECONTROL, "Tempomat Soll", " km/h", valid = { it in 0.0..400.0 }, path = arrayOf("cruiseControl", "desiredSpeed")),
        field(CDS.NAVIGATION.GPSEXTENDEDINFO, "Höhe", " m", valid = { it in -1000.0..10000.0 }, path = arrayOf("GPSExtendedInfo", "altitude")),
        field(CDS.SENSORS.TEMPERATUREEXTERIOR, "Außen", " °C", 1, { it in -80.0..100.0 }, "temperatureExterior"),
        field(CDS.SENSORS.TEMPERATUREINTERIOR, "Innen", " °C", 1, { it in -80.0..100.0 }, "temperatureInterior"),
        flowOf("Parksensoren"), flowOf("Siehe Kategorie 430d PDC")
    )
}
