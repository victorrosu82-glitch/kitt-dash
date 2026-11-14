package ro.vix.kittdash.obd

import android.util.Log

/** Un raspuns brut al unui calculator al masinii, asa cum a venit. */
data class RandSonda(
    val adresa: String,
    val cerere: String,
    val descriere: String,
    val raspuns: String,
    val octeti: Int,
) {
    val areDate: Boolean
        get() = octeti > 0

    override fun toString(): String =
        "ATSH$adresa $cerere  ${descriere.padEnd(34)} -> $raspuns"
}

/**
 * Intreaba calculatoarele masinii, nu doar motorul, si strange raspunsurile
 * BRUTE. Nu decodeaza nimic: formulele hibride nu se scriu din memorie, se
 * confirma pe raspunsurile masinii asteia.
 *
 * Datele hibride stau la alte adrese decat motorul (7E0): calculatorul
 * hibrid la 7E2 si bateria HV la 7E3. Schimbarea adresei se face cu ATSH,
 * iar clonele ELM327 1.5 o executa adesea gresit - daca totul iese
 * "NO DATA", adaptorul e primul suspect, nu masina.
 *
 * Nimic din ce trimitem nu scrie in masina: modul 21 e tot o citire.
 */
class SondaHibrid(private val elm: Elm327) {

    fun ruleaza(): List<RandSonda> {
        val rezultate = mutableListOf<RandSonda>()
        try {
            // Intai mastile de PID-uri standard, brute: ele spun daca masina
            // da PID-urile hibride din standard (5B, 9A), nu doar pe cele
            // specifice Toyota.
            incearca("ATH0")
            for (baza in listOf("00", "20", "40", "60", "80", "A0")) {
                val r = incearca("01$baza")
                rezultate += RandSonda("7E0", "01$baza", "masca PID-uri $baza", r, numaraOcteti(r))
            }
            // Cererile standard pentru hibrid, cu raspunsul deja asamblat
            // de adaptor (fara antete): asa se vede valoarea, nu cadrele.
            for ((adresa, cerere, descriere) in CERERI_CURATE) {
                incearca("ATSH$adresa")
                val r = incearca(cerere)
                rezultate += RandSonda(adresa, cerere, descriere, r, numaraOcteti(r))
            }

            // Probe repetate, cu valorile standard alaturi: asa se vede CARE
            // octet se misca odata cu viteza, turatia si incarcarea bateriei.
            // Un singur esantion nu spune nimic despre ce inseamna un octet.
            // Antetul ramane pe 7E2 tot timpul: schimbarea lui la fiecare
            // proba costa doua comenzi in plus si l-a facut sa cada.
            incearca("ATSH7E2")
            val inceput = System.currentTimeMillis()
            for (i in 1..PROBE_REPETATE) {
                val t = System.currentTimeMillis() - inceput
                val b5 = incearca("015B")
                val b9 = incearca("019A")
                val viteza = incearca("010D")
                val turatie = incearca("010C")
                rezultate += RandSonda(
                    "7E2", "proba%02d".format(i), "${t}ms  5B | 9A | 0D | 0C",
                    "$b5 | $b9 | $viteza | $turatie", 1,
                )
            }

            // Antetele pornite: asa se vede CARE calculator a raspuns, nu doar
            // ce a raspuns. Fara ele nu putem deosebi un raspuns bun de un ecou.
            incearca("ATH1")
            for ((adresa, cerere, descriere) in CERERI) {
                val raspunsSh = incearca("ATSH$adresa")
                if ("OK" !in raspunsSh.uppercase()) {
                    rezultate += RandSonda(
                        adresa, cerere, descriere,
                        "adaptorul a refuzat ATSH: $raspunsSh", 0,
                    )
                    continue
                }
                val brut = incearca(cerere)
                val n = numaraOcteti(brut)
                val rand = RandSonda(adresa, cerere, descriere, brut, n)
                rezultate += rand
                Log.i(TAG, rand.toString())
            }
        } finally {
            // Lasam adaptorul cum l-am gasit, ca bucla normala sa mearga mai departe.
            try {
                incearca("ATH0")
                elm.comandaBruta("ATSH7E0")
            } catch (_: Exception) {
            }
        }
        return rezultate
    }

    /**
     * Trimite o comanda si intoarce raspunsul, dar nu lasa o tacere sa
     * darame toata sonda: o sonda pe jumatate facuta si pierduta nu ajuta
     * pe nimeni. Ce a tacut se scrie in rezultat ca atare.
     */
    private fun incearca(comanda: String): String = try {
        elm.comandaBruta(comanda, TIMEOUT_SONDA).curat()
    } catch (e: ObdException) {
        "(tacere: ${e.message})"
    }

    private fun String.curat(): String =
        replace('\r', ' ').replace('\n', ' ').split(" ")
            .filter { it.isNotBlank() }.joinToString(" ")

    /** Cati octeti de date utile are raspunsul (0 daca e NO DATA, ? sau gol). */
    private fun numaraOcteti(raspuns: String): Int {
        val t = raspuns.uppercase()
        if ("NODATA" in t.replace(" ", "") || "?" in t || "ERROR" in t) return 0
        if ("UNABLETOCONNECT" in t.replace(" ", "")) return 0
        return t.count { it in "0123456789ABCDEF" } / 2
    }

    companion object {
        private const val TAG = "SondaHibrid"
        // Raspunsurile lungi vin in mai multe cadre; adaptorul are nevoie de timp.
        private const val TIMEOUT_SONDA = 5_000L
        private const val PROBE_REPETATE = 40

        /**
         * Ce intrebam si pe cine. Adresele sunt cele obisnuite la Toyota
         * hibrid: 7E0 motorul termic, 7E2 calculatorul hibrid, 7E3 bateria.
         * Lista e de descoperire - ne asteptam ca o parte sa iasa goala.
         */
        /**
         * Cereri cu raspunsul asamblat de adaptor (fara antete). PID-urile
         * 5B si 9A sunt din standard, nu din lista Toyota: 5B da cat a mai
         * ramas din bateria HV, 9A da tensiunea si curentul bateriei plus
         * starea sistemului hibrid. Le cerem si de la motor (7E0), si de la
         * calculatorul hibrid (7E2), fiindca nu stim cine le tine.
         */
        val CERERI_CURATE: List<Triple<String, String, String>> = listOf(
            Triple("7E0", "015B", "standard 5B: viata bateriei HV"),
            Triple("7E0", "019A", "standard 9A: tensiune/curent baterie HV"),
            Triple("7E2", "015B", "standard 5B, cerut de la hibrid"),
            Triple("7E2", "019A", "standard 9A, cerut de la hibrid"),
            Triple("7E2", "0101", "mod 01 de la hibrid, de proba"),
            Triple("7E2", "2101", "hibrid bloc 1, raspuns asamblat"),
        )

        val CERERI: List<Triple<String, String, String>> = listOf(
            Triple("7E2", "2101", "hibrid bloc 1 (SOC, curent, tensiune)"),
            Triple("7E2", "2102", "hibrid bloc 2"),
            Triple("7E2", "2103", "hibrid bloc 3 (MG1/MG2)"),
            Triple("7E2", "2104", "hibrid bloc 4"),
            Triple("7E2", "2105", "hibrid bloc 5"),
            Triple("7E2", "2106", "hibrid bloc 6"),
            Triple("7E3", "2181", "baterie HV bloc 81 (tensiuni celule)"),
            Triple("7E3", "2182", "baterie HV bloc 82"),
            Triple("7E3", "2183", "baterie HV bloc 83 (temperaturi)"),
            Triple("7E0", "2101", "motor termic bloc 1"),
        )
    }
}
