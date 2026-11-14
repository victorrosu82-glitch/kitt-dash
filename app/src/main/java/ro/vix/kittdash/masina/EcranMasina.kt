package ro.vix.kittdash.masina

import androidx.car.app.CarContext
import androidx.car.app.Screen
import androidx.car.app.model.Action
import androidx.car.app.model.ActionStrip
import androidx.car.app.model.CarColor
import androidx.car.app.model.CarIcon
import androidx.car.app.model.Template
import androidx.car.app.navigation.model.NavigationTemplate
import androidx.core.graphics.drawable.IconCompat
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
        desenator.porneste()
    }

    override fun onStop(owner: LifecycleOwner) {
        desenator.opreste()
    }

    override fun onGetTemplate(): Template {
        val banda = ActionStrip.Builder()
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
            .addAction(
                Action.Builder()
                    .setIcon(
                        CarIcon.Builder(
                            IconCompat.createWithResource(
                                carContext,
                                android.R.drawable.ic_menu_close_clear_cancel,
                            ),
                        ).setTint(CarColor.RED).build(),
                    )
                    .setOnClickListener { opreste() }
                    .build(),
            )
            .build()

        return NavigationTemplate.Builder()
            .setActionStrip(banda)
            .build()
    }

    private fun esteLegat(): Boolean =
        ro.vix.kittdash.obd.CarStateRepository.INSTANTA.stare.value.conectat

    private fun reconecteaza() {
        val adresa = AdaptorTinutMinte.citeste(carContext)
        if (adresa == null) {
            carContext.getCarService(androidx.car.app.AppManager::class.java)
                .showToast(
                    carContext.getString(R.string.fara_adaptor_ales),
                    androidx.car.app.CarToast.LENGTH_LONG,
                )
            return
        }
        ObdService.porneste(carContext, adresa)
        invalidate()
    }

    private fun opreste() {
        ObdService.opreste(carContext)
        invalidate()
    }
}
