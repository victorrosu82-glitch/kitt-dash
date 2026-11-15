package ro.vix.kittdash.masina

import android.content.pm.PackageManager
import android.util.Log
import androidx.car.app.CarAppService
import androidx.car.app.Session
import androidx.car.app.SessionInfo
import androidx.car.app.validation.HostValidator
import ro.vix.kittdash.BuildConfig
import ro.vix.kittdash.R
import java.security.MessageDigest

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
     * Cine are voie sa ne porneasca.
     *
     * In timpul lucrului lasam orice gazda, ca sa mearga si emulatorul DHU.
     * In varianta de predat cerem o amprenta din [R.array.gazde_permise].
     *
     * Lista aceea NU e lista oficiala din biblioteca si bine ca nu e: lista
     * oficiala (`hosts_allowlist_sample`) a ramas in urma fata de Android
     * Auto-ul care se instaleaza azi din Play, iar bordul era respins de
     * propriul telefon. A noastra are, pe deasupra, amprenta masurata pe
     * telefonul acesta.
     */
    override fun createHostValidator(): HostValidator {
        spuneCeGazdeVede()
        return if (BuildConfig.DEBUG) {
            HostValidator.ALLOW_ALL_HOSTS_VALIDATOR
        } else {
            HostValidator.Builder(applicationContext)
                .addAllowedHosts(R.array.gazde_permise)
                .build()
        }
    }

    override fun onCreateSession(sessionInfo: SessionInfo): Session = KittSession()

    /**
     * Scrie in jurnal amprenta fiecarei gazde de pe telefon si daca e sau nu
     * in lista noastra.
     *
     * Fara asta, o gazda nepotrivita e respinsa in tacere: bordul nu apare pe
     * ecranul masinii si nimeni nu spune de ce. Exact asa am pierdut un drum.
     */
    private fun spuneCeGazdeVede() {
        val permise = try {
            resources.getStringArray(R.array.gazde_permise)
                .map { it.substringBefore(',').trim().lowercase() }
                .toSet()
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot citi lista de gazde: ${e.message}")
            emptySet()
        }
        for (pachet in GAZDE_CUNOSCUTE) {
            for (amprenta in amprentele(pachet)) {
                val stare = if (amprenta in permise) "in lista" else "NU E IN LISTA"
                Log.i(TAG, "Gazda $pachet: $amprenta - $stare")
            }
        }
    }

    private fun amprentele(pachet: String): List<String> = try {
        val info = packageManager.getPackageInfo(pachet, PackageManager.GET_SIGNING_CERTIFICATES)
        val semnaturi = info.signingInfo?.let {
            if (it.hasMultipleSigners()) it.apkContentsSigners else it.signingCertificateHistory
        } ?: emptyArray()
        semnaturi.map { amprenta(it.toByteArray()) }
    } catch (e: PackageManager.NameNotFoundException) {
        emptyList()
    } catch (e: Exception) {
        Log.w(TAG, "Nu pot citi semnatura lui $pachet: ${e.message}")
        emptyList()
    }

    private fun amprenta(octeti: ByteArray): String =
        MessageDigest.getInstance("SHA-256").digest(octeti)
            .joinToString("") { String.format("%02x", it) }

    companion object {
        private const val TAG = "KittCarApp"

        /** Gazdele care pot exista pe un telefon: Android Auto si cea din masinile cu Automotive. */
        private val GAZDE_CUNOSCUTE = listOf(
            "com.google.android.projection.gearhead",
            "com.google.android.apps.automotive.templates.host",
        )
    }
}
