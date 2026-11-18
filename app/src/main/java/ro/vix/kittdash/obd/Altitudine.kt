package ro.vix.kittdash.obd

import android.content.Context
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import kotlin.math.pow

/**
 * Altitudinea din presiunea aerului, cu formula atmosferei standard.
 *
 * Presiunea de la nivelul marii se schimba cu vremea (±15 hPa), deci
 * altitudinea absoluta poate fi decalata cu ±100 m de la o zi la alta. Cat
 * urci sau cobori pe un drum iese insa corect.
 */
object Altitudine {
    const val PRESIUNE_MARE_HPA = 1013.25

    fun dinPresiune(hPa: Double, laMareHpa: Double = PRESIUNE_MARE_HPA): Double =
        44330.8 * (1.0 - (hPa / laMareHpa).pow(0.190263))
}

/**
 * Barometrul telefonului, daca are: 0,01 hPa, adica ~10 cm. Cel al masinii
 * da doar kPa intregi, adica trepte de ~85 m; il folosim numai cand
 * telefonul n-are barometru.
 */
class Barometru(context: Context, private val laCitire: (Double) -> Unit) : SensorEventListener {

    private val senzori = context.getSystemService(Context.SENSOR_SERVICE) as SensorManager
    private val senzor: Sensor? = senzori.getDefaultSensor(Sensor.TYPE_PRESSURE)
    private var netezit: Double? = null
    private var ultimaMs = 0L

    val exista: Boolean get() = senzor != null

    fun porneste() {
        senzor?.let { senzori.registerListener(this, it, SensorManager.SENSOR_DELAY_NORMAL) }
    }

    fun opreste() {
        senzori.unregisterListener(this)
    }

    override fun onSensorChanged(e: SensorEvent) {
        val p = e.values.firstOrNull()?.toDouble() ?: return
        // netezit, ca sa nu tremure cifra la fiecare usa inchisa
        netezit = netezit?.let { it + (p - it) * 0.1 } ?: p
        val acum = System.currentTimeMillis()
        if (acum - ultimaMs >= 1000) {
            ultimaMs = acum
            netezit?.let(laCitire)
        }
    }

    override fun onAccuracyChanged(s: Sensor?, a: Int) = Unit
}
