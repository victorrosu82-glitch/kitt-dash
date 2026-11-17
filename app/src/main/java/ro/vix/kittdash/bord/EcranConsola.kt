package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Paint
import ro.vix.kittdash.bord.Pensula.Companion.ALB
import ro.vix.kittdash.bord.Pensula.Companion.GALBEN
import ro.vix.kittdash.bord.Pensula.Companion.GALBEN_STINS
import ro.vix.kittdash.bord.Pensula.Companion.GRI
import ro.vix.kittdash.bord.Pensula.Companion.LAMPA
import ro.vix.kittdash.bord.Pensula.Companion.LAMPA_STINSA
import ro.vix.kittdash.bord.Pensula.Companion.ROSU
import ro.vix.kittdash.bord.Pensula.Companion.STINS
import ro.vix.kittdash.bord.Pensula.Companion.VERDE
import ro.vix.kittdash.bord.Pensula.Companion.VERDE_STINS
import ro.vix.kittdash.obd.ModHibrid
import kotlin.math.PI
import kotlin.math.atan2
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Ecranul 4: pupitrul lui KITT din serial - ceasuri de bare verzi, galbene
 * si rosii, cifre de sapte segmente si lampi galbene. Fiecare ceas e o
 * marime pe care Lexusul chiar o da; vezi tabelul din
 * `docs/macheta-ecran-consola.png` si din `docs/ce-da-masina.md`.
 *
 * Lampile galbene se aprind numai cand e ceva de spus, cu aceleasi praguri
 * ca avertizarile bordului ([Avertizare]).
 */
class EcranConsola(private val p: Pensula) {

    fun deseneaza(c: Canvas, s: StareBord) {
        p.antet(c, s, FelEcran.CONSOLA.titlu, cuScaner = false)
        turatie(c, s)

        // sase ceasuri de bare, in doua coloane
        ceas(c, 40f, 268f, false, "AER ADMISIE °C", s.tempAer, 0f, 80f, ::verdeApoiRosu, grade(s.tempAer))
        ceas(c, 330f, 268f, false, "EXTERIOR °C", s.tempExterioara, -20f, 40f, { VERDE }, grade(s.tempExterioara))
        ceas(c, 40f, 338f, false, "CATALIZATOR °C", s.tempCatalizator, 0f, 900f, ::verdeApoiRosu, grade(s.tempCatalizator))
        val tensiuneMica = s.tensiune12 != null && s.tensiune12 <= Avertizare.PRAG_TENSIUNE
        ceas(c, 330f, 338f, tensiuneMica, "BORD 12 V", s.tensiune12, 10f, 15f, ::rosuApoiVerde,
            s.tensiune12?.let { "%.1f".format(it).replace('.', ',') } ?: BordKitt.LIPSA)
        val fierbinte = s.tempLichid != null && s.tempLichid >= Avertizare.PRAG_LICHID
        ceas(c, 40f, 408f, fierbinte, "LICHID RACIRE °C", s.tempLichid, 0f, 120f, ::verdeApoiRosu, grade(s.tempLichid))
        ceas(c, 330f, 408f, false, "CONSUM L/H", s.consumLh, 0f, 20f, ::verdeApoiRosu,
            s.consumLh?.let { "%.1f".format(it).replace('.', ',') } ?: BordKitt.LIPSA)

        bateriaHv(c, s)
        vitezaSiModuri(c, s)
        coloane(c, s)
        p.puncte(c, FelEcran.CONSOLA)
    }

    /** Arcul care urca din stanga-jos, cu turatia in cifre sub el. */
    private fun turatie(c: Canvas, s: StareBord) {
        val fractie = s.turatie / TURATIE_MAXIMA
        for (i in 0 until ARC) {
            val f = i / (ARC - 1f)
            val x = 60f + f * 490f
            val y = yArc(f)
            val unghi = atan2(yArc(f + 0.01f) - yArc(f - 0.01f), 0.02f * 490f) * 180f / PI.toFloat()
            val culoare = if (f < 0.45f) VERDE else if (f < 0.72f) GALBEN else ROSU
            val aprins = s.areLegatura && f <= fractie
            c.save()
            c.rotate(unghi, x, y)
            p.dreptunghi(c, x - 5f, y - 11f, 10f, 22f, if (aprins) culoare else stinsa(culoare), 2f)
            c.restore()
        }
        listOf(0f to "0", 2000f to "2000", 4000f to "4000", 6000f to "6000").forEach { (v, eticheta) ->
            val f = v / TURATIE_MAXIMA
            p.text(c, 60f + f * 490f, yArc(f) + 30f, 11f, ALB, eticheta, Paint.Align.CENTER, 1f, gros = true)
        }
        lampa(c, 60f, 92f, false)
        val rpm = if (s.areLegatura) s.turatie.roundToInt().toString() else "----"
        p.afisaj(c, 250f, 146f, rpm, 4, 26f, 44f, ROSU)
        p.dreptunghi(c, 412f, 170f, 46f, 20f, ROSU, 2f)
        p.text(c, 435f, 185f, 12f, NEGRU, "RPM", Paint.Align.CENTER, 1f, gros = true)
    }

    /** Tensiunea, curentul si puterea bateriei HV - VDC, AMP si AUX POWER din serial. */
    private fun bateriaHv(c: Canvas, s: StareBord) {
        val x = 650f
        val randuri = listOf(
            Triple("TENSIUNE HV", s.hvTensiune?.let { (it - 200f) / 80f }, s.hvTensiune?.let { "${it.roundToInt()} V" }),
            Triple("CURENT HV", s.hvCurent?.let { kotlin.math.abs(it) / 150f }, s.hvCurent?.let { "%+.0f A".format(it) }),
            Triple("PUTERE", if (s.areLegatura) kotlin.math.abs(s.putereKw) / 30f else null,
                if (s.areLegatura) "%+.0f kW".format(s.putereKw) else null),
        )
        // Tensiunea HV are o banda normala lata (~200-280 V), deci ramane verde.
        val culori = listOf<(Float) -> Int>({ VERDE }, ::verdeApoiRosu, ::verdeApoiRosu)
        randuri.forEachIndexed { i, (eticheta, fractie, val_) ->
            val y = 96f + i * 60f
            lampa(c, x, y, false)
            p.text(c, x + 170f, y + 11f, 11f, ALB, eticheta, Paint.Align.RIGHT, 1f, gros = true)
            bare(c, x, y + 20f, 4, fractie ?: 0f, culori[i], 170f)
            p.text(c, x + 176f, y + 34f, 11f, GRI, val_ ?: BordKitt.LIPSA, spatiere = 1f)
        }
    }

    private fun vitezaSiModuri(c: Canvas, s: StareBord) {
        val viteza = if (s.areLegatura) s.vitezaKmh.roundToInt().toString() else "--"
        p.afisaj(c, 910f, 84f, viteza, 4, 28f, 50f, ROSU)
        p.text(c, 1150f, 160f, 11f, ALB, "KM/H", Paint.Align.RIGHT, 2f, gros = true)
        listOf(ModHibrid.EV, ModHibrid.HYBRID, ModHibrid.REGEN, ModHibrid.READY).forEachIndexed { i, mod ->
            val x = 880f + i * 70f
            val activ = s.mod == mod
            p.text(c, x + 20f, 186f, 10f, ALB, mod.eticheta, Paint.Align.CENTER, 0.5f, gros = true)
            p.dreptunghi(c, x + 4f, 194f, 32f, 14f, if (activ) VERDE else VERDE_STINS, 1f)
            p.dreptunghi(c, x + 4f, 210f, 32f, 14f, if (activ) ROSU else STINS, 1f)
        }
    }

    /** Coloanele mari din dreapta - POWER, NORMAL, AUTO, PURSUIT in serial. */
    private fun coloane(c: Canvas, s: StareBord) {
        val bateriaScazuta = s.socProcent != null && s.socProcent <= Avertizare.PRAG_BATERIE
        val recuperare = if (s.putereKw < 0f) -s.putereKw / 30f * 100f else 0f
        val lista = listOf(
            Coloana("BATERIE", s.socProcent, if (bateriaScazuta) ROSU else VERDE, bateriaScazuta),
            Coloana("CLAPETA", s.clapeta, VERDE, false),
            Coloana("SARCINA", s.sarcina, GALBEN, false),
            Coloana("REGEN", if (s.areLegatura) recuperare.coerceIn(0f, 100f) else null, ROSU, false),
        )
        lista.forEachIndexed { i, col ->
            val x = 860f + i * 80f
            lampa(c, x, 262f, col.lampa)
            p.text(c, x + 13f, 296f, 11f, ALB, col.eticheta, Paint.Align.CENTER, 0.5f, gros = true)
            val h = 130f
            val y = 306f
            val plin = h * ((col.procent ?: 0f) / 100f).coerceIn(0f, 1f)
            p.dreptunghi(c, x, y, 26f, h, stinsa(col.culoare), 1f)
            p.dreptunghi(c, x, y + h - plin, 26f, plin, col.culoare, 1f)
            p.text(c, x + 13f, y + h + 16f, 11f, GRI, col.procent?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA,
                Paint.Align.CENTER, 1f)
        }
    }

    private data class Coloana(val eticheta: String, val procent: Float?, val culoare: Int, val lampa: Boolean)

    private fun ceas(
        c: Canvas, x: Float, y: Float, lampaAprinsa: Boolean, eticheta: String,
        valoare: Float?, min: Float, max: Float, culori: (Float) -> Int, text: String,
    ) {
        lampa(c, x, y - 26f, lampaAprinsa)
        p.text(c, x + 270f, y - 12f, 12f, ALB, eticheta, Paint.Align.RIGHT, 1.5f, gros = true)
        val fractie = valoare?.let { ((it - min) / (max - min)).coerceIn(0f, 1f) } ?: 0f
        bare(c, x, y, 7, fractie, culori, 270f)
        p.text(c, x + 270f, y + 34f, 11f, if (lampaAprinsa) LAMPA else GRI, text, Paint.Align.RIGHT, 1f)
    }

    private fun bare(c: Canvas, x: Float, y: Float, n: Int, fractie: Float, culori: (Float) -> Int, latime: Float) {
        val w = latime / n
        val aprinse = (fractie * n).roundToInt()
        for (i in 0 until n) {
            val culoare = culori(i / n.toFloat())
            p.dreptunghi(c, x + i * w, y, w - 3f, 16f, if (i < aprinse) culoare else stinsa(culoare), 1f)
        }
    }

    private fun lampa(c: Canvas, x: Float, y: Float, aprinsa: Boolean) {
        if (aprinsa) p.dreptunghi(c, x - 3f, y - 3f, 32f, 19f, LAMPA, 2f, 0.18f)
        p.dreptunghi(c, x, y, 26f, 13f, if (aprinsa) LAMPA else LAMPA_STINSA, 1f)
    }

    private fun yArc(f: Float): Float = 200f - sin(f * PI.toFloat() * 0.62f) * 118f + f * f * 40f

    private fun grade(v: Float?): String = v?.roundToInt()?.toString() ?: BordKitt.LIPSA

    companion object {
        private const val ARC = 44
        const val TURATIE_MAXIMA = 6000f
        private val NEGRU = android.graphics.Color.BLACK

        fun verdeApoiRosu(f: Float): Int = if (f < 0.65f) VERDE else if (f < 0.85f) GALBEN else ROSU
        fun rosuApoiVerde(f: Float): Int = if (f < 0.2f) ROSU else if (f < 0.35f) GALBEN else VERDE

        fun stinsa(culoare: Int): Int = when (culoare) {
            VERDE -> VERDE_STINS
            GALBEN -> GALBEN_STINS
            else -> STINS
        }
    }
}
