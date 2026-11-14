package ro.vix.kittdash.obd

/**
 * O marime pe care o arata bordul. E despartita de PID-ul care o aduce,
 * fiindca o singura cerere poate da mai multe marimi: PID-ul 9A aduce
 * deodata tensiunea, curentul si starea sistemului hibrid.
 */
enum class Marime(val eticheta: String, val unitate: String) {
    VITEZA("Viteza", "km/h"),
    TURATIE("Turatie", "rot/min"),
    SARCINA_MOTOR("Sarcina motor", "%"),
    CLAPETA("Clapeta", "%"),
    TEMP_LICHID("Temp. lichid racire", "°C"),
    TEMP_AER_ADMISIE("Temp. aer admisie", "°C"),
    TEMP_EXTERIOARA("Temp. exterioara", "°C"),
    TENSIUNE_MODUL("Tensiune modul", "V"),
    NIVEL_COMBUSTIBIL("Combustibil", "%"),
    TIMP_FUNCTIONARE("Timp de la pornire", "s"),

    // Hibrid
    BATERIE_HV("Baterie HV", "%"),
    HV_TENSIUNE("Tensiune baterie HV", "V"),
    HV_CURENT("Curent baterie HV", "A"),
    HV_PUTERE("Putere baterie HV", "kW"),
    HV_STARE("Stare sistem hibrid", ""),
}

/**
 * O cerere catre masina si marimile care ies din raspuns.
 *
 * [antet] e calculatorul caruia ii vorbim: null inseamna cel implicit
 * (motorul), "7E2" e calculatorul hibrid. Schimbarea se face cu ATSH si
 * costa o comanda in plus, deci cererile sunt grupate pe antet.
 *
 * Formulele modului 01 sunt din SAE J1979. Cele hibride (5B si 9A) sunt tot
 * din standard - nu sunt scrise din memorie, ci verificate pe masina lui
 * Victor: 5B s-a potrivit cu ultimul octet al blocului Toyota 2101 la doua
 * citiri diferite (A3 si 9D), iar curentul din 9A a iesit exact 0,0 A cu
 * masina stand pe loc.
 */
enum class ObdPid(
    val pid: Int,
    val octeti: Int,
    val antet: String?,
    val perioadaMs: Long,
    val marimi: List<Pair<Marime, (IntArray) -> Double>>,
) {
    VITEZA(0x0D, 1, null, 100L, listOf(Marime.VITEZA to { b: IntArray -> b[0].toDouble() })),
    TURATIE(
        0x0C, 2, null, 100L,
        listOf(Marime.TURATIE to { b: IntArray -> (b[0] * 256 + b[1]) / 4.0 }),
    ),
    SARCINA_MOTOR(
        0x04, 1, null, 250L,
        listOf(Marime.SARCINA_MOTOR to { b: IntArray -> b[0] * 100.0 / 255.0 }),
    ),
    POZITIE_CLAPETA(
        0x11, 1, null, 250L,
        listOf(Marime.CLAPETA to { b: IntArray -> b[0] * 100.0 / 255.0 }),
    ),
    TEMP_LICHID(
        0x05, 1, null, 2000L,
        listOf(Marime.TEMP_LICHID to { b: IntArray -> b[0] - 40.0 }),
    ),
    TEMP_AER_ADMISIE(
        0x0F, 1, null, 2000L,
        listOf(Marime.TEMP_AER_ADMISIE to { b: IntArray -> b[0] - 40.0 }),
    ),
    /**
     * Temperatura de afara. O dau amandoua calculatoarele; o cerem de la cel
     * al motorului, ca sa nu mai schimbam adresa pentru ea.
     */
    TEMP_EXTERIOARA(
        0x46, 1, null, 5000L,
        listOf(Marime.TEMP_EXTERIOARA to { b: IntArray -> b[0] - 40.0 }),
    ),
    TENSIUNE_MODUL(
        0x42, 2, null, 2000L,
        listOf(Marime.TENSIUNE_MODUL to { b: IntArray -> (b[0] * 256 + b[1]) / 1000.0 }),
    ),
    NIVEL_COMBUSTIBIL(
        0x2F, 1, null, 5000L,
        listOf(Marime.NIVEL_COMBUSTIBIL to { b: IntArray -> b[0] * 100.0 / 255.0 }),
    ),
    TIMP_FUNCTIONARE(
        0x1F, 2, null, 5000L,
        listOf(Marime.TIMP_FUNCTIONARE to { b: IntArray -> (b[0] * 256 + b[1]).toDouble() }),
    ),

    /** Cat a mai ramas din bateria HV. Il da numai calculatorul hibrid. */
    BATERIE_HV(
        0x5B, 1, "7E2", 1000L,
        listOf(Marime.BATERIE_HV to { b: IntArray -> b[0] * 100.0 / 255.0 }),
    ),

    /**
     * Tensiunea, curentul si starea bateriei HV, dintr-o singura cerere.
     * Octetii: A starea de incarcare, B starea extinsa, C-D tensiunea,
     * E-F curentul. Puterea iese din tensiune ori curent.
     */
    BATERIE_HV_ELECTRIC(
        0x9A, 6, "7E2", 200L,
        listOf(
            Marime.HV_STARE to { b: IntArray -> b[0].toDouble() },
            Marime.HV_TENSIUNE to { b: IntArray -> tensiune(b) },
            Marime.HV_CURENT to { b: IntArray -> curent(b) },
            Marime.HV_PUTERE to { b: IntArray -> tensiune(b) * curent(b) / 1000.0 },
        ),
    ),
    ;

    /** Cererea trimisa adaptorului, de ex. "010D". */
    val cerere: String get() = "01%02X".format(pid)

    /** Antetul raspunsului asteptat, de ex. "410D". */
    val antetRaspuns: String get() = "41%02X".format(pid)

    /** Numele primei marimi - pentru liste si jurnale. */
    val eticheta: String get() = marimi.first().first.eticheta

    companion object {
        /**
         * Impartirile pentru tensiunea si curentul bateriei HV.
         *
         * Tensiunea: un pas de 1/64 V. Verificata fata de Dr Prius, care
         * citea aceeasi masina in aceleasi minute si arata 274,87 - 283,51 V
         * (suma celor 17 blocuri ale bateriei). Octetii nostri dau, impartiti
         * la 64, intre 266 si 285 V - aceeasi banda. Impartirea la 100, cum
         * incepusem, dadea 174-182 V, adica mult prea putin.
         * Cea mai buna dovada: in clipa in care demarorul a tras 19,8 A,
         * tensiunea a cazut la 266 V si s-a ridicat la loc dupa - o baterie
         * sub sarcina face exact asta.
         *
         * Curentul: un pas de 0,1 A. Dr Prius arata "DISCHARGING 1.3 amp" in
         * toate cele patru capturi, iar octetii nostri dau 00 0D = 1,3 A.
         *
         * Semnul curentului e confirmat pe 14.11.2026, in clipa in care
         * motorul termic a pornit singur ca sa incarce bateria:
         *  - curentul a sarit de la 00 0D la FF 89, adica de la +1,3 A la
         *    -11,9 A. E un intreg cu semn pe doi octeti, nu unul fara semn;
         *  - in aceeasi clipa tensiunea a urcat de la 176,6 la 182,4 V si
         *    procentul bateriei de la 62,7 la 63,1 - exact ce face o baterie
         *    care se incarca. Trei lucruri potrivindu-se deodata;
         *  - la pornirea motorului s-a vazut un varf de +19,8 A (3,6 kW),
         *    adica demarorul tragand din bateria HV.
         *
         * Semnul: plus inseamna ca bateria da curent (tractiune), minus ca
         * primeste (incarcare sau recuperare la franare).
         */
        const val SCARA_TENSIUNE = 64.0
        const val SCARA_CURENT = 10.0

        private fun tensiune(b: IntArray): Double = (b[2] * 256 + b[3]) / SCARA_TENSIUNE

        private fun curent(b: IntArray): Double {
            val brut = b[4] * 256 + b[5]
            val cuSemn = if (brut >= 0x8000) brut - 0x10000 else brut
            return cuSemn / SCARA_CURENT
        }

        /** Cererile pe care le repetam des: ele dau senzatia de timp real. */
        val RAPIDE: List<ObdPid> = listOf(VITEZA, TURATIE, BATERIE_HV_ELECTRIC, BATERIE_HV)

        fun dupaPid(pid: Int): ObdPid? = entries.firstOrNull { it.pid == pid }
    }
}
