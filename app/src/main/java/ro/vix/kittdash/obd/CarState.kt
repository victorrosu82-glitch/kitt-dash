package ro.vix.kittdash.obd

/** Stadiul legaturii cu adaptorul. */
enum class StareConexiune {
    DECONECTAT,
    SE_CONECTEAZA,
    SE_INITIALIZEAZA,
    CONECTAT,
    EROARE,
}

/** O singura valoare citita din masina, cu momentul citirii. */
data class Citire(
    val valoare: Double,
    val laMs: Long,
) {
    /** Cat de veche e citirea, in milisecunde. */
    fun vecheDeMs(acumMs: Long): Long = acumMs - laMs
}

/**
 * Starea masinii, asa cum o vede aplicatia. Un singur obiect, imutabil,
 * publicat prin [CarStateRepository]. Etapa 3 deseneaza din el; etapa 1
 * doar il afiseaza ca lista de cifre.
 */
data class CarState(
    val conexiune: StareConexiune = StareConexiune.DECONECTAT,
    val numeAdaptor: String? = null,
    val mesajEroare: String? = null,
    /** Varianta de ELM327 raportata de adaptor la ATI. */
    val versiuneElm: String? = null,
    /** Protocolul ales de adaptor (ATDP). */
    val protocol: String? = null,
    /** Cererile pe care masina le-a confirmat ca suportate. */
    val pidSuportate: Set<ObdPid> = emptySet(),
    /** Ultima valoare citita pentru fiecare marime. */
    val valori: Map<Marime, Citire> = emptyMap(),
    /** Raspunsuri pe secunda, mediat - arata cat de repede merge adaptorul. */
    val raspunsuriPeSecunda: Double = 0.0,
    /** Numarul de erori de la ultima conectare reusita. */
    val erori: Int = 0,
    /** Simulatorul e activ in locul masinii. */
    val simulator: Boolean = false,
    /** Raspunsurile brute ale sondei hibride, daca a rulat. */
    val sonda: List<RandSonda> = emptyList(),
    /** Ce face explorarea acum, sau null daca nu ruleaza. */
    val explorare: String? = null,
    /** Cat din explorare s-a facut, 0..1. */
    val explorareProgres: Float = 0f,
    /** Unde s-a scris raportul ultimei explorari. */
    val fisierExplorare: String? = null,
) {
    fun valoare(marime: Marime): Double? = valori[marime]?.valoare

    /** Masina da datele bateriei HV. */
    val areDateHibride: Boolean
        get() = Marime.BATERIE_HV in valori || Marime.HV_PUTERE in valori

    val conectat: Boolean get() = conexiune == StareConexiune.CONECTAT
}
