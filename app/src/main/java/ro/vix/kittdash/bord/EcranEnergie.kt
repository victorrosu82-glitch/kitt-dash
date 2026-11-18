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
import kotlin.math.hypot
import kotlin.math.roundToInt

/**
 * Ecranul 3: pe unde curge energia, pe schema NX 300h E-Four vazuta de sus,
 * cu fata in stanga. Pe fiecare drum activ alunecă pulsuri de lumina in
 * sensul energiei, ca pe ecranul Lexus: portocalii dinspre motorul termic,
 * albastre pe partea electrica. In dreapta, cifrele
 * bateriei HV. Macheta: `docs/macheta-ecran-3-*.png`.
 */
class EcranEnergie(private val p: Pensula) {

    fun deseneaza(c: Canvas, s: StareBord) {
        p.antet(c, s, FelEcran.ENERGIE.titlu)
        val f = FluxEnergie.din(s.turatie, s.putereKw, s.vitezaKmh)
        // cat au inaintat pulsurile, in puncte
        val faza = s.timpS * VITEZA_PULS

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
        p.umplere.color = Color.parseColor("#0c0303")
        c.drawPath(p.drum, p.umplere)
        // conturul, cu halou, ca sa se vada si ziua pe ecranul masinii
        p.linie.color = ROSU
        p.linie.alpha = 50
        p.linie.strokeWidth = 9f
        c.drawPath(p.drum, p.linie)
        p.linie.alpha = 255
        p.linie.color = CONTUR
        p.linie.strokeWidth = 3f
        c.drawPath(p.drum, p.linie)
        // parbrizul si luneta, plus osiile
        p.linie(c, X0 + 330f, Y0 + 16f, X0 + 330f, Y1 - 16f, PARBRIZ, 2f)
        p.linie(c, X1 - 50f, Y0 + 16f, X1 - 50f, Y1 - 16f, PARBRIZ, 2f)
        p.linie(c, FA, Y0 + 40f, FA, Y1 - 40f, OSIE, 2f)
        p.linie(c, SA, Y0 + 40f, SA, Y1 - 40f, OSIE, 2f)

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

        roata(c, FA, Y0 + 24f, f.rotiFata, cRotiFata)
        roata(c, FA, Y1 - 24f, f.rotiFata, cRotiFata)
        roata(c, SA, Y0 + 24f, f.spate, CYAN)
        roata(c, SA, Y1 - 24f, f.spate, CYAN)

        // motorul termic, cu turatia
        caseta(c, ENG_X, YM, 100f, 78f, "MOTOR", f.motor, AMBRA)
        val rpm = if (s.areLegatura) s.turatie.roundToInt().toString() else BordKitt.LIPSA
        p.text(c, ENG_X, YM + 9f, 24f, if (f.motor) AMBRA else ETICHETA, rpm, Paint.Align.CENTER, gros = true)
        p.text(c, ENG_X, YM + 29f, 10f, if (f.motor) AMBRA_STINSA else ETICHETA, "RPM", Paint.Align.CENTER, 2f)

        // transmisia hibrida: generatorul si motorul din fata
        val trAprinsa = f.rotiFata || f.generator
        val cTr = if (f.motor) AMBRA else CYAN
        caseta(c, TR_X, YM, 120f, 92f, "", trAprinsa, cTr)
        p.text(c, TR_X, YM + 38f, 11f, if (trAprinsa) cTr else ETICHETA, "MG1 · MG2", Paint.Align.CENTER, 2f, gros = true)
        val cG = if (f.motor) AMBRA else ETICHETA
        val cM = if (f.fata) CYAN else ETICHETA
        p.cerc(c, TR_X - 25f, YM - 8f, 20f, cG, plin = false, grosime = 3f)
        p.cerc(c, TR_X + 25f, YM - 8f, 20f, cM, plin = false, grosime = 3f)
        p.text(c, TR_X - 25f, YM - 3f, 13f, cG, "G", Paint.Align.CENTER, gros = true)
        p.text(c, TR_X + 25f, YM - 3f, 13f, cM, "M", Paint.Align.CENTER, gros = true)

        // bateria HV, cu treptele de incarcare
        caseta(c, BAT_X, YM, 140f, 80f, "BATERIE HV", true, CYAN)
        val soc = s.socProcent
        for (i in 0 until 10) {
            val aprinsa = soc != null && i < soc / 10f
            p.dreptunghi(c, BAT_X - 60f + i * 12.2f, YM - 24f, 9f, 26f, if (aprinsa) (if (i < 2) ROSU else CYAN) else STINS)
        }
        p.text(c, BAT_X, YM + 28f, 17f, CYAN, soc?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA, Paint.Align.CENTER, 1f, gros = true)

        // motorul din spate
        caseta(c, SA, YM, 70f, 72f, "MGR", f.spate, CYAN)
        p.text(c, SA, YM + 6f, 17f, if (f.spate) CYAN else ETICHETA, "M", Paint.Align.CENTER, gros = true)

        p.text(c, X0 + 6f, Y0 - 8f, 12f, ETICHETA, "FATA", spatiere = 3f)
        p.text(c, X1 - 6f, Y0 - 8f, 12f, ETICHETA, "SPATE", Paint.Align.RIGHT, 3f)
    }

    private fun coloanaBaterie(c: Canvas, s: StareBord) {
        val cx = 868f
        p.linie(c, cx - 24f, 96f, cx - 24f, 420f, LINIE_PANOU, 1.5f)
        p.text(c, cx, 112f, 13f, ETICHETA, "PUTERE BATERIE", spatiere = 2f)
        val kw = s.putereKw.roundToInt()
        val textKw = if (kw > 0) "+$kw" else "$kw"
        val culoare = if (s.putereKw < 0f) CYAN else AMBRA
        p.text(c, cx, 160f, 52f, culoare, if (s.areLegatura) textKw else BordKitt.LIPSA)
        p.text(c, cx + p.latimeText(52f, textKw) + 8f, 160f, 18f, ETICHETA, "kW", spatiere = 2f)

        // bara cu mijlocul la zero: stanga incarca, dreapta consuma; scara
        // cu radacina, ca puterile mici, cele de zi cu zi, sa se vada
        val bw = 282f
        val jumatate = BARA_PE_PARTE
        val aprinse = if (kotlin.math.abs(s.putereKw) < 0.5f) 0 else kotlin.math.ceil(ScaraPutere.fractie(s.putereKw) * jumatate).toInt()
        val pas = bw / (2 * jumatate)
        for (i in 0 until 2 * jumatate) {
            val x = cx + i * pas
            val dreapta = i >= jumatate
            val departe = if (dreapta) i - jumatate else jumatate - 1 - i
            val aprins = departe < aprinse && (if (s.putereKw > 0f) dreapta else !dreapta && s.putereKw < 0f)
            p.dreptunghi(c, x, 178f, pas - 3f, 16f, if (aprins) (if (s.putereKw > 0f) AMBRA else CYAN) else STINS)
        }
        val mij = cx + bw / 2f
        p.linie(c, mij - 1.5f, 172f, mij - 1.5f, 200f, ETICHETA, 1.5f)
        p.text(c, cx, 214f, 11f, ETICHETA, "INCARCA", spatiere = 2f)
        p.text(c, cx + bw, 214f, 11f, ETICHETA, "CONSUMA", Paint.Align.RIGHT, 2f)

        valoare(c, cx, 256f, "TENSIUNE", s.hvTensiune?.let { "${it.roundToInt()} V" })
        valoare(c, cx + 160f, 256f, "CURENT", s.hvCurent?.let { "%+.0f A".format(it) })
        valoare(c, cx, 334f, "VITEZA", if (s.areLegatura) "${s.vitezaKmh.roundToInt()} km/h" else null)
        valoare(c, cx + 160f, 334f, "MOTOR", s.tempLichid?.let { "${it.roundToInt()}°C" })
    }

    private fun valoare(c: Canvas, x: Float, y: Float, eticheta: String, v: String?) {
        p.text(c, x, y, 13f, ETICHETA, eticheta, spatiere = 2f)
        p.text(c, x, y + 34f, 30f, VALOARE, v ?: BordKitt.LIPSA)
    }

    private fun roata(c: Canvas, x: Float, y: Float, aprinsa: Boolean, culoare: Int) {
        val w = 96f
        val h = 36f
        if (aprinsa) p.dreptunghi(c, x - w / 2f - 5f, y - h / 2f - 5f, w + 10f, h + 10f, culoare, 9f, 0.22f)
        p.dreptunghi(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else ROATA_STINSA, 7f)
        p.rama(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else CONTUR, 2.5f, 7f)
        // brazdele anvelopei
        for (k in 1..5) {
            val bx = x - w / 2f + k * w / 6f
            p.linie(c, bx, y - h / 2f + 6f, bx, y + h / 2f - 6f, if (aprinsa) Color.argb(90, 0, 0, 0) else OSIE, 2f)
        }
    }

    private fun caseta(c: Canvas, x: Float, y: Float, w: Float, h: Float, titlu: String, aprinsa: Boolean, culoare: Int) {
        p.dreptunghi(c, x - w / 2f, y - h / 2f, w, h, CASETA, 5f)
        p.rama(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else LINIE_PANOU, if (aprinsa) 2.5f else 1.5f, 5f)
        if (titlu.isNotEmpty()) {
            p.text(c, x, y - h / 2f - 8f, 11f, if (aprinsa) culoare else ETICHETA, titlu, Paint.Align.CENTER, 3f)
        }
    }

    /**
     * Un drum de energie. Stins: o linie subtire. Activ: un fascicul cu halou,
     * iar pe el pulsuri luminoase cu coada, care alearga in sensul energiei.
     * [faza] e cat au inaintat pulsurile, in puncte.
     */
    private fun flux(c: Canvas, puncte: List<Pair<Float, Float>>, culoare: Int, activ: Boolean, faza: Float) {
        p.drum.reset()
        p.drum.moveTo(puncte[0].first, puncte[0].second)
        for (k in 1 until puncte.size) p.drum.lineTo(puncte[k].first, puncte[k].second)
        if (!activ) {
            p.linie.color = DRUM_STINS
            p.linie.strokeWidth = 2f
            c.drawPath(p.drum, p.linie)
            return
        }
        // fasciculul: halou larg si slab, apoi miezul
        for ((latime, alfa) in FASCICUL) {
            p.linie.color = culoare
            p.linie.alpha = alfa
            p.linie.strokeWidth = latime
            c.drawPath(p.drum, p.linie)
        }
        p.linie.alpha = 255

        val lungimi = FloatArray(puncte.size - 1) { k ->
            hypot(puncte[k + 1].first - puncte[k].first, puncte[k + 1].second - puncte[k].second)
        }
        val total = lungimi.sum()
        val cap = deschide(culoare)
        // pulsurile, la SPATIU_PULS unul de altul; primul porneste din capatul drumului
        var d = faza % SPATIU_PULS
        while (d < total + COADA_PULS) {
            for (k in COADA_PASI downTo 0) {
                val dk = d - k * (COADA_PULS / COADA_PASI)
                if (dk < 0f || dk > total) continue
                val (x, y) = punctPe(puncte, lungimi, dk)
                val stingere = 1f - k / (COADA_PASI + 1f)
                puls.color = culoare
                puls.alpha = (70 * stingere).roundToInt()
                c.drawCircle(x, y, 4f + 8f * stingere, puls)
                if (k == 0) {
                    puls.color = cap
                    puls.alpha = 255
                    c.drawCircle(x, y, 4.5f, puls)
                }
            }
            d += SPATIU_PULS
        }
    }

    /** Punctul aflat la distanta [d] de inceputul drumului. */
    private fun punctPe(puncte: List<Pair<Float, Float>>, lungimi: FloatArray, d: Float): Pair<Float, Float> {
        var rest = d
        for (k in lungimi.indices) {
            val l = lungimi[k]
            if (rest <= l || k == lungimi.lastIndex) {
                val t = if (l > 0f) (rest / l).coerceIn(0f, 1f) else 0f
                val (ax, ay) = puncte[k]
                val (bx, by) = puncte[k + 1]
                return (ax + (bx - ax) * t) to (ay + (by - ay) * t)
            }
            rest -= l
        }
        return puncte.last()
    }

    /** Culoarea pulsului, spre alb, ca un varf de lumina. */
    private fun deschide(culoare: Int): Int = Color.rgb(
        (Color.red(culoare) + 255 * 2) / 3,
        (Color.green(culoare) + 255 * 2) / 3,
        (Color.blue(culoare) + 255 * 2) / 3,
    )

    private val puls = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }

    companion object {
        private const val X0 = 24f
        private const val X1 = 818f
        private const val Y0 = 104f
        private const val Y1 = 428f
        private const val YM = (Y0 + Y1) / 2f

        /** Osiile: fata si spate. */
        private const val FA = 170f
        private const val SA = 668f

        private const val ENG_X = 96f
        private const val TR_X = 258f
        private const val BAT_X = 486f

        /** Pulsurile de energie: cat de repede alearga, cat de rare, cat de lunga e coada. */
        private const val VITEZA_PULS = 120f
        private const val SPATIU_PULS = 56f
        private const val COADA_PULS = 30f
        private const val COADA_PASI = 6

        /** Fasciculul: (grosime, opacitate din 255), de la halou spre miez. */
        private val FASCICUL = listOf(14f to 28, 7f to 60, 2.5f to 170)

        /** Casute de fiecare parte a barei de putere. */
        private const val BARA_PE_PARTE = 10

        private val PARBRIZ = Color.parseColor("#3a100c")
        private val CONTUR = Color.parseColor("#a8352a")
        private val OSIE = Color.parseColor("#2e0d0a")
        private val DRUM_STINS = Color.parseColor("#1c0806")
        private val ROATA_STINSA = Color.parseColor("#1e0806")

        private val MOTOR_LA_TR = listOf(ENG_X + 50f to YM, TR_X - 60f to YM)
        private val TR_LA_FATA_SUS = listOf(TR_X - 25f to YM - 46f, TR_X - 25f to YM - 84f, FA to YM - 84f, FA to Y0 + 44f)
        private val TR_LA_FATA_JOS = listOf(TR_X - 25f to YM + 46f, TR_X - 25f to YM + 84f, FA to YM + 84f, FA to Y1 - 44f)
        private val BAT_LA_TR = listOf(BAT_X - 70f to YM, TR_X + 60f to YM)
        private val BAT_LA_SPATE = listOf(BAT_X + 70f to YM, SA - 35f to YM)
        private val MGR_SUS = listOf(SA to YM - 36f, SA to Y0 + 44f)
        private val MGR_JOS = listOf(SA to YM + 36f, SA to Y1 - 44f)
    }
}
