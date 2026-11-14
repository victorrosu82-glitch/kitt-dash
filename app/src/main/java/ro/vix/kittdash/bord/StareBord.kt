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
    val tempExterioara: Float? = null,
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
    /**
     * Melodia de anuntat acum, sau null. Apare o singura data, cand se
     * schimba melodia, si trece ca o banda peste modulatorul de voce.
     */
    val melodie: String? = null,
    /** Cat a strabatut banda cu melodia, 0..1. */
    val melodieProgres: Float = 0f,
    /** Noaptea bordul se stinge, ca sa nu bata in parbriz. */
    val noapte: Boolean = false,
    /** Ce merita spus soferului acum. */
    val avertizari: List<Avertizare> = emptyList(),
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
class Netezitor(
    private val ceas: () -> Long = { System.nanoTime() },
    /**
     * De unde aflam ce cânta. Intoarce null cand nu canta nimic sau cand
     * n-avem acces la notificari. E intrebat rar, o data pe secunda.
     */
    private val melodiaAcum: () -> String? = { null },
    /** Spune daca e noapte: masina stie, telefonul se ia dupa tema. */
    private val esteNoapte: () -> Boolean = { false },
    /**
     * Ceasul de perete. Separat de [ceas], care masoara doar scurgerea
     * timpului: citirile din masina poarta ora lumii, nu nanosecundele de
     * la pornire, iar daca le-am compara intre ele ar iesi ca toate datele
     * sunt vechi.
     */
    private val ceasulLumii: () -> Long = { System.currentTimeMillis() },
) {

    private var ultimaNs = ceas()
    private var pornireNs = ultimaNs

    private var viteza = 0f
    private var turatie = 0f
    private var putere = 0f
    private var soc: Float? = null
    private var voce = 0f

    private var ultimaIntrebareNs = 0L
    private var melodiaStiuta: String? = null
    private var melodiaDeAratat: String? = null
    private var melodieInceputS = 0f

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

        urmaresteMelodia(acum, t)

        return StareBord(
            vitezaKmh = viteza,
            turatie = turatie,
            putereKw = putere,
            socProcent = soc,
            tempLichid = masina.valoare(Marime.TEMP_LICHID)?.toFloat(),
            combustibil = masina.valoare(Marime.NIVEL_COMBUSTIBIL)?.toFloat(),
            tempAer = masina.valoare(Marime.TEMP_AER_ADMISIE)?.toFloat(),
            tempExterioara = masina.valoare(Marime.TEMP_EXTERIOARA)?.toFloat(),
            tempBaterie = null,
            mod = ModHibrid.din(masina),
            areLegatura = masina.conectat,
            peSimulator = masina.simulator,
            scanerFaza = (t * SCANER_PE_SECUNDA) % (2f * SCANER_PASI),
            vocePlin = voce,
            timpS = t,
            noapte = esteNoapte(),
            avertizari = Avertizare.din(masina, ceasulLumii()),
            melodie = melodiaDeAratat,
            melodieProgres = if (melodiaDeAratat == null) {
                0f
            } else {
                ((t - melodieInceputS) / SECUNDE_MELODIE).coerceIn(0f, 1f)
            },
        )
    }

    /**
     * Intreaba o data pe secunda ce cânta. Cand melodia s-a schimbat,
     * porneste banda; dupa ce trece, modulatorul isi ia locul inapoi.
     *
     * Prima melodie gasita la pornire nu se anunta: n-ai schimbat-o tu
     * atunci, doar ai urcat in masina peste ea.
     */
    private fun urmaresteMelodia(acumNs: Long, t: Float) {
        if (melodiaDeAratat != null && t - melodieInceputS >= SECUNDE_MELODIE) {
            melodiaDeAratat = null
        }
        if (acumNs - ultimaIntrebareNs < NS_INTRE_INTREBARI) return
        val primaOara = ultimaIntrebareNs == 0L
        ultimaIntrebareNs = acumNs

        val acum = melodiaAcum()
        if (acum != null && acum != melodiaStiuta && !primaOara) {
            melodiaDeAratat = acum
            melodieInceputS = t
        }
        melodiaStiuta = acum
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

        /** Cat tine banda cu melodia, de la un capat la altul. */
        const val SECUNDE_MELODIE = 9f

        /** Cat de rar intrebam ce cânta: o data pe secunda ajunge. */
        const val NS_INTRE_INTREBARI = 1_000_000_000L

        /** Cate casute are scanerul si cat de repede le strabate. */
        const val SCANER_PASI = 29f
        const val SCANER_PE_SECUNDA = 24f
    }
}
