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
     * Trimite o comanda si intoarce raspunsul brut, nedecodat. Serveste
     * sondei hibride, care are nevoie sa vada exact ce spune masina.
     */
    fun comandaBruta(comanda: String, timeoutMs: Long = TIMEOUT_AT): String =
        legatura.comanda(comanda, timeoutMs)

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

    /** Calculatorul caruia ii vorbim acum; null = cel implicit. */
    private var antetCurent: String? = null

    /** Dupa ce altcineva a umblat la ATSH (sonda), nu mai stim pe ce adresa suntem. */
    fun uitaAntetul() {
        antetCurent = "necunoscut"
    }

    /**
     * Cere un PID si intoarce marimile decodate, sau o harta goala daca
     * masina nu da PID-ul (NO DATA) ori raspunsul e stricat.
     *
     * Daca PID-ul sta la alt calculator (de ex. bateria HV, la 7E2),
     * schimba intai adresa cu ATSH - dar numai cand chiar se schimba,
     * fiindca fiecare comanda in plus fura din cele ~10 raspunsuri pe
     * secunda pe care le da adaptorul.
     */
    fun citeste(pid: ObdPid): Map<Marime, Double> {
        alegeAntetul(pid.antet)
        val raspuns = legatura.comanda(pid.cerere, TIMEOUT_PID)
        val octeti = octetiDin(raspuns, pid.antetRaspuns) ?: return emptyMap()
        if (octeti.size < pid.octeti) return emptyMap()
        return try {
            pid.marimi.associate { (marime, formula) -> marime to formula(octeti) }
        } catch (_: IndexOutOfBoundsException) {
            emptyMap()
        }
    }

    /**
     * Cere mai multe PID-uri ale aceluiasi calculator intr-o singura
     * intrebare ("010D0C11"). Standardul permite pana la 6; masina raspunde
     * cu toate, unul dupa altul: 41 0D xx 0C xx xx 11 xx. O intrebare in loc
     * de trei inseamna de doua-trei ori mai multe citiri pe secunda.
     *
     * Intoarce null daca raspunsul nu are forma asteptata - atunci bucla se
     * intoarce la intrebarile cate una.
     */
    fun citesteGrup(pids: List<ObdPid>): Map<ObdPid, Map<Marime, Double>>? {
        require(pids.isNotEmpty() && pids.all { it.antet == pids[0].antet })
        alegeAntetul(pids[0].antet)
        val cerere = "01" + pids.joinToString("") { "%02X".format(it.pid) }
        val raspuns = legatura.comanda(cerere, TIMEOUT_PID)
        val octeti = octetiDin(raspuns, "41%02X".format(pids[0].pid)) ?: return null
        // dupa primul PID vin datele lui, apoi PID, date, PID, date...
        val rezultat = mutableMapOf<ObdPid, Map<Marime, Double>>()
        var poz = 0
        var pid: ObdPid? = pids[0]
        while (pid != null) {
            if (poz + pid.octeti > octeti.size) return null
            val bucata = octeti.copyOfRange(poz, poz + pid.octeti)
            rezultat[pid] = try {
                pid.marimi.associate { (marime, formula) -> marime to formula(bucata) }
            } catch (_: IndexOutOfBoundsException) {
                return null
            }
            poz += pid.octeti
            if (poz >= octeti.size) break
            val urmator = octeti[poz]
            pid = pids.firstOrNull { it.pid == urmator } ?: break
            poz++
        }
        return rezultat
    }

    /**
     * Cere un bloc Toyota (serviciul 21) de la calculatorul [antet] si
     * intoarce octetii de dupa "61xx", sau null. Pentru blocurile bateriei HV.
     */
    fun citesteBloc(antet: String, bloc: Int): IntArray? {
        alegeAntetul(antet)
        val raspuns = legatura.comanda("21%02X".format(bloc), TIMEOUT_PID)
        return octetiDin(raspuns, "61%02X".format(bloc))
    }

    private fun alegeAntetul(antet: String?) {
        if (antet == antetCurent) return
        val tinta = antet ?: ANTET_IMPLICIT
        val raspuns = legatura.comanda("ATSH$tinta", TIMEOUT_AT)
        if (!raspuns.contineOk()) {
            throw ObdException("Adaptorul a refuzat ATSH$tinta: ${raspuns.peUnRand()}")
        }
        antetCurent = antet
    }

    companion object {
        private const val TIMEOUT_AT = 2_000L
        private const val TIMEOUT_RESET = 6_000L
        /** Adresa motorului termic; cu ea lucreaza PID-urile obisnuite. */
        private const val ANTET_IMPLICIT = "7E0"
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
        /** Cadru dintr-un raspuns lung: "0:419A07004580". */
        private val CADRU = Regex("""(\d)\s*:\s*([0-9A-F]+)""")

        fun octetiDin(raspuns: String, antet: String): IntArray? {
            val brut = raspuns.uppercase()
            if ("NO DATA" in brut || "NODATA" in brut.replace(" ", "")) return null
            if ("UNABLE TO CONNECT" in brut || "STOPPED" in brut) return null

            // Raspunsurile mai lungi de 7 octeti vin in cadre numerotate:
            //   008 0:419A07004580 1:000D0000000000
            // Daca lipim totul de-a valma, cifra cadrului ("1") intra in
            // date si strica octetii de dupa ea - asa a iesit un curent de
            // 409 A acolo unde erau 1,3 A.
            val cadre = CADRU.findAll(brut).toList()
            val hex: String
            var deAsteptat = -1
            if (cadre.isNotEmpty()) {
                hex = cadre.sortedBy { it.groupValues[1].toInt() }
                    .joinToString("") { it.groupValues[2] }
                // Inaintea cadrelor sta lungimea intregului raspuns, in hex.
                val inainte = brut.substring(0, cadre.first().range.first)
                    .filter { it in HEX }
                if (inainte.isNotEmpty()) {
                    deAsteptat = inainte.takeLast(3).toIntOrNull(16) ?: -1
                }
            } else {
                hex = brut.filter { it in HEX }
            }

            val poz = hex.indexOf(antet)
            if (poz < 0) return null
            var date = hex.substring(poz + antet.length).takeWhile { it in HEX }
            // Cadrul din urma e umplut cu zerouri pana la 7 octeti; lungimea
            // anuntata spune cat din el conteaza cu adevarat.
            if (deAsteptat > 0) {
                val utili = (deAsteptat - antet.length / 2) * 2
                if (utili in 1..date.length) date = date.substring(0, utili)
            }
            val lungime = date.length - date.length % 2
            if (lungime == 0) return null
            return IntArray(lungime / 2) { i ->
                date.substring(i * 2, i * 2 + 2).toInt(16)
            }
        }

        private const val HEX = "0123456789ABCDEF"

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
