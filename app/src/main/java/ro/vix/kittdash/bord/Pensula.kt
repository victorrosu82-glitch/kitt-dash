package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import ro.vix.kittdash.obd.ModHibrid
import kotlin.math.roundToInt

/**
 * Uneltele comune ale ecranelor noi (viteza, energie, consola): culorile,
 * scrisul, antetul cu scanerul lui KITT si cifrele de sapte segmente.
 *
 * Ecranele noi deseneaza pe un caiet de [LATIME] x [INALTIME], exact panza
 * pe care o lasa Android Auto pe Lexus. Coordonatele sunt cele din machete
 * (`docs/macheta-ecrane-2-3.html`), ca desenul sa iasa la fel.
 */
class Pensula {

    val umplere = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        style = Paint.Style.STROKE
        strokeCap = Paint.Cap.ROUND
        strokeJoin = Paint.Join.ROUND
    }
    private val scris = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val scrisGros = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val dreptunghi = RectF()
    val drum = Path()

    fun dreptunghi(c: Canvas, x: Float, y: Float, w: Float, h: Float, culoare: Int, raza: Float = 0f, alfa: Float = 1f) {
        umplere.color = culoare
        umplere.alpha = (alfa * 255).roundToInt().coerceIn(0, 255)
        dreptunghi.set(x, y, x + w, y + h)
        if (raza > 0f) c.drawRoundRect(dreptunghi, raza, raza, umplere) else c.drawRect(dreptunghi, umplere)
        umplere.alpha = 255
    }

    fun rama(c: Canvas, x: Float, y: Float, w: Float, h: Float, culoare: Int, grosime: Float, raza: Float = 0f) {
        linie.color = culoare
        linie.strokeWidth = grosime
        dreptunghi.set(x, y, x + w, y + h)
        c.drawRoundRect(dreptunghi, raza, raza, linie)
    }

    fun linie(c: Canvas, x1: Float, y1: Float, x2: Float, y2: Float, culoare: Int, grosime: Float, alfa: Float = 1f) {
        linie.color = culoare
        linie.alpha = (alfa * 255).roundToInt().coerceIn(0, 255)
        linie.strokeWidth = grosime
        c.drawLine(x1, y1, x2, y2, linie)
        linie.alpha = 255
    }

    fun cerc(c: Canvas, x: Float, y: Float, raza: Float, culoare: Int, plin: Boolean, grosime: Float = 2f) {
        if (plin) {
            umplere.color = culoare
            c.drawCircle(x, y, raza, umplere)
        } else {
            linie.color = culoare
            linie.strokeWidth = grosime
            c.drawCircle(x, y, raza, linie)
        }
    }

    fun text(
        c: Canvas,
        x: Float,
        y: Float,
        marime: Float,
        culoare: Int,
        continut: String,
        aliniere: Paint.Align = Paint.Align.LEFT,
        spatiere: Float = 0f,
        gros: Boolean = false,
        alfa: Float = 1f,
    ) {
        val p = if (gros) scrisGros else scris
        p.textSize = marime
        p.color = culoare
        p.alpha = (alfa * 255).roundToInt().coerceIn(0, 255)
        p.textAlign = aliniere
        // In machete spatierea e in pixeli, aici se cere in "em".
        p.letterSpacing = if (marime > 0f) spatiere / marime else 0f
        c.drawText(continut, x, y, p)
        p.letterSpacing = 0f
        p.alpha = 255
    }

    fun latimeText(marime: Float, continut: String, gros: Boolean = false): Float {
        val p = if (gros) scrisGros else scris
        p.textSize = marime
        p.letterSpacing = 0f
        return p.measureText(continut)
    }

    /**
     * Antetul comun: numele masinii, numele ecranului la mijloc, modul in
     * dreapta, iar sub ele scanerul rosu care alearga dus-intors.
     */
    fun antet(c: Canvas, stare: StareBord, titlu: String, cuScaner: Boolean = true) {
        text(c, 40f, 32f, 17f, ROSU_APRINS, "LEXUS NX 300h", spatiere = 4f)
        text(c, LATIME / 2f, 32f, 13f, ROSU_STERS, titlu, Paint.Align.CENTER, 6f)
        text(c, 1150f, 32f, 17f, culoareMod(stare.mod), stare.mod.eticheta, Paint.Align.RIGHT, 4f)
        linie(c, 40f, 42f, 1150f, 42f, LINIE_PANOU, 1.5f)
        if (cuScaner) scaner(c, stare)
    }

    private fun scaner(c: Canvas, stare: StareBord) {
        val pozitie = LogicaBord.pozitiaScanerului(stare.scanerFaza)
        val directie = LogicaBord.directiaScanerului(stare.scanerFaza)
        val n = Netezitor.SCANER_PASI.toInt() + 1
        val pas = SCANER_LATIME / n
        val start = (LATIME - SCANER_LATIME) / 2f
        for (i in 0 until n) {
            val op = LogicaBord.stralucireScaner(i, pozitie, directie)
            val x = start + i * pas
            if (op > 0.08f) dreptunghi(c, x - 3f, 49f, pas + 2f, 34f, ROSU, alfa = op * 0.28f)
            if (op > 0.08f) {
                dreptunghi(c, x, 52f, pas - 4f, 28f, ROSU, alfa = maxOf(0.12f, op))
            } else {
                dreptunghi(c, x, 52f, pas - 4f, 28f, STINS)
            }
        }
    }

    /** Punctele de jos: pe care ecran esti, din cate. */
    fun puncte(c: Canvas, fel: FelEcran) {
        val toate = FelEcran.entries
        val x0 = LATIME / 2f - (toate.size - 1) * 9f
        toate.forEachIndexed { i, f ->
            cerc(c, x0 + i * 18f, 447f, 4f, if (f == fel) ROSU else STINS, plin = true)
        }
    }

    /**
     * O cifra de sapte segmente, ca afisajele lui KITT. Segmentele stinse
     * raman vizibile, foarte intunecate, ca la afisajele adevarate.
     */
    fun cifra7(c: Canvas, x: Float, y: Float, w: Float, h: Float, ch: Char, culoare: Int) {
        val aprinse = SEGMENTE[ch] ?: ""
        val g = w * 0.22f
        val jum = h / 2f
        fun seg(cod: Char, sx: Float, sy: Float, sw: Float, sh: Float) {
            dreptunghi(c, sx + 1f, sy + 1f, sw - 2f, sh - 2f, if (cod in aprinse) culoare else SEGMENT_STINS, 2f)
        }
        seg('a', x + g, y, w - 2 * g, g)
        seg('d', x + g, y + h - g, w - 2 * g, g)
        seg('g', x + g, y + jum - g / 2, w - 2 * g, g)
        seg('f', x, y + g, g, jum - 1.5f * g)
        seg('b', x + w - g, y + g, g, jum - 1.5f * g)
        seg('e', x, y + jum + g / 2, g, jum - 1.5f * g)
        seg('c', x + w - g, y + jum + g / 2, g, jum - 1.5f * g)
    }

    /** Un afisaj intreg: rama inchisa si [n] cifre, aliniate la dreapta. */
    fun afisaj(c: Canvas, x: Float, y: Float, textul: String, n: Int, w: Float, h: Float, culoare: Int) {
        dreptunghi(c, x - 10f, y - 10f, n * (w + 12f) + 8f, h + 20f, Color.parseColor("#120303"), 3f)
        rama(c, x - 10f, y - 10f, n * (w + 12f) + 8f, h + 20f, Color.parseColor("#2a0606"), 1.5f, 3f)
        textul.takeLast(n).padStart(n, ' ').forEachIndexed { i, ch ->
            cifra7(c, x + i * (w + 12f), y, w, h, ch, culoare)
        }
    }

    fun culoareMod(mod: ModHibrid): Int = when (mod) {
        ModHibrid.HYBRID -> AMBRA
        ModHibrid.REGEN, ModHibrid.EV -> CYAN
        else -> ROSU_STERS
    }

    companion object {
        /** Panza pe care o lasa Android Auto pe ecranul Lexusului. */
        const val LATIME = 1190f
        const val INALTIME = 460f

        private const val SCANER_LATIME = 790f

        val STINS = Color.parseColor("#2b0c0a")
        val ROSU = Color.parseColor("#ff2d1c")
        val ROSU_APRINS = Color.parseColor("#ff3b2a")
        val ROSU_STERS = Color.parseColor("#8a2a22")
        val PORTOCALIU = Color.parseColor("#ff7a1a")
        val AMBRA = Color.parseColor("#ffb21c")
        val AMBRA_STINSA = Color.parseColor("#b8861c")
        val CYAN = Color.parseColor("#35d0ff")
        val ETICHETA = Color.parseColor("#9a2f26")
        val VALOARE = Color.parseColor("#ff5a2a")
        val LINIE_PANOU = Color.parseColor("#3a0f0c")
        val VARF = Color.parseColor("#ffd6cc")
        val FUNDAL = Color.parseColor("#050505")
        val CASETA = Color.parseColor("#0a0302")
        val SEGMENT_STINS = Color.parseColor("#1a0404")

        // Culorile pupitrului din serial: verde, galben, rosu - si stinse.
        val VERDE = Color.parseColor("#2fd34a")
        val GALBEN = Color.parseColor("#ffd21c")
        val VERDE_STINS = Color.parseColor("#0c2a12")
        val GALBEN_STINS = Color.parseColor("#2e2706")
        val LAMPA = Color.parseColor("#ffd21c")
        val LAMPA_STINSA = Color.parseColor("#2a2306")
        val ALB = Color.parseColor("#d8d8d8")
        val GRI = Color.parseColor("#7a7a7a")

        private val SEGMENTE = mapOf(
            '0' to "abcdef", '1' to "bc", '2' to "abged", '3' to "abgcd", '4' to "fgbc",
            '5' to "afgcd", '6' to "afgedc", '7' to "abc", '8' to "abcdefg", '9' to "abcdfg",
            '-' to "g", ' ' to "",
        )
    }
}
