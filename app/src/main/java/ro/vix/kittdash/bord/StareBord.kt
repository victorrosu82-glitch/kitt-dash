package ro.vix.kittdash.bord

import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Marime
import ro.vix.kittdash.obd.ModHibrid
import kotlin.math.abs
import kotlin.math.min

/**
 * Ce deseneaza bordul in clipa asta. E deja netezit si gata de desenat -
 * desenatorul nu mai socoteste nimic, doar pune pe ecran.
 *
 * Valorile care pot lipsi sunt null, iar bordul arata "---" in locul lor.
 * Nu punem zero: un zero inventat arata ca o masura adevarata.
 */
data class StareBord(
    val vitezaKmh: Float = 0f,
    val turatie: Float = 0f,
    val putereKw: Float = 0f,
    val socProcent: Float? = null,
    val tempLichid: Float? = null,
    val combustibil: Float? = null,
    val tempAer: Float? = null,
    val tempBaterie: Float? = null,
    val mod: ModHibrid = ModHibrid.NECUNOSCUT,
    val areLegatura: Boolean = false,
    val peSimulator: Boolean = false,
    /** Unde a ajuns scanerul rosu, 0..58. */
    val scanerFaza: Float = 0f,
    /** Cat de tare "vorbeste" modulatorul de voce, 0..1. */
    val vocePlin: Float = 0f,
    /** Ceasul desenului, pentru undele modulatorului. */
    val timpS: Float = 0f,
)

/**
 * Trece de la citirile care vin rar (de 1-2 ori pe secunda) la o imagine
 * care se misca lin, de 60 de ori pe secunda.
 *
 * Fara asta bordul ar sari din treapta in treapta: un ELM327 da cam zece
 * raspunsuri pe secunda in total, deci viteza se reinnoieste de vreo doua
 * ori pe secunda. Intre doua citiri mergem spre valoarea noua cu un pas
 * proportional cu cat a trecut - acelasi lucru pe care il face si macheta.
 */
class Netezitor(private val ceas: () -> Long = { System.nanoTime() }) {

    private var ultimaNs = ceas()
    private var pornireNs = ultimaNs

    private var viteza = 0f
    private var turatie = 0f
    private var putere = 0f
    private var soc: Float? = null
    private var voce = 0f

    fun urmatoareaStare(masina: CarState): StareBord {
        val acum = ceas()
        val dt = ((acum - ultimaNs) / 1_000_000_000.0).toFloat().coerceIn(0.001f, 0.05f)
        ultimaNs = acum
        val t = ((acum - pornireNs) / 1_000_000_000.0).toFloat()

        viteza = apropie(viteza, masina.valoare(Marime.VITEZA)?.toFloat(), dt, VITEZA_PE_SECUNDA)
        turatie = apropie(turatie, masina.valoare(Marime.TURATIE)?.toFloat(), dt, TURATIE_PE_SECUNDA)
        putere = apropie(putere, masina.valoare(Marime.HV_PUTERE)?.toFloat(), dt, PUTERE_PE_SECUNDA)

        val socCitit = masina.valoare(Marime.BATERIE_HV)?.toFloat()
        if (socCitit != null) {
            soc = apropie(soc ?: socCitit, socCitit, dt, SOC_PE_SECUNDA)
        }

        // Modulatorul de voce creste cu puterea, incotro ar merge ea. Asa
        // bordul "vorbeste" cand masina trage sau recupereaza.
        val vocePropusa = min(1f, abs(putere) / PUTERE_VOCE_PLINA)
        voce += (vocePropusa - voce) * min(1f, dt * VOCE_PE_SECUNDA)

        return StareBord(
            vitezaKmh = viteza,
            turatie = turatie,
            putereKw = putere,
            socProcent = soc,
            tempLichid = masina.valoare(Marime.TEMP_LICHID)?.toFloat(),
            combustibil = masina.valoare(Marime.NIVEL_COMBUSTIBIL)?.toFloat(),
            tempAer = masina.valoare(Marime.TEMP_AER_ADMISIE)?.toFloat(),
            tempBaterie = null,
            mod = ModHibrid.din(masina),
            areLegatura = masina.conectat,
            peSimulator = masina.simulator,
            scanerFaza = (t * SCANER_PE_SECUNDA) % (2f * SCANER_PASI),
            vocePlin = voce,
            timpS = t,
        )
    }

    private fun apropie(acum: Float, tinta: Float?, dt: Float, viteza: Float): Float {
        if (tinta == null) return acum
        return acum + (tinta - acum) * min(1f, dt * viteza)
    }

    companion object {
        /** Cat de repede alearga valoarea desenata dupa cea citita. */
        const val VITEZA_PE_SECUNDA = 3f
        const val TURATIE_PE_SECUNDA = 4f
        const val PUTERE_PE_SECUNDA = 6f
        const val SOC_PE_SECUNDA = 1f
        const val VOCE_PE_SECUNDA = 8f

        /** Puterea la care modulatorul de voce e plin. */
        const val PUTERE_VOCE_PLINA = 25f

        /** Cate casute are scanerul si cat de repede le strabate. */
        const val SCANER_PASI = 29f
        const val SCANER_PE_SECUNDA = 24f
    }
}
