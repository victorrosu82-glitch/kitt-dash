package ro.vix.kittdash.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Un pas al probei ghidate, asa cum il vede Victor pe telefon.
 *
 * [campuri] sunt intrebarile la care raspunde el (ce arata bordul sau clima),
 * [automat] inseamna ca pasul merge singur, fara buton: drumul, cand nu se
 * atinge telefonul.
 */
data class PasGhid(
    val nr: Int,
    val total: Int,
    val titlu: String,
    val text: String,
    val campuri: List<String> = emptyList(),
    val automat: Boolean = false,
    val tururi: Int = 0,
    val secundeRamase: Int? = null,
)

/**
 * Legatura dintre ecranul telefonului si proba care ruleaza in serviciu:
 * proba publica pasul, telefonul raspunde cu „Gata” si cu ce a scris Victor.
 */
object GhidProba {
    private val _pas = MutableStateFlow<PasGhid?>(null)
    val pas: StateFlow<PasGhid?> = _pas

    @Volatile
    internal var raspuns: Map<String, String>? = null

    @Volatile
    internal var renuntat = false

    /** Victor a terminat pasul; [note] sunt raspunsurile lui, pe campuri. */
    fun gata(note: Map<String, String>) {
        raspuns = note
    }

    fun renunta() {
        renuntat = true
    }

    internal fun publica(p: PasGhid?) {
        _pas.value = p
    }

    internal fun incepe() {
        raspuns = null
        renuntat = false
    }
}

/**
 * Proba ghidata: cauta in blocurile calculatoarelor modul de condus, temperaturile
 * climei si turatiile MG1 / MG2. Pentru fiecare pas citeste blocurile in tururi,
 * pana apasa Victor „Gata”, iar raspunsurile lui merg in raport langa citiri.
 * Comparand pasii intre ei se vede care octet s-a schimbat odata cu butonul.
 *
 * Ca explorarea, trimite numai cereri de citire ([ExploratorMasina.esteDoarCitire]).
 */
class ProbaGhidata(
    private val legatura: ObdLink,
    private val progres: (String, Float) -> Unit = { _, _ -> },
    private val ceas: () -> Long = { System.currentTimeMillis() },
    private val asteapta: (Long) -> Unit = { Thread.sleep(it) },
    private val pasi: List<Pas> = PASI,
) {

    /** Un pas: ce vede Victor si ce citim. */
    data class Pas(
        val titlu: String,
        val text: String,
        val calculatoare: List<String>,
        val campuri: List<String> = emptyList(),
        /** Daca nu e null, pasul merge singur atatea secunde, fara buton. */
        val durataS: Int? = null,
        /** Cereri standard in plus, ca „7E0:0146” (temperatura de afara la motor). */
        val standard: List<String> = emptyList(),
    )

    private val raport = StringBuilder()
    private val ora = SimpleDateFormat("HH:mm:ss", Locale.US)

    fun ruleaza(): String {
        GhidProba.incepe()
        scrie("Proba ghidata KITT Dash: mod de condus, clima, MG1 / MG2")
        scrie("Fiecare pas: tururi de citiri, apoi ce a scris Victor.")
        scrie("=".repeat(72))
        try {
            for (c in listOf("ATE0", "ATL0", "ATS1", "ATH1", "ATAT1", "ATCAF1")) cere(c)
            for ((i, pas) in pasi.withIndex()) {
                if (GhidProba.renuntat) break
                unPas(i, pas)
            }
            if (GhidProba.renuntat) scrie("\n!! Victor a oprit proba.")
        } catch (e: ObdException) {
            scrie("!! Proba s-a oprit: ${e.message}")
        } finally {
            GhidProba.publica(null)
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

    private fun unPas(i: Int, pas: Pas) {
        GhidProba.raspuns = null
        scrie("")
        scrie("##### pasul ${i + 1}: ${pas.titlu}, ${ora.format(Date(ceas()))}")
        val inceput = ceas()
        var tururi = 0
        while (true) {
            val trecut = ((ceas() - inceput) / 1000).toInt()
            val ramase = pas.durataS?.let { (it - trecut).coerceAtLeast(0) }
            GhidProba.publica(
                PasGhid(i + 1, pasi.size, pas.titlu, pas.text, pas.campuri, pas.durataS != null, tururi, ramase),
            )
            progres("Pasul ${i + 1} din ${pasi.size}: ${pas.titlu}", i.toFloat() / pasi.size)

            val terminat = when {
                GhidProba.renuntat -> true
                pas.durataS != null -> trecut >= pas.durataS
                else -> tururi >= TURURI_MINIME && GhidProba.raspuns != null
            }
            if (terminat || ceas() - inceput > MAXIM_PE_PAS_MS) break

            tururi++
            scrie("--- tur $tururi, ${ora.format(Date(ceas()))}, la ${(ceas() - inceput) / 1000.0} s")
            if (pas.durataS != null) unTurDeDrum(pas) else unTur(pas)
        }
        val note = GhidProba.raspuns.orEmpty()
        scrie("= Victor la pasul ${i + 1}: " + if (note.isEmpty()) "(nimic scris)" else note.entries.joinToString("; ") { "${it.key}: ${it.value}" })
    }

    /** Toate blocurile calculatoarelor pasului, o data. */
    private fun unTur(pas: Pas) {
        for (adresa in pas.calculatoare) {
            alege(adresa)
            for (bloc in BLOCURI[adresa].orEmpty()) {
                if (GhidProba.renuntat) return
                cere("21%02X".format(bloc))
            }
        }
        for (c in pas.standard) {
            val (adresa, cerere) = c.split(":")
            alege(adresa)
            cere(cerere)
        }
    }

    /**
     * In mers: blocurile hibridului, si la fiecare cateva blocuri viteza si
     * turatia, ca fiecare citire sa aiba langa ea viteza din aceeasi clipa.
     */
    private fun unTurDeDrum(pas: Pas) {
        val adresa = pas.calculatoare.first()
        alege(adresa)
        for ((k, bloc) in BLOCURI[adresa].orEmpty().withIndex()) {
            if (GhidProba.renuntat) return
            if (k % 4 == 0) cere("010D0C")
            cere("21%02X".format(bloc))
        }
    }

    private fun alege(adresa: String) {
        cere("ATSH$adresa")
        cere("ATCRA%03X".format(adresa.toInt(16) + 8))
    }

    private fun cere(comanda: String): String {
        require(ExploratorMasina.esteDoarCitire(comanda)) { "Cerere refuzata, nu e doar citire: $comanda" }
        val r = try {
            legatura.comanda(comanda, TIMEOUT_MS)
        } catch (e: ObdException) {
            "(tacere)"
        }
        if (comanda.startsWith("ATSH")) scrie(comanda)
        if (!comanda.startsWith("AT")) scrie("$comanda -> ${r.replace('\r', ' ').replace('\n', ' ').trim()}")
        return r
    }

    private fun scrie(linie: String) {
        raport.append(linie).append('\n')
    }

    companion object {
        private const val TIMEOUT_MS = 1_500L
        private const val TURURI_MINIME = 2
        private const val MAXIM_PE_PAS_MS = 5 * 60_000L

        /**
         * Blocurile care au raspuns la explorarea din 18.11.2026, pe calculatoare.
         * Fara cele lungi si mereu goale (2132-2136, 213E, 21A1-21A3 la hibrid).
         */
        val BLOCURI: Map<String, List<Int>> = mapOf(
            "7E2" to listOf(
                0x01, 0x06, 0x20, 0x25, 0x26, 0x28, 0x29, 0x2A, 0x2B, 0x3F, 0x40, 0x41, 0x44, 0x46,
                0x4C, 0x60, 0x61, 0x62, 0x63, 0x67, 0x68, 0x69, 0x70, 0x71, 0x72, 0x74, 0x75, 0x78,
                0x79, 0x7C, 0x7D, 0x80, 0x8A, 0x8E, 0x98, 0x9B, 0xA0, 0xA6, 0xAF, 0xB9, 0xC0, 0xC2,
                0xD1, 0xDB, 0xE0, 0xE1, 0xE2, 0xE3,
            ),
            "7C0" to listOf(
                0x01, 0x12, 0x13, 0x15, 0x20, 0x21, 0x22, 0x23, 0x29, 0x2B, 0x40, 0x43, 0x44, 0x60,
                0x64, 0x80, 0xA0, 0xA1, 0xA7, 0xAE, 0xAF, 0xB0, 0xC0, 0xC4, 0xCA, 0xCB, 0xCC, 0xCD,
                0xCE, 0xCF, 0xD1, 0xD7, 0xD8, 0xD9, 0xDA, 0xDB, 0xE0, 0xE1, 0xE2,
            ),
            "7C4" to listOf(
                0x01, 0x20, 0x21, 0x22, 0x24, 0x25, 0x26, 0x2B, 0x2C, 0x3C, 0x3D, 0x40, 0x41, 0x42,
                0x43, 0x44, 0x49, 0x4A, 0x4B, 0x4C, 0x53, 0x60, 0x61, 0x70, 0x7C, 0x7E, 0x7F, 0x80,
                0x81, 0x82, 0x84, 0x86, 0x89, 0x8A, 0x8B, 0x91, 0x92, 0x98, 0x9A, 0xA0, 0xB3, 0xB4,
                0xB5, 0xB8, 0xBA, 0xC0, 0xE0, 0xE1, 0xE2,
            ),
        )

        private val MOD = listOf("7E2", "7C0")
        private val CLIMA = listOf("7C4", "7C0")

        val PASI = listOf(
            Pas(
                "NORMAL",
                "Masina pe loc, in READY, frana de mana trasa. Modul NORMAL, EV oprit, clima " +
                    "pornita pe AUTO. Scrie ce arata bordul si clima, apoi asteapta sa se " +
                    "citeasca de doua ori si apasa Gata.",
                MOD + "7C4",
                listOf("Temperatura afara (bord)", "Clima stanga", "Clima dreapta"),
                standard = listOf("7E0:0146", "7E2:0146", "7E0:0133", "7E2:0133"),
            ),
            Pas("ECO", "Apasa ECO pe selectorul de mod. Cand bordul arata ECO, asteapta doua tururi si apasa Gata.", MOD),
            Pas("SPORT", "Pune SPORT (sau SPORT S, daca are). Asteapta doua tururi si apasa Gata.", MOD,
                listOf("Ce scrie pe bord")),
            Pas("NORMAL, EV pornit", "Inapoi pe NORMAL, apoi apasa butonul EV. Scrie daca s-a aprins EV pe bord.", MOD,
                listOf("S-a aprins EV? (da / nu)")),
            Pas("NORMAL, EV oprit", "Apasa iar EV, ca sa se stinga. Asteapta doua tururi si apasa Gata.", MOD),
            Pas("Clima 18 / 26", "Pune clima stanga pe 18 si dreapta pe 26. Asteapta doua tururi si apasa Gata.", CLIMA),
            Pas("Clima inapoi", "Pune clima cum era. Daca ai un termometru in masina, scrie ce arata. " +
                "Apasa Gata abia cand esti gata de plecare: urmeaza drumul de 2 minute, fara telefon.", CLIMA,
                listOf("Termometru in habitaclu (daca ai)")),
            Pas(
                "Drum, 2 minute",
                "Dupa ce apesi Gata la pasul de dinainte, pleaca. Doua minute: porneste lin " +
                    "pe baterie, accelereaza apoi mai tare pana la 50, mergi putin, franeaza " +
                    "pana la oprire. NU atinge telefonul: proba se opreste singura.",
                listOf("7E2"),
                durataS = 120,
            ),
        )
    }
}
