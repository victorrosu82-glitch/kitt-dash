package ro.vix.kittdash.masina

import android.content.Context

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

    /**
     * Panoul cu instructiunile Waze e ascuns: bordul ramane pe tot ecranul
     * chiar daca merge navigatia. Se uita la repornirea aplicatiei, ca sa nu
     * ramai fara indicatii fara sa stii de ce.
     */
    @Volatile
    var panouNavAscuns: Boolean = false

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
