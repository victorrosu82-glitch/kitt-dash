package ro.vix.kittdash.obd

import kotlin.math.abs
import kotlin.math.max
import kotlin.math.min
import kotlin.math.roundToInt
import kotlin.math.sin

/**
 * Un ELM327 fals. Raspunde ca adaptorul real - acelasi text, acelasi prompt -
 * dar valorile vin dintr-un drum inventat. Cu el lucram si testam pe PC,
 * fara sa stam in masina.
 *
 * Imita si neajunsurile adaptorului real:
 *  - intarziere de cativa zeci de milisecunde la fiecare raspuns;
 *  - "NO DATA" la PID-urile pe care masina nu le da (combustibilul, ca la
 *    multe Toyota/Lexus);
 *  - "?" la comenzile necunoscute.
 *
 * [intarziereMs] se poate pune 0 in teste, ca sa ruleze instantaneu.
 */
class SimulatorObdLink(
    private val intarziereMs: Long = 35L,
    private val ceas: () -> Long = { System.currentTimeMillis() },
    /** PID-uri pe care "masina" nu le da, ca sa testam cazul lipsa. */
    private val pidLipsa: Set<Int> = setOf(ObdPid.NIVEL_COMBUSTIBIL.pid),
) : ObdLink {

    override val nume: String = "Simulator ELM327 v1.5"
    override val esteSimulator: Boolean = true

    private var deschis = false
    private var ecou = true
    private var inceputMs = 0L
    private val drum = Drum()

    override fun deschide() {
        deschis = true
        inceputMs = ceas()
    }

    override fun comanda(comanda: String, timeoutMs: Long): String {
        if (!deschis) throw ObdException("Simulatorul nu e deschis")
        if (intarziereMs > 0) Thread.sleep(intarziereMs)

        val c = comanda.trim().uppercase().replace(" ", "")
        val raspuns = raspunde(c)
        // Adaptorul reala pune ecoul comenzii inainte de raspuns, pana la ATE0.
        val prefix = if (ecou) comanda.trim() + "\r" else ""
        return prefix + raspuns
    }

    private fun raspunde(c: String): String = when {
        c == "ATZ" -> {
            ecou = true
            "\rELM327 v1.5\r\r"
        }
        c == "ATE0" -> {
            ecou = false
            "OK\r\r"
        }
        c == "ATI" -> "ELM327 v1.5\r\r"
        c == "ATDP" -> "AUTO, ISO 15765-4 (CAN 11/500)\r\r"
        c == "ATRV" -> "13.9V\r\r"
        c.startsWith("AT") -> "OK\r\r"
        c == "0100" -> "4100BE3FA813\r\r"
        c == "0120" -> "412080000000\r\r"
        c.startsWith("01") && c.length >= 4 -> raspundePid(c)
        else -> "?\r\r"
    }

    private fun raspundePid(c: String): String {
        val numar = c.substring(2, 4).toIntOrNull(16) ?: return "?\r\r"
        if (numar in pidLipsa) return "NO DATA\r\r"
        val pid = ObdPid.dupaPid(numar) ?: return "NO DATA\r\r"
        drum.avanseaza(ceas() - inceputMs)
        val octeti = octetiPentru(pid)
        val hex = octeti.joinToString("") { "%02X".format(it) }
        return pid.antetRaspuns + hex + "\r\r"
    }

    private fun octetiPentru(pid: ObdPid): IntArray = when (pid) {
        ObdPid.VITEZA -> intArrayOf(drum.vitezaKmh.roundToInt().coerceIn(0, 255))
        ObdPid.TURATIE -> {
            val sferturi = (drum.turatie * 4).roundToInt().coerceIn(0, 16383)
            intArrayOf(sferturi / 256, sferturi % 256)
        }
        ObdPid.SARCINA_MOTOR -> intArrayOf(procentOctet(drum.sarcina))
        ObdPid.POZITIE_CLAPETA -> intArrayOf(procentOctet(drum.clapeta))
        ObdPid.TEMP_LICHID -> intArrayOf((drum.tempLichid + 40).roundToInt().coerceIn(0, 255))
        ObdPid.TEMP_AER_ADMISIE -> intArrayOf((18.0 + 40).roundToInt())
        ObdPid.TENSIUNE_MODUL -> {
            val mv = (drum.tensiune * 1000).roundToInt().coerceIn(0, 65535)
            intArrayOf(mv / 256, mv % 256)
        }
        ObdPid.NIVEL_COMBUSTIBIL -> intArrayOf(procentOctet(62.0))
        ObdPid.TIMP_FUNCTIONARE -> {
            val s = ((ceas() - inceputMs) / 1000).toInt().coerceIn(0, 65535)
            intArrayOf(s / 256, s % 256)
        }
    }

    private fun procentOctet(procent: Double): Int =
        (procent * 255.0 / 100.0).roundToInt().coerceIn(0, 255)

    override fun close() {
        deschis = false
    }

    /**
     * Un drum inventat: porniri, accelerari, mers constant si frane, intr-un
     * ciclu de doua minute. Nu e un model de masina, doar valori plauzibile
     * care se mai si schimba, ca sa vedem bordul viu.
     */
    private class Drum {
        var vitezaKmh = 0.0; private set
        var turatie = 0.0; private set
        var sarcina = 0.0; private set
        var clapeta = 0.0; private set
        var tempLichid = 22.0; private set
        var tensiune = 14.1; private set

        fun avanseaza(deLaPornireMs: Long) {
            val t = deLaPornireMs / 1000.0
            val cicluS = 120.0
            val faza = (t % cicluS) / cicluS

            // Profil de viteza pe ciclu: oprit, accelerare, constant, frana.
            val vizata = when {
                faza < 0.08 -> 0.0
                faza < 0.30 -> 90.0 * (faza - 0.08) / 0.22
                faza < 0.60 -> 90.0 + 8.0 * sin(t / 4.0)
                faza < 0.75 -> 50.0 + 12.0 * sin(t / 3.0)
                faza < 0.92 -> 50.0 * (1.0 - (faza - 0.75) / 0.17)
                else -> 0.0
            }.coerceAtLeast(0.0)

            // Netezim, ca sa nu sara valorile de la o citire la alta.
            vitezaKmh += (vizata - vitezaKmh) * 0.12
            if (vitezaKmh < 0.4) vitezaKmh = 0.0

            val accelereaza = vizata > vitezaKmh + 0.5
            clapeta = when {
                vitezaKmh < 0.5 -> 12.0
                accelereaza -> min(85.0, 25.0 + abs(vizata - vitezaKmh) * 4.0)
                else -> 18.0 + 6.0 * sin(t / 5.0)
            }
            sarcina = (clapeta * 0.8 + 10.0).coerceIn(0.0, 100.0)

            // Pe hibrid motorul termic se opreste; la viteza mica turatia e 0.
            turatie = when {
                vitezaKmh < 12.0 && !accelereaza -> 0.0
                else -> (900.0 + vitezaKmh * 22.0 + clapeta * 6.0).coerceIn(0.0, 5800.0)
            }

            // Lichidul se incalzeste la 88 grade si ramane acolo.
            val tinta = if (turatie > 0) 88.0 else max(tempLichid - 0.01, 70.0)
            tempLichid += (tinta - tempLichid) * 0.004

            tensiune = 13.6 + 0.5 * sin(t / 9.0)
        }
    }
}
