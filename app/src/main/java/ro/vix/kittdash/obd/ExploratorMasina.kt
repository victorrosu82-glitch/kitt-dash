package ro.vix.kittdash.obd

import java.util.Locale

/**
 * Exploreaza toata masina: cauta fiecare calculator care raspunde, apoi il
 * intreaba tot ce se poate intreba FARA sa schimbe ceva, si strange
 * raspunsurile brute intr-un raport text. Nu decodeaza nimic - asta se face
 * dupa, pe calculator, comparand citiri facute in situatii diferite.
 *
 * Pasii:
 *  1. intrebarea catre toti (7DF): calculatoarele care tin standardul OBD;
 *  2. adresele 700-7F7, una cate una: orice raspuns, chiar si "nu stiu"
 *     (7F), inseamna ca acolo sta un calculator;
 *  3. adresele cu octet suplimentar (750 + xx), unde Toyota tine calculatoarele
 *     de caroserie - numai daca adaptorul stie ATCEA;
 *  4. pentru fiecare calculator gasit: valorile standard (modul 01), datele de
 *     identificare (modul 09), blocurile Toyota (modul 21), identificarea
 *     (22 F1xx si 1A);
 *  5. citiri repetate ale blocurilor gasite, ca sa se vada ce octeti se misca;
 *  6. cateva secunde de ascultare a ce isi spun calculatoarele intre ele.
 *
 * SIGURANTA: inainte de fiecare cerere, [esteDoarCitire] verifica serviciul.
 * Trec numai 01, 09, 21, 22 si 1A, care doar citesc, plus comenzile AT ale
 * adaptorului. Stergerea erorilor (04), scrierea (2E, 3B), rutinele (31),
 * sesiunile (10) si resetarile (11) nu pot pleca niciodata din clasa asta.
 */
class ExploratorMasina(
    private val legatura: ObdLink,
    /** Ce se intampla acum si cat s-a facut, 0..1. */
    private val progres: (String, Float) -> Unit = { _, _ -> },
    private val ceas: () -> Long = { System.currentTimeMillis() },
    /** Cate tururi de citiri repetate facem la pasul 5. */
    private val tururi: Int = TURURI_REPETATE,
    /** Pauza dinaintea repornirii adaptorului; in teste nu asteptam. */
    private val asteapta: (Long) -> Unit = { Thread.sleep(it) },
) {

    private val raport = StringBuilder()
    private var taceriLaRand = 0
    private var reporniri = 0

    // Ce era pus in adaptor, ca sa-l punem la loc dupa o repornire.
    private var antet: String? = null
    private var extins: String? = null
    private var filtru: String? = null

    /** Un calculator gasit: adresa la care il intrebam si de unde a raspuns. */
    data class Calculator(val adresa: String, val raspundeDe: String, val extins: String? = null) {
        val eticheta: String get() = if (extins == null) adresa else "$adresa/$extins"
    }

    fun ruleaza(): String {
        val inceput = ceas()
        try {
            pregateste()

            sectiune("1. Calculatoarele care tin standardul OBD (intrebare catre toti, 7DF)")
            progres("Intreb toate calculatoarele deodata", 0.01f)
            cere("ATAR")
            cere("ATSH7DF")
            cere("0100")
            cere("0900")

            sectiune("2. Cautare pe adrese, 700-7F7 (cerere 2101; orice raspuns = calculator)")
            val gasite = cautaAdrese()

            sectiune("3. Adrese cu octet suplimentar (750 + xx), calculatoarele de caroserie")
            val extinse = cautaAdreseExtinse()

            val toate = gasite + extinse
            sectiune("Rezumat: ${toate.size} calculatoare gasite")
            for (c in toate) scrie("  ${c.eticheta.padEnd(8)} raspunde de la ${c.raspundeDe}")

            val blocuri = mutableListOf<Pair<Calculator, String>>()
            toate.forEachIndexed { i, c ->
                sectiune("4. Calculatorul ${c.eticheta} (raspunde de la ${c.raspundeDe})")
                progres("Intreb calculatorul ${c.eticheta} (${i + 1} din ${toate.size})", 0.45f + 0.35f * i / toate.size.coerceAtLeast(1))
                blocuri += intrebaCalculatorul(c)
            }

            sectiune("5. Citiri repetate ale blocurilor gasite ($tururi tururi)")
            citiriRepetate(blocuri)

            sectiune("6. Ascultare: ce isi spun calculatoarele intre ele (${ASCULTARE_MS / 1000} s)")
            progres("Ascult ce isi spun calculatoarele", 0.97f)
            asculta()
        } catch (e: ObdException) {
            scrie("!! Explorarea s-a oprit: ${e.message}")
        } finally {
            refa()
        }
        val durata = (ceas() - inceput) / 1000
        scrie("")
        scrie("Gata in ${durata / 60} min ${durata % 60} s.")
        progres("Explorare terminata", 1f)
        return raport.toString()
    }

    // --- pasii -------------------------------------------------------------

    private fun pregateste() {
        scrie("Explorare KITT Dash - raspunsuri brute, nedecodate")
        scrie("Numai cereri de citire: servicii 01, 09, 21, 22, 1A si comenzi AT.")
        scrie("=".repeat(72))
        // Antete si spatii pornite: asa se vede CINE a raspuns si unde se
        // despart octetii. Temporizare scurta, ca adresele mute sa nu tina mult.
        for (c in SETARI) cere(c)
        cere("ATI")
        cere("ATDP")
        cere("ATRV")
    }

    private fun cautaAdrese(): List<Calculator> {
        val gasite = mutableListOf<Calculator>()
        val adrese = (0x700..0x7F7).filter { it != 0x7DF }
        adrese.forEachIndexed { i, a ->
            val adresa = "%03X".format(a)
            if (i % 8 == 0) progres("Caut calculatoare: adresa $adresa", 0.02f + 0.30f * i / adrese.size)
            cere("ATSH$adresa", tacut = true)
            // raspunsul vine de la adresa + 8; restul traficului nu ne intereseaza
            cere("ATCRA%03X".format(a + 8), tacut = true)
            val r = cere("2101", tacut = true)
            val cine = cineARaspuns(r)
            if (cine != null) {
                gasite += Calculator(adresa, cine)
                scrie("ATSH$adresa 2101 -> ${r.peUnRand()}")
            }
        }
        if (gasite.isEmpty()) scrie("(niciun calculator nu a raspuns la 2101)")
        return gasite
    }

    private fun cautaAdreseExtinse(): List<Calculator> {
        val gasite = mutableListOf<Calculator>()
        cere("ATSH750")
        cere("ATCRA758")
        val proba = cere("ATCEA40")
        if (!proba.uppercase().contains("OK")) {
            scrie("(adaptorul nu stie ATCEA - sarim pasul)")
            return gasite
        }
        for (x in 0x00..0xFF) {
            val ext = "%02X".format(x)
            if (x % 16 == 0) progres("Caut calculatoare de caroserie: 750/$ext", 0.32f + 0.12f * x / 256f)
            cere("ATCEA$ext", tacut = true)
            val r = cere("2101", tacut = true)
            val cine = cineARaspuns(r)
            if (cine != null) {
                gasite += Calculator("750", cine, ext)
                scrie("ATSH750 ATCEA$ext 2101 -> ${r.peUnRand()}")
            }
        }
        cere("ATCEA")
        if (gasite.isEmpty()) scrie("(niciun calculator de caroserie nu a raspuns)")
        return gasite
    }

    /** Intoarce blocurile modului 21 care au dat date, pentru pasul 5. */
    private fun intrebaCalculatorul(c: Calculator): List<Pair<Calculator, String>> {
        alege(c)
        val blocuri = mutableListOf<Pair<Calculator, String>>()

        if (c.extins == null) {
            // valorile standard: intai mastile, apoi fiecare valoare anuntata
            val anuntate = mutableListOf<Int>()
            var baza = 0x00
            while (baza <= 0xE0) {
                val r = cere("01%02X".format(baza))
                val masca = masca(r, "41%02X".format(baza)) ?: break
                for (i in 0 until 32) if ((masca shr (31 - i)) and 1L == 1L) anuntate += baza + i + 1
                if (masca and 1L == 0L) break
                baza += 0x20
            }
            for (pid in anuntate.filter { it % 0x20 != 0 }) cere("01%02X".format(pid))

            // identificarea: seria de sasiu, numele calculatorului, calibrarea
            val r09 = cere("0900")
            val m09 = masca(r09, "4900")
            if (m09 != null) {
                for (i in 0 until 32) if ((m09 shr (31 - i)) and 1L == 1L) cere("09%02X".format(i + 1))
            }
        }

        // blocurile Toyota: daca serviciul 21 nu e cunoscut (7F 21 11), sarim
        val r21 = cere("2101")
        if (!refuzaServiciul(r21, "21")) {
            for (bloc in 0x01..0xFF) {
                val cerere = "21%02X".format(bloc)
                val r = if (bloc == 0x01) r21 else cere(cerere, tacut = true)
                if (arePozitiv(r, "61%02X".format(bloc))) {
                    if (bloc != 0x01) scrie("$cerere -> ${r.peUnRand()}")
                    blocuri += c to cerere
                }
            }
        }

        // identificarea dupa standardul nou (22 F1xx)
        val r22 = cere("22F190")
        if (!refuzaServiciul(r22, "22")) {
            for (did in 0xF180..0xF19F) {
                if (did == 0xF190) continue
                val r = cere("22%04X".format(did), tacut = true)
                if (arePozitiv(r, "62%04X".format(did))) scrie("22%04X -> ${r.peUnRand()}".format(did))
            }
        }

        // identificarea dupa standardul vechi (1A)
        val r1a = cere("1A80")
        if (!refuzaServiciul(r1a, "1A")) {
            for (id in 0x81..0x9F) {
                val r = cere("1A%02X".format(id), tacut = true)
                if (arePozitiv(r, "5A%02X".format(id))) scrie("1A%02X -> ${r.peUnRand()}".format(id))
            }
        }
        return blocuri
    }

    private fun citiriRepetate(blocuri: List<Pair<Calculator, String>>) {
        if (blocuri.isEmpty()) {
            scrie("(niciun bloc de citit)")
            return
        }
        val inceput = ceas()
        for (tur in 1..tururi) {
            progres("Citiri repetate: turul $tur din $tururi", 0.80f + 0.17f * tur / tururi)
            scrie("--- turul $tur, la ${(ceas() - inceput) / 1000.0} s")
            var ultimul: Calculator? = null
            for ((c, cerere) in blocuri) {
                if (c != ultimul) {
                    alege(c)
                    ultimul = c
                }
                cere(cerere)
            }
            // si cateva valori standard alaturi, ca reper: viteza, turatia, bateria
            alege(Calculator("7E0", "7E8"))
            cere("010D")
            cere("010C")
            alege(Calculator("7E2", "7EA"))
            cere("015B")
            cere("019A")
        }
    }

    private fun asculta() {
        cere("ATCEA")
        cere("ATAR")
        cere("ATSH7E0")
        val auzit = try {
            legatura.asculta(ASCULTARE_MS)
        } catch (e: ObdException) {
            "(ascultarea a esuat: ${e.message})"
        }
        if (auzit.isBlank()) {
            scrie("(nu s-a auzit nimic - sau adaptorul nu stie sa asculte)")
        } else {
            auzit.split('\r', '\n').map { it.trim() }.filter { it.isNotEmpty() }.forEach { scrie("  $it") }
        }
    }

    /** Lasa adaptorul cum il vrea bucla obisnuita. */
    private fun refa() {
        for (c in listOf("ATCEA", "ATAR", "ATH0", "ATS0", "ATAT1", "ATST32", "ATSH7E0")) {
            try {
                legatura.comanda(c, TIMEOUT_MS)
            } catch (_: ObdException) {
            }
        }
    }

    // --- unelte ------------------------------------------------------------

    private fun alege(c: Calculator) {
        if (c.extins != null) {
            cere("ATSH${c.adresa}")
            cere("ATCEA${c.extins}")
        } else {
            cere("ATCEA")
            cere("ATSH${c.adresa}")
        }
        cere("ATCRA${c.raspundeDe}")
    }

    /**
     * Trimite o cerere si o trece in raport (daca nu e [tacut]). O tacere a
     * adaptorului nu opreste explorarea; multe la rand inseamna ca legatura
     * a cazut, si atunci ne oprim.
     */
    private fun cere(comanda: String, tacut: Boolean = false): String {
        require(esteDoarCitire(comanda)) { "Cerere refuzata, nu e doar citire: $comanda" }
        tine(comanda)
        val r = try {
            val raspuns = legatura.comanda(comanda, TIMEOUT_MS)
            taceriLaRand = 0
            raspuns
        } catch (e: ObdException) {
            taceriLaRand++
            if (taceriLaRand < TACERI_INAINTE_DE_REPORNIRE) {
                "(tacere)"
            } else if (reporneste()) {
                // dupa repornire mai incercam o data aceeasi cerere
                try {
                    legatura.comanda(comanda, TIMEOUT_MS).also { taceriLaRand = 0 }
                } catch (_: ObdException) {
                    "(tacere)"
                }
            } else {
                throw ObdException("adaptorul a tacut de $taceriLaRand ori la rand si nu si-a revenit", e)
            }
        }
        if (!tacut) scrie("$comanda -> ${r.peUnRand()}")
        return r
    }

    /** Tine minte adresa, octetul suplimentar si filtrul puse in adaptor. */
    private fun tine(comanda: String) {
        val c = comanda.uppercase(Locale.US)
        when {
            c.startsWith("ATSH") -> antet = c.removePrefix("ATSH")
            c == "ATCEA" -> extins = null
            c.startsWith("ATCEA") -> extins = c.removePrefix("ATCEA")
            c.startsWith("ATCRA") -> filtru = c.removePrefix("ATCRA")
            c == "ATAR" -> filtru = null
        }
    }

    /**
     * Clona ELM327 amuteste din cand in cand si nu mai raspunde la nimic
     * (s-a vazut de doua ori pe 18.11.2026, la calculatorul de airbag si
     * chiar la inceput). Atunci inchidem legatura, o deschidem iar, punem
     * adaptorul la loc cum era si mergem mai departe de unde am ramas.
     */
    private fun reporneste(): Boolean {
        if (reporniri >= REPORNIRI_MAXIME) return false
        reporniri++
        scrie("!! adaptorul a amutit; il repornesc (a $reporniri-a oara) si continui")
        progres("Adaptorul a amutit, il repornesc", -1f)
        return try {
            legatura.close()
            asteapta(PAUZA_REPORNIRE_MS)
            legatura.deschide()
            legatura.comanda("ATZ", 6_000L)
            for (c in SETARI) legatura.comanda(c, TIMEOUT_MS)
            // prima cerere catre masina alege din nou protocolul
            legatura.comanda("0100", 8_000L)
            antet?.let { legatura.comanda("ATSH$it", TIMEOUT_MS) }
            extins?.let { legatura.comanda("ATCEA$it", TIMEOUT_MS) }
            legatura.comanda(filtru?.let { "ATCRA$it" } ?: "ATAR", TIMEOUT_MS)
            taceriLaRand = 0
            true
        } catch (e: ObdException) {
            scrie("!! repornirea n-a mers: ${e.message}")
            false
        }
    }

    private fun scrie(linie: String) {
        raport.append(linie).append('\n')
    }

    private fun sectiune(titlu: String) {
        scrie("")
        scrie("== $titlu")
    }

    companion object {
        private const val TIMEOUT_MS = 1_500L
        private const val TACERI_INAINTE_DE_REPORNIRE = 3
        private const val REPORNIRI_MAXIME = 4
        private const val PAUZA_REPORNIRE_MS = 1_500L

        /** Cum vrem adaptorul pe toata explorarea. */
        private val SETARI = listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATAT0", "ATST19", "ATCAF1")
        const val TURURI_REPETATE = 15
        const val ASCULTARE_MS = 5_000L

        /** Serviciile care doar citesc. Orice altceva e refuzat. */
        private val SERVICII_DE_CITIRE = setOf("01", "09", "21", "22", "1A")

        /** Comenzile adaptorului pe care le folosim. ATMA e ascultarea. */
        private val COMENZI_AT = listOf(
            "ATE", "ATL", "ATS", "ATH", "ATAT", "ATST", "ATCAF", "ATI", "ATDP", "ATRV",
            "ATSH", "ATCEA", "ATCRA", "ATAR", "ATMA",
        )

        fun esteDoarCitire(comanda: String): Boolean {
            val c = comanda.uppercase(Locale.US).replace(" ", "")
            if (c.startsWith("AT")) return COMENZI_AT.any { c.startsWith(it) }
            if (c.length < 2 || c.any { it !in "0123456789ABCDEF" }) return false
            return c.substring(0, 2) in SERVICII_DE_CITIRE
        }

        private val LINIE_CU_ANTET = Regex("""^([0-9A-F]{3})\s+((?:[0-9A-F]{2}\s*)+)$""")

        /** Adresa care a raspuns, sau null daca n-a raspuns nimeni. */
        fun cineARaspuns(raspuns: String): String? =
            raspuns.uppercase(Locale.US).split('\r', '\n')
                .map { it.trim() }
                .firstNotNullOfOrNull { LINIE_CU_ANTET.find(it)?.groupValues?.get(1) }

        /** Octetii de date de pe toate liniile cu antet, fara antet si lungime. */
        private fun octeti(raspuns: String): String =
            raspuns.uppercase(Locale.US).split('\r', '\n')
                .mapNotNull { LINIE_CU_ANTET.find(it.trim())?.groupValues?.get(2) }
                .joinToString("") { it.replace(" ", "") }

        /** Raspunsul contine un raspuns pozitiv, de ex. "6101..." la 2101. */
        fun arePozitiv(raspuns: String, inceput: String): Boolean = octeti(raspuns).contains(inceput)

        /** Calculatorul a spus "serviciul asta nu-l stiu" (7F xx 11). */
        fun refuzaServiciul(raspuns: String, serviciu: String): Boolean {
            val o = octeti(raspuns)
            return cineARaspuns(raspuns) == null || o.contains("7F${serviciu}11")
        }

        /** Masca de 32 de biti dintr-un raspuns 41xx / 4900. */
        fun masca(raspuns: String, antet: String): Long? {
            val o = octeti(raspuns)
            val poz = o.indexOf(antet)
            if (poz < 0 || o.length < poz + antet.length + 8) return null
            return o.substring(poz + antet.length, poz + antet.length + 8).toLongOrNull(16)
        }

        private fun String.peUnRand(): String =
            replace('\r', ' ').replace('\n', ' ').split(" ").filter { it.isNotBlank() }.joinToString(" ")
    }
}
