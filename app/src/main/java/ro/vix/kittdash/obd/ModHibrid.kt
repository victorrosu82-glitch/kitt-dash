package ro.vix.kittdash.obd

/**
 * Modul in care merge masina, asa cum il arata bordul KITT din macheta.
 *
 * Nu vine gata facut din masina: nu exista un PID care sa spuna "acum sunt
 * in EV". Il scoatem din ce stim sigur - turatia motorului termic si semnul
 * curentului bateriei, amandoua confirmate pe masina lui Victor.
 */
enum class ModHibrid(val eticheta: String) {
    /** Motorul termic oprit, masina merge pe baterie. */
    EV("EV"),

    /** Motorul termic porneste, singur sau impreuna cu electricul. */
    HYBRID("HYBRID"),

    /** Bateria primeste curent: franare cu recuperare sau incarcare. */
    REGEN("REGEN"),

    /** Masina sta, sistemul e pornit si nu se intampla nimic. */
    READY("READY"),

    /** Inca nu stim: n-au venit datele din care se vede. */
    NECUNOSCUT("---"),
    ;

    companion object {
        /**
         * Pragul de la care socotim ca bateria chiar primeste curent, nu ca
         * doar isi echilibreaza consumul de bord. Masina stand pe loc, cu
         * sistemul pornit, arata ~1,3 A; un kilowatt inseamna altceva.
         */
        const val PRAG_REGEN_KW = 1.0

        /** Sub turatia asta socotim ca motorul termic e oprit. */
        const val PRAG_TURATIE = 200.0

        /**
         * [putereKw] e puterea bateriei: pozitiva cand bateria da curent,
         * negativa cand primeste. Regenerarea bate totul: si cu motorul
         * pornit, daca bateria se incarca puternic, bordul arata REGEN.
         */
        fun din(turatie: Double?, putereKw: Double?, vitezaKmh: Double?): ModHibrid {
            if (turatie == null && putereKw == null) return NECUNOSCUT
            val motorPornit = (turatie ?: 0.0) >= PRAG_TURATIE
            // Pe loc nu ai ce recupera: daca bateria primeste curent cu masina
            // oprita, o incarca motorul termic prin generator (vazut pe
            // 18.11.2026: 0 km/h, 1200 rot/min, -3 kW, iar bordul scria REGEN).
            val pePloc = (vitezaKmh ?: 0.0) < 1.0
            if (putereKw != null && putereKw <= -PRAG_REGEN_KW && !(pePloc && motorPornit)) return REGEN
            if (motorPornit) return HYBRID
            val staPeLoc = (vitezaKmh ?: 0.0) < 1.0 &&
                (putereKw == null || kotlin.math.abs(putereKw) < PRAG_REGEN_KW)
            return if (staPeLoc) READY else EV
        }

        fun din(stare: CarState): ModHibrid = din(
            turatie = stare.valoare(Marime.TURATIE),
            putereKw = stare.valoare(Marime.HV_PUTERE),
            vitezaKmh = stare.valoare(Marime.VITEZA),
        )
    }
}
