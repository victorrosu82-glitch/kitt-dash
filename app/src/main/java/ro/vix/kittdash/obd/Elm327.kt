package ro.vix.kittdash.obd

/**
 * Dialogul cu un ELM327: initializarea, interogarea unui PID si citirea
 * octetilor din raspuns. Nu stie nimic despre Bluetooth - lucreaza peste
 * [ObdLink], deci merge la fel cu adaptorul real si cu simulatorul.
 */
class Elm327(private val legatura: ObdLink) {

    var versiune: String? = null
        private set

    var protocol: String? = null
        private set

    /**
     * Pune adaptorul in starea in care il vrem: fara ecou, fara spatii,
     * fara antete, protocol ales automat. Fiecare pas conteaza - cu ecoul
     * pornit raspunsurile vin amestecate cu comenzile.
     */
    fun initializeaza() {
        // Dupa reset adaptorul uita tot si raspunde lent; ii dam timp.
        versiune = legatura.comanda("ATZ", TIMEOUT_RESET).liniiCurate()
            .lastOrNull { it.contains("ELM", ignoreCase = true) }

        // Fara ecou nu putem lucra: raspunsurile ar veni amestecate cu comenzile.
        val raspunsEcou = legatura.comanda("ATE0", TIMEOUT_AT)
        if (!raspunsEcou.contineOk()) {
            throw ObdException("Adaptorul a refuzat ATE0: ${raspunsEcou.peUnRand()}")
        }
        // Restul sunt doar de confort. Clonele refuza cate una, dar merg oricum.
        for (comanda in COMENZI_SETARE) {
            legatura.comanda(comanda, TIMEOUT_AT)
        }
        versiune = versiune ?: legatura.comanda("ATI", TIMEOUT_AT).peUnRand()

        // Prima cerere la masina: aici se alege protocolul si aici se vede
        // daca masina chiar vorbeste cu noi.
        val sondaj = legatura.comanda("0100", TIMEOUT_PRIMA_CERERE)
        val text = sondaj.peUnRand().uppercase()
        if ("UNABLE TO CONNECT" in text) {
            throw ObdException("Adaptorul nu ajunge la masina. E contactul pus?")
        }
        if ("NO DATA" in text || "STOPPED" in text) {
            throw ObdException("Masina nu a raspuns la prima cerere (${text.take(40)})")
        }
        protocol = legatura.comanda("ATDP", TIMEOUT_AT).peUnRand()
    }

    /**
     * Intreaba masina ce PID-uri standard da, grupa cu grupa (0100, 0120, ...).
     * Intoarce numerele PID-urilor anuntate.
     */
    fun pidSuportate(): Set<Int> {
        val gasite = mutableSetOf<Int>()
        var baza = 0x00
        while (baza <= 0xC0) {
            val raspuns = legatura.comanda("01%02X".format(baza), TIMEOUT_PID)
            val octeti = octetiDin(raspuns, "41%02X".format(baza)) ?: break
            if (octeti.size < 4) break
            var masca = 0L
            for (i in 0 until 4) masca = (masca shl 8) or octeti[i].toLong()
            for (i in 0 until 32) {
                if ((masca shr (31 - i)) and 1L == 1L) gasite.add(baza + i + 1)
            }
            // Ultimul bit spune daca exista si grupa urmatoare.
            if (masca and 1L == 0L) break
            baza += 0x20
        }
        return gasite
    }

    /**
     * Cere un PID si intoarce valoarea decodata, sau null daca masina nu
     * da PID-ul (NO DATA) ori raspunsul e stricat.
     */
    fun citeste(pid: ObdPid): Double? {
        val raspuns = legatura.comanda(pid.cerere, TIMEOUT_PID)
        val octeti = octetiDin(raspuns, pid.antetRaspuns) ?: return null
        if (octeti.size < pid.octeti) return null
        return try {
            pid.decodeaza(octeti)
        } catch (_: IndexOutOfBoundsException) {
            null
        }
    }

    companion object {
        private const val TIMEOUT_AT = 2_000L
        private const val TIMEOUT_RESET = 6_000L
        private const val TIMEOUT_PRIMA_CERERE = 8_000L
        // Viecar-ul real a depasit 1200 ms din cand in cand si a rupt legatura
        // degeaba. Mai bine asteptam, ca o reconectare costa secunde.
        private const val TIMEOUT_PID = 2_500L

        private val COMENZI_SETARE = listOf(
            "ATL0", // fara linefeed
            "ATS0", // fara spatii
            "ATH0", // fara antete (le pornim in etapa 2, la datele hibride)
            "ATSP0", // protocol ales automat
            "ATAT1", // temporizare adaptiva: raspunsuri mai dese
        )

        /**
         * Scoate octetii de date dintr-un raspuns, de ex. "410D1F" cu antetul
         * "410D" da [0x1F]. Merge si cu spatii, si cu raspunsuri pe mai multe
         * linii, si cu ecoul comenzii in fata.
         */
        fun octetiDin(raspuns: String, antet: String): IntArray? {
            val text = raspuns
                .filter { it.isLetterOrDigit() }
                .uppercase()
            if ("NODATA" in text || "UNABLETOCONNECT" in text) return null
            val poz = text.indexOf(antet)
            if (poz < 0) return null
            val hex = text.substring(poz + antet.length)
                .takeWhile { it in "0123456789ABCDEF" }
            val lungime = hex.length - hex.length % 2
            if (lungime == 0) return null
            return IntArray(lungime / 2) { i ->
                hex.substring(i * 2, i * 2 + 2).toInt(16)
            }
        }

        private fun String.peUnRand(): String =
            replace('\r', ' ').replace('\n', ' ').split(" ")
                .filter { it.isNotBlank() }.joinToString(" ")

        private fun String.liniiCurate(): List<String> =
            split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }

        private fun String.contineOk(): Boolean {
            val t = uppercase()
            // Unele clone raspund "?" la ATAT1 dar merg oricum; "OK" e ce cerem.
            return "OK" in t || "ELM" in t
        }
    }
}
