package ro.vix.kittdash.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import ro.vix.kittdash.obd.CarStateRepository
import ro.vix.kittdash.obd.ObdService

/** Un adaptor imperecheat, asa cum il arata lista. */
data class Adaptor(val nume: String, val adresa: String)

class MainViewModel(app: Application) : AndroidViewModel(app) {

    val stare = CarStateRepository.INSTANTA.stare

    /**
     * Adaptoarele deja imperecheate din setarile telefonului. Nu facem
     * scanare: ELM327-ul se imperecheaza o data, din Setari, cu codul 1234.
     */
    @SuppressLint("MissingPermission")
    fun adaptoare(): List<Adaptor> {
        val bt = getApplication<Application>()
            .getSystemService(BluetoothManager::class.java)?.adapter ?: return emptyList()
        return try {
            bt.bondedDevices.orEmpty()
                .map { Adaptor(it.name ?: it.address, it.address) }
                .sortedBy { it.nume }
        } catch (_: SecurityException) {
            emptyList()
        }
    }

    /** Ghiceste care dispozitiv imperecheat e adaptorul OBD. */
    fun adaptorProbabil(): Adaptor? {
        val lista = adaptoare()
        return lista.firstOrNull { a ->
            NUME_CUNOSCUTE.any { a.nume.contains(it, ignoreCase = true) }
        } ?: lista.firstOrNull()
    }

    fun conecteaza(adaptor: Adaptor) =
        ObdService.porneste(getApplication(), adaptor.adresa)

    fun conecteazaSimulator() =
        ObdService.pornesteSimulator(getApplication())

    fun deconecteaza() =
        ObdService.opreste(getApplication())

    private companion object {
        val NUME_CUNOSCUTE = listOf("VIECAR", "OBD", "ELM", "V-LINK", "VGATE", "OBDII")
    }
}
