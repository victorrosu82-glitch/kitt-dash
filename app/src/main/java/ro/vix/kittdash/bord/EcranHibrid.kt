package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Paint
import ro.vix.kittdash.bord.Pensula.Companion.ALB
import ro.vix.kittdash.bord.Pensula.Companion.AMBRA
import ro.vix.kittdash.bord.Pensula.Companion.CYAN
import ro.vix.kittdash.bord.Pensula.Companion.ETICHETA
import ro.vix.kittdash.bord.Pensula.Companion.LINIE_PANOU
import ro.vix.kittdash.bord.Pensula.Companion.ROSU
import ro.vix.kittdash.bord.Pensula.Companion.ROSU_STERS
import ro.vix.kittdash.bord.Pensula.Companion.VALOARE
import ro.vix.kittdash.bord.Pensula.Companion.VERDE
import ro.vix.kittdash.bord.Pensula.Companion.VERDE_STINS
import ro.vix.kittdash.obd.DateHibrid
import ro.vix.kittdash.obd.Drum
import kotlin.math.roundToInt

/**
 * Ecranul 5: sanatatea sistemului hibrid. In stanga cele 17 blocuri ale
 * bateriei pe o scara fixa de 15-18 V (un bloc slabit trebuie sa se vada
 * cazut), cu rezistenta interna sub fiecare; in dreapta bateria acum,
 * temperaturile ei si drumul de azi. Macheta: `docs/macheta-ecran-hibrid.png`.
 *
 * Ce nu e confirmat inca (temperaturile din sistem, rezervorul) are un
 * semn de intrebare si culoarea stinsa.
 */
class EcranHibrid(private val p: Pensula) {

    fun deseneaza(c: Canvas, s: StareBord, h: DateHibrid?, drum: Drum) {
        p.antet(c, s, FelEcran.HIBRID.titlu)
        blocuri(c, h)
        p.linie(c, 680f, 96f, 680f, 450f, LINIE_PANOU, 1.5f)
        bateriaAcum(c, s, h)
        temperaturi(c, h)
        drumul(c, drum, h)
        p.puncte(c, FelEcran.HIBRID)
    }

    private fun blocuri(c: Canvas, h: DateHibrid?) {
        p.text(c, 40f, 112f, 13f, ETICHETA, "BATERIE HV · 17 BLOCURI", spatiere = 2f)
        p.text(c, 640f, 112f, 13f, ROSU_STERS, "V / BLOC", Paint.Align.RIGHT, 2f)
        val x0 = 40f
        val x1 = 640f
        val jos = 330f
        val inalt = 190f
        val pas = (x1 - x0) / DateHibrid.BLOCURI
        fun y(v: Float) = jos - ((v - SCARA_JOS) / (SCARA_SUS - SCARA_JOS)).coerceIn(0f, 1f) * inalt
        for (v in listOf(15f, 15.5f, 16f, 16.5f, 17f, 17.5f, 18f)) {
            p.linie(c, x0, y(v), x1, y(v), LINIE_PANOU, 1f)
            p.text(c, x1 + 4f, y(v) + 4f, 10f, if (v <= 16f) ROSU else ROSU_STERS,
                "%.1f".format(v).replace('.', ','))
        }
        val tensiuni = h?.tensiuni.orEmpty()
        if (tensiuni.isEmpty()) {
            p.text(c, (x0 + x1) / 2f, jos - inalt / 2f, 16f, ETICHETA, "ASTEPT BLOCURILE BATERIEI…", Paint.Align.CENTER, 3f)
        }
        val slab = h?.celMaiSlab
        val tare = h?.celMaiTare
        for (i in 0 until DateHibrid.BLOCURI) {
            val x = x0 + i * pas + 4f
            val w = pas - 8f
            val v = tensiuni.getOrNull(i)
            val culoare = when (i) {
                slab -> ROSU
                tare -> AMBRA
                else -> CYAN
            }
            if (v != null) {
                val top = y(v)
                p.dreptunghi(c, x, top, w, jos - top, culoare, 2f, 0.85f)
                p.dreptunghi(c, x - 2f, top - 2f, w + 4f, 4f, culoare, 1f)
            }
            val marcat = i == slab || i == tare
            p.text(c, x + w / 2f, jos + 16f, 11f, if (marcat) culoare else ROSU_STERS, "${i + 1}",
                Paint.Align.CENTER, gros = marcat)
            h?.rezistente?.getOrNull(i)?.let {
                p.text(c, x + w / 2f, jos + 32f, 10f, REZISTENTA, "$it", Paint.Align.CENTER)
            }
        }
        p.text(c, x0, jos + 50f, 11f, ETICHETA, "REZISTENTA INTERNA PE BLOC", spatiere = 1.5f)

        val diferenta = h?.diferentaV
        p.text(c, 40f, 418f, 12f, ETICHETA, "DIFERENTA", spatiere = 2f)
        p.text(c, 40f, 446f, 26f,
            when {
                diferenta == null -> ETICHETA
                diferenta < 0.3f -> VERDE
                diferenta < 0.5f -> AMBRA
                else -> ROSU
            },
            diferenta?.let { "${(it * 1000).roundToInt()} mV" } ?: BordKitt.LIPSA)
        p.text(c, 200f, 418f, 12f, ETICHETA, "CEL MAI SLAB", spatiere = 2f)
        p.text(c, 200f, 446f, 26f, ROSU, slab?.let { "blocul ${it + 1}" } ?: BordKitt.LIPSA)
        p.text(c, 400f, 418f, 12f, ETICHETA, "REZISTENTA MEDIE", spatiere = 2f)
        val medie = h?.rezistente?.takeIf { it.isNotEmpty() }?.average()
        p.text(c, 400f, 446f, 26f, VALOARE, medie?.let { "${it.roundToInt()} mΩ" } ?: BordKitt.LIPSA)
    }

    private fun bateriaAcum(c: Canvas, s: StareBord, h: DateHibrid?) {
        val cx = 705f
        p.text(c, cx, 112f, 13f, ETICHETA, "INCARCARE", spatiere = 2f)
        p.text(c, cx, 160f, 48f, CYAN, s.socProcent?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA)
        p.text(c, cx + 170f, 112f, 13f, ETICHETA, "TENSIUNE", spatiere = 2f)
        val tensiune = s.hvTensiune ?: h?.tensiuni?.takeIf { it.isNotEmpty() }?.sum()
        p.text(c, cx + 170f, 150f, 28f, VALOARE, tensiune?.let { "${it.roundToInt()} V" } ?: BordKitt.LIPSA)
        p.text(c, cx + 330f, 112f, 13f, ETICHETA, "PUTERE", spatiere = 2f)
        val kw = s.putereKw.roundToInt()
        p.text(c, cx + 330f, 150f, 28f, if (s.putereKw < 0f) CYAN else AMBRA,
            if (!s.areLegatura) BordKitt.LIPSA else if (kw > 0) "+$kw kW" else "$kw kW")
    }

    private fun temperaturi(c: Canvas, h: DateHibrid?) {
        val cx = 705f
        p.text(c, cx, 196f, 13f, ETICHETA, "TEMPERATURI BATERIE · 7 SENZORI", spatiere = 2f)
        val t = h?.temperaturiBaterie.orEmpty()
        for (i in 0 until 7) {
            val x = cx + i * 62f
            p.dreptunghi(c, x, 212f, 40f, 70f, VERDE_STINS, 3f)
            val v = t.getOrNull(i) ?: continue
            val inalt = (v / 50f).coerceIn(0.05f, 1f) * 70f
            p.dreptunghi(c, x, 282f - inalt, 40f, inalt, if (v > 45f) ROSU else if (v > 35f) AMBRA else VERDE, 3f)
            p.text(c, x + 20f, 300f, 12f, ALB, "${v.roundToInt()}°", Paint.Align.CENTER, gros = true)
        }
        // senzorii 2-4 sunt cei trei ai Dr Prius (confirmat pe 18.11.2026, la 0,01 °C);
        // primul pare aerul de racire, ultimii trei nu stim inca ce sunt
        for (i in 0 until 7) {
            val confirmat = i in 1..3
            p.text(c, cx + i * 62f + 20f, 316f, 10f, if (confirmat) ETICHETA else ROSU_STERS,
                ETICHETE_TERMOMETRE[i], Paint.Align.CENTER, 1f, gros = confirmat)
        }
        p.text(c, cx, 330f, 13f, ETICHETA, "MOTOARE SI INVERTOR", spatiere = 2f)
        p.text(c, cx + 440f, 330f, 10f, ROSU_STERS, "(DE CONFIRMAT)", Paint.Align.RIGHT, 1f)
        h?.temperaturiSistem.orEmpty().take(4).forEachIndexed { i, (eticheta, v) ->
            val x = cx + i * 112f
            p.text(c, x, 350f, 11f, ROSU_STERS, eticheta, spatiere = 1.5f)
            p.text(c, x, 376f, 24f, AMBRA, "${v.roundToInt()}°C", alfa = 0.75f)
        }
    }

    private fun drumul(c: Canvas, d: Drum, h: DateHibrid?) {
        val cx = 705f
        p.linie(c, cx, 392f, 1150f, 392f, LINIE_PANOU, 1f)
        val randuri = listOf(
            Triple("PE BATERIE", d.procentBaterie?.let { "${it.roundToInt()}%" }, CYAN),
            Triple("RECUPERAT", if (d.km > 0.1) "%.2f kWh".format(d.kWhRecuperat).replace('.', ',') else null, CYAN),
            Triple("CONSUM", d.litriLa100?.let { "%.1f l/100".format(it).replace('.', ',') }, AMBRA),
            Triple("REZERVOR ?", h?.rezervorLitri?.let { "${it.roundToInt()} l" }, AMBRA),
        )
        randuri.forEachIndexed { i, (eticheta, valoare, culoare) ->
            val x = cx + i * 112f
            p.text(c, x, 414f, 11f, ETICHETA, eticheta, spatiere = 1.5f)
            p.text(c, x, 442f, 22f, culoare, valoare ?: BordKitt.LIPSA, alfa = if (i == 3) 0.75f else 1f)
        }
    }

    companion object {
        const val SCARA_JOS = 15f
        const val SCARA_SUS = 18f
        private val REZISTENTA = android.graphics.Color.parseColor("#7a3a30")
        private val ETICHETE_TERMOMETRE = listOf("AER?", "BAT 1", "BAT 2", "BAT 3", "", "", "")
    }
}
