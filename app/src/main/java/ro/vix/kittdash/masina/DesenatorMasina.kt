package ro.vix.kittdash.masina

import android.graphics.Rect
import kotlin.math.abs
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import ro.vix.kittdash.Jurnal
import ro.vix.kittdash.bord.BordKitt
import ro.vix.kittdash.bord.Netezitor
import ro.vix.kittdash.muzica.Muzica
import ro.vix.kittdash.obd.CarStateRepository

/**
 * Deseneaza bordul pe suprafata primita de la Android Auto.
 *
 * Suprafata nu e un ecran gol: Android Auto isi pune peste ea barele lui
 * (ceasul, butoanele, uneori o bara laterala). Partea ramasa libera o
 * spune [onVisibleAreaChanged]. Bordul se aseaza in ea, nu pe toata
 * suprafata - altfel numele masinii ar sta sub ceasul masinii, exact
 * greseala pe care am facut-o intai pe telefon.
 *
 * Deseneaza acelasi [BordKitt] ca pe telefon. Singura deosebire e panza.
 */
class DesenatorMasina(private val carContext: CarContext) : SurfaceCallback {

    private val bord = BordKitt()
    private val muzica = Muzica(carContext)
    private val netezitor = Netezitor(
        melodiaAcum = { muzica.melodia()?.text },
        // Masina stie ea cand e noapte: are senzor de lumina si stie daca
        // sunt aprinse farurile. Ne luam dupa ea.
        esteNoapte = { carContext.isDarkMode },
    )
    private val mana = Handler(Looper.getMainLooper())

    private var suprafata: Surface? = null
    private var latime = 0
    private var inaltime = 0

    /** Partea de suprafata pe care Android Auto nu pune nimic peste acum. */
    private var zonaLibera: Rect? = null

    /** Partea libera tot timpul. O tinem doar ca sprijin, pana vine cea de sus. */
    private var zonaStabila: Rect? = null

    /** Ultima zona in care chiar am desenat - ca jurnalul sa nu presupuna. */
    private var zonaDesenata: Rect? = null

    private var deseneaza = false

    private val cadru = object : Runnable {
        override fun run() {
            if (!deseneaza) return
            unCadru()
            mana.postDelayed(this, PERIOADA_CADRU_MS)
        }
    }

    fun porneste() {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
    }

    fun opreste() {
        deseneaza = false
        mana.removeCallbacks(cadru)
    }

    // --- ce ne spune Android Auto ---------------------------------------

    override fun onSurfaceAvailable(container: SurfaceContainer) {
        suprafata = container.surface
        latime = container.width
        inaltime = container.height
        Jurnal.scrie(carContext, "Am primit panza masinii: ${latime}x$inaltime")
        if (suprafata != null && !deseneaza) {
            deseneaza = true
            mana.post(cadru)
        }
    }

    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        Jurnal.scrie(carContext, "Masina mi-a luat panza inapoi")
        opreste()
        suprafata = null
    }

    /**
     * Zona libera in clipa asta. O luam doar cat timp masina nu ne-a spus-o
     * inca pe cea de mai jos.
     *
     * Am incercat sa ne asezam pe ea, ca bordul sa iasa mai mare. Nu tine:
     * pe unitatea principala incepe 776x376 si se strange tot la 776x300 dupa
     * doua secunde. Castigul ar fi fost doar o resezare a bordului in mers.
     */
    override fun onVisibleAreaChanged(visibleArea: Rect) {
        if (zonaStabila == null) asaza("libera acum", visibleArea)
    }

    /**
     * Zona libera care nu se schimba in timp. O preferam celei de mai sus
     * acolo unde exista: daca bordul s-ar muta de fiecare data cand apare
     * si dispare o bara, ar juca pe ecran cat conduci.
     */
    override fun onStableAreaChanged(stableArea: Rect) {
        if (stableArea.isEmpty) return
        zonaStabila = Rect(stableArea)
        asaza("libera mereu", stableArea)
    }

    /**
     * Ia zona noua, dar numai daca e altfel decat cea de acum.
     *
     * Fara pragul asta bordul ar sari pe ecran de fiecare data cand apare si
     * dispare o bara, cat conduci. Cateva puncte nu merita o resezare.
     */
    private fun asaza(fel: String, zona: Rect) {
        if (zona.isEmpty) return
        val acum = zonaLibera
        if (acum != null && aproapeLaFel(acum, zona)) return
        spuneZona(fel, zona)
        zonaLibera = Rect(zona)
    }

    private fun aproapeLaFel(a: Rect, b: Rect): Boolean =
        abs(a.left - b.left) <= PRAG && abs(a.top - b.top) <= PRAG &&
            abs(a.width() - b.width()) <= PRAG && abs(a.height() - b.height()) <= PRAG

    /**
     * Scrie in cutia neagra cat din ecran ne ramane.
     *
     * Aici se vede daca bordul iese mic pe ecranul masinii: daca zona libera
     * e mult mai mica decat panza, ori daca forma ei nu seamana cu 1280x720,
     * bordul se aseaza in ea si lasa negru in rest. Fara cifrele astea e doar
     * banuiala.
     */
    private fun spuneZona(fel: String, zona: Rect) {
        val forma = if (zona.height() > 0) zona.width().toFloat() / zona.height() else 0f
        Jurnal.scrie(
            carContext,
            "Zona $fel: ${zona.width()}x${zona.height()} la (${zona.left},${zona.top}), " +
                "forma %.2f, din panza ${latime}x$inaltime".format(forma),
        )
    }

    // --- desenul ---------------------------------------------------------

    private fun unCadru() {
        val s = suprafata ?: return
        if (!s.isValid) return

        val panza = try {
            s.lockCanvas(null)
        } catch (e: IllegalArgumentException) {
            Log.w(TAG, "Nu pot lua panza: ${e.message}")
            return
        } ?: return

        try {
            val stare = netezitor.urmatoareaStare(CarStateRepository.INSTANTA.stare.value)
            val zona = zonaLibera
            if (zona != zonaDesenata) {
                zonaDesenata = zona?.let { Rect(it) }
                Jurnal.scrie(carContext, "Desenez in ${zona ?: "toata panza"}")
            }
            if (zona == null) {
                bord.deseneaza(panza, latime.toFloat(), inaltime.toFloat(), stare)
            } else {
                // Fundalul acopera toata suprafata, ca marginile sa nu
                // ramana cu ce a desenat altcineva inainte.
                panza.drawColor(android.graphics.Color.parseColor("#050505"))
                val salvat = panza.save()
                panza.translate(zona.left.toFloat(), zona.top.toFloat())
                bord.deseneaza(panza, zona.width().toFloat(), zona.height().toFloat(), stare)
                panza.restoreToCount(salvat)
            }
        } finally {
            try {
                s.unlockCanvasAndPost(panza)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Nu pot da panza inapoi: ${e.message}")
            }
        }
    }

    companion object {
        private const val TAG = "DesenatorMasina"

        /**
         * Cam 30 de cadre pe secunda. Ecranul masinii nu cere mai mult, iar
         * bordul se misca lin oricum: valorile sunt netezite, nu sarite.
         */
        const val PERIOADA_CADRU_MS = 33L

        /** Sub atatea puncte, o zona noua nu merita o resezare a bordului. */
        private const val PRAG = 24
    }
}
