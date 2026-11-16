package ro.vix.kittdash.navigatie

import android.graphics.Bitmap

/**
 * Urmatoarea manevra, asa cum o anunta aplicatia de navigatie in notificarea
 * ei de pe telefon.
 *
 * Masurat pe 16.11.2026, cu Waze: cand bordul KITT ia locul lui Waze pe
 * ecranul masinii, Waze merge mai departe pe telefon si tine o notificare pe
 * canalul "Waze Navigation Instructions", categoria `navigation`, cu:
 *  - titlul = distanta pana la manevra ("70 m"),
 *  - textul = strada pe care intri ("Str. ..."),
 *  - imaginea mare = sageata manevrei (36x36).
 * Ora sosirii nu e in notificare, deci n-o aratam.
 *
 * Cat timp Waze sta el insusi pe ecranul masinii, notificarea lipseste - nici
 * nu e nevoie, atunci nu se vede bordul.
 */
data class InstructiuneNav(
    /** De la cine vine: "WAZE", "MAPS" etc. */
    val sursa: String,
    /** Distanta, asa cum o scrie aplicatia: "70 m", "1,2 km". */
    val distanta: String,
    /** Strada sau indicatia, daca exista. */
    val strada: String?,
    /** Sageata manevrei, daca aplicatia a dat-o. */
    val sageata: Bitmap?,
    /**
     * De la ce distanta am inceput sa urmarim manevra asta. Bara de apropiere
     * se umple de la ea spre zero.
     */
    val metriLaInceput: Float? = null,
) {
    /** Distanta in metri, daca o putem intelege; altfel null. */
    val metri: Float? get() = Distanta.inMetri(distanta)

    /** Cat din drumul pana la manevra e facut, 0..1; null daca nu stim. */
    val apropiere: Float?
        get() {
            val acum = metri ?: return null
            val start = metriLaInceput ?: return null
            if (start <= 0f) return null
            return (1f - acum / start).coerceIn(0f, 1f)
        }

    companion object {
        /** Sub atatia metri manevra e iminenta: sageata clipeste in rosu. */
        const val APROAPE_METRI = 50f

        /**
         * Distanta de la care incepe sa urmareasca manevra noua: daca strada
         * e alta, sau daca distanta a crescut (s-a recalculat traseul), o luam
         * de la capat; altfel pastram inceputul vechi.
         */
        fun inceput(veche: InstructiuneNav?, strada: String?, metri: Float?): Float? {
            if (metri == null) return null
            val start = veche?.metriLaInceput
            return if (veche == null || veche.strada != strada || start == null || metri > start) {
                metri
            } else {
                start
            }
        }
    }
}

/** Citirea distantei scrise de aplicatia de navigatie. */
object Distanta {

    private val forma = Regex("""^\s*(\d+(?:[.,]\d+)?)\s*(m|km|ft|mi|yd)\b""", RegexOption.IGNORE_CASE)

    /**
     * "70 m" -> 70, "1,2 km" -> 1200, "0.3 mi" -> ~483. Orice altceva (de
     * pilda "Acum" sau un text fara cifre) -> null.
     */
    fun inMetri(text: String): Float? {
        val m = forma.find(text) ?: return null
        val numar = m.groupValues[1].replace(',', '.').toFloatOrNull() ?: return null
        return when (m.groupValues[2].lowercase()) {
            "m" -> numar
            "km" -> numar * 1000f
            "ft" -> numar * 0.3048f
            "yd" -> numar * 0.9144f
            "mi" -> numar * 1609.344f
            else -> null
        }
    }

    /**
     * Din titlul si textul unei notificari de navigatie, care e distanta si
     * care e strada. Waze pune distanta in titlu; daca o gasim in text, le
     * inversam. Fara nicio distanta (Google Maps tine si o notificare doar cu
     * numele strazii, masurat pe 16.11.2026) nu e o manevra: null.
     */
    fun alege(titlu: String?, text: String?): Pair<String, String?>? = when {
        titlu != null && inMetri(titlu) != null -> titlu to text
        text != null && inMetri(text) != null -> text to titlu
        else -> null
    }

    /**
     * Desparte "70 m" in cifra si unitate, ca bordul sa scrie cifra mare si
     * unitatea mica dedesubt. Fara unitate recunoscuta, totul e "cifra".
     */
    fun desparte(text: String): Pair<String, String> {
        val m = forma.find(text) ?: return text.trim() to ""
        return m.groupValues[1] to m.groupValues[2].uppercase()
    }
}
