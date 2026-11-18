package ro.vix.kittdash.masina

import android.content.Intent
import android.net.Uri
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.CarToast
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
    private val voce = VoceKitt(carContext)

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
        voce.inchide()
    }

    override fun onGetTemplate(): Template {
        // Banda de sus, cu text: cel mult patru butoane. Oprirea a coborat
        // in banda de jos, ca sa incapa butonul "Ecran".
        val banda = ActionStrip.Builder()
            .addAction(
                Action.Builder()
                    .setTitle("Acasa")
                    .setOnClickListener { mergiAcasa() }
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
            // Fara banda de jos (masini foarte vechi), oprirea ramane aici.
            .apply {
                if (!areBandaDeJos() && !BuildConfig.DEBUG) addAction(butonOprire())
            }
            .build()

        val sablon = NavigationTemplate.Builder().setActionStrip(banda)
        // Banda de jos, langa harta: numai iconite. Masinile foarte vechi
        // (nivelul 1 de Android Auto) n-o au; acolo raman doar cele de sus.
        if (areBandaDeJos()) {
            sablon.setMapActionStrip(
                ActionStrip.Builder()
                    .addAction(
                        Action.Builder()
                            .setIcon(iconita(android.R.drawable.ic_menu_mapmode))
                            .setOnClickListener { comutaPanoul() }
                            .build(),
                    )
                    .addAction(
                        Action.Builder()
                            .setIcon(iconita(android.R.drawable.ic_lock_silent_mode_off))
                            .setOnClickListener { vorbeste() }
                            .build(),
                    )
                    .addAction(butonOprire())
                    .build(),
            )
        }
        return sablon.build()
    }

    private fun areBandaDeJos(): Boolean = carContext.carAppApiLevel >= 2

    /** Oprirea legaturii cu masina: iconita rosie. */
    private fun butonOprire(): Action =
        Action.Builder()
            .setIcon(
                CarIcon.Builder(
                    IconCompat.createWithResource(carContext, android.R.drawable.ic_menu_close_clear_cancel),
                ).setTint(CarColor.RED).build(),
            )
            .setOnClickListener { opreste() }
            .build()

    /** Bord, viteza, energie, consola - si iar de la capat. */
    private fun schimbaEcranul() {
        val nou = PreferinteMasina.urmatorulEcran(carContext)
        anunta(nou.titlu)
    }

    private fun iconita(resursa: Int): CarIcon =
        CarIcon.Builder(IconCompat.createWithResource(carContext, resursa)).build()

    private fun anunta(text: String) {
        carContext.getCarService(AppManager::class.java).showToast(text, CarToast.LENGTH_SHORT)
    }

    /**
     * Porneste navigatia spre casa in aplicatia de navigatie aleasa in
     * Android Auto (Waze sau Maps). KITT nu navigheaza el insusi.
     */
    private fun mergiAcasa() {
        val adresa = PreferinteMasina.acasa(carContext)
        if (adresa == null) {
            anunta("Scrie adresa de acasa in KITT, pe telefon")
            return
        }
        val intent = Intent(CarContext.ACTION_NAVIGATE, Uri.parse("geo:0,0?q=" + Uri.encode(adresa)))
        // KITT e si el aplicatie de navigatie, asa ca o cerere fara adresant
        // se poate intoarce la el (s-a vazut pe 17.11.2026). Daca e Waze pe
        // telefon, i-o trimitem lui direct.
        if (areWaze()) intent.component = WAZE
        try {
            carContext.startCarApp(intent)
            ro.vix.kittdash.Jurnal.scrie(carContext, "Am cerut navigatie spre casa")
        } catch (e: Exception) {
            ro.vix.kittdash.Jurnal.scrie(carContext, "Navigatia spre casa n-a pornit: ${e.message}")
            anunta("Nu pot porni navigatia")
        }
    }

    private fun areWaze(): Boolean = try {
        carContext.packageManager.getPackageInfo(WAZE.packageName, 0)
        true
    } catch (_: Exception) {
        false
    }

    private fun comutaPanoul() {
        PreferinteMasina.panouNavAscuns = !PreferinteMasina.panouNavAscuns
        anunta(if (PreferinteMasina.panouNavAscuns) "Panoul Waze e ascuns" else "Panoul Waze se vede")
    }

    private fun vorbeste() {
        voce.spune(
            VoceKitt.rezumat(
                ro.vix.kittdash.obd.CarStateRepository.INSTANTA.stare.value,
                ro.vix.kittdash.navigatie.Navigatie.acum,
            ),
        )
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

    companion object {
        /** Waze pentru Android Auto, citit de pe telefonul lui Victor pe 18.11.2026. */
        private val WAZE = android.content.ComponentName("com.waze", "com.waze.car_lib.WazeCarAppService")
    }
}
