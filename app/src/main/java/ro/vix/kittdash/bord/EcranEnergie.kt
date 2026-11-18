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
        caroseria(c, s.timpS)

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

        roata(c, FA, Y0 + 24f, f.rotiFata, cRotiFata, s.timpS)
        roata(c, FA, Y1 - 24f, f.rotiFata, cRotiFata, s.timpS)
        roata(c, SA, Y0 + 24f, f.spate, CYAN, s.timpS)
        roata(c, SA, Y1 - 24f, f.spate, CYAN, s.timpS)

        // motorul termic, cu turatia mare
        caseta(c, ENG_X, YM, 124f, 104f, "MOTOR", f.motor, AMBRA)
        val rpm = if (s.areLegatura) s.turatie.roundToInt().toString() else BordKitt.LIPSA
        p.text(c, ENG_X, YM + 12f, 34f, if (f.motor) AMBRA else ETICHETA, rpm, Paint.Align.CENTER, gros = true)
        p.text(c, ENG_X, YM + 36f, 11f, if (f.motor) AMBRA_STINSA else ETICHETA, "RPM", Paint.Align.CENTER, 3f)

        // transmisia hibrida: generatorul (G) si motorul din fata (M), fiecare cu turatia lui
        val trAprinsa = f.rotiFata || f.generator
        val cTr = if (f.motor) AMBRA else CYAN
        caseta(c, TR_X, YM, 152f, 144f, "", trAprinsa, cTr)
        p.linie(c, TR_X, YM - 58f, TR_X, YM + 38f, LINIE_PANOU, 1.5f)
        val rot = s.hibrid?.turatiiProaspete().orEmpty()
        val cG = if (f.motor) AMBRA else ETICHETA
        val cM = if (f.fata) CYAN else ETICHETA
        motorElectric(c, TR_X - 38f, YM - 36f, "G", rot["MG1"], cG)
        motorElectric(c, TR_X + 38f, YM - 36f, "M", rot["MG2"], cM)
        p.text(c, TR_X, YM + 58f, 12f, if (trAprinsa) cTr else ETICHETA, "MG1 · MG2", Paint.Align.CENTER, 3f, gros = true)

        // bateria HV, cu treptele de incarcare
        caseta(c, BAT_X, YM, 140f, 80f, "BATERIE HV", true, CYAN)
        val soc = s.socProcent
        for (i in 0 until 10) {
            val aprinsa = soc != null && i < soc / 10f
            p.dreptunghi(c, BAT_X - 60f + i * 12.2f, YM - 24f, 9f, 26f, if (aprinsa) (if (i < 2) ROSU else CYAN) else STINS)
        }
        p.text(c, BAT_X, YM + 28f, 17f, CYAN, soc?.let { "${it.roundToInt()}%" } ?: BordKitt.LIPSA, Paint.Align.CENTER, 1f, gros = true)

        // motorul din spate, cu turatia mare
        caseta(c, SA, YM, 108f, 124f, "MGR", f.spate, CYAN)
        motorElectric(c, SA, YM - 30f, "M", rot["MGR"], if (f.spate) CYAN else ETICHETA)

        p.text(c, X0 + 6f, Y0 - 8f, 12f, ETICHETA, "FATA", spatiere = 3f)
        p.text(c, X1 - 6f, Y0 - 8f, 12f, ETICHETA, "SPATE", Paint.Align.RIGHT, 3f)
    }

    /** Un motor electric: cercul cu litera, iar dedesubt turatia, mare, si RPM. */
    private fun motorElectric(c: Canvas, x: Float, y: Float, litera: String, rpm: Int?, culoare: Int) {
        p.cerc(c, x, y, 20f, culoare, plin = false, grosime = 3f)
        p.text(c, x, y + 5f, 14f, culoare, litera, Paint.Align.CENTER, gros = true)
        p.text(c, x, y + 50f, 24f, culoare, rpm?.toString() ?: BordKitt.LIPSA, Paint.Align.CENTER, gros = true)
        p.text(c, x, y + 67f, 10f, culoare, "RPM", Paint.Align.CENTER, 2f, alfa = 0.75f)
    }

    /**
     * Masina vazuta de sus, cu fata in stanga: bot ascutit, talie, oglinzi,
     * parbriz si luneta de sticla fumurie (schema se vede prin ele), faruri,
     * stopuri si bara scanerului rosu in varful botului.
     */
    private fun caroseria(c: Canvas, timpS: Float) {
        val d = p.drum
        d.reset()
        d.moveTo(70f, 112f)
        d.cubicTo(160f, 104f, 300f, 104f, 420f, 108f)
        d.lineTo(700f, 110f)
        d.cubicTo(760f, 112f, 800f, 120f, 812f, 150f)
        d.lineTo(816f, 382f)
        d.cubicTo(804f, 412f, 760f, 420f, 700f, 422f)
        d.lineTo(420f, 424f)
        d.cubicTo(300f, 428f, 160f, 428f, 70f, 420f)
        d.cubicTo(42f, 414f, 24f, 384f, 14f, 334f)
        d.quadTo(6f, 296f, 6f, 266f)
        d.quadTo(6f, 236f, 14f, 198f)
        d.cubicTo(24f, 148f, 42f, 118f, 70f, 112f)
        d.close()
        p.umplere.color = Color.parseColor("#0c0303")
        c.drawPath(d, p.umplere)
        p.linie.color = ROSU
        p.linie.alpha = 50
        p.linie.strokeWidth = 10f
        c.drawPath(d, p.linie)
        p.linie.alpha = 255
        p.linie.color = CONTUR
        p.linie.strokeWidth = 2.5f
        c.drawPath(d, p.linie)

        // oglinzile
        for ((y0, y1) in listOf(108f to 92f, 424f to 440f)) {
            d.reset(); d.moveTo(338f, y0); d.lineTo(350f, y1); d.lineTo(372f, y1); d.lineTo(370f, y0); d.close()
            p.umplere.color = Color.parseColor("#140504"); c.drawPath(d, p.umplere)
            p.linie.color = CONTUR; p.linie.strokeWidth = 1.8f; c.drawPath(d, p.linie)
        }
        // parbrizul, acoperisul si luneta
        d.reset(); d.moveTo(336f, 132f); d.cubicTo(360f, 126f, 388f, 124f, 412f, 124f); d.lineTo(412f, 408f)
        d.cubicTo(388f, 408f, 360f, 406f, 336f, 400f); d.cubicTo(326f, 330f, 326f, 202f, 336f, 132f); d.close()
        sticla(c, d)
        p.dreptunghi(c, 412f, 126f, 234f, 280f, Color.parseColor("#0a0202"), 0f, 0.55f)
        p.rama(c, 412f, 126f, 234f, 280f, Color.parseColor("#3a0e0a"), 1.2f)
        d.reset(); d.moveTo(646f, 126f); d.cubicTo(690f, 128f, 716f, 134f, 728f, 150f); d.lineTo(728f, 382f)
        d.cubicTo(716f, 398f, 690f, 404f, 646f, 406f); d.close()
        sticla(c, d)
        // liniile capotei si osiile
        p.linie.color = PARBRIZ; p.linie.strokeWidth = 1.5f
        d.reset(); d.moveTo(90f, 150f); d.cubicTo(180f, 142f, 260f, 140f, 322f, 140f); c.drawPath(d, p.linie)
        d.reset(); d.moveTo(90f, 382f); d.cubicTo(180f, 390f, 260f, 392f, 322f, 392f); c.drawPath(d, p.linie)
        p.linie(c, FA, 150f, FA, 382f, OSIE, 2f)
        p.linie(c, SA, 150f, SA, 382f, OSIE, 2f)
        // farurile, albe, si stopurile, rosii
        d.reset(); d.moveTo(28f, 168f); d.cubicTo(42f, 136f, 70f, 122f, 104f, 118f); lumina(c, d, FAR)
        d.reset(); d.moveTo(28f, 364f); d.cubicTo(42f, 396f, 70f, 410f, 104f, 414f); lumina(c, d, FAR)
        d.reset(); d.moveTo(800f, 136f); d.lineTo(812f, 160f); d.lineTo(812f, 372f); d.lineTo(800f, 396f); lumina(c, d, ROSU)
        // bara scanerului, in varful botului: un punct rosu care merge sus-jos
        val f2 = (timpS / 0.65f) % 2f
        val poz = (if (f2 < 1f) f2 else 2f - f2) * (SCANER_BOT - 1)
        for (i in 0 until SCANER_BOT) {
            val dist = kotlin.math.abs(i - poz)
            val cul = when {
                dist < 1f -> Color.parseColor("#ff3020")
                dist < 2.5f -> Color.parseColor("#a01810")
                else -> Color.parseColor("#3a0806")
            }
            if (dist < 1.2f) p.dreptunghi(c, 7f, 225f + i * 7.2f, 11f, 11f, cul, 3f, 0.35f)
            p.dreptunghi(c, 10f, 228f + i * 7.2f, 5f, 5f, cul, 1f)
        }
    }

    private fun sticla(c: Canvas, d: android.graphics.Path) {
        p.umplere.color = Color.parseColor("#2a0c0a")
        p.umplere.alpha = 170
        c.drawPath(d, p.umplere)
        p.umplere.alpha = 255
        p.linie.color = Color.parseColor("#5a1a14")
        p.linie.strokeWidth = 1.5f
        c.drawPath(d, p.linie)
    }

    /** O linie luminoasa: halou larg si stins, apoi miezul. */
    private fun lumina(c: Canvas, d: android.graphics.Path, culoare: Int) {
        p.linie.color = culoare
        p.linie.alpha = 60
        p.linie.strokeWidth = 10f
        c.drawPath(d, p.linie)
        p.linie.alpha = 220
        p.linie.strokeWidth = 4f
        c.drawPath(d, p.linie)
        p.linie.alpha = 255
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

    private fun roata(c: Canvas, x: Float, y: Float, aprinsa: Boolean, culoare: Int, timpS: Float) {
        val w = 96f
        val h = 36f
        if (aprinsa) p.dreptunghi(c, x - w / 2f - 5f, y - h / 2f - 5f, w + 10f, h + 10f, culoare, 9f, 0.22f)
        p.dreptunghi(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else ROATA_STINSA, 7f)
        p.rama(c, x - w / 2f, y - h / 2f, w, h, if (aprinsa) culoare else CONTUR, 2.5f, 7f)
        // brazdele anvelopei: alearga cand roata trage
        val pas = w / 7f
        val dx = if (aprinsa) (timpS * 60f) % pas else 0f
        val salvat = c.save()
        c.clipRect(x - w / 2f + 3f, y - h / 2f, x + w / 2f - 3f, y + h / 2f)
        for (k in -1..7) {
            val bx = x - w / 2f + k * pas + dx
            p.linie(c, bx, y - h / 2f + 6f, bx, y + h / 2f - 6f, if (aprinsa) Color.argb(90, 0, 0, 0) else OSIE, 2.5f)
        }
        c.restoreToCount(salvat)
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

        private const val ENG_X = 104f
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
        private val FAR = Color.parseColor("#d8e8ff")
        private const val SCANER_BOT = 11

        private val MOTOR_LA_TR = listOf(ENG_X + 62f to YM, TR_X - 76f to YM)
        private val TR_LA_FATA_SUS = listOf(TR_X - 25f to YM - 72f, TR_X - 25f to YM - 84f, FA to YM - 84f, FA to Y0 + 44f)
        private val TR_LA_FATA_JOS = listOf(TR_X - 25f to YM + 72f, TR_X - 25f to YM + 84f, FA to YM + 84f, FA to Y1 - 44f)
        private val BAT_LA_TR = listOf(BAT_X - 70f to YM, TR_X + 76f to YM)
        private val BAT_LA_SPATE = listOf(BAT_X + 70f to YM, SA - 54f to YM)
        private val MGR_SUS = listOf(SA to YM - 62f, SA to Y0 + 44f)
        private val MGR_JOS = listOf(SA to YM + 62f, SA to Y1 - 44f)
    }
}
