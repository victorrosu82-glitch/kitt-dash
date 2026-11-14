package ro.vix.kittdash.obd

/**
 * Un PID standard OBD-II (modul 01) pe care il interogam.
 *
 * [cerere] e textul trimis adaptorului; [octeti] e numarul de octeti de date
 * asteptati in raspuns (fara cei doi de antet, 41 xx); [decodeaza] transforma
 * octetii in valoarea fizica.
 *
 * Formulele sunt cele din SAE J1979 (modul 01) - nu sunt specifice Toyota.
 * PID-urile hibride vin in etapa 2, cu alta adresare (ATSH 7E2).
 */
enum class ObdPid(
    val pid: Int,
    val octeti: Int,
    val eticheta: String,
    val unitate: String,
    val perioadaMs: Long,
    val decodeaza: (IntArray) -> Double,
) {
    VITEZA(0x0D, 1, "Viteza", "km/h", 100L, { b -> b[0].toDouble() }),
    TURATIE(0x0C, 2, "Turatie", "rot/min", 100L, { b -> (b[0] * 256 + b[1]) / 4.0 }),
    SARCINA_MOTOR(0x04, 1, "Sarcina motor", "%", 250L, { b -> b[0] * 100.0 / 255.0 }),
    POZITIE_CLAPETA(0x11, 1, "Clapeta", "%", 250L, { b -> b[0] * 100.0 / 255.0 }),
    TEMP_LICHID(0x05, 1, "Temp. lichid racire", "°C", 2000L, { b -> b[0] - 40.0 }),
    TEMP_AER_ADMISIE(0x0F, 1, "Temp. aer admisie", "°C", 2000L, { b -> b[0] - 40.0 }),
    TENSIUNE_MODUL(0x42, 2, "Tensiune modul", "V", 2000L, { b -> (b[0] * 256 + b[1]) / 1000.0 }),
    NIVEL_COMBUSTIBIL(0x2F, 1, "Combustibil", "%", 5000L, { b -> b[0] * 100.0 / 255.0 }),
    TIMP_FUNCTIONARE(0x1F, 2, "Timp de la pornire", "s", 5000L, { b -> (b[0] * 256 + b[1]).toDouble() }),
    ;

    /** Cererea trimisa adaptorului, de ex. "010D". */
    val cerere: String get() = "01%02X".format(pid)

    /** Antetul raspunsului asteptat, de ex. "410D". */
    val antetRaspuns: String get() = "41%02X".format(pid)

    companion object {
        /** PID-urile pe care le cerem des: ele dau senzatia de timp real. */
        val RAPIDE: List<ObdPid> = listOf(VITEZA, TURATIE, SARCINA_MOTOR, POZITIE_CLAPETA)

        /** PID-urile lente: temperaturi, tensiune, combustibil. */
        val LENTE: List<ObdPid> = listOf(
            TEMP_LICHID, TEMP_AER_ADMISIE, TENSIUNE_MODUL,
            NIVEL_COMBUSTIBIL, TIMP_FUNCTIONARE,
        )

        fun dupaPid(pid: Int): ObdPid? = entries.firstOrNull { it.pid == pid }
    }
}
