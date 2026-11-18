package ro.vix.kittdash.masina

import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.lifecycle.DefaultLifecycleObserver
import androidx.lifecycle.LifecycleOwner
import ro.vix.kittdash.BuildConfig
import ro.vix.kittdash.R
import ro.vix.kittdash.obd.ObdService

/**
 * Ecranul de pe masina.
 *
 * Sablonul de navigatie e doar rama: el cere o banda de butoane si ne da in
 * schimb suprafata pe care desenam bordul. Tot ce se vede frumos e desenat
 * de noi; din sablon raman butoanele, pe care Android Auto le vrea ca sa
 * poata fi apasate si in mers.
 */
class EcranMasina(carContext: CarContext) : Screen(carContext), DefaultLifecycleObserver {

    private val desenator = DesenatorMasina(carContext)

    init {
        lifecycle.addObserver(this)
    }

    override fun onStart(owner: LifecycleOwner) {
        ro.vix.kittdash.Jurnal.scrie(carContext, "Ecranul KITT e iar pe masina")
        desenator.porneste()
    }

    override fun onStop(owner: LifecycleOwner) {
        ro.vix.kittdash.Jurnal.scrie(carContext, "Ecranul KITT a trecut in spate")
        desenator.opreste()
    }

    override fun onDestroy(owner: LifecycleOwner) {
        desenator.inchide()
    }

    override fun onGetTemplate(): Template {
        // Numai trei butoane, toate in banda de sus: panoul Waze, ecranul si
        // legatura cu masina. Fara banda de langa harta, bordul ia toata
        // latimea (cerut de Victor pe 18.11.2026).
        val banda = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Harta")
                    .setOnClickListener { comutaPanoul() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle("Ecran")
                    .setOnClickListener { schimbaEcranul() }
                    .build(),
            )
            .addAction(
                Action.Builder()
                    .setTitle(if (esteLegat()) "Reconecteaza" else "Conecteaza")
                    .setOnClickListener { reconecteaza() }
                    .build(),
            )
            // In timpul lucrului vrem sa vedem bordul viu si pe emulator,
            // unde nu exista nici masina, nici adaptor. In varianta de
            // predat butonul asta nu apare.
            .apply {
                if (BuildConfig.DEBUG) {
                    addAction(
                        Action.Builder()
                            .setTitle("Simulator")
                            .setOnClickListener {
                                ObdService.pornesteSimulator(carContext)
                                invalidate()
                            }
                            .build(),
                    )
                }
            }
            .build()
        return NavigationTemplate.Builder().setActionStrip(banda).build()
    }

    /** Bord, viteza, energie, consola, hibrid - si iar de la capat. */
    private fun schimbaEcranul() {
        val nou = PreferinteMasina.urmatorulEcran(carContext)
        anunta(nou.titlu)
    }

    private fun anunta(text: String) {
        carContext.getCarService(AppManager::class.java).showToast(text, CarToast.LENGTH_SHORT)
    }

    private fun comutaPanoul() {
        PreferinteMasina.panouNavAscuns = !PreferinteMasina.panouNavAscuns
        anunta(if (PreferinteMasina.panouNavAscuns) "Panoul Waze e ascuns" else "Panoul Waze se vede")
    }

    private fun esteLegat(): Boolean =
        ro.vix.kittdash.obd.CarStateRepository.INSTANTA.stare.value.conectat

    private fun reconecteaza() {
        val adresa = AdaptorTinutMinte.citeste(carContext)
        if (adresa == null) {
            anunta(carContext.getString(R.string.fara_adaptor_ales))
            return
        }
        ObdService.porneste(carContext, adresa)
        invalidate()
    }
}
