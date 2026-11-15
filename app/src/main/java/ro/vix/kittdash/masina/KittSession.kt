package ro.vix.kittdash.masina

import android.content.Intent
import androidx.car.app.Screen
import androidx.car.app.Session
import ro.vix.kittdash.Jurnal
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
        Jurnal.scrie(carContext, "Masina cere ecranul")
        porneste()
        return EcranMasina(carContext)
    }

    override fun onNewIntent(intent: Intent) {
        porneste()
    }

    private fun porneste() {
        val adresa = AdaptorTinutMinte.citeste(carContext)
        if (adresa == null) {
            Jurnal.scrie(carContext, "Niciun adaptor tinut minte")
            return
        }
        Jurnal.scrie(carContext, "Cer legatura cu adaptorul")
        ObdService.porneste(carContext, adresa)
    }
}
