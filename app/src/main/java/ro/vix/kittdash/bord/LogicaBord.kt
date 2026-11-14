package ro.vix.kittdash.bord

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Deciziile bordului, despartite de desenul propriu-zis: cate bare se aprind,
 * ce culoare are fiecare, cat de tare straluceste o casuta a scanerului.
 *
 * Sunt scoase aici fiindca desenul cere un ecran, iar deciziile nu. Asa pot
 * fi probate pe calculator, fara telefon - si tocmai ele se pot gresi fara
 * sa se vada, de pilda daca bara de putere ar ramane goala la recuperare.
 */
object LogicaBord {

    /** Ce culoare are o bara a ceasului de viteza. */
    enum class CuloareBara { STINSA, VARF, ROSIE, PORTOCALIE }

    /** Ce culoare are o treapta a bateriei. */
    enum class CuloareBaterie { STINSA, ROSIE, AMBRA }

    /** Ce culoare are o treapta a barei de putere. */
    enum class CuloarePutere { STINSA, AMBRA, PORTOCALIE, ROSIE, CYAN }

    /**
     * Ultima bara aprinsa a ceasului, de la 0 la [BARE]-1. Masina oprita
     * aprinde doar prima bara, ca ceasul sa nu arate stins de tot.
     */
    fun bareAprinse(vitezaKmh: Float, bare: Int = BARE): Int =
        (vitezaKmh / BordKitt.VITEZA_MAXIMA * (bare - 1)).roundToInt().coerceIn(0, bare - 1)

    fun culoareBara(index: Int, aprinse: Int, vitezaKmh: Float): CuloareBara = when {
        vitezaKmh <= 0.5f && index > 0 -> CuloareBara.STINSA
        index > aprinse -> CuloareBara.STINSA
        index == aprinse -> CuloareBara.VARF
        index > 43 -> CuloareBara.PORTOCALIE
        else -> CuloareBara.ROSIE
    }

    /** Cate trepte ale bateriei sunt pline, cu zecimale. */
    fun trepteBaterie(socProcent: Float?, trepte: Int = TREPTE_BATERIE): Float =
        (socProcent ?: 0f) / 100f * trepte

    fun culoareBaterie(index: Int, trepte: Float): CuloareBaterie = when {
        index >= trepte -> CuloareBaterie.STINSA
        index < 3 -> CuloareBaterie.ROSIE
        else -> CuloareBaterie.AMBRA
    }

    /**
     * Cate trepte se aprind in sus (tractiune) si in jos (recuperare).
     * Semnul puterii vine din masina: plus cand bateria da curent.
     */
    fun trepteSus(putereKw: Float, trepte: Int = TREPTE_PUTERE): Float =
        if (putereKw > 0f) putereKw / BordKitt.PUTERE_TRACTIUNE * trepte else 0f

    fun trepteJos(putereKw: Float, trepte: Int = TREPTE_PUTERE): Float =
        if (putereKw < 0f) -putereKw / BordKitt.PUTERE_RECUPERARE * trepte else 0f

    fun culoarePutereSus(index: Int, trepte: Float): CuloarePutere = when {
        index >= trepte -> CuloarePutere.STINSA
        index > 4 -> CuloarePutere.ROSIE
        index > 2 -> CuloarePutere.PORTOCALIE
        else -> CuloarePutere.AMBRA
    }

    fun culoarePutereJos(index: Int, trepte: Float): CuloarePutere =
        if (index < trepte) CuloarePutere.CYAN else CuloarePutere.STINSA

    /**
     * Unde se afla scanerul si incotro merge, dintr-o faza 0..2*pasi.
     * La intoarcere se schimba si directia, iar coada ramane in urma lui.
     */
    fun pozitiaScanerului(faza: Float, pasi: Float = Netezitor.SCANER_PASI): Float =
        if (faza < pasi) faza else 2f * pasi - faza

    fun directiaScanerului(faza: Float, pasi: Float = Netezitor.SCANER_PASI): Float =
        if (faza < pasi) 1f else -1f

    /**
     * Cat de aprinsa e o casuta a scanerului: plina acolo unde se afla,
     * stingandu-se repede inainte si incet in urma - asa pare ca lasa dara.
     */
    fun stralucireScaner(index: Int, pozitie: Float, directie: Float): Float {
        val d = index - pozitie
        val departare = abs(d)
        return if (d * directie < 0f) {
            max(0.1f, 1f - departare / 7f)
        } else {
            max(0.1f, 1f - departare / 2.5f)
        }
    }

    const val BARE = 54
    const val TREPTE_BATERIE = 14
    const val TREPTE_PUTERE = 7
}
