package ro.vix.kittdash.obd

import android.content.Context
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import kotlin.math.abs

/** Nota bateriei HV, cu motivul care a dat-o. */
enum class Nota(val text: String) { BUNA("BUNA"), DE_URMARIT("DE URMARIT"), SLABA("SLABA") }

data class Sanatate(
    val nota: Nota,
    val motiv: String,
    /** Diferenta dintre blocuri cu bateria in repaus, mV (null daca n-a stat in repaus). */
    val diferentaRepausMv: Int?,
    val rezistentaMedie: Double,
    val rezistentaImprastiere: Int,
)

/**
 * Sanatatea bateriei din ce citim deja pe ecranul HIBRID:
 * - diferenta dintre blocuri, dar numai cu bateria in repaus (curent sub 5 A),
 *   pentru ca sub sarcina orice baterie se imprastie;
 * - rezistenta interna medie: creste cand bateria imbatraneste;
 * - cat de departe e cel mai rezistent bloc de cel mai bun: un bloc care
 *   o ia inainte e primul semn al unui bloc pe duca.
 *
 * Pragurile sunt prudente; urmarirea in timp spune mai mult decat o zi.
 */
object SanatateBaterie {
    const val CURENT_REPAUS_A = 5f

    fun evalueaza(h: DateHibrid, curentA: Float?, diferentaRepausMv: Int?): Sanatate? {
        if (h.rezistente.isEmpty()) return null
        val medie = h.rezistente.average()
        val imprastiere = h.rezistente.max() - h.rezistente.min()
        val note = buildList {
            diferentaRepausMv?.let {
                add(
                    when {
                        it < 200 -> Nota.BUNA to "blocuri egale"
                        it < 400 -> Nota.DE_URMARIT to "blocuri departe: $it mV"
                        else -> Nota.SLABA to "blocuri departe: $it mV"
                    },
                )
            }
            add(
                when {
                    imprastiere <= 3 -> Nota.BUNA to "rezistente egale"
                    imprastiere <= 6 -> Nota.DE_URMARIT to "un bloc mai rezistent"
                    else -> Nota.SLABA to "un bloc mult mai rezistent"
                },
            )
            add(
                when {
                    medie <= 30 -> Nota.BUNA to "rezistenta mica"
                    medie <= 40 -> Nota.DE_URMARIT to "rezistenta crescuta"
                    else -> Nota.SLABA to "rezistenta mare"
                },
            )
        }
        val cea = note.maxBy { it.first.ordinal }
        return Sanatate(cea.first, cea.second, diferentaRepausMv, medie, imprastiere)
    }

    /** Diferenta in mV, daca bateria e acum in repaus; altfel null. */
    fun diferentaInRepaus(h: DateHibrid, curentA: Float?): Int? {
        val d = h.diferentaV ?: return null
        if (curentA == null || abs(curentA) >= CURENT_REPAUS_A) return null
        return (d * 1000).toInt()
    }
}

/**
 * Istoricul bateriei, o linie pe zi: cea mai mica diferenta in repaus si
 * rezistenta medie. Ramane in telefon, ca sa vedem daca bateria se strica
 * incet, de la o luna la alta.
 */
class IstoricBaterie(context: Context) {
    private val prefs = context.getSharedPreferences("istoric_baterie", Context.MODE_PRIVATE)
    private val zi = SimpleDateFormat("yyyy-MM-dd", Locale.US)

    /** Pune citirea de acum in ziua de azi. */
    fun noteaza(s: Sanatate, acumMs: Long = System.currentTimeMillis()) {
        val cheie = zi.format(Date(acumMs))
        val vechi = prefs.getString(cheie, null)?.split(";")
        val difVeche = vechi?.getOrNull(0)?.toIntOrNull()
        val dif = listOfNotNull(difVeche, s.diferentaRepausMv).minOrNull()
        val ir = "%.1f".format(Locale.US, s.rezistentaMedie)
        prefs.edit().putString(cheie, "${dif ?: ""};$ir").apply()
    }

    /** Prima zi notata si rezistenta medie de atunci. */
    fun prima(): Pair<String, Double>? =
        prefs.all.keys.minOrNull()?.let { k ->
            prefs.getString(k, null)?.split(";")?.getOrNull(1)?.toDoubleOrNull()?.let { k to it }
        }
}
