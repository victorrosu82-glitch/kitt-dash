package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import ro.vix.kittdash.bord.Pensula.Companion.AMBRA
import ro.vix.kittdash.bord.Pensula.Companion.CYAN
import ro.vix.kittdash.bord.Pensula.Companion.ETICHETA
import ro.vix.kittdash.bord.Pensula.Companion.PORTOCALIU
import ro.vix.kittdash.bord.Pensula.Companion.ROSU
import ro.vix.kittdash.bord.Pensula.Companion.ROSU_APRINS
import ro.vix.kittdash.bord.Pensula.Companion.ROSU_STERS
import ro.vix.kittdash.bord.Pensula.Companion.STINS
import ro.vix.kittdash.bord.Pensula.Companion.VALOARE
import ro.vix.kittdash.bord.Pensula.Companion.VARF
import ro.vix.kittdash.navigatie.InstructiuneNav
import kotlin.math.roundToInt

/**
 * Ecranul 2: viteza, mare, cat sa se citeasca dintr-o privire. Sub ea o
 * rigla de la 0 la 180, in stanga bateria, in dreapta puterea, iar jos
 * manevra urmatoare pe un singur rand, numai cat merge navigatia.
 * Macheta: `docs/macheta-ecran-2.png`.
 */
class EcranViteza(private val p: Pensula) {

    private val imagine = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)
    private val tinta = RectF()

    fun deseneaza(c: Canvas, s: StareBord, nav: InstructiuneNav?) {
        p.antet(c, s, FelEcran.VITEZA.titlu)

        // cifrele mari, de sapte segmente, ca afisajele consolei; cele
        // nefolosite raman stinse, ca la un afisaj adevarat
        val viteza = if (s.areLegatura) s.vitezaKmh.roundToInt().coerceIn(0, 999).toString() else "---"
        val xc = 595f - (3 * CIFRA_L + 2 * CIFRA_GOL) / 2f
        viteza.padStart(3, ' ').forEachIndexed { i, ch ->
            p.cifra7(c, xc + i * (CIFRA_L + CIFRA_GOL), 112f, CIFRA_L, CIFRA_H, ch, ROSU_APRINS)
        }
        p.text(c, 595f, 352f, 20f, ROSU_STERS, "KM/H", Paint.Align.CENTER, 8f)

        // rigla de viteza pe toata latimea
        val x0 = 95f
        val x1 = 1095f
        val pas = (x1 - x0) / RIGLA
        val aprinse = (s.vitezaKmh / VITEZA_MAXIMA * RIGLA).roundToInt().coerceIn(0, RIGLA)
        for (i in 0 until RIGLA) {
            val culoare = when {
                i >= aprinse -> STINS
                i == aprinse - 1 -> VARF
                i > RIGLA * 0.75f -> PORTOCALIU
                else -> ROSU
            }
            p.dreptunghi(c, x0 + i * pas, 374f, pas - 4f, 14f, culoare)
        }
        for (v in 0..180 step 30) {
            p.text(c, x0 + v / VITEZA_MAXIMA * (x1 - x0), 408f, 13f, ROSU_STERS, "$v", Paint.Align.CENTER)
        }

        // bateria, in stanga
        p.text(c, 40f, 120f, 13f, ETICHETA, "HV BATTERY", spatiere = 2f)
        val trepteBat = (s.socProcent ?: 0f) / 10f
        for (i in 0 until 10) {
            val culoare = if (i < trepteBat) (if (i < 2) ROSU else AMBRA) else STINS
            p.dreptunghi(c, 40f, 130f + (9 - i) * 15f, 48f, 11f, culoare)
        }
        p.text(c, 100f, 262f, 38f, AMBRA, s.socProcent?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA)

        // altitudinea, sub baterie
        p.text(c, 40f, 300f, 13f, ETICHETA, "ALTITUDINE", spatiere = 2f)
        p.text(c, 40f, 338f, 30f, VALOARE, s.altitudineM?.let { "${it.roundToInt()} m" } ?: BordKitt.LIPSA)

        // puterea, in dreapta: sus cat trage, jos cat recupereaza
        p.text(c, 1150f, 120f, 13f, ETICHETA, "POWER", Paint.Align.RIGHT, 2f)
        val sus = LogicaBord.trepteSus(s.putereKw, TREPTE_PUTERE)
        val jos = LogicaBord.trepteJos(s.putereKw, TREPTE_PUTERE)
        for (j in 0 until TREPTE_PUTERE) {
            val culoareSus = if (j < sus) (if (j > 2) PORTOCALIU else AMBRA) else STINS
            p.dreptunghi(c, 1102f, 130f + (TREPTE_PUTERE - 1 - j) * 15f, 48f, 11f, culoareSus)
            p.dreptunghi(c, 1102f, 215f + j * 15f, 48f, 11f, if (j < jos) CYAN else STINS)
        }
        val kw = s.putereKw.roundToInt()
        val textKw = if (kw > 0) "+$kw" else "$kw"
        p.text(c, 1090f, 262f, 38f, if (s.putereKw < 0f) CYAN else AMBRA, textKw, Paint.Align.RIGHT)
        p.text(c, 1150f, 262f, 14f, ETICHETA, "kW", Paint.Align.RIGHT, 1f)

        if (nav != null) manevra(c, nav)
        p.puncte(c, FelEcran.VITEZA)
    }

    /** Manevra urmatoare, pe un rand, centrata sub rigla. */
    private fun manevra(c: Canvas, nav: InstructiuneNav) {
        val strada = nav.strada?.uppercase()?.let { taie(it, 16f, 380f) } ?: ""
        val latDist = p.latimeText(22f, nav.distanta, gros = false)
        val latStrada = p.latimeText(16f, strada) * 1.15f
        val total = 36f + latDist + 16f + latStrada
        var x = 595f - total / 2f

        val sageata = nav.sageata
        if (sageata != null && !sageata.isRecycled) {
            imagine.colorFilter = PorterDuffColorFilter(AMBRA, PorterDuff.Mode.SRC_IN)
            tinta.set(x, 414f, x + 28f, 442f)
            c.drawBitmap(sageata, null, tinta, imagine)
        }
        x += 36f
        val aproape = (nav.metri ?: Float.MAX_VALUE) < InstructiuneNav.APROAPE_METRI
        p.text(c, x, 437f, 22f, if (aproape) ROSU else ROSU_APRINS, nav.distanta)
        x += latDist + 16f
        p.text(c, x, 437f, 16f, VALOARE, strada, spatiere = 2f)
    }

    private fun taie(text: String, marime: Float, maxim: Float): String {
        if (p.latimeText(marime, text) <= maxim) return text
        var t = text
        while (t.length > 1 && p.latimeText(marime, "$t…") > maxim) t = t.dropLast(1)
        return "$t…"
    }

    companion object {
        const val RIGLA = 60
        const val CIFRA_L = 100f
        const val CIFRA_H = 210f
        const val CIFRA_GOL = 26f
        const val VITEZA_MAXIMA = 180f
        const val TREPTE_PUTERE = 5
    }
}
