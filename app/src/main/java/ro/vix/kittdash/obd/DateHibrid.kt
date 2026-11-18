package ro.vix.kittdash.obd

import kotlin.math.abs

/**
 * Datele sistemului hibrid pe care le citim rar, din blocurile Toyota ale
 * calculatorului hibrid (7E2): bateria pe blocuri, temperaturile ei si cateva
 * temperaturi inca neconfirmate. Vezi `docs/explorare/ce-a-gasit.md`.
 */
data class DateHibrid(
    /** Tensiunea fiecaruia dintre cele 17 blocuri, V (2181, confirmat). */
    val tensiuni: List<Float> = emptyList(),
    /** Rezistenta interna a fiecarui bloc, in unitatile calculatorului (2195). */
    val rezistente: List<Int> = emptyList(),
    /** Temperaturile bateriei, °C (2187, 7 senzori). */
    val temperaturiBaterie: List<Float> = emptyList(),
    /** Temperaturi din sistemul hibrid, de confirmat: (eticheta, °C). */
    val temperaturiSistem: List<Pair<String, Float>> = emptyList(),
    /** Combustibilul din rezervor, litri (7C0 2129, de confirmat). */
    val rezervorLitri: Float? = null,
    val laMs: Long = 0L,
) {
    val celMaiSlab: Int? get() = tensiuni.indices.minByOrNull { tensiuni[it] }
    val celMaiTare: Int? get() = tensiuni.indices.maxByOrNull { tensiuni[it] }
    val diferentaV: Float? get() = if (tensiuni.isEmpty()) null else tensiuni.max() - tensiuni.min()

    companion object {
        const val BLOCURI = 17

        /** Formulele Toyota pentru Prius, confirmate pe NX prin suma si min/max. */
        fun tensiuni(o: IntArray?): List<Float> {
            if (o == null || o.size < BLOCURI * 2) return emptyList()
            return List(BLOCURI) { i -> ((o[2 * i] * 256 + o[2 * i + 1]) * 79.99 / 65535).toFloat() }
        }

        fun rezistente(o: IntArray?): List<Int> {
            if (o == null || o.size < BLOCURI) return emptyList()
            return List(BLOCURI) { o[it] }
        }

        fun temperaturi(o: IntArray?): List<Float> {
            if (o == null || o.size < 2) return emptyList()
            return List(minOf(7, o.size / 2)) { i ->
                ((o[2 * i] * 256 + o[2 * i + 1]) * 255.9 / 65535 - 50).toFloat()
            }
        }

        /** Primul octet al blocurilor 2161-2170, minus 40: urca lin in mers. */
        fun temperaturaSimpla(o: IntArray?): Float? = o?.firstOrNull()?.let { (it - 40).toFloat() }

        /** 7C0 2129: A / 2 litri, formula Toyota cunoscuta. */
        fun rezervor(o: IntArray?): Float? = o?.firstOrNull()?.let { it / 2f }
    }
}

/**
 * Statisticile drumului de acum: socotite din ce citim deja, fara cereri noi.
 * Drumul incepe la fiecare legatura noua cu adaptorul.
 */
data class Drum(
    val km: Double = 0.0,
    val secundeInMers: Double = 0.0,
    val secundePeBaterie: Double = 0.0,
    val kWhRecuperat: Double = 0.0,
    val litri: Double = 0.0,
) {
    /** Cat din timpul in mers a fost pe baterie, 0..100; null la inceput. */
    val procentBaterie: Double?
        get() = if (secundeInMers < 30) null else secundePeBaterie / secundeInMers * 100

    /** l/100 km; null pana la primul kilometru, ca sa nu arate prostii. */
    val litriLa100: Double?
        get() = if (km < 1.0) null else litri / km * 100

    /**
     * Adauga [secunde] de drum, cu valorile din [stare]. Pauzele mai lungi de
     * cateva secunde (legatura cazuta) nu se socotesc.
     */
    fun cu(stare: CarState, secunde: Double): Drum {
        if (secunde <= 0.0 || secunde > PAUZA_MAXIMA_S) return this
        val viteza = stare.valoare(Marime.VITEZA) ?: return this
        val turatie = stare.valoare(Marime.TURATIE) ?: 0.0
        val putere = stare.valoare(Marime.HV_PUTERE) ?: 0.0
        val aer = stare.valoare(Marime.DEBIT_AER) ?: 0.0
        val merge = viteza > 2.0
        val peBaterie = merge && turatie < ModHibrid.PRAG_TURATIE
        // energie recuperata: bateria primeste curent cu motorul oprit, in mers
        val recuperat = if (merge && putere < 0 && turatie < ModHibrid.PRAG_TURATIE) abs(putere) * secunde / 3600 else 0.0
        return copy(
            km = km + viteza * secunde / 3600,
            secundeInMers = secundeInMers + if (merge) secunde else 0.0,
            secundePeBaterie = secundePeBaterie + if (peBaterie) secunde else 0.0,
            kWhRecuperat = kWhRecuperat + recuperat,
            litri = litri + Consum.litriPeOra(aer) * secunde / 3600,
        )
    }

    companion object {
        const val PAUZA_MAXIMA_S = 5.0
    }
}

/**
 * Ce date rare vrea ecranul acum. Ecranul HIBRID anunta la fiecare cadru ca
 * e pe ecran; bucla de citire cere blocurile bateriei numai atunci, ca sa nu
 * incetineasca viteza si turatia pe celelalte ecrane.
 */
object CereriRare {
    @Volatile
    private var hibridVazutLaMs = 0L

    fun hibridPeEcran(acumMs: Long = System.currentTimeMillis()) {
        hibridVazutLaMs = acumMs
    }

    fun vreaHibrid(acumMs: Long = System.currentTimeMillis()): Boolean =
        acumMs - hibridVazutLaMs < 3_000L
}
