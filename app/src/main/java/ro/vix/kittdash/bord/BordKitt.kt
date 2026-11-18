package ro.vix.kittdash.bord

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PorterDuff
import android.graphics.PorterDuffColorFilter
import android.graphics.RectF
import android.graphics.Typeface
import ro.vix.kittdash.navigatie.Distanta
import ro.vix.kittdash.navigatie.InstructiuneNav
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
    private val imagine = Paint(Paint.ANTI_ALIAS_FLAG or Paint.FILTER_BITMAP_FLAG)


    /**
     * Deseneaza un cadru pe toata suprafata data.
     *
     * [latime] si [inaltime] sunt in pixelii suprafetei; bordul se aseaza in
     * mijloc, pastrandu-si proportia, ca sa nu se lateasca pe ecrane late.
     */
    fun deseneaza(
        canvas: Canvas,
        latime: Float,
        inaltime: Float,
        stare: StareBord,
        nav: InstructiuneNav? = null,
    ) {
        canvas.drawColor(fundal)
        if (latime <= 0f || inaltime <= 0f) return

        if (nav == null) {
            bord(canvas, latime, inaltime, stare)
        } else if (latime / inaltime < FORMA_DOAR_PANOU) {
            // Fereastra aproape patrata (Android Auto ne-a lasat doar un colt,
            // de pilda cat e deschisa lista de aplicatii): bordul si panoul
            // n-ar mai incapea amandoua lizibil. Ramane manevra, pe tot locul.
            panouNavigatie(canvas, 0f, latime, inaltime, nav, stare, sus = 12f)
        } else {
            // Cat merge navigatia, bordul se strange in stanga si lasa loc
            // panoului cu manevra. Fara navigatie, panoul nu exista deloc.
            val latimeBord = latime * FRACTIE_BORD
            bord(canvas, latimeBord, inaltime, stare)
            panouNavigatie(canvas, latimeBord, latime, inaltime, nav, stare)
        }

        // Noaptea tragem o perdea subtire peste tot. E mai bine decat sa
        // stingem fiecare culoare in parte: asa raman aceleasi culori, doar
        // cu mai putina lumina, si nu se poate uita vreuna.
        if (stare.noapte) canvas.drawColor(PERDEA_NOAPTE)
    }

    private fun bord(canvas: Canvas, latime: Float, inaltime: Float, stare: StareBord) {
        // Caietul are 720 de randuri, dar desenul ocupa doar fasia dintre
        // SUS_CONTINUT si JOS_CONTINUT; restul e margine goala. Pe ecranele
        // late si joase ale masinii inaltimea hotaraste marimea, deci
        // potrivim dupa desen, nu dupa caiet - bordul iese cu o treime mai mare.
        val inaltimeDesen = JOS_CONTINUT - SUS_CONTINUT
        val scara = min(latime / LATIME, inaltime / inaltimeDesen)
        val stanga = (latime - LATIME * scara) / 2f
        val sus = (inaltime - inaltimeDesen * scara) / 2f - SUS_CONTINUT * scara

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
    }

    // --- panoul de navigatie --------------------------------------------

    /**
     * Manevra urmatoare, in dreapta bordului: sageata, distanta mare, bara de
     * apropiere si strada. Aspectul e cel din `docs/macheta-waze.html`.
     *
     * Se deseneaza direct in pixelii ecranului, nu in caietul de 1280x720:
     * panoul are alta forma decat bordul si trebuie sa umple exact locul ramas.
     */
    private fun panouNavigatie(
        canvas: Canvas,
        stanga: Float,
        dreapta: Float,
        inaltime: Float,
        nav: InstructiuneNav,
        stare: StareBord,
        sus: Float = 70f,
    ) {
        val u = inaltime / 460f
        val x0 = stanga + 8f * u
        val x1 = dreapta - 10f * u
        val y0 = sus * u
        val y1 = inaltime - 14f * u
        val l = x1 - x0
        val h = y1 - y0
        if (l < 80f * u || h < 120f * u) return

        // rama, cu colturile aprinse ca o consola KITT
        umplere.color = Color.parseColor("#0a0302")
        oval.set(x0, y0, x1, y1)
        canvas.drawRoundRect(oval, 6f * u, 6f * u, umplere)
        linie.color = Color.parseColor("#5a1610")
        linie.strokeWidth = 1.5f * u
        canvas.drawRoundRect(oval, 6f * u, 6f * u, linie)
        linie.color = rosu
        linie.strokeWidth = 3f * u
        val c = 18f * u
        for ((x, y, dx, dy) in listOf(
            listOf(x0, y0, 1f, 1f), listOf(x1, y0, -1f, 1f),
            listOf(x0, y1, 1f, -1f), listOf(x1, y1, -1f, -1f),
        )) {
            canvas.drawLine(x, y, x + dx * c, y, linie)
            canvas.drawLine(x, y, x, y + dy * c, linie)
        }

        val margine = 22f * u
        text(canvas, x0 + margine, y0 + 30f * u, 13f * u, eticheta, "NAVIGATION", Paint.Align.LEFT, 3f * u)
        // Numele aplicatiei doar daca incape langa "NAVIGATION", nu peste el.
        if (l > 300f * u) {
            text(canvas, x1 - margine, y0 + 30f * u, 13f * u, rosuSters, nav.sursa, Paint.Align.RIGHT, 3f * u)
        }

        val metri = nav.metri
        val iminenta = metri != null && metri < InstructiuneNav.APROAPE_METRI
        val clipeste = iminenta && (stare.timpS * 4f).toInt() % 2 == 0
        val culoareSageata = if (clipeste) rosu else ambra

        // sageata, in stanga panoului
        val sageata = nav.sageata
        val latura = minOf(l * 0.40f, h * 0.42f)
        val sx = x0 + margine
        val sy = y0 + h * 0.40f - latura / 2f
        if (sageata != null && !sageata.isRecycled) {
            val tinta = RectF(sx, sy, sx + latura, sy + latura)
            // haloul rosu, putin mai mare, apoi sageata plina
            imagine.colorFilter = PorterDuffColorFilter(rosu, PorterDuff.Mode.SRC_IN)
            imagine.alpha = 70
            val halou = RectF(tinta).apply { inset(-6f * u, -6f * u) }
            canvas.drawBitmap(sageata, null, halou, imagine)
            imagine.colorFilter = PorterDuffColorFilter(culoareSageata, PorterDuff.Mode.SRC_IN)
            imagine.alpha = 255
            canvas.drawBitmap(sageata, null, tinta, imagine)
        }

        // distanta, mare, in dreapta
        val (cifra, unitate) = Distanta.desparte(nav.distanta)
        var marime = h * 0.26f
        scrisGros.textSize = marime
        val loc = l - latura - margine * 3f
        val lat = scrisGros.measureText(cifra)
        if (lat > loc && lat > 0f) marime *= loc / lat
        val yCifra = y0 + h * 0.46f
        text(canvas, x1 - margine, yCifra, marime, rosuAprins, cifra, Paint.Align.RIGHT, 0f, gros = true)
        val numeUnitate = when (unitate) {
            "M" -> "METRI"
            else -> unitate
        }
        if (numeUnitate.isNotEmpty()) {
            text(canvas, x1 - margine, yCifra + h * 0.10f, h * 0.065f, rosuSters, numeUnitate, Paint.Align.RIGHT, 4f * u)
        }

        // bara de apropiere: se umple pe masura ce te apropii de manevra
        val apropiere = nav.apropiere
        if (apropiere != null) {
            val segmente = 12
            val bx1 = x1 - margine
            val bx0 = maxOf(sx + latura + margine, bx1 - 230f * u)
            val pas = (bx1 - bx0) / segmente
            val by = y0 + h * 0.64f
            for (i in 0 until segmente) {
                val aprins = i < apropiere * segmente
                umplere.color = when {
                    !aprins -> stins
                    i > 8 -> rosu
                    else -> ambra
                }
                canvas.drawRect(bx0 + i * pas, by, bx0 + i * pas + pas * 0.75f, by + 10f * u, umplere)
            }
        }

        // strada, jos
        val strada = nav.strada
        if (strada != null) {
            text(canvas, x0 + margine, y0 + h * 0.78f, 13f * u, eticheta, "URMEAZA", Paint.Align.LEFT, 2.5f * u)
            var m = h * 0.085f
            val maxim = l - 2f * margine
            scris.textSize = m
            val ls = 2f * u / m
            scris.letterSpacing = ls
            val w = scris.measureText(strada.uppercase())
            scris.letterSpacing = 0f
            if (w > maxim && w > 0f) m = maxOf(m * maxim / w, h * 0.05f)
            text(
                canvas, x0 + margine, y0 + h * 0.90f, m, valoare,
                taie(strada.uppercase(), m, maxim), Paint.Align.LEFT, 2f * u,
            )
        }
    }

    /** Taie textul cu "..." daca tot nu incape la marimea data. */
    private fun taie(text: String, marime: Float, maxim: Float): String {
        scris.textSize = marime
        if (scris.measureText(text) <= maxim) return text
        var t = text
        while (t.length > 1 && scris.measureText("$t…") > maxim) t = t.dropLast(1)
        return "$t…"
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

        // Scanerul se lateste odata cu bordul, ca sa ramana semnul lui KITT
        // si pe ecranul lat al masinii, nu o dunga mica in mijloc.
        val latimeScaner = SCANER_LATIME + SCANER_INTINDERE * intindere
        val pas = latimeScaner / SCANER_CASUTE
        val casuta = pas - SCANER_GOL
        val start = CX + intindere - latimeScaner / 2f

        for (i in 0 until SCANER_CASUTE) {
            val opacitate = LogicaBord.stralucireScaner(i, pozitie, directie)
            val x = start + i * pas
            // Haloul din jurul casutelor aprinse: lumina rosie care se varsa
            // putin in jur, ca la lampile adevarate.
            if (opacitate > 0.3f) {
                umplere.color = rosu
                umplere.alpha = (opacitate * 70).roundToInt().coerceIn(0, 255)
                canvas.drawRect(
                    x - SCANER_HALOU, SCANER_SUS - SCANER_HALOU,
                    x + casuta + SCANER_HALOU, SCANER_JOS + SCANER_HALOU, umplere,
                )
            }
            umplere.color = rosu
            umplere.alpha = (opacitate * 255).roundToInt().coerceIn(0, 255)
            canvas.drawRect(x, SCANER_SUS, x + casuta, SCANER_JOS, umplere)
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
        // Pe 18.11.2026: mai mare, ingrosata si numai in fasia din mijloc,
        // cam cat chenarul galben desenat de Victor (vezi [Ecrane]).
        scrisGros.textSize = 42f
        scrisGros.letterSpacing = 0.04f
        val latimeText = scrisGros.measureText(melodie)

        val stanga = CX - JUMATATE_BANDA
        val dreapta = CX + JUMATATE_BANDA
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
        canvas.clipRect(stanga, 600f, dreapta, 668f)
        scrisGros.color = ambra
        scrisGros.alpha = (opacitate * 255).roundToInt().coerceIn(0, 255)
        scrisGros.textAlign = Paint.Align.LEFT
        canvas.drawText(melodie, x, 654f, scrisGros)
        scrisGros.alpha = 255
        scrisGros.letterSpacing = 0f
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

        /**
         * Fasia din caiet pe care chiar se deseneaza: de la numele masinii
         * pana sub modulatorul de voce. Dupa ea se potriveste bordul pe ecran.
         */
        /** Cat din latime ramane bordului cand apare panoul de navigatie. */
        const val FRACTIE_BORD = 0.64f

        /**
         * Sub forma asta (latime / inaltime) fereastra e prea ingusta pentru
         * bord si panou impreuna; atunci ramane doar panoul. Zona de 386x412
         * pe care o da Android Auto cand deschizi lista de aplicatii are 0,94.
         */
        const val FORMA_DOAR_PANOU = 1.6f

        const val SUS_CONTINUT = 16f
        const val JOS_CONTINUT = 690f

        /**
         * Scanerul. In macheta avea 600 x 20; aici e mai lat si de peste doua
         * ori mai inalt, iar pe ecranele late creste odata cu intinderea.
         * Sta intre linia de sub antet (62) si etichetele coloanelor (~100);
         * arcul ceasului incepe abia la 148.
         */
        private const val SCANER_CASUTE = 30
        private const val SCANER_LATIME = 720f
        private const val SCANER_INTINDERE = 1.2f
        private const val SCANER_GOL = 5f
        private const val SCANER_SUS = 72f
        private const val SCANER_JOS = 116f
        private const val SCANER_HALOU = 4f

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

        /** Jumatatea fasiei melodiei, in caietul bordului (~ chenarul galben). */
        const val JUMATATE_BANDA = 520f

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
        const val INTINDERE_MAXIMA = 360f
    }
}
