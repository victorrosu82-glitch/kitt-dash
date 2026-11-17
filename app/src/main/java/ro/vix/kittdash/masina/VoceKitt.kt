package ro.vix.kittdash.masina

import android.content.Context
import android.media.AudioAttributes
import android.speech.tts.TextToSpeech
import ro.vix.kittdash.Jurnal
import ro.vix.kittdash.navigatie.InstructiuneNav
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Marime
import java.util.Locale
import kotlin.math.roundToInt

/**
 * Vocea KITT: la o apasare spune cu voce tare ce arata bordul, ca sa nu
 * trebuiasca sa te uiti la ecran.
 *
 * Foloseste vocea de citire a telefonului. Sunetul iese pe difuzoarele
 * masinii, ca o indicatie de navigatie: muzica se lasa putin mai incet cat
 * vorbeste KITT.
 */
class VoceKitt(private val context: Context) {

    private var tts: TextToSpeech? = null
    private var gata = false

    /** Ce trebuie spus imediat ce vocea e gata (pornirea ei dureaza o clipa). */
    private var deSpus: String? = null

    fun spune(text: String) {
        val t = tts
        if (t != null && gata) {
            t.speak(text, TextToSpeech.QUEUE_FLUSH, null, "kitt")
            return
        }
        deSpus = text
        if (t == null) porneste()
    }

    private fun porneste() {
        tts = TextToSpeech(context.applicationContext) { rezultat ->
            val t = tts ?: return@TextToSpeech
            if (rezultat != TextToSpeech.SUCCESS) {
                Jurnal.scrie(context, "Vocea KITT nu a pornit (cod $rezultat)")
                return@TextToSpeech
            }
            val limba = t.setLanguage(Locale.forLanguageTag("ro-RO"))
            if (limba == TextToSpeech.LANG_MISSING_DATA || limba == TextToSpeech.LANG_NOT_SUPPORTED) {
                Jurnal.scrie(context, "Telefonul nu are voce in romana; vorbesc cu vocea implicita")
            }
            t.setAudioAttributes(
                AudioAttributes.Builder()
                    .setUsage(AudioAttributes.USAGE_ASSISTANCE_NAVIGATION_GUIDANCE)
                    .setContentType(AudioAttributes.CONTENT_TYPE_SPEECH)
                    .build(),
            )
            gata = true
            deSpus?.let { t.speak(it, TextToSpeech.QUEUE_FLUSH, null, "kitt") }
            deSpus = null
        }
    }

    fun inchide() {
        tts?.stop()
        tts?.shutdown()
        tts = null
        gata = false
    }

    companion object {
        /** Textul spus de KITT, din starea masinii si urmatoarea manevra. */
        fun rezumat(stare: CarState, nav: InstructiuneNav?): String {
            if (!stare.conectat) {
                return "Nu sunt legat la mașină. Apasă Conectează."
            }
            val parti = mutableListOf<String>()
            stare.valoare(Marime.VITEZA)?.let {
                parti += "Viteza ${it.roundToInt()} de kilometri pe oră."
            }
            stare.valoare(Marime.BATERIE_HV)?.let {
                parti += "Bateria hibridă ${it.roundToInt()} la sută."
            }
            stare.valoare(Marime.TEMP_LICHID)?.let {
                parti += "Motorul are ${it.roundToInt()} de grade."
            }
            if (nav != null) {
                val unde = nav.strada?.let { ", pe $it" } ?: ""
                parti += "Următoarea manevră peste ${deCitit(nav.distanta)}$unde."
            }
            if (parti.isEmpty()) return "Sunt legat la mașină, dar nu am încă valori."
            return parti.joinToString(" ")
        }

        /** "1,2 km" se citeste "1,2 kilometri", nu "1,2 ca-me". */
        private fun deCitit(distanta: String): String =
            distanta
                .replace(Regex("""(\d)\s*km\b"""), "$1 kilometri")
                .replace(Regex("""(\d)\s*m\b"""), "$1 metri")
    }
}
