package ro.vix.kittdash.masina

import android.graphics.Rect
import kotlin.math.abs
import android.os.Handler
import android.os.HandlerThread
import android.os.Looper
import android.os.Process
import android.os.SystemClock
import android.util.Log
import android.view.Surface
import androidx.car.app.AppManager
import androidx.car.app.CarContext
import androidx.car.app.SurfaceCallback
import androidx.car.app.SurfaceContainer
import ro.vix.kittdash.Jurnal
import ro.vix.kittdash.bord.Ecrane
import ro.vix.kittdash.bord.Netezitor
import ro.vix.kittdash.muzica.Muzica
import ro.vix.kittdash.navigatie.Navigatie
import ro.vix.kittdash.obd.CarStateRepository
import java.util.concurrent.CountDownLatch
import java.util.concurrent.TimeUnit

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

    private val ecrane = Ecrane()
    private val muzica = Muzica(carContext)
    private val netezitor = Netezitor(
        melodiaAcum = { muzica.melodia()?.text },
        // Masina stie ea cand e noapte: are senzor de lumina si stie daca
        // sunt aprinse farurile. Ne luam dupa ea.
        esteNoapte = { carContext.isDarkMode },
    )
    /**
     * Firul principal: aici ne cheama Android Auto si aici sta paznicul.
     * Desenul nu mai sta aici - vezi [desen].
     */
    private val mana = Handler(Looper.getMainLooper())

    /**
     * Firul pe care desenam, numai al bordului.
     *
     * Pe 17.11.2026, in Lexus, bordul a inghetat la cateva secunde dupa ce
     * camera telefonului a pornit filmarea. Pana atunci desenul statea pe
     * firul principal, impreuna cu tot restul aplicatiei: o intarziere acolo -
     * telefonul ocupat cu camera - oprea si bordul. Pe firul lui, cu
     * prioritatea pe care Android o da desenului de ecran, bordul nu mai
     * asteapta dupa nimeni.
     */
    private val fir = HandlerThread("KittDesen", Process.THREAD_PRIORITY_DISPLAY).apply { start() }
    private val desen = Handler(fir.looper)

    // Campurile de mai jos se schimba numai pe firul de desen. Cele citite si
    // de paznic, de pe firul principal, sunt volatile.
    @Volatile
    private var suprafata: Surface? = null
    private var latime = 0
    private var inaltime = 0

    /** Partea de suprafata pe care Android Auto nu pune nimic peste acum. */
    private var zonaLibera: Rect? = null

    /** Partea libera tot timpul. O tinem doar ca sprijin, pana vine cea de sus. */
    private var zonaStabila: Rect? = null

    /** Ultima zona in care chiar am desenat - ca jurnalul sa nu presupuna. */
    private var zonaDesenata: Rect? = null

    @Volatile
    private var deseneaza = false

    /** Cand am pus ultimul cadru pe ecran (ceasul de la pornirea telefonului). */
    @Volatile
    private var ultimulCadruMs = 0L

    /** Paznicul a scris deja in jurnal ca bordul sta pe loc. */
    @Volatile
    private var blocajSpus = false

    private val cadru = object : Runnable {
        override fun run() {
            if (!deseneaza) return
            unCadru()
            desen.postDelayed(this, PERIOADA_CADRU_MS)
        }
    }

    /**
     * Se uita o data pe secunda daca bordul chiar se mai deseneaza. Daca nu,
     * scrie in jurnal o singura data unde a ramas firul de desen - urma care
     * ne-a lipsit pe 17.11, cand singurul semn a fost ca bordul a stat pe loc.
     */
    private val paznic = object : Runnable {
        override fun run() {
            val stat = SystemClock.uptimeMillis() - ultimulCadruMs
            if (deseneaza && suprafata != null && stat > BLOCAJ_MS && !blocajSpus) {
                blocajSpus = true
                val unde = fir.stackTrace.take(15).joinToString("\n    ") { it.toString() }
                Jurnal.scrie(
                    carContext,
                    "Bordul nu s-a mai desenat de ${stat / 1000} s. Firul de desen e ${fir.state}, la:\n    $unde",
                )
            }
            mana.postDelayed(this, PERIOADA_PAZNIC_MS)
        }
    }

    fun porneste() {
        carContext.getCarService(AppManager::class.java).setSurfaceCallback(this)
        // Android Auto poate pune ecranul pe pauza si apoi sa-l reia fara sa
        // ne dea o panza noua. Atunci o folosim mai departe pe cea pe care o
        // avem; altfel bordul ar ramane inghetat pe ultimul cadru.
        desen.post {
            if (suprafata != null && !deseneaza) {
                Jurnal.scrie(carContext, "Reiau desenul pe aceeasi panza")
                pornesteBucla()
            }
        }
        mana.removeCallbacks(paznic)
        mana.post(paznic)
    }

    fun opreste() {
        mana.removeCallbacks(paznic)
        desen.post { opresteBucla() }
    }

    /** La sfarsitul ecranului: firul de desen nu mai are ce face. */
    fun inchide() {
        opreste()
        fir.quitSafely()
    }

    private fun pornesteBucla() {
        ultimulCadruMs = SystemClock.uptimeMillis()
        deseneaza = true
        desen.removeCallbacks(cadru)
        desen.post(cadru)
    }

    private fun opresteBucla() {
        deseneaza = false
        desen.removeCallbacks(cadru)
    }

    // --- ce ne spune Android Auto ---------------------------------------

    /**
     * Android Auto poate da o panza noua fara s-o ia pe cea veche (s-a vazut
     * pe 16.11.2026: doua panze in aceeasi clipa, dupa care bordul a ramas
     * inghetat). Oprim bucla, uitam tot ce stiam despre panza veche si o
     * pornim din nou pe cea noua - altfel desenam mai departe pe una moarta.
     */
    override fun onSurfaceAvailable(container: SurfaceContainer) {
        val noua = container.surface
        val l = container.width
        val h = container.height
        desen.post {
            val aveamPanza = suprafata != null
            opresteBucla()
            suprafata = noua
            latime = l
            inaltime = h
            zonaDesenata = null
            // Zonele libere le spune masina din nou pentru panza noua; cele vechi
            // ar aseza bordul o clipa intr-un loc care nu mai e al lui.
            zonaLibera = null
            zonaStabila = null
            esecuriLaRand = 0
            Jurnal.scrie(
                carContext,
                "Am primit panza masinii: ${latime}x$inaltime" +
                    if (aveamPanza) " (o inlocuieste pe cea veche)" else "",
            )
            if (suprafata != null) pornesteBucla()
        }
    }

    /**
     * Panza trebuie lasata inainte sa ne intoarcem de aici: dupa asta Android
     * Auto o distruge. Asteptam firul de desen sa termine cadrul inceput, dar
     * nu la nesfarsit - daca el e blocat, nu blocam si Android Auto.
     */
    override fun onSurfaceDestroyed(container: SurfaceContainer) {
        Jurnal.scrie(carContext, "Masina mi-a luat panza inapoi")
        val gata = CountDownLatch(1)
        desen.post {
            opresteBucla()
            suprafata = null
            gata.countDown()
        }
        if (!gata.await(ASTEPTARE_PANZA_MS, TimeUnit.MILLISECONDS)) {
            Jurnal.scrie(carContext, "Firul de desen n-a lasat panza in $ASTEPTARE_PANZA_MS ms")
        }
    }

    /**
     * De cate ori la rand n-am putut lua panza. Cand bordul ingheata pe
     * ecranul masinii, asta e urma: o scriem in cutia neagra o singura data,
     * nu de 30 de ori pe secunda.
     */
    private var esecuriLaRand = 0

    private fun nuPotLuaPanza(motiv: String?) {
        esecuriLaRand++
        if (esecuriLaRand == ESECURI_DE_RAPORTAT) {
            Jurnal.scrie(
                carContext,
                "Nu pot desena pe panza de $ESECURI_DE_RAPORTAT ori la rand: ${motiv ?: "fara motiv"}",
            )
        }
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
        val zona = Rect(visibleArea)
        desen.post { if (zonaStabila == null) asaza("libera acum", zona) }
    }

    /**
     * Zona libera care nu se schimba in timp. O preferam celei de mai sus
     * acolo unde exista: daca bordul s-ar muta de fiecare data cand apare
     * si dispare o bara, ar juca pe ecran cat conduci.
     */
    override fun onStableAreaChanged(stableArea: Rect) {
        if (stableArea.isEmpty) return
        val zona = Rect(stableArea)
        desen.post {
            zonaStabila = zona
            asaza("libera mereu", zona)
        }
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

    /**
     * Zona libera, dar pe toata inaltimea panzei.
     *
     * Android Auto pastreaza sus o fasie de 88 de puncte pentru banda de
     * butoane a sablonului de navigatie. Pe Lexus (panza 1190x460) asta lua
     * aproape o cincime din ecran, iar banda nu se vede decat cand atingi
     * ecranul - atunci butoanele apar o clipa peste coltul din dreapta-sus al
     * bordului si se ascund singure. Latimea o luam tot din zona libera, ca
     * bordul sa nu intre sub bara de aplicatii sau sub o fereastra alaturata.
     */
    private fun inaltimeIntreaga(zona: Rect): Rect =
        Rect(zona.left, MARGINE, zona.right, inaltime - MARGINE)

    private fun unCadru() {
        val s = suprafata ?: return
        if (!s.isValid) return

        val panza = try {
            s.lockCanvas(null)
        } catch (e: IllegalArgumentException) {
            nuPotLuaPanza("IllegalArgumentException ${e.message}")
            return
        } catch (e: IllegalStateException) {
            nuPotLuaPanza("IllegalStateException ${e.message}")
            return
        } ?: return
        if (esecuriLaRand >= ESECURI_DE_RAPORTAT) {
            Jurnal.scrie(carContext, "Pot desena din nou, dupa $esecuriLaRand incercari")
        }
        esecuriLaRand = 0

        try {
            val stare = netezitor.urmatoareaStare(CarStateRepository.INSTANTA.stare.value)
            val zona = zonaLibera?.let { inaltimeIntreaga(it) }
            if (zona != zonaDesenata) {
                zonaDesenata = zona?.let { Rect(it) }
                Jurnal.scrie(carContext, "Desenez in ${zona ?: "toata panza"}")
            }
            // Cu panoul ascuns de la butonul de pe masina, bordul ia tot
            // ecranul chiar daca navigatia merge.
            val nav = if (PreferinteMasina.panouNavAscuns) null else Navigatie.acum
            val fel = PreferinteMasina.ecran(carContext)
            if (zona == null) {
                ecrane.deseneaza(panza, latime.toFloat(), inaltime.toFloat(), stare, nav, fel)
            } else {
                // Fundalul acopera toata suprafata, ca marginile sa nu
                // ramana cu ce a desenat altcineva inainte.
                panza.drawColor(android.graphics.Color.parseColor("#050505"))
                val salvat = panza.save()
                panza.translate(zona.left.toFloat(), zona.top.toFloat())
                ecrane.deseneaza(panza, zona.width().toFloat(), zona.height().toFloat(), stare, nav, fel)
                panza.restoreToCount(salvat)
            }
        } finally {
            try {
                s.unlockCanvasAndPost(panza)
            } catch (e: IllegalArgumentException) {
                Log.w(TAG, "Nu pot da panza inapoi: ${e.message}")
            } catch (e: IllegalStateException) {
                Log.w(TAG, "Nu pot da panza inapoi: ${e.message}")
            }
        }

        val acum = SystemClock.uptimeMillis()
        if (blocajSpus) {
            blocajSpus = false
            Jurnal.scrie(carContext, "Bordul se deseneaza din nou, dupa ${(acum - ultimulCadruMs) / 1000} s")
        }
        ultimulCadruMs = acum
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

        /** Cat lasam liber sus si jos, ca bordul sa nu atinga colturile rotunjite. */
        private const val MARGINE = 12

        /** Cam o secunda de cadre ratate: atunci bordul chiar a inghetat. */
        private const val ESECURI_DE_RAPORTAT = 30

        /** Peste atata timp fara cadru, bordul chiar sta pe loc pe ecran. */
        private const val BLOCAJ_MS = 2_000L

        private const val PERIOADA_PAZNIC_MS = 1_000L

        /** Cat asteptam firul de desen sa lase panza cand o ia masina. */
        private const val ASTEPTARE_PANZA_MS = 1_000L
    }
}
