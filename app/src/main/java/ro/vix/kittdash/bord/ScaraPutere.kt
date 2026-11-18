package ro.vix.kittdash.bord

import kotlin.math.abs
import kotlin.math.sqrt

/**
 * Cat din bara de putere se aprinde pentru o putere data a bateriei HV.
 *
 * Masurat in Lexus pe 18.11.2026: puterea sta de obicei intre 1 si 5 kW si
 * trece rar de 15. Cu o scara dreapta pana la 30 kW, bara aprindea o
 * casuta-doua si atat. Scara cu radacina patrata da loc valorilor mici
 * (3 kW umple deja o treime) si tot ajunge la capat la [PLINA_KW], cat da
 * bateria asta la varf (Dr Prius: ~22 kW la descarcare, ~24 la incarcare).
 */
object ScaraPutere {
    const val PLINA_KW = 25f

    /** 0..1, fara semn: cat din jumatatea de bara se umple. */
    fun fractie(putereKw: Float): Float = sqrt((abs(putereKw) / PLINA_KW).coerceIn(0f, 1f))
}
