package ro.vix.kittdash.ui

import android.annotation.SuppressLint
import android.app.Application
import android.bluetooth.BluetoothManager
import androidx.lifecycle.AndroidViewModel
import ro.vix.kittdash.masina.AdaptorTinutMinte
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

    /**
     * Spune daca numele arata a adaptor OBD. Serveste doar la insemnarea
     * randului din lista - alegerea o face userul, nu aplicatia. Prima data
     * am ghicit, si aplicatia s-a legat la primul aparat in ordine
     * alfabetica, care nu era adaptorul.
     */
    fun pareAdaptorObd(adaptor: Adaptor): Boolean =
        NUME_CUNOSCUTE.any { adaptor.nume.contains(it, ignoreCase = true) }

    fun conecteaza(adaptor: Adaptor, sondaHibrida: Boolean = false) {
        // Tinem minte alegerea: pe ecranul masinii nu exista lista de
        // adaptoare, acolo bordul trebuie sa porneasca singur.
        AdaptorTinutMinte.scrie(getApplication(), adaptor.adresa, adaptor.nume)
        ObdService.porneste(getApplication(), adaptor.adresa, sondaHibrida)
    }

    fun conecteazaSimulator() =
        ObdService.pornesteSimulator(getApplication())

    fun deconecteaza() =
        ObdService.opreste(getApplication())

    private companion object {
        // Numele sub care se dau clonele ELM327. "CAR2-xxxx" e numele cu care
        // se anunta chiar adaptorul lui Victor.
        val NUME_CUNOSCUTE = listOf(
            "VIECAR", "OBD", "ELM", "V-LINK", "VGATE", "CAR2", "OBDII", "KONNWEI",
        )
    }
}
