package me.hufman.androidautoidrive.carapp.carinfo

import io.bimmergestalt.idriveconnectkit.CDS
import kotlinx.coroutines.flow.*
import me.hufman.androidautoidrive.AppSettings
import me.hufman.androidautoidrive.ChassisCode
import me.hufman.androidautoidrive.cds.CDSMetrics
import me.hufman.androidautoidrive.cds.flow
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsJsonPrimitive
import me.hufman.androidautoidrive.utils.GsonNullable.tryAsString

object VehicleNames {
	/** BMW type keys (characters 4 to 7 of a full 17 character VIN) to model names, to be filled with verified entries only */
	val vinModelCodes: Map<String, String> = emptyMap()

	private val bodies = mapOf(
			"F32" to "4er Coupé", "F33" to "4er Cabrio", "F36" to "4er Gran Coupé",
			"G22" to "4er Coupé", "G23" to "4er Cabrio", "G26" to "4er Gran Coupé",
	)
	private val seriesName = Regex("^(\\d) Series$")

	fun fuelSuffix(fuelType: Int?) = when (fuelType) {
		6, 7 -> " d"
		4, 5 -> " i"
		else -> ""
	}

	fun resolve(customName: String?, vin: String?, chassisCode: String?, fuelType: Int?): String {
		customName?.trim()?.takeIf { it.isNotEmpty() }?.let { return it }
		// the car often only reports the last 7 characters of the VIN, which don't contain the type key
		if (vin != null && vin.length >= 17) vinModelCodes[vin.substring(3, 7).uppercase()]?.let { return it }
		val code = chassisCode?.trim()?.uppercase().orEmpty()
		val body = bodies[code] ?: ChassisCode.fromCode(code)?.takeIf { it.brand == "BMW" }?.chassis?.let { seriesName.replace(it, "$1er") }
		return when {
			body == null -> "BMW"
			body.first().isDigit() -> body + fuelSuffix(fuelType)
			else -> body
		}
	}
}

/** The current name of the car, such as "4er Gran Coupé d", from the settings, the VIN or the chassis code */
class VehicleName(metrics: CDSMetrics, private val capabilities: Map<String, Any?>, private val settings: AppSettings) {
	private val vinFlow = metrics.carInfo.cachedCdsData.flow[CDS.VEHICLE.VIN].map { it.tryAsJsonPrimitive("VIN")?.tryAsString }.onStart { emit(null) }
	private val fuelFlow = metrics.fuelType.onStart { emit(null) }

	@Volatile private var vin: String? = null
	@Volatile private var fuelType: Int? = null

	/** The name from the data seen so far, always available */
	fun current(): String = VehicleNames.resolve(settings[AppSettings.KEYS.DASHBOARD_MODEL_NAME], vin, capabilities["vehicle.type"] as? String, fuelType)

	val flow: Flow<String> = combine(vinFlow, fuelFlow) { vin, fuel ->
		this.vin = vin ?: this.vin
		this.fuelType = fuel ?: this.fuelType
		current()
	}.distinctUntilChanged()
}
