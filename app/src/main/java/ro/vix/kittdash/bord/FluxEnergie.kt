package ro.vix.kittdash.bord

import ro.vix.kittdash.obd.ModHibrid
import kotlin.math.abs

/**
 * Pe unde curge energia in NX 300h E-Four, dedus din ce da masina.
 *
 * Sistemul e hibrid serie-paralel:
 *  - in fata, motorul termic si transmisia hibrida cu doua masini electrice,
 *    generatorul MG1 si motorul MG2, legate prin angrenajul planetar. Motorul
 *    termic poate trage rotile si, in acelasi timp, prin MG1, incarca bateria;
 *  - in spate, al treilea motor electric, MGR, numai pe rotile din spate;
 *  - bateria HV sub bancheta din spate.
 *
 * Masina nu spune cum se imparte curentul intre MG2 si MGR. Stim doar daca
 * merge motorul termic (turatia) si incotro merge curentul bateriei (semnul
 * puterii). Restul e dedus, iar motorul din spate e o estimare: il aprindem
 * numai cand puterea bateriei e mare, la accelerari puternice si la franari.
 */
data class FluxEnergie(
    /** Motorul termic merge. */
    val motor: Boolean,
    /** Motorul termic trage rotile din fata. */
    val motorLaRoti: Boolean,
    /** Motorul termic incarca bateria, prin generator. */
    val generator: Boolean,
    /** MG2 lucreaza: trage din baterie sau recupereaza la franare. */
    val fata: Boolean,
    /** MGR lucreaza (estimat). */
    val spate: Boolean,
    /** Energia intra in baterie. */
    val inBaterie: Boolean,
    /** Franare: rotile dau energie inapoi, motorul termic e oprit. */
    val recuperare: Boolean,
) {
    /** Rotile din fata primesc sau dau energie. */
    val rotiFata: Boolean get() = motorLaRoti || fata

    companion object {
        /** Sub atat, curentul bateriei e doar consumul de bord, nu tractiune. */
        const val PRAG_BATERIE_KW = ModHibrid.PRAG_REGEN_KW

        /** De la atat in sus socotim ca lucreaza si motorul din spate. */
        const val PRAG_SPATE_KW = 12.0

        /** Sub viteza asta masina sta: motorul termic nu trage roti. */
        const val VITEZA_OPRIT = 2.0

        fun din(turatie: Float, putereKw: Float, vitezaKmh: Float): FluxEnergie {
            val motor = turatie > ModHibrid.PRAG_TURATIE
            val merge = vitezaKmh > VITEZA_OPRIT
            val daCurent = putereKw > PRAG_BATERIE_KW
            val primeste = putereKw < -PRAG_BATERIE_KW
            val recuperare = primeste && !motor && merge
            return FluxEnergie(
                motor = motor,
                motorLaRoti = motor && merge,
                generator = primeste && motor,
                fata = (daCurent && merge) || recuperare,
                spate = merge && (daCurent || recuperare) && abs(putereKw) >= PRAG_SPATE_KW,
                inBaterie = primeste,
                recuperare = recuperare,
            )
        }
    }
}
