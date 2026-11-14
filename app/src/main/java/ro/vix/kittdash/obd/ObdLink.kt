package ro.vix.kittdash.obd

import java.io.Closeable

/**
 * O legatura cu adaptorul, vazuta ca text: trimitem o comanda, citim raspunsul
 * pana la promptul '>'. Exista doua implementari - [BluetoothObdLink] pentru
 * adaptorul real si [SimulatorObdLink] pentru lucrul pe PC.
 *
 * Nu e sigura pe mai multe fire de executie: o foloseste numai bucla de
 * interogare din [ObdPoller].
 */
interface ObdLink : Closeable {

    val nume: String

    /** Simulatorul raspunde fara masina. */
    val esteSimulator: Boolean get() = false

    /** Deschide legatura. Arunca [ObdException] daca nu reuseste. */
    fun deschide()

    /**
     * Trimite [comanda] si intoarce raspunsul brut, fara promptul '>',
     * cu spatiile si sfarsiturile de linie pastrate.
     */
    fun comanda(comanda: String, timeoutMs: Long = 2000L): String
}

class ObdException(mesaj: String, cauza: Throwable? = null) : Exception(mesaj, cauza)
