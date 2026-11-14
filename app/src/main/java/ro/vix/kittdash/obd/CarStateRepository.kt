package ro.vix.kittdash.obd

import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.flow.update

/**
 * Un singur loc unde sta starea masinii. Bucla de interogare scrie aici;
 * ecranul telefonului si, de la etapa 4, bordul de pe masina citesc de aici.
 *
 * E un obiect unic pe aplicatie, ca sa nu existe doua stari diferite cand
 * serviciul si ecranul merg in acelasi timp.
 */
class CarStateRepository {

    private val _stare = MutableStateFlow(CarState())
    val stare: StateFlow<CarState> = _stare.asStateFlow()

    fun actualizeaza(schimbare: (CarState) -> CarState) {
        _stare.update(schimbare)
    }

    /** Pune o valoare citita, pastrand momentul citirii. */
    fun pune(pid: ObdPid, valoare: Double, laMs: Long) {
        _stare.update { vechi ->
            vechi.copy(valori = vechi.valori + (pid to Citire(valoare, laMs)))
        }
    }

    /** Dupa deconectare: pastram ultimele valori, dar marcam starea. */
    fun deconectat() {
        _stare.update {
            it.copy(conexiune = StareConexiune.DECONECTAT, raspunsuriPeSecunda = 0.0)
        }
    }

    companion object {
        /** Instanta unica, creata la pornirea aplicatiei. */
        val INSTANTA: CarStateRepository by lazy { CarStateRepository() }
    }
}
