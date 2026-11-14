package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.graphics.Typeface
import ro.vix.kittdash.obd.ModHibrid
import kotlin.math.abs
import kotlin.math.cos
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Deseneaza bordul KITT.
 *
 * Lucreaza intr-un caiet de 1280 x 720, acelasi cu al machetei aprobate, si
 * il aseaza in mijlocul suprafetei primite, oricat ar fi ea de mare. Asa
 * aceeasi bucata de cod deseneaza si pe telefon, si - de la etapa 4 - pe
 * ecranul masinii, care are alta rezolutie.
 *
 * Deseneaza pe [Canvas]-ul gol al platformei, nu pe ceva din Compose, tocmai
 * ca ecranul masinii sa-l poata folosi fara nicio schimbare.
 *
 * Nu socoteste nimic si nu tine minte nimic intre cadre: tot ce arata vine
 * din [StareBord]. Obiectele de desen se fac o singura data, la pornire,
 * fiindca un bord deseneaza de 60 de ori pe secunda si nu-si permite sa
 * ceara memorie la fiecare cadru.
 */
class BordKitt {

    // --- culorile machetei ---------------------------------------------

    private val stins = Color.parseColor("#2b0c0a")
    private val rosu = Color.parseColor("#ff2d1c")
    private val portocaliu = Color.parseColor("#ff7a1a")
    private val ambra = Color.parseColor("#ffb21c")
    private val cyan = Color.parseColor("#35d0ff")
    private val eticheta = Color.parseColor("#9a2f26")
    private val fundal = Color.parseColor("#050505")
    private val rosuAprins = Color.parseColor("#ff3b2a")
    private val rosuSters = Color.parseColor("#8a2a22")
    private val valoare = Color.parseColor("#ff5a2a")
    private val liniePanou = Color.parseColor("#3a0f0c")
    private val varfAc = Color.parseColor("#ffd6cc")
    private val vocetStinsa = Color.parseColor("#4a120e")
    private val ambraStinsa = Color.parseColor("#b8861c")

    // --- unelte de desen, facute o singura data ------------------------

    private val umplere = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.FILL }
    private val linie = Paint(Paint.ANTI_ALIAS_FLAG).apply { style = Paint.Style.STROKE }
    private val scris = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.NORMAL)
    }
    private val scrisGros = Paint(Paint.ANTI_ALIAS_FLAG).apply {
        typeface = Typeface.create(Typeface.SANS_SERIF, Typeface.BOLD)
    }
    private val oval = RectF()


    /**
     * Deseneaza un cadru pe toata suprafata data.
     *
     * [latime] si [inaltime] sunt in pixelii suprafetei; bordul se aseaza in
     * mijloc, pastrandu-si proportia, ca sa nu se lateasca pe ecrane late.
     */
    fun deseneaza(canvas: Canvas, latime: Float, inaltime: Float, stare: StareBord) {
        canvas.drawColor(fundal)
        if (latime <= 0f || inaltime <= 0f) return

        val scara = min(latime / LATIME, inaltime / INALTIME)
        val stanga = (latime - LATIME * scara) / 2f
        val sus = (inaltime - INALTIME * scara) / 2f

        // Ecranul unei masini e de obicei mai lat decat caietul de 1280x720.
        // Daca am lasa totul la mijloc, ar ramane benzi negre in stanga si in
        // dreapta, cum s-a si vazut pe emulator. Ceasul ramane in mijloc, dar
        // coloanele se duc catre margini, ca bordul sa umple ecranul.
        val latimeLogica = latime / scara
        val intindere = ((latimeLogica - LATIME) / 2f).coerceIn(0f, INTINDERE_MAXIMA)

        val salvat = canvas.save()
        canvas.translate(stanga - intindere * scara, sus)
        canvas.scale(scara, scara)

        antetSiScaner(canvas, stare, intindere)
        ceasViteza(canvas, stare, intindere)

        val salvatStanga = canvas.save()
        canvas.translate(0f, 0f)
        coloanaStanga(canvas, stare)
        canvas.restoreToCount(salvatStanga)

        val salvatDreapta = canvas.save()
        canvas.translate(2f * intindere, 0f)
        coloanaDreapta(canvas, stare)
        canvas.restoreToCount(salvatDreapta)

        canvas.restoreToCount(salvat)

        // Noaptea tragem o perdea subtire peste tot. E mai bine decat sa
        // stingem fiecare culoare in parte: asa raman aceleasi culori, doar
        // cu mai putina lumina, si nu se poate uita vreuna.
        if (stare.noapte) canvas.drawColor(PERDEA_NOAPTE)
    }

    // --- partile bordului ----------------------------------------------

    private fun antetSiScaner(canvas: Canvas, stare: StareBord, intindere: Float) {
        text(canvas, 60f, 48f, 26f, rosuAprins, "LEXUS NX 300h", Paint.Align.LEFT, 6f)

        val culoareMod = when (stare.mod) {
            ModHibrid.HYBRID -> ambra
            ModHibrid.REGEN, ModHibrid.EV -> cyan
            else -> rosuSters
        }
        text(
            canvas, 1220f + 2f * intindere, 48f, 26f, culoareMod,
            stare.mod.eticheta, Paint.Align.RIGHT, 6f,
        )

        linie.color = liniePanou
        linie.strokeWidth = 2f
        canvas.drawLine(60f, 62f, 1220f + 2f * intindere, 62f, linie)

        scaner(canvas, stare, intindere)
    }

    /**
     * Scanerul rosu care alearga dus-intors, semnul lui KITT. Casuta pe care
     * sta acum e plina, iar cele din urma ei se sting treptat - coada e mai
     * lunga inspre partea din care a venit.
     */
    private fun scaner(canvas: Canvas, stare: StareBord, intindere: Float) {
        val pozitie = LogicaBord.pozitiaScanerului(stare.scanerFaza)
        val directie = LogicaBord.directiaScanerului(stare.scanerFaza)

        for (i in 0 until 30) {
            val opacitate = LogicaBord.stralucireScaner(i, pozitie, directie)
            umplere.color = rosu
            umplere.alpha = (opacitate * 255).roundToInt().coerceIn(0, 255)
            val x = 340f + intindere + i * 20f
            canvas.drawRect(x, 76f, x + 16f, 96f, umplere)
        }
        umplere.alpha = 255
    }

    /** Ceasul de viteza: 54 de bare pe un arc de 270 de grade. */
    private fun ceasViteza(canvas: Canvas, stare: StareBord, intindere: Float) {
        val salvat = canvas.save()
        canvas.translate(intindere, 0f)
        arc(canvas, 252f, liniePanou, 3f)
        arc(canvas, 186f, stins, 2f)

        val aprinse = LogicaBord.bareAprinse(stare.vitezaKmh)
        linie.strokeWidth = 8f
        for (i in 0 until BARE) {
            val unghi = Math.toRadians((135.0 + i * 270.0 / (BARE - 1)))
            val c = cos(unghi).toFloat()
            val s = sin(unghi).toFloat()
            linie.color = when (LogicaBord.culoareBara(i, aprinse, stare.vitezaKmh)) {
                LogicaBord.CuloareBara.STINSA -> stins
                LogicaBord.CuloareBara.VARF -> varfAc
                LogicaBord.CuloareBara.PORTOCALIE -> portocaliu
                LogicaBord.CuloareBara.ROSIE -> rosu
            }
            canvas.drawLine(
                CX + 200f * c, CY + 200f * s,
                CX + 234f * c, CY + 234f * s,
                linie,
            )
        }

        var v = 0
        while (v <= 180) {
            val unghi = Math.toRadians(135.0 + v / 180.0 * 270.0)
            textCentrat(
                canvas,
                CX + 162f * cos(unghi).toFloat(),
                CY + 162f * sin(unghi).toFloat(),
                24f, rosuSters, v.toString(),
            )
            v += 30
        }

        // Locul asta e ingust: intre cifrele 60 si 120 ale ceasului incap
        // vreo cinci litere. "FARA LEGATURA" se incaleca peste ele, cum s-a
        // vazut pe ecranul masinii, deci cand nu avem legatura o spunem jos,
        // unde e loc, nu aici.
        if (stare.areLegatura) {
            text(canvas, 640f, 290f, 26f, ambra, "READY", Paint.Align.CENTER, 8f)
        }

        text(
            canvas, 640f, 445f, 170f, rosuAprins,
            stare.vitezaKmh.roundToInt().toString(), Paint.Align.CENTER, 0f, gros = true,
        )
        text(canvas, 640f, 488f, 28f, rosuSters, "km/h", Paint.Align.CENTER, 4f)

        modulatorVoce(canvas, stare, intindere)
        avertizari(canvas, stare)
        canvas.restoreToCount(salvat)
    }

    /**
     * Avertizarile, sub ceas. Una singura: cea mai grava. Doua randuri pe un
     * bord de masina nu se citesc din mers, iar daca motorul e fierbinte nu
     * conteaza ca si bateria e scazuta.
     */
    private fun avertizari(canvas: Canvas, stare: StareBord) {
        val prima = stare.avertizari.firstOrNull() ?: return
        val culoare = if (prima.gravitate == Avertizare.Gravitate.MARE) rosu else ambra
        // Clipeste rar, cat sa se vada, nu cat sa enerveze.
        val vizibila = prima.gravitate == Avertizare.Gravitate.MICA ||
            (stare.timpS * 1.6f).toInt() % 2 == 0
        if (!vizibila) return
        text(canvas, 640f, 566f, 30f, culoare, prima.text, Paint.Align.CENTER, 4f, gros = true)
    }

    private fun coloanaStanga(canvas: Canvas, stare: StareBord) {
        text(canvas, 60f, 118f, 22f, eticheta, "COOLANT", Paint.Align.LEFT, 3f)
        text(canvas, 60f, 160f, 44f, valoare, grade(stare.tempLichid), Paint.Align.LEFT)

        // In machta aici statea FUEL. Masina asta nu da nivelul de
        // combustibil (PID 2F raspunde NO DATA), deci locul ramanea mort cu
        // un "---" vesnic. Il ia temperatura de afara, care chiar vine.
        text(canvas, 60f, 204f, 22f, eticheta, "OUTSIDE", Paint.Align.LEFT, 3f)
        text(canvas, 60f, 246f, 44f, valoare, grade(stare.tempExterioara), Paint.Align.LEFT)

        text(canvas, 60f, 290f, 22f, eticheta, "HV BATTERY", Paint.Align.LEFT, 3f)
        // Paisprezece trepte, de jos in sus. Primele trei sunt rosii: sub
        // 21% bateria e goala pentru un hibrid.
        val trepte = LogicaBord.trepteBaterie(stare.socProcent)
        for (i in 0 until LogicaBord.TREPTE_BATERIE) {
            umplere.color = when (LogicaBord.culoareBaterie(i, trepte)) {
                LogicaBord.CuloareBaterie.STINSA -> stins
                LogicaBord.CuloareBaterie.ROSIE -> rosu
                LogicaBord.CuloareBaterie.AMBRA -> ambra
            }
            val y = 304f + (13 - i) * 25f
            canvas.drawRect(60f, y, 150f, y + 19f, umplere)
        }

        text(canvas, 180f, 470f, 70f, ambra, procent(stare.socProcent), Paint.Align.LEFT)
        text(canvas, 180f, 506f, 22f, eticheta, "SOC", Paint.Align.LEFT, 2f)
        val temp = stare.tempBaterie
        if (temp != null) {
            text(
                canvas, 180f, 546f, 26f, ambraStinsa,
                "BATT ${temp.roundToInt()}°C", Paint.Align.LEFT,
            )
        }
    }

    private fun coloanaDreapta(canvas: Canvas, stare: StareBord) {
        text(canvas, 1220f, 118f, 22f, eticheta, "ENGINE", Paint.Align.RIGHT, 3f)
        text(
            canvas, 1220f, 160f, 44f, valoare,
            "${stare.turatie.roundToInt()} rpm", Paint.Align.RIGHT,
        )

        text(canvas, 1220f, 204f, 22f, eticheta, "INTAKE", Paint.Align.RIGHT, 3f)
        text(canvas, 1220f, 246f, 44f, valoare, grade(stare.tempAer), Paint.Align.RIGHT)

        text(canvas, 1220f, 290f, 22f, eticheta, "POWER", Paint.Align.RIGHT, 3f)

        // Puterea in doua sensuri: tractiunea urca de la mijloc, recuperarea
        // coboara. Semnul vine din masina - plus cand bateria da curent.
        val kw = stare.putereKw
        val sus = LogicaBord.trepteSus(kw)
        val jos = LogicaBord.trepteJos(kw)
        for (j in 0 until LogicaBord.TREPTE_PUTERE) {
            umplere.color = culoarePutere(LogicaBord.culoarePutereSus(j, sus))
            val ySus = 479f - 6f - 19f - j * 25f
            canvas.drawRect(1130f, ySus, 1220f, ySus + 19f, umplere)

            umplere.color = culoarePutere(LogicaBord.culoarePutereJos(j, jos))
            val yJos = 479f + 6f + j * 25f
            canvas.drawRect(1130f, yJos, 1220f, yJos + 19f, umplere)
        }

        val semn = if (kw > 0f) "+" else ""
        text(
            canvas, 1105f, 470f, 54f, if (kw < 0f) cyan else ambra,
            semn + kw.roundToInt(), Paint.Align.RIGHT,
        )
        text(canvas, 1105f, 506f, 24f, eticheta, "kW", Paint.Align.RIGHT, 2f)
    }

    /**
     * Modulatorul de voce: 24 de bare care joaca cu puterea. Cand nu avem
     * legatura cu masina n-are ce sa joace, asa ca in locul lui scriem de ce.
     */
    private fun modulatorVoce(canvas: Canvas, stare: StareBord, intindere: Float) {
        val melodie = stare.melodie
        if (melodie != null) {
            bandaMelodie(canvas, melodie, stare.melodieProgres, intindere)
            return
        }
        if (!stare.areLegatura) {
            text(
                canvas, 640f, 648f, 26f, ambra,
                "FARA LEGATURA CU MASINA", Paint.Align.CENTER, 6f,
            )
            return
        }
        val plin = stare.vocePlin
        val t = stare.timpS
        for (i in 0 until 24) {
            val unda = abs(
                sin((t * 9f + i * 1.7f).toDouble()).toFloat() *
                    cos((t * 4.3f + i * 0.6f).toDouble()).toFloat(),
            )
            val h = 6f + plin * 84f * unda
            umplere.color = if (plin > 0.1f) rosu else vocetStinsa
            val x = 496f + i * 12f
            canvas.drawRect(x, 640f - h / 2f, x + 8f, 640f + h / 2f, umplere)
        }
    }

    /**
     * Melodia noua, trecand ca o banda prin locul modulatorului.
     *
     * Intra din dreapta si iese prin stanga, o singura data. Se stinge usor
     * la capete, ca sa nu apara si sa dispara brusc.
     */
    private fun bandaMelodie(
        canvas: Canvas,
        melodie: String,
        progres: Float,
        intindere: Float,
    ) {
        scris.textSize = 34f
        scris.letterSpacing = 0.06f
        val latimeText = scris.measureText(melodie)

        // Banda tine toata latimea ecranului, nu doar caietul de 1280:
        // altfel, pe un ecran lat, textul intra peste coloana din dreapta.
        val stanga = -intindere
        val dreapta = LATIME_BANDA + intindere
        val deLa = dreapta + 20f
        val panaLa = stanga - latimeText - 20f
        val x = deLa + (panaLa - deLa) * progres

        // Se stinge in primele si ultimele doua zecimi ale drumului.
        val opacitate = when {
            progres < 0.2f -> progres / 0.2f
            progres > 0.8f -> (1f - progres) / 0.2f
            else -> 1f
        }

        val salvat = canvas.save()
        canvas.clipRect(stanga, 612f, dreapta, 668f)
        scris.color = ambra
        scris.alpha = (opacitate * 255).roundToInt().coerceIn(0, 255)
        scris.textAlign = Paint.Align.LEFT
        canvas.drawText(melodie, x, 652f, scris)
        scris.alpha = 255
        scris.letterSpacing = 0f
        canvas.restoreToCount(salvat)
    }

    // --- unelte ----------------------------------------------------------

    private fun culoarePutere(c: LogicaBord.CuloarePutere): Int = when (c) {
        LogicaBord.CuloarePutere.STINSA -> stins
        LogicaBord.CuloarePutere.AMBRA -> ambra
        LogicaBord.CuloarePutere.PORTOCALIE -> portocaliu
        LogicaBord.CuloarePutere.ROSIE -> rosu
        LogicaBord.CuloarePutere.CYAN -> cyan
    }

    private fun arc(canvas: Canvas, raza: Float, culoare: Int, grosime: Float) {
        oval.set(CX - raza, CY - raza, CX + raza, CY + raza)
        linie.color = culoare
        linie.strokeWidth = grosime
        canvas.drawArc(oval, 135f, 270f, false, linie)
    }

    private fun text(
        canvas: Canvas,
        x: Float,
        y: Float,
        marime: Float,
        culoare: Int,
        continut: String,
        aliniere: Paint.Align = Paint.Align.LEFT,
        spatiere: Float = 0f,
        gros: Boolean = false,
    ) {
        val p = if (gros) scrisGros else scris
        p.textSize = marime
        p.color = culoare
        p.textAlign = aliniere
        // In macheta spatierea e in pixeli, aici se cere in "em".
        p.letterSpacing = if (marime > 0f) spatiere / marime else 0f
        canvas.drawText(continut, x, y, p)
        p.letterSpacing = 0f
    }

    /** Text asezat cu mijlocul lui pe punctul dat, pe amandoua directiile. */
    private fun textCentrat(
        canvas: Canvas,
        x: Float,
        y: Float,
        marime: Float,
        culoare: Int,
        continut: String,
    ) {
        scris.textSize = marime
        scris.color = culoare
        scris.textAlign = Paint.Align.CENTER
        scris.letterSpacing = 0f
        val m = scris.fontMetrics
        canvas.drawText(continut, x, y - (m.ascent + m.descent) / 2f, scris)
    }

    private fun grade(v: Float?): String =
        if (v == null) LIPSA else "${v.roundToInt()}°C"

    private fun procent(v: Float?): String =
        if (v == null) LIPSA else "${v.roundToInt()}%"

    companion object {
        /** Caietul de desen, acelasi cu al machetei aprobate. */
        const val LATIME = 1280f
        const val INALTIME = 720f

        private const val CX = 640f
        private const val CY = 400f
        private const val BARE = 54

        /** Capatul ceasului de viteza. */
        const val VITEZA_MAXIMA = 180f

        /**
         * Capetele barei de putere. Dr Prius arata pentru bateria asta
         * ~22 kW la descarcare si ~24 kW la incarcare, deci barele se umplu
         * cam acolo unde se umple si bateria.
         */
        const val PUTERE_TRACTIUNE = 22f
        const val PUTERE_RECUPERARE = 24f

        /**
         * Cat de lata e banda pe care trece melodia. E masurata in caietul
         * de desen, nu pe ecran: banda sta sub ceas, unde e si modulatorul.
         */
        const val LATIME_BANDA = 1280f

        /**
         * Cat de tare se stinge bordul noaptea. 0x8C din 0xFF inseamna ca
         * mai trece cam 45% din lumina - destul cat sa se citeasca, putin
         * cat sa nu bata in parbriz.
         */
        val PERDEA_NOAPTE = Color.argb(0x8C, 0, 0, 0)

        /** Ce scriem acolo unde masina nu da valoarea. */
        const val LIPSA = "---"

        /**
         * Cat lasam bordul sa se intinda in lateral pe ecranele late, in
         * unitati de caiet. Peste atat, coloanele ar ajunge atat de departe
         * de ceas incat n-ai mai cuprinde bordul dintr-o privire.
         */
        const val INTINDERE_MAXIMA = 220f
    }
}
