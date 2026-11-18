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
        // Mastile se socotesc din ce stie simulatorul sa raspunda, ca sa nu
        // anunte altceva decat da. Prima data erau scrise de mana si
        // temperatura de afara lipsea din ele, desi simulatorul o avea.
        c == "0100" -> masca(0x00)
        c == "0120" -> masca(0x20)
        c == "0140" -> masca(0x40)
        c == "0160" -> masca(0x60)
        c == "0180" -> masca(0x80)
        c.startsWith("01") && c.length > 4 -> raspundeGrup(c)
        c.startsWith("01") && c.length >= 4 -> raspundePid(c)
        c.startsWith("21") && c.length == 4 -> BLOCURI[c.substring(2)]?.let { "61" + c.substring(2) + it + "\r\r" } ?: "7F2112\r\r"
        else -> "?\r\r"
    }

    /** Mai multe PID-uri intr-o intrebare: 41 0D xx 0C xx xx ... */
    private fun raspundeGrup(c: String): String {
        drum.avanseaza(ceas() - inceputMs)
        val parti = c.substring(2).chunked(2).mapNotNull { it.toIntOrNull(16) }
        val sb = StringBuilder("41")
        for (numar in parti) {
            if (numar in pidLipsa) continue
            val pid = ObdPid.dupaPid(numar) ?: continue
            sb.append("%02X".format(numar))
            octetiPentru(pid).forEach { sb.append("%02X".format(it)) }
        }
        return if (sb.length == 2) "NO DATA\r\r" else "$sb\r\r"
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
        ObdPid.TEMP_EXTERIOARA -> intArrayOf((11.0 + 40).roundToInt())
        // Catalizatorul e cald cat merge motorul si se raceste incet dupa.
        ObdPid.TEMP_CATALIZATOR -> {
            val v = ((if (drum.turatie > 0) 520.0 + drum.sarcina * 2.0 else 380.0) + 40.0) * 10.0
            intArrayOf(v.roundToInt() / 256, v.roundToInt() % 256)
        }
        // Aerul creste cu turatia si cu sarcina; motorul oprit nu trage nimic.
        ObdPid.DEBIT_AER -> {
            val gs = if (drum.turatie > 0) drum.turatie / 1000.0 * (2.0 + drum.sarcina / 10.0) else 0.0
            val v = (gs * 100.0).roundToInt().coerceIn(0, 0xFFFF)
            intArrayOf(v / 256, v % 256)
        }
        ObdPid.TENSIUNE_MODUL -> {
            val mv = (drum.tensiune * 1000).roundToInt().coerceIn(0, 65535)
            intArrayOf(mv / 256, mv % 256)
        }
        ObdPid.NIVEL_COMBUSTIBIL -> intArrayOf(procentOctet(62.0))
        ObdPid.TIMP_FUNCTIONARE -> {
            val s = ((ceas() - inceputMs) / 1000).toInt().coerceIn(0, 65535)
            intArrayOf(s / 256, s % 256)
        }
        ObdPid.BATERIE_HV -> intArrayOf(procentOctet(drum.bateriaHv))
        ObdPid.BATERIE_HV_ELECTRIC -> {
            // Aceeasi asezare ca pe masina: A stare, B stare extinsa,
            // C-D tensiune, E-F curent.
            val v = (drum.tensiuneHv * ObdPid.SCARA_TENSIUNE).roundToInt().coerceIn(0, 65535)
            // Curentul e un intreg cu semn pe doi octeti, ca pe masina.
            val iBrut = (drum.curentHv * ObdPid.SCARA_CURENT).roundToInt().coerceIn(-32768, 32767)
            val i = if (iBrut < 0) iBrut + 0x10000 else iBrut
            intArrayOf(0x07, 0x00, v / 256, v % 256, i / 256, i % 256)
        }
    }

    /**
     * Raspunsul la "ce PID-uri stii din grupa asta": 32 de biti, plus
     * ultimul, care spune daca mai urmeaza o grupa.
     */
    private fun masca(baza: Int): String {
        var biti = 0L
        for (i in 0 until 32) {
            val pid = baza + i + 1
            if (ObdPid.dupaPid(pid) != null && pid !in pidLipsa) {
                biti = biti or (1L shl (31 - i))
            }
        }
        if (ObdPid.entries.any { it.pid > baza + 32 }) biti = biti or 1L
        return "41%02X%08X".format(baza, biti) + "\r\r"
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
        var bateriaHv = 62.0; private set
        var tensiuneHv = 278.0; private set
        var curentHv = 1.3; private set

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

            // Bateria HV: curentul creste la accelerare si la franare, cand
            // masina recupereaza. Pe masina reala nu stim inca semnul
            // curentului, asa ca simulatorul da tot valori pozitive, ca
            // ele sa fie citite la fel ca acolo.
            val franeaza = vizata < vitezaKmh - 1.0
            // Semnul: plus cand bateria da curent, minus cand primeste.
            // Dr Prius si masina arata ~22 kW la descarcare si ~24 kW la
            // incarcare ca limite, deci curentii stau sub ~80 A.
            val curentTinta = when {
                franeaza -> -(30.0 + vitezaKmh * 0.3)
                accelereaza -> 20.0 + clapeta * 0.5
                vitezaKmh < 0.5 -> 1.3
                else -> 4.0 + 2.0 * sin(t / 6.0)
            }
            curentHv += (curentTinta - curentHv) * 0.2
            // Banda masurata pe masina: 266-285 V, cu caderi sub sarcina.
            tensiuneHv = 280.0 + 5.0 * sin(t / 13.0) - curentHv * 0.35
            // Se descarca la tractiune si se incarca la franare.
            bateriaHv = (bateriaHv + if (franeaza) 0.004 else -0.002).coerceIn(38.0, 78.0)
        }
    }

    private companion object {
        /** Blocurile bateriei, asa cum le-a dat Lexusul pe 18.11.2026 (7E2). */
        val BLOCURI = mapOf(
            "81" to "37BE37BE376C377C37A537A5377C37BE37F7382037A53795379537A5376C377C37BE" + "AE870B86",
            "95" to "1313121212131313141413131313121313",
            "87" to "448A4C6B4D944A0249CC4CF04BCA",
            "61" to "4E455080",
            "62" to "4B424D80",
            "63" to "3C363C80",
            "70" to "40425000",
            "29" to "49",
        )
    }
}
