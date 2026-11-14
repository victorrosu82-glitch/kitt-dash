package ro.vix.kittdash.bord

import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Marime

/**
 * Ce merită spus șoferului, pe scurt și la vedere.
 *
 * Bordul nu e un aparat de diagnoză: aici intră doar lucrurile pentru care
 * ai opri mașina sau ai schimba felul in care conduci. Restul rămâne in
 * ecranul tehnic.
 *
 * Pragurile sunt alese pe masina asta. Lexus NX 300h merge cu lichidul la
 * 85-90 °C; peste 105 e cald rau. Bateria HV lucreaza intre 40 si 80 %, iar
 * hibridul o tine singur acolo - sub 35 % inseamna ca a tras mult si inca
 * nu a apucat sa se incarce.
 */
enum class Avertizare(val text: String, val gravitate: Gravitate) {
    MOTOR_FIERBINTE("MOTOR FIERBINTE", Gravitate.MARE),
    BATERIE_HV_SCAZUTA("BATERIE HV SCAZUTA", Gravitate.MICA),
    TENSIUNE_BORD_MICA("TENSIUNE BORD MICA", Gravitate.MARE),
    ADAPTOR_TACE("ADAPTORUL NU MAI RASPUNDE", Gravitate.MICA),
    ;

    enum class Gravitate { MICA, MARE }

    companion object {
        /** Peste atatea grade, lichidul de racire e prea cald. */
        const val PRAG_LICHID = 105.0

        /** Sub atatea procente, bateria HV e goala pentru un hibrid. */
        const val PRAG_BATERIE = 35.0

        /**
         * Sub atatia volti la bord, alternatorul-generator nu mai incarca
         * bateria de 12 V si masina se opreste la un moment dat. 11,8 e
         * pragul la care nu mai e vorba de o masuratoare proasta.
         */
        const val PRAG_TENSIUNE = 11.8

        /** Dupa atatea secunde fara o citire noua, datele sunt vechi. */
        const val SECUNDE_TACERE = 10

        /**
         * Avertizarile de aratat acum, cele grave inaintea celorlalte.
         * [acumMs] serveste la aflarea datelor vechi.
         */
        fun din(stare: CarState, acumMs: Long): List<Avertizare> {
            if (!stare.conectat) return emptyList()
            val gasite = mutableListOf<Avertizare>()

            stare.valoare(Marime.TEMP_LICHID)?.let {
                if (it >= PRAG_LICHID) gasite += MOTOR_FIERBINTE
            }
            stare.valoare(Marime.TENSIUNE_MODUL)?.let {
                if (it <= PRAG_TENSIUNE) gasite += TENSIUNE_BORD_MICA
            }
            stare.valoare(Marime.BATERIE_HV)?.let {
                if (it <= PRAG_BATERIE) gasite += BATERIE_HV_SCAZUTA
            }

            // Daca valorile rapide au imbatranit, adaptorul a amutit fara
            // sa pice legatura - se intampla la clonele ELM327.
            val ultima = stare.valori[Marime.VITEZA] ?: stare.valori[Marime.TURATIE]
            if (ultima != null && ultima.vecheDeMs(acumMs) > SECUNDE_TACERE * 1000L) {
                gasite += ADAPTOR_TACE
            }

            return gasite.sortedByDescending { it.gravitate.ordinal }
        }
    }
}
