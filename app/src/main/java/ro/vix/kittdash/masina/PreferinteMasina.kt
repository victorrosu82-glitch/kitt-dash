package ro.vix.kittdash.masina

import android.content.Context
import ro.vix.kittdash.bord.FelEcran

/**
 * Ce alege Victor pentru ecranul masinii: adresa de acasa, pentru butonul
 * "Acasa", si daca panoul de navigatie se vede langa bord.
 *
 * Adresa se scrie o singura data, din aplicatia de pe telefon; pe ecranul
 * masinii nu se tasteaza.
 */
object PreferinteMasina {

    private const val FISIER = "kitt"
    private const val CHEIE_ACASA = "adresa_acasa"
    private const val CHEIE_ECRAN = "ecran"

    /**
     * Panoul cu instructiunile Waze e ascuns: bordul ramane pe tot ecranul
     * chiar daca merge navigatia. Se uita la repornirea aplicatiei, ca sa nu
     * ramai fara indicatii fara sa stii de ce.
     */
    @Volatile
    var panouNavAscuns: Boolean = false

    /** Ecranul ales cu butonul "Ecran"; tinut minte de la un drum la altul. */
    @Volatile
    private var ecranAles: FelEcran? = null

    fun ecran(context: Context): FelEcran =
        ecranAles ?: FelEcran.dupaNume(
            context.applicationContext
                .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
                .getString(CHEIE_ECRAN, null),
        ).also { ecranAles = it }

    /** Trece la ecranul urmator si il intoarce. */
    fun urmatorulEcran(context: Context): FelEcran {
        val nou = ecran(context).urmatorul()
        ecranAles = nou
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .edit()
            .putString(CHEIE_ECRAN, nou.name)
            .apply()
        return nou
    }

    fun acasa(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .getString(CHEIE_ACASA, null)
            ?.trim()
            ?.takeIf { it.isNotEmpty() }

    fun scrieAcasa(context: Context, adresa: String) {
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .edit()
            .putString(CHEIE_ACASA, adresa.trim())
            .apply()
    }
}
