package ro.vix.kittdash

import android.content.Context
import android.util.Log
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

/**
 * Cutia neagra a bordului.
 *
 * Jurnalul telefonului (logcat) se sterge la repornire, si tocmai asta s-a
 * intamplat cand bordul a disparut de pe ecranul Lexusului: pana sa ajunga
 * telefonul la calculator, trei reporniri stersesera tot. Nu mai stim nici
 * azi de ce a disparut.
 *
 * Aici scriem noi, intr-un fisier al nostru, care tine peste reporniri. Cate
 * randuri incap in [MARIME_MAXIMA]; cand se umple, jumatatea veche se arunca.
 *
 * Ce scriem: clipele care conteaza daca bordul nu apare pe ecranul masinii -
 * cand ne cheama Android Auto, ce gazda e, cand primim si cand pierdem
 * suprafata de desenat, si orice cadere, cu tot cu urma ei.
 *
 * Sta intr-un loc de unde se poate lua fara drepturi speciale - varianta
 * de predat nu e de depanare, deci `run-as` nu merge pe ea. De pe calculator:
 *
 *     adb pull /sdcard/Android/data/ro.vix.kittdash/files/jurnal.txt
 *
 * sau, de pe telefon, cu Fisiere: Android > data > ro.vix.kittdash > files.
 */
object Jurnal {

    private const val TAG = "KittJurnal"
    private const val NUME = "jurnal.txt"
    private const val MARIME_MAXIMA = 256 * 1024

    private val CEAS = SimpleDateFormat("MM-dd HH:mm:ss", Locale.US)

    @Synchronized
    fun scrie(context: Context, text: String) {
        Log.i(TAG, text)
        try {
            val fisier = File(dosar(context), NUME)
            if (fisier.length() > MARIME_MAXIMA) injumatateste(fisier)
            fisier.appendText("${CEAS.format(Date())}  $text\n")
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot scrie in jurnal: ${e.message}")
        }
    }

    /**
     * Unde tinem fisierul. Intai pe memoria pe care o vede si calculatorul;
     * daca nu se poate (card scos, memorie plina), in casa aplicatiei, ca
     * macar sa se scrie ceva.
     */
    private fun dosar(context: Context): File =
        context.getExternalFilesDir(null) ?: context.filesDir

    /** Arunca jumatatea veche, ca fisierul sa nu creasca la nesfarsit. */
    private fun injumatateste(fisier: File) {
        val randuri = fisier.readLines()
        fisier.writeText(randuri.drop(randuri.size / 2).joinToString("\n", postfix = "\n"))
    }

    /**
     * Prinde caderile care omoara aplicatia si le scrie inainte sa moara.
     *
     * Fara asta, o cadere in masina nu lasa nicio urma pe care s-o gasim a
     * doua zi: Android o trece in jurnalul lui, care se sterge la repornire.
     */
    fun prindeCaderile(context: Context) {
        val dinainte = Thread.getDefaultUncaughtExceptionHandler()
        Thread.setDefaultUncaughtExceptionHandler { fir, necaz ->
            try {
                scrie(context, "CADERE pe firul ${fir.name}: ${necaz.stackTraceToString()}")
            } catch (e: Throwable) {
                // Daca nici asta nu merge, macar lasam Android sa-si faca treaba.
            }
            dinainte?.uncaughtException(fir, necaz)
        }
    }
}
