package ro.vix.kittdash.obd

import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Proba de baterie pentru comparatia cu Dr Prius: 2 minute, la 2 secunde,
 * numai blocurile bateriei si cele cateva candidate. Scrie raspunsurile
 * brute (cu antete) si, alaturi, valorile socotite, ca sa poata fi puse
 * langa jurnalul Dr Prius. Planul: `docs/explorare/plan-confirmare-drprius.md`.
 *
 * Ca si explorarea, trimite numai cereri de citire ([ExploratorMasina.esteDoarCitire]).
 */
class ProbaBaterie(
    private val legatura: ObdLink,
    private val progres: (String, Float) -> Unit = { _, _ -> },
    private val ceas: () -> Long = { System.currentTimeMillis() },
    private val asteapta: (Long) -> Unit = { Thread.sleep(it) },
    private val durataMs: Long = DURATA_MS,
) {

    private val raport = StringBuilder()
    private val ora = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun ruleaza(): String {
        scrie("Proba baterie KITT Dash - pentru comparatia cu Dr Prius")
        scrie("Fiecare tur: raspunsurile brute, apoi valorile socotite.")
        scrie("=".repeat(72))
        try {
            for (c in listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATAT1", "ATCAF1")) cere(c)
            val inceput = ceas()
            var tur = 0
            while (ceas() - inceput < durataMs) {
                tur++
                val trecut = ceas() - inceput
                progres("Proba baterie: ${trecut / 1000} s din ${durataMs / 1000}", trecut.toFloat() / durataMs)
                scrie("")
                scrie("--- turul $tur, ${ora.format(Date(ceas()))}, la ${trecut / 1000.0} s")
                unTur()
                val rest = PERIOADA_MS - (ceas() - inceput - trecut)
                if (rest > 0) asteapta(rest)
            }
        } catch (e: ObdException) {
            scrie("!! Proba s-a oprit: ${e.message}")
        } finally {
            for (c in listOf("ATH0", "ATS0", "ATAR", "ATSH7E0")) {
                try {
                    legatura.comanda(c, TIMEOUT_MS)
                } catch (_: ObdException) {
                }
            }
        }
        progres("Proba terminata", 1f)
        return raport.toString()
    }

    private fun unTur() {
        cere("ATSH7E2")
        cere("ATCRA7EA")
        val r = BLOCURI.associateWith { cere(it) }
        val soc = cere("015B")
        val electric = cere("019A")
        cere("ATSH7C0")
        cere("ATCRA7C8")
        val rezervor = cere("2129")

        // valorile socotite, pe randuri usor de comparat cu Dr Prius
        val v = DateHibrid.tensiuni(octeti(r["2181"], "6181"))
        if (v.isNotEmpty()) scrie("= tensiuni (V): " + v.joinToString(" ") { "%.3f".format(it) } + "  suma %.1f".format(v.sum()))
        val ir = DateHibrid.rezistente(octeti(r["2195"], "6195"))
        if (ir.isNotEmpty()) scrie("= rezistente (brut): " + ir.joinToString(" "))
        val t = DateHibrid.temperaturi(octeti(r["2187"], "6187"))
        if (t.isNotEmpty()) scrie("= temperaturi (°C): " + t.joinToString(" ") { "%.2f".format(it) })
        octeti(soc, "415B")?.firstOrNull()?.let { scrie("= SOC: %.1f %%".format(it * 100.0 / 255)) }
        octeti(electric, "419A")?.let { o ->
            if (o.size >= 6) {
                val brut = o[4] * 256 + o[5]
                val a = (if (brut >= 0x8000) brut - 0x10000 else brut) / ObdPid.SCARA_CURENT
                scrie("= curent: %.1f A, tensiune: %.1f V".format(a, (o[2] * 256 + o[3]) / ObdPid.SCARA_TENSIUNE))
            }
        }
        DateHibrid.rezervor(octeti(rezervor, "6129"))?.let { scrie("= rezervor (A/2): %.1f l".format(it)) }
    }

    private fun cere(comanda: String): String {
        require(ExploratorMasina.esteDoarCitire(comanda)) { "Cerere refuzata, nu e doar citire: $comanda" }
        val r = try {
            legatura.comanda(comanda, TIMEOUT_MS)
        } catch (e: ObdException) {
            "(tacere)"
        }
        if (!comanda.startsWith("AT")) scrie("$comanda -> ${r.replace('\r', ' ').replace('\n', ' ').trim()}")
        return r
    }

    private fun scrie(linie: String) {
        raport.append(linie).append('\n')
    }

    companion object {
        const val DURATA_MS = 120_000L
        const val PERIOADA_MS = 2_000L
        private const val TIMEOUT_MS = 1_500L

        /** Blocurile citite la fiecare tur, de la calculatorul hibrid. */
        val BLOCURI = listOf("2181", "2187", "2192", "2195", "2161", "2162", "2163", "2170", "2101", "2146", "214C", "2198")

        /** Datele de dupa [antet], din raspunsul cu antete si cadre (ATH1). */
        fun octeti(raspuns: String?, antet: String): IntArray? {
            if (raspuns == null) return null
            val cadre = Regex("""\b[0-9A-F]{3}((?: [0-9A-F]{2}(?![0-9A-F]))+)""").findAll(raspuns.uppercase())
            val date = mutableListOf<Int>()
            var total = -1
            for (m in cadre) {
                val b = m.groupValues[1].trim().split(" ").map { it.toInt(16) }
                when (b[0] shr 4) {
                    0 -> { date.clear(); date += b.drop(1).take(b[0] and 0x0F); total = date.size }
                    1 -> { date.clear(); total = ((b[0] and 0x0F) shl 8) or b[1]; date += b.drop(2) }
                    2 -> date += b.drop(1)
                }
            }
            val toate = if (total >= 0) date.take(total) else date
            val hex = toate.joinToString("") { "%02X".format(it) }
            val poz = hex.indexOf(antet)
            if (poz < 0 || poz % 2 != 0) return null
            val rest = hex.substring(poz + antet.length)
            return IntArray(rest.length / 2) { rest.substring(it * 2, it * 2 + 2).toInt(16) }
        }
    }
}
