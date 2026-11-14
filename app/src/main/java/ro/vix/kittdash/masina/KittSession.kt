package ro.vix.kittdash.masina

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import ro.vix.kittdash.obd.ObdService

/**
 * O sesiune tine cat sta aplicatia pe ecranul masinii.
 *
 * La pornire cere serviciului OBD sa se lege la adaptorul ales ultima data:
 * cand urci in masina si pornesti bordul, nu ai chef sa alegi adaptorul din
 * lista de fiecare data.
 */
class KittSession : Session() {

    override fun onCreateScreen(intent: Intent): Screen {
        porneste()
        return EcranMasina(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        porneste()
    }

    private fun porneste() {
        val adresa = AdaptorTinutMinte.citeste(carContext)
        if (adresa != null) {
            ObdService.porneste(carContext, adresa)
        }
    }
}
