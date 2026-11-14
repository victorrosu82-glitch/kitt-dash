package ro.vix.kittdash.masina

import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import ro.vix.kittdash.BuildConfig

/**
 * Usa prin care Android Auto intra in aplicatie.
 *
 * Aplicatia e declarata in categoria de navigatie - nu fiindca am face
 * navigatie, ci fiindca numai aplicatiile de navigatie primesc de la Android
 * Auto o suprafata pe care sa deseneze ce vor. Sabloanele obisnuite (liste,
 * grile) n-ar putea arata niciodata ca macheta.
 *
 * Consecinta, acceptata inca de la plan: cat e bordul pe ecran, locul hartii
 * e luat, deci nu se vede Google Maps sau Waze.
 */
class KittCarAppService : CarAppService() {

    /**
     * Cine are voie sa ne porneasca. La o aplicatie instalata direct, in
     * timpul lucrului, lasam orice gazda, ca sa mearga si emulatorul DHU de
     * pe calculator. In varianta de predat ramane lista oficiala, ca sa nu
     * se lege la noi altcineva decat Android Auto.
     */
    override fun createHostValidator(): HostValidator =
        if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(androidx.car.app.R.array.hosts_allowlist_sample)
                .build()
        }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = KittSession()
}
