package ro.vix.kittdash.masina

import android.content.Context

/**
 * Tine minte adaptorul ales ultima data, ca bordul sa porneasca singur pe
 * ecranul masinii. Pe ecranul masinii nu exista lista de adaptoare: acolo
 * nu alegi aparate Bluetooth, acolo conduci.
 */
object AdaptorTinutMinte {

    private const val FISIER = "kitt"
    private const val CHEIE_ADRESA = "adresa_adaptor"
    private const val CHEIE_NUME = "nume_adaptor"

    fun scrie(context: Context, adresa: String, nume: String) {
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .edit()
            .putString(CHEIE_ADRESA, adresa)
            .putString(CHEIE_NUME, nume)
            .apply()
    }

    fun citeste(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .getString(CHEIE_ADRESA, null)

    fun nume(context: Context): String? =
        context.applicationContext
            .getSharedPreferences(FISIER, Context.MODE_PRIVATE)
            .getString(CHEIE_NUME, null)
}
