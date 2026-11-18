package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.LinearGradient
import android.graphics.Paint
import android.graphics.Shader
import ro.vix.kittdash.obd.CereriRare
import kotlin.math.roundToInt
import ro.vix.kittdash.navigatie.InstructiuneNav
import kotlin.math.min

/**
 * Alege ce ecran se deseneaza: bordul obisnuit sau unul din cele noi.
 *
 * Bordul obisnuit isi face singur asezarea (si panoul Waze). Ecranele noi
 * sunt desenate pe caietul de 1190 x 460 al Lexusului si aici doar se
 * potrivesc pe panza primita, pastrandu-si proportia.
 */
class Ecrane {

    private val bord = BordKitt()
    private val pensula = Pensula()
    private val viteza = EcranViteza(pensula)
    private val energie = EcranEnergie(pensula)
    private val consola = EcranConsola(pensula)
    private val hibrid = EcranHibrid(pensula)
    private val umbraBanda = Paint(Paint.ANTI_ALIAS_FLAG)

    /**
     * Cat asteptam intre cadre pe fiecare ecran. Unde se misca scanerul sau
     * sagetile raman 30 de cadre; consola n-are nimic care sa alerge, deci
     * 15 ii ajung.
     */
    fun perioadaMs(fel: FelEcran): Long = when (fel) {
        FelEcran.CONSOLA -> 66L
        else -> 33L
    }

    fun deseneaza(
        canvas: Canvas,
        latime: Float,
        inaltime: Float,
        stare: StareBord,
        nav: InstructiuneNav?,
        fel: FelEcran,
    ) {
        if (fel == FelEcran.HIBRID) CereriRare.hibridPeEcran()
        if (fel == FelEcran.ENERGIE) CereriRare.energiePeEcran()
        if (fel == FelEcran.BORD) {
            bord.deseneaza(canvas, latime, inaltime, stare, nav)
            return
        }
        canvas.drawColor(Pensula.FUNDAL)
        if (latime <= 0f || inaltime <= 0f) return

        val scara = min(latime / Pensula.LATIME, inaltime / Pensula.INALTIME)
        val salvat = canvas.save()
        canvas.translate(
            (latime - Pensula.LATIME * scara) / 2f,
            (inaltime - Pensula.INALTIME * scara) / 2f,
        )
        canvas.scale(scara, scara)
        when (fel) {
            FelEcran.VITEZA -> viteza.deseneaza(canvas, stare, if (stare.melodie != null) null else nav)
            FelEcran.ENERGIE -> energie.deseneaza(canvas, stare)
            FelEcran.CONSOLA -> consola.deseneaza(canvas, stare)
            FelEcran.HIBRID -> hibrid.deseneaza(canvas, stare, stare.hibrid, stare.drum)
            FelEcran.BORD -> Unit
        }
        stare.melodie?.let { bandaMelodie(canvas, it, stare.melodieProgres) }
        canvas.restoreToCount(salvat)

        if (stare.noapte) canvas.drawColor(BordKitt.PERDEA_NOAPTE)
    }

    /**
     * Melodia noua, trecand o data de la dreapta la stanga prin fasia de jos
     * din mijloc (x 225-935, y 392-432, cum a desenat-o Victor pe 18.11.2026).
     * Fara chenar: doar o umbra neagra, ca sa nu se amestece cu ce e dedesubt.
     */
    private fun bandaMelodie(c: Canvas, melodie: String, progres: Float) {
        val st = BANDA_STANGA
        val dr = BANDA_DREAPTA
        val opacitate = when {
            progres < 0.12f -> progres / 0.12f
            progres > 0.88f -> (1f - progres) / 0.12f
            else -> 1f
        }.coerceIn(0f, 1f)
        val negru = android.graphics.Color.argb((235 * opacitate).roundToInt(), 5, 5, 5)
        val gol = android.graphics.Color.argb(0, 5, 5, 5)
        umbraBanda.shader = LinearGradient(
            st - 40f, 0f, dr + 40f, 0f,
            intArrayOf(gol, negru, negru, gol), floatArrayOf(0f, 0.08f, 0.92f, 1f), Shader.TileMode.CLAMP,
        )
        c.drawRect(st - 40f, BANDA_SUS, dr + 40f, BANDA_JOS, umbraBanda)

        val marime = 28f
        val latime = pensula.latimeText(marime, melodie, gros = true) * 1.05f
        val x = (dr + 10f) + ((st - latime - 10f) - (dr + 10f)) * progres
        val salvat = c.save()
        c.clipRect(st, BANDA_SUS, dr, BANDA_JOS)
        pensula.text(c, x, BANDA_JOS - 10f, marime, Pensula.AMBRA, melodie, spatiere = 1.5f, gros = true, alfa = opacitate)
        c.restoreToCount(salvat)
    }

    companion object {
        const val BANDA_STANGA = 225f
        const val BANDA_DREAPTA = 935f
        const val BANDA_SUS = 392f
        const val BANDA_JOS = 434f
    }
}
