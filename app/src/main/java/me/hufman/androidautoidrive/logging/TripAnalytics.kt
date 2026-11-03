package me.hufman.androidautoidrive.logging

import com.google.gson.JsonElement
import com.google.gson.JsonObject
import me.hufman.androidautoidrive.R

enum class MetricKind { CURVE, STATE }
data class Metric(val id: String, val title: Int, val property: String, val path: String,
                  val unit: String = "", val kind: MetricKind = MetricKind.CURVE,
                  val low: Double = -1e9, val high: Double = 1e9, val suffix: String = "")
data class Reading(val time: Long, val value: Double)
data class RoutePoint(val time: Long, val lat: Double, val lon: Double, val speedKmh: Double? = null)
data class TripAnalysis(val series: Map<String, List<Reading>>, val route: List<List<RoutePoint>>,
                        val latest: Map<String, JsonObject>)

object TripAnalytics {
    val metrics = listOf(
        Metric("engine", R.string.tl_engine, "engine.temperature", "temperature.engine", "°C", low=-50.0, high=254.0),
        Metric("oil", R.string.tl_oil, "engine.temperature", "temperature.oil", "°C", low=-50.0, high=254.0),
        Metric("speed", R.string.tl_speed, "driving.speedActual", "speedActual", "km/h", low=0.0, high=400.0),
        Metric("torque", R.string.tl_torque, "engine.torque", "torque", "Nm", low=0.0, high=2000.0),
        Metric("rpm", R.string.tl_rpm, "engine.RPMSpeed", "RPMSpeed", "rpm", low=0.0, high=10000.0),
        Metric("gear", R.string.tl_gear, "driving.gear", "gear", kind=MetricKind.STATE, low=1.0, high=16.0),
        Metric("fuel", R.string.tl_fuel, "sensors.fuel", "fuel.tanklevel", "l", low=0.0, high=200.0),
        Metric("range", R.string.tl_range, "sensors.fuel", "fuel.range", "km", low=0.0, high=5000.0),
        Metric("lat", R.string.tl_lat, "driving.acceleration", "acceleration.lateral", "m/s²", low=-100.0, high=100.0),
        Metric("long", R.string.tl_long, "driving.acceleration", "acceleration.longitudinal", "m/s²", low=-100.0, high=100.0),
        Metric("park", R.string.tl_park, "driving.parkingBrake", "parkingBrake", kind=MetricKind.STATE, low=0.0, high=254.0),
        Metric("brake", R.string.tl_brake, "driving.brakeContact", "brakeContact", kind=MetricKind.STATE, low=0.0, high=254.0),
        Metric("mode", R.string.tl_mode, "driving.mode", "mode", kind=MetricKind.STATE, low=0.0, high=14.0),
        Metric("dsc", R.string.tl_dsc, "driving.DSCActive", "DSCActive", kind=MetricKind.STATE, low=0.0, high=254.0),
        Metric("cruise", R.string.tl_cruise, "controls.cruiseControl", "cruiseControl.desiredSpeed", "km/h", low=0.0, high=400.0),
        Metric("outside", R.string.tl_outside, "sensors.temperatureExterior", "temperatureExterior", "°C", low=-80.0, high=100.0),
        Metric("inside", R.string.tl_inside, "sensors.temperatureInterior", "temperatureInterior", "°C", low=-80.0, high=100.0),
        Metric("altitude", R.string.tl_altitude, "navigation.GPSExtendedInfo", "GPSExtendedInfo.altitude", "m", low=-1000.0, high=10000.0),
        Metric("pedal", R.string.tl_pedal, "driving.acceleratorPedal", "acceleratorPedal.position", "%", low=0.0, high=100.0),
        Metric("battery", R.string.tl_battery, "sensors.battery", "battery", "%", low=0.0, high=100.0),
        Metric("batteryTemp", R.string.tl_battery_temp, "sensors.batteryTemp", "batteryTemp", "°C", low=-80.0, high=254.0),
        Metric("styleAccel", R.string.tl_style_accel, "driving.drivingStyle", "accelerate", kind=MetricKind.STATE, low=0.0, high=5.0),
        Metric("styleBrake", R.string.tl_style_brake, "driving.drivingStyle", "brake", kind=MetricKind.STATE, low=0.0, high=5.0),
        Metric("styleShift", R.string.tl_style_shift, "driving.drivingStyle", "shift", kind=MetricKind.STATE, low=0.0, high=5.0)
    ) + listOf("outLeft", "left", "middleLeft", "middleRight", "right", "outRight").flatMapIndexed { index, key ->
        listOf(
            Metric("pdcF$index", R.string.tl_pdc_front, "sensors.PDCRangeFront", "PDCRangeFront.$key", "cm", low=0.0, high=254.0, suffix="${index+1}"),
            Metric("pdcR$index", R.string.tl_pdc_rear, "sensors.PDCRangeRear", "PDCRangeRear.$key", "cm", low=0.0, high=254.0, suffix="${index+1}")
        )
    }
    val defaults = setOf("route", "engine", "oil", "speed", "torque", "gear")

    fun number(json: JsonObject, path: String): Double? = runCatching {
        var current: JsonElement = json
        path.split('.').forEach { current = current.asJsonObject.get(it) ?: return null }
        current.asDouble.takeIf { it.isFinite() }
    }.getOrNull()

    /** Min/max buckets retain peaks while keeping multi-hour charts bounded in memory. */
    fun analyze(events: Sequence<JsonObject>, start: Long, end: Long): TripAnalysis {
        val span = maxOf(1L, end - start)
        val buckets = hashMapOf<String, java.util.TreeMap<Int, MutableList<Reading>>>()
        val states = hashMapOf<String, java.util.TreeMap<Int, Reading>>()
        val latest = linkedMapOf<String, JsonObject>()
        val route = java.util.TreeMap<Int, RoutePoint>()
        val routeSpeeds = java.util.TreeMap<Int, Reading>()
        val byProperty = metrics.groupBy { it.property }
        events.forEach { event ->
            if (event.get("type")?.asString == "sample") {
                val property = event.get("property")?.asString ?: return@forEach
                latest[property] = event
                if (event.get("cached")?.asBoolean == true) return@forEach
                val time = event.get("timestamp")?.asLong ?: return@forEach
                val json = event.getAsJsonObject("value") ?: return@forEach
                val bucket = (((time - start).coerceIn(0, span) * 400) / span).toInt()
                val routeBucket = (((time - start).coerceIn(0, span) * 20000) / span).toInt()
                if (property == "driving.speedActual") {
                    val speed = number(json, "speedActual")?.takeIf { it in 0.0..400.0 }
                    routeSpeeds[routeBucket] = Reading(time, speed ?: Double.NaN)
                }
                byProperty[property].orEmpty().forEach { metric ->
                    val value = number(json, metric.path)?.takeIf { it in metric.low..metric.high }
                    // NaN marks an explicit unavailable reading: never bridge it with a curve.
                    val point = Reading(time, value ?: Double.NaN)
                    if (metric.kind == MetricKind.STATE) {
                        states.getOrPut(metric.id) { java.util.TreeMap() }[bucket] = point
                    } else {
                        val points = buckets.getOrPut(metric.id) { java.util.TreeMap() }.getOrPut(bucket) { mutableListOf() }
                        points.add(point)
                        if (points.size > 6) {
                            val finite=points.filter{it.value.isFinite()}
                            val keep = listOfNotNull(points.first(), finite.minByOrNull { it.value },
                                finite.maxByOrNull { it.value }, points.last()) + points.filter { !it.value.isFinite() }.take(1)
                            points.clear(); points.addAll(keep.distinct().sortedBy { it.time })
                        }
                    }
                }
                if (property == "navigation.GPSPosition") {
                    val lat = number(json, "GPSPosition.latitude")
                    val lon = number(json, "GPSPosition.longitude")
                    if (lat != null && lon != null && lat in -85.0..85.0 && lon in -180.0..180.0 && !(lat == 0.0 && lon == 0.0)) {
                        route[routeBucket] = RoutePoint(time, lat, lon)
                    }
                }
            }
        }
        val series = metrics.associate { metric ->
            metric.id to if (metric.kind == MetricKind.STATE) states[metric.id]?.values?.toList().orEmpty()
                else buckets[metric.id]?.values?.flatten()?.distinct()?.sortedBy { it.time }.orEmpty()
        }
        val segments = mutableListOf<MutableList<RoutePoint>>()
        val speeds = routeSpeeds.values.toList()
        route.values.forEach { point ->
            val previous = segments.lastOrNull()?.lastOrNull()
            if (previous == null || point.time - previous.time > 30000) segments.add(mutableListOf())
            segments.last().add(point.copy(speedKmh = routeSpeed(speeds, point.time)))
        }
        return TripAnalysis(series, segments, latest)
    }

    private fun routeSpeed(speeds: List<Reading>, time: Long): Double? {
        val index = speeds.binarySearchBy(time) { it.time }
        if (index >= 0) return speeds[index].value.takeIf(Double::isFinite)
        val nextIndex = -index - 1
        val before = speeds.getOrNull(nextIndex - 1)
        val after = speeds.getOrNull(nextIndex)
        if (before != null && after != null && after.time - before.time <= 5_000 &&
            before.value.isFinite() && after.value.isFinite()) {
            val fraction = (time - before.time).toDouble() / (after.time - before.time)
            return before.value + (after.value - before.value) * fraction
        }
        return listOfNotNull(before, after).minByOrNull { kotlin.math.abs(it.time - time) }
            ?.takeIf { kotlin.math.abs(it.time - time) <= 2_500 && it.value.isFinite() }?.value
    }
}
