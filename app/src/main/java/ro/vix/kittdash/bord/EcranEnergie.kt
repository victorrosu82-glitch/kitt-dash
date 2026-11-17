package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Paint
import ro.vix.kittdash.bord.Pensula.Companion.AMBRA
import ro.vix.kittdash.bord.Pensula.Companion.AMBRA_STINSA
import ro.vix.kittdash.bord.Pensula.Companion.CASETA
import ro.vix.kittdash.bord.Pensula.Companion.CYAN
import ro.vix.kittdash.bord.Pensula.Companion.ETICHETA
import ro.vix.kittdash.bord.Pensula.Companion.LINIE_PANOU
import ro.vix.kittdash.bord.Pensula.Companion.ROSU
import ro.vix.kittdash.bord.Pensula.Companion.STINS
import ro.vix.kittdash.bord.Pensula.Companion.VALOARE
import android.graphics.Color
import kotlin.math.floor
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.roundToInt

/**
 * Ecranul 3: pe unde curge energia, pe schema NX 300h E-Four vazuta de sus,
 * cu fata in stanga. Sagetile mici curg in sensul energiei: portocalii
 * dinspre motorul termic, albastre pe partea electrica. In dreapta, cifrele
 * bateriei HV. Macheta: `docs/macheta-ecran-3-*.png`.
 */
class EcranEnergie(private val p: Pensula) {

    fun deseneaza(c: Canvas, s: StareBord) {
        p.antet(c, s, FelEcran.ENERGIE.titlu)
        val f = FluxEnergie.din(s.turatie, s.putereKw, s.vitezaKmh)
        // cat au inaintat sagetile: patru pasi pe secunda
        val faza = s.timpS * PASI_PE_SECUNDA

        schema(c, s, f, faza)
        coloanaBaterie(c, s)
        p.puncte(c, FelEcran.ENERGIE)
    }

    private fun schema(c: Canvas, s: StareBord, f: FluxEnergie, faza: Float) {
        // conturul masinii, de sarma
        p.drum.reset()
        p.drum.moveTo(X0 + 60f, Y0)
        p.drum.lineTo(X1 - 40f, Y0)
        p.drum.quadTo(X1, Y0, X1, Y0 + 40f)
        p.drum.lineTo(X1, Y1 - 40f)
        p.drum.quadTo(X1, Y1, X1 - 40f, Y1)
        p.drum.lineTo(X0 + 60f, Y1)
        p.drum.quadTo(X0, Y1, X0, Y1 - 60f)
        p.drum.lineTo(X0, Y0 + 60f)
        p.drum.quadTo(X0, Y0, X0 + 60f, Y0)
        p.drum.close()
        p.umplere.color = Color.parseColor("#070202")
        c.drawPath(p.drum, p.umplere)
        p.linie.color = LINIE_PANOU
        p.linie.strokeWidth = 2f
        c.drawPath(p.drum, p.linie)
        p.linie(c, 330f, Y0 + 14f, 330f, Y1 - 14f, PARBRIZ, 1.5f)
        p.linie(c, 680f, Y0 + 14f, 680f, Y1 - 14f, PARBRIZ, 1.5f)

        val cRotiFata = if (f.recuperare || !f.motorLaRoti) CYAN else AMBRA
        val inapoi = f.recuperare

        // motorul termic -> transmisie
        flux(c, MOTOR_LA_TR, AMBRA, f.motorLaRoti || f.generator, faza)
        // transmisie <-> rotile din fata
        flux(c, if (inapoi) TR_LA_FATA_SUS.reversed() else TR_LA_FATA_SUS, cRotiFata, f.rotiFata, faza)
        flux(c, if (inapoi) TR_LA_FATA_JOS.reversed() else TR_LA_FATA_JOS, cRotiFata, f.rotiFata, faza)
        // baterie <-> transmisie (MG2 trage, sau MG1 incarca)
        val batTr = f.fata || f.generator
        flux(c, if (f.inBaterie) BAT_LA_TR.reversed() else BAT_LA_TR, if (f.generator) AMBRA else CYAN, batTr, faza)
        // baterie <-> motorul din spate <-> rotile din spate
        flux(c, if (f.inBaterie) BAT_LA_SPATE.reversed() else BAT_LA_SPATE, CYAN, f.spate, faza)
        flux(c, if (f.inBaterie) MGR_SUS.reversed() else MGR_SUS, CYAN, f.spate, faza)
        flux(c, if (f.inBaterie) MGR_JOS.reversed() else MGR_JOS, CYAN, f.spate, faza)

        roata(c, FA, Y0 + 22f, f.rotiFata, cRotiFata)
        roata(c, FA, Y1 - 22f, f.rotiFata, cRotiFata)
        roata(c, SA, Y0 + 22f, f.spate, CYAN)
        roata(c, SA, Y1 - 22f, f.spate, CYAN)

        // motorul termic, cu turatia
        caseta(c, ENG_X, YM, 80f, 64f, "MOTOR", f.motor, AMBRA)
        val rpm = if (s.areLegatura) s.turatie.roundToInt().toString() else BordKitt.LIPSA
        p.text(c, ENG_X, YM + 7f, 18f, if (f.motor) AMBRA else STINS, rpm, Paint.Align.CENTER)
        p.text(c, ENG_X, YM + 24f, 9f, if (f.motor) AMBRA_STINSA else STINS, "RPM", Paint.Align.CENTER, 2f)

        // transmisia hibrida: generatorul si motorul din fata
        val trAprinsa = f.rotiFata || f.generator
        val cTr = if (f.motor) AMBRA else CYAN
        caseta(c, TR_X, YM, 96f, 76f, "", trAprinsa, cTr)
        p.text(c, TR_X, YM + 31f, 9f, if (trAprinsa) cTr else ETICHETA, "MG1 · MG2", Paint.Align.CENTER, 2f)
        val cG = if (f.motor) AMBRA else STINS
        val cM = if (f.fata) CYAN else STINS
        p.cerc(c, TR_X - 20f, YM - 6f, 16f, cG, plin = false)
        p.cerc(c, TR_X + 20f, YM - 6f, 16f, cM, plin = false)
        p.text(c, TR_X - 20f, YM - 2f, 10f, if (cG == STINS) ETICHETA else cG, "G", Paint.Align.CENTER, gros = true)
        p.text(c, TR_X + 20f, YM - 2f, 10f, if (cM == STINS) ETICHETA else cM, "M", Paint.Align.CENTER, gros = true)

        // bateria HV, cu treptele de incarcare
        caseta(c, BAT_X, YM, 116f, 64f, "BATERIE HV", true, CYAN)
        val soc = s.socProcent
        for (i in 0 until 10) {
            val aprinsa = soc != null && i < soc / 10f
            p.dreptunghi(c, BAT_X - 50f + i * 10.2f, YM - 18f, 7.5f, 20f, if (aprinsa) (if (i < 2) ROSU else CYAN) else STINS)
        }
        p.text(c, BAT_X, YM + 22f, 13f, CYAN, soc?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA, Paint.Align.CENTER, 1f)

        // motorul din spate
        caseta(c, SA, YM, 56f, 60f, "MGR", f.spate, CYAN)
        p.text(c, SA, YM + 5f, 13f, if (f.spate) CYAN else STINS, "M", Paint.Align.CENTER, gros = true)

        p.text(c, X0 + 6f, Y0 - 6f, 11f, ETICHETA, "FATA", spatiere = 3f)
        p.text(c, X1 - 6f, Y0 - 6f, 11f, ETICHETA, "SPATE", Paint.Align.RIGHT, 3f)
    }

    private fun coloanaBaterie(c: Canvas, s: StareBord) {
        val cx = 800f
        p.linie(c, cx - 20f, 96f, cx - 20f, 420f, LINIE_PANOU, 1.5f)
        p.text(c, cx, 112f, 13f, ETICHETA, "PUTERE BATERIE", spatiere = 2f)
        val kw = s.putereKw.roundToInt()
        val textKw = if (kw > 0) "+$kw" else "$kw"
        val culoare = if (s.putereKw < 0f) CYAN else AMBRA
        p.text(c, cx, 160f, 52f, culoare, if (s.areLegatura) textKw else BordKitt.LIPSA)
        p.text(c, cx + p.latimeText(52f, textKw) + 8f, 160f, 18f, ETICHETA, "kW", spatiere = 2f)

        // bara cu mijlocul la zero: stanga incarca, dreapta consuma
        val bw = 350f
        val mij = cx + bw / 2f
        val n = 14
        for (i in 0 until n) {
            val x = cx + i * (bw / n)
            val centru = x + bw / (2 * n)
            val pana = mij + s.putereKw / PUTERE_PLINA * bw / 2f
            val aprins = if (s.putereKw > 0f) centru > mij && centru < pana else centru < mij && centru > pana
            p.dreptunghi(c, x, 178f, bw / n - 4f, 14f, if (aprins) (if (s.putereKw > 0f) AMBRA else CYAN) else STINS)
        }
        p.text(c, cx, 210f, 11f, ETICHETA, "INCARCA", spatiere = 2f)
        p.text(c, cx + bw, 210f, 11f, ETICHETA, "CONSUMA", Paint.Align.RIGHT, 2f)

        valoare(c, cx, 256f, "TENSIUNE", s.hvTensiune?.let { "${it.roundToInt()} V" })
        valoare(c, cx + 190f, 256f, "CURENT", s.hvCurent?.let { "%+.0f A".format(it) })
        valoare(c, cx, 334f, "VITEZA", if (s.areLegatura) "${s.vitezaKmh.roundToInt()} km/h" else null)
        valoare(c, cx + 190f, 334f, "MOTOR", s.tempLichid?.let { "${it.roundToInt()}°C" })
    }

    private fun valoare(c: Canvas, x: Float, y: Float, eticheta: String, v: String?) {
        p.text(c, x, y, 13f, ETICHETA, eticheta, spatiere = 2f)
        p.text(c, x, y + 34f, 30f, VALOARE, v ?: BordKitt.LIPSA)
    }

    private fun roata(c: Canvas, x: Float, y: Float, aprinsa: Boolean, culoare: Int) {
        p.dreptunghi(c, x - 34f, y - 14f, 68f, 28f, if (aprinsa) culoare else ROATA_STINSA, 6f, if (aprinsa) 0.9f else 1f)
        p.rama(c, x - 34f, y - 14f, 68f, 28f, if (aprinsa) culoare else LINIE_PANOU, 2f, 6f)
    }

    private fun caseta(c: Canvas, x: Float, y: Float, w: Float, h: Float, titlu: String, aprinsa: Boolean, culoare: Int) {
        p.dreptunghi(c, x - w / 2f, y - h / 2f, w, h, CASETA, 5f)
        p.rama(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else LINIE_PANOU, if (aprinsa) 2.5f else 1.5f, 5f)
        if (titlu.isNotEmpty()) {
            p.text(c, x, y - h / 2f - 8f, 11f, if (aprinsa) culoare else ETICHETA, titlu, Paint.Align.CENTER, 3f)
        }
    }

    /**
     * Un drum de energie: linie subtire, iar peste ea sageti mici care se
     * aprind pe rand, in sensul in care curge energia.
     */
    private fun flux(c: Canvas, puncte: List<Pair<Float, Float>>, culoare: Int, activ: Boolean, faza: Float) {
        for (k in 0 until puncte.size - 1) {
            val (ax, ay) = puncte[k]
            val (bx, by) = puncte[k + 1]
            p.linie(c, ax, ay, bx, by, if (activ) culoare else DRUM_STINS, if (activ) 3f else 2f, if (activ) 0.3f else 1f)
        }
        if (!activ) return
        var parcurs = 0f
        for (k in 0 until puncte.size - 1) {
            val (ax, ay) = puncte[k]
            val (bx, by) = puncte[k + 1]
            val l = hypot(bx - ax, by - ay)
            val ux = (bx - ax) / l
            val uy = (by - ay) / l
            val n = max(2, (l / PAS_SAGETI).roundToInt())
            val pas = l / n
            for (i in 1 until n) {
                val x = ax + ux * i * pas
                val y = ay + uy * i * pas
                // stralucirea urca spre varf si alearga inainte cu timpul
                val pozitie = (parcurs + i * pas) / PAS_SAGETI - faza
                val treapta = pozitie - 4f * floor(pozitie / 4f)
                val alfa = 0.35f + 0.65f * (treapta / 3f).coerceIn(0f, 1f)
                p.linie.color = culoare
                p.linie.alpha = (alfa * 255).roundToInt()
                p.linie.strokeWidth = 3.5f
                p.drum.reset()
                p.drum.moveTo(x - ux * 6f - uy * 7f, y - uy * 6f + ux * 7f)
                p.drum.lineTo(x + ux * 5f, y + uy * 5f)
                p.drum.lineTo(x - ux * 6f + uy * 7f, y - uy * 6f - ux * 7f)
                c.drawPath(p.drum, p.linie)
            }
            parcurs += l
        }
        p.linie.alpha = 255
    }

    companion object {
        private const val X0 = 70f
        private const val X1 = 740f
        private const val Y0 = 118f
        private const val Y1 = 402f
        private const val YM = (Y0 + Y1) / 2f

        /** Osiile: fata si spate. */
        private const val FA = 190f
        private const val SA = 610f

        private const val ENG_X = 120f
        private const val TR_X = 250f
        private const val BAT_X = 470f

        private const val PAS_SAGETI = 24f
        private const val PASI_PE_SECUNDA = 4f

        /** Puterea la care bara din dreapta e plina, in fiecare sens. */
        private const val PUTERE_PLINA = 30f

        private val PARBRIZ = Color.parseColor("#2a0b09")
        private val DRUM_STINS = Color.parseColor("#1c0806")
        private val ROATA_STINSA = Color.parseColor("#120403")

        private val MOTOR_LA_TR = listOf(ENG_X + 40f to YM, TR_X - 48f to YM)
        private val TR_LA_FATA_SUS = listOf(TR_X - 20f to YM - 38f, TR_X - 20f to YM - 70f, FA to YM - 70f, FA to Y0 + 40f)
        private val TR_LA_FATA_JOS = listOf(TR_X - 20f to YM + 38f, TR_X - 20f to YM + 70f, FA to YM + 70f, FA to Y1 - 40f)
        private val BAT_LA_TR = listOf(BAT_X - 58f to YM, TR_X + 48f to YM)
        private val BAT_LA_SPATE = listOf(BAT_X + 58f to YM, SA - 28f to YM)
        private val MGR_SUS = listOf(SA to YM - 30f, SA to Y0 + 40f)
        private val MGR_JOS = listOf(SA to YM + 30f, SA to Y1 - 40f)
    }
}
