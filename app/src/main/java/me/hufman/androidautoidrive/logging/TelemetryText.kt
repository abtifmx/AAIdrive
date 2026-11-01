package me.hufman.androidautoidrive.logging

import com.google.gson.JsonElement
import com.google.gson.JsonObject

object TelemetryText {
    private val names = mapOf(
        "engine.temperature" to "Motortemperaturen", "engine.torque" to "Drehmoment (Nm)",
        "engine.RPMSpeed" to "Drehzahl (1/min)", "engine.consumption" to "Motorverbrauch (Rohwert)",
        "engine.info" to "Motordaten", "engine.status" to "Motorstatus (Code)",
        "driving.acceleration" to "Beschleunigung (m/s²)", "driving.acceleratorPedal" to "Gaspedal (%)",
        "driving.averageConsumption" to "Durchschnittsverbrauch", "driving.averageSpeed" to "Durchschnittstempo",
        "driving.brakeContact" to "Bremse (Code)", "driving.clutchPedal" to "Kupplung (Code)",
        "driving.DSCActive" to "DSC-Eingriff (Rohwert)", "driving.gear" to "Gang (Code)",
        "driving.mode" to "Fahrmodus (Code)", "driving.keyPosition" to "Zündung / Motorlauf",
        "driving.odometer" to "Kilometerstand (km)", "driving.parkingBrake" to "Parkbremse (Code)",
        "driving.speedActual" to "Geschwindigkeit Ist (km/h)", "driving.speedDisplayed" to "Geschwindigkeit Tacho (km/h)",
        "driving.steeringWheel" to "Lenkrad", "driving.drivingStyle" to "BMW-Fahrstilwerte",
        "driving.ecoRangeWon" to "ECO-Reichweitengewinn (km)", "driving.ecoRange" to "ECO-Reichweite (km)",
        "driving.ecoTip" to "ECO-Hinweis (Code)", "driving.FDRControl" to "Fahrdynamik (Code)",
        "driving.shiftIndicator" to "Schaltempfehlung (Code)",
        "sensors.fuel" to "Tank / Reichweite", "sensors.battery" to "Batterieladestand (%)",
        "sensors.batteryTemp" to "Batterietemperatur (°C)",
        "sensors.temperatureInterior" to "Innenraumtemperatur (°C)",
        "sensors.temperatureExterior" to "Außentemperatur (°C)",
        "sensors.PDCRangeFront" to "PDC vorn (Rohwerte)", "sensors.PDCRangeRear" to "PDC hinten (Rohwerte)",
        "sensors.PDCStatus" to "PDC-Status", "sensors.doors" to "Türen (Codes)",
        "sensors.seatOccupiedPassenger" to "Beifahrersitz belegt (Code)", "sensors.seatbelt" to "Sicherheitsgurte (Codes)",
        "sensors.trunk" to "Kofferraum (Code)", "sensors.lid" to "Motorhaube (Code)",
        "navigation.GPSExtendedInfo" to "GPS-Höhe / Richtung (Rohdaten)",
        "controls.cruiseControl" to "Tempomat", "controls.startStopStatus" to "Start-Stopp (Code)",
        "controls.startStopLEDs" to "Start-Stopp-Anzeige (Code)", "controls.lights" to "Lichtstatus",
        "controls.headlights" to "Scheinwerfer (Code)", "controls.turnSignal" to "Blinker (Code)",
        "controls.windshieldWiper" to "Scheibenwischer (Code)", "controls.defrostRear" to "Heckscheibenheizung",
        "controls.sunroof" to "Schiebedach", "controls.windowDriverFront" to "Fenster Fahrer",
        "controls.windowDriverRear" to "Fenster hinten links", "controls.windowPassengerFront" to "Fenster Beifahrer",
        "controls.windowPassengerRear" to "Fenster hinten rechts", "climate.ACCompressor" to "Klimakompressor",
        "climate.ACMode" to "Klimamodus", "climate.ACSystemTemperatures" to "Klimatemperaturen (°C)",
        "climate.driverSettings" to "Klima Fahrer", "climate.passengerSettings" to "Klima Beifahrer",
        "climate.residualHeat" to "Restwärme", "climate.seatHeatDriver" to "Sitzheizung Fahrer",
        "climate.seatHeatPassenger" to "Sitzheizung Beifahrer", "climate.airConditionerCompressor" to "Klimakompressordaten",
        "vehicle.units" to "Fahrzeugeinheiten", "vehicle.unitSpeed" to "Geschwindigkeitseinheit (Code)",
        "vehicle.time" to "Fahrzeuguhr", "vehicle.systemTime" to "Fahrzeitsystem"
    )
    private val labels = mapOf("engine" to "Motor (°C)", "oil" to "Öl (°C)", "lateral" to "Quer",
        "longitudinal" to "Längs", "range" to "Reichweite (km)", "tanklevel" to "Tank (l)",
        "reserve" to "Reserve", "accelerate" to "Beschleunigen", "brake" to "Bremsen", "shift" to "Schalten",
        "desiredSpeed" to "Solltempo (km/h)", "altitude" to "Höhe (m)", "heading" to "Richtung",
        "position" to "Position", "running" to "Motor läuft", "starting" to "Startvorgang",
        "accessory" to "Zubehör", "error" to "Nicht verfügbar / Fehlercode")

    fun name(property: String) = names[property] ?: property
    fun value(json: JsonObject): String {
        val fields = mutableListOf<String>()
        fun flatten(value: JsonElement, key: String) {
            if (value.isJsonObject) value.asJsonObject.entrySet().forEach { flatten(it.value, it.key) }
            else fields.add("${labels[key] ?: key}: ${if (value.isJsonNull) "--" else value.toString().trim('"')}")
        }
        flatten(json, "")
        return fields.joinToString(" · ")
    }
}
