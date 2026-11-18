package ro.vix.kittdash.obd

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import android.util.Log
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale
import ro.vix.kittdash.R
import ro.vix.kittdash.ui.MainActivity

/**
 * Serviciul care tine legatura cu masina cat timp aplicatia e pornita, chiar
 * si cu ecranul stins. Android cere pentru asta un serviciu in prim-plan, cu
 * o notificare la vedere - de aceea apare in bara de sus.
 *
 * Porneste cu adresa adaptorului (sau cu simulatorul) si lasa [ObdPoller] sa
 * se descurce cu reconectarile.
 */
class ObdService : LifecycleService() {

    private var bucla: Job? = null

    /** Adaptorul la care lucreaza bucla acum. */
    private var adresaCurenta: String? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        pornesteInPrimPlan()

        val adresa = intent?.getStringExtra(EXTRA_ADRESA)
        val simulator = intent?.getBooleanExtra(EXTRA_SIMULATOR, false) ?: false
        val sonda = intent?.getBooleanExtra(EXTRA_SONDA, false) ?: false
        val explorare = intent?.getBooleanExtra(EXTRA_EXPLORARE, false) ?: false
        val doarDacaNuMerge = intent?.getBooleanExtra(EXTRA_DOAR_DACA_NU_MERGE, false) ?: false

        // Ecranul masinii cere legatura de fiecare data cand revine. Daca deja
        // lucram cu acelasi adaptor, nu rupem nimic: pe 17.11.2026 o asemenea
        // cerere a taiat explorarea masinii la jumatate.
        if (doarDacaNuMerge && bucla?.isActive == true && adresa == adresaCurenta) {
            return START_STICKY
        }
        adresaCurenta = if (simulator) null else adresa

        bucla?.cancel()
        bucla = lifecycleScope.launch {
            val poller = ObdPoller(
                depozit = CarStateRepository.INSTANTA,
                faLegatura = { construiesteLegatura(adresa, simulator) },
                faceSondaHibrida = sonda,
                scrieSonda = ::scrieJurnalSonda,
                faceExplorare = explorare,
                scrieExplorare = ::scrieRaportExplorare,
            )
            poller.ruleaza()
        }
        return START_STICKY
    }

    /**
     * Scrie raspunsurile sondei intr-un fisier, nu doar in jurnalul
     * telefonului: jurnalul se roteste si inghite liniile exact cand ai
     * nevoie de ele. Fisierul se ia cu
     * `adb pull /sdcard/Android/data/ro.vix.kittdash/files/`.
     */
    private fun scrieJurnalSonda(randuri: List<RandSonda>) {
        try {
            val dosar = getExternalFilesDir(null) ?: filesDir
            val acum = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
            val fisier = File(dosar, "sonda-$acum.txt")
            fisier.printWriter().use { scriitor ->
                scriitor.println("Sonda hibrida KITT Dash  $acum")
                scriitor.println("=".repeat(70))
                for (rand in randuri) scriitor.println(rand.toString())
            }
            Log.i(TAG, "Sonda scrisa in ${fisier.absolutePath}")
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot scrie jurnalul sondei: ${e.message}")
        }
    }

    /**
     * Raportul explorarii merge in Download, langa APK, ca sa-l poti trimite
     * direct din telefon. O copie ramane si in fisierele aplicatiei.
     */
    private fun scrieRaportExplorare(text: String): String? {
        val acum = SimpleDateFormat("yyyyMMdd-HHmmss", Locale.US).format(Date())
        val nume = "KittDash-explorare-$acum.txt"
        try {
            File(getExternalFilesDir(null) ?: filesDir, nume).writeText(text)
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot scrie copia explorarii: ${e.message}")
        }
        return try {
            val valori = android.content.ContentValues().apply {
                put(android.provider.MediaStore.Downloads.DISPLAY_NAME, nume)
                put(android.provider.MediaStore.Downloads.MIME_TYPE, "text/plain")
                put(android.provider.MediaStore.Downloads.RELATIVE_PATH, android.os.Environment.DIRECTORY_DOWNLOADS)
            }
            val uri = contentResolver.insert(android.provider.MediaStore.Downloads.EXTERNAL_CONTENT_URI, valori)
                ?: return null
            contentResolver.openOutputStream(uri)?.use { it.write(text.toByteArray(Charsets.UTF_8)) }
            "Download/$nume"
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot scrie explorarea in Download: ${e.message}")
            null
        }
    }

    private fun construiesteLegatura(adresa: String?, simulator: Boolean): ObdLink {
        if (simulator || adresa == null) return SimulatorObdLink()
        val adaptor: BluetoothAdapter = getSystemService(BluetoothManager::class.java)?.adapter
            ?: throw ObdException("Telefonul nu are Bluetooth")
        if (!adaptor.isEnabled) throw ObdException("Bluetooth-ul e oprit")
        val dispozitiv = adaptor.getRemoteDevice(adresa)
            ?: throw ObdException("Nu gasesc adaptorul $adresa")
        return BluetoothObdLink(dispozitiv)
    }

    private fun pornesteInPrimPlan() {
        val deschide = PendingIntent.getActivity(
            this,
            0,
            Intent(this, MainActivity::class.java),
            PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
        )
        val notificare: Notification = Notification.Builder(this, CANAL)
            .setContentTitle(getString(R.string.app_name))
            .setContentText("Citesc datele din masina")
            .setSmallIcon(android.R.drawable.stat_sys_data_bluetooth)
            .setContentIntent(deschide)
            .setOngoing(true)
            .build()

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            startForeground(
                ID_NOTIFICARE,
                notificare,
                ServiceInfo.FOREGROUND_SERVICE_TYPE_CONNECTED_DEVICE,
            )
        } else {
            startForeground(ID_NOTIFICARE, notificare)
        }
    }

    override fun onDestroy() {
        bucla?.cancel()
        CarStateRepository.INSTANTA.deconectat()
        super.onDestroy()
    }

    companion object {
        private const val TAG = "ObdService"
        const val CANAL = "obd"
        private const val ID_NOTIFICARE = 1
        private const val EXTRA_ADRESA = "adresa"
        private const val EXTRA_SIMULATOR = "simulator"
        private const val EXTRA_SONDA = "sonda"
        private const val EXTRA_EXPLORARE = "explorare"
        private const val EXTRA_DOAR_DACA_NU_MERGE = "doar_daca_nu_merge"

        /**
         * Porneste serviciul pe adaptorul cu adresa data. Cu [sondaHibrida],
         * intreaba o data calculatoarele hibride si pastreaza raspunsurile brute.
         */
        fun porneste(
            context: Context,
            adresa: String,
            sondaHibrida: Boolean = false,
            explorare: Boolean = false,
        ) {
            val intent = Intent(context, ObdService::class.java)
                .putExtra(EXTRA_ADRESA, adresa)
                .putExtra(EXTRA_SONDA, sondaHibrida)
                .putExtra(EXTRA_EXPLORARE, explorare)
            context.startForegroundService(intent)
        }

        /**
         * Porneste legatura numai daca nu merge deja cu acelasi adaptor. Pentru
         * ecranul masinii, care o cere la fiecare intoarcere.
         */
        fun pornesteDacaNuMerge(context: Context, adresa: String) {
            val intent = Intent(context, ObdService::class.java)
                .putExtra(EXTRA_ADRESA, adresa)
                .putExtra(EXTRA_DOAR_DACA_NU_MERGE, true)
            context.startForegroundService(intent)
        }

        /** Porneste serviciul pe simulator, fara masina. */
        fun pornesteSimulator(context: Context) {
            val intent = Intent(context, ObdService::class.java)
                .putExtra(EXTRA_SIMULATOR, true)
            context.startForegroundService(intent)
        }

        fun opreste(context: Context) {
            context.stopService(Intent(context, ObdService::class.java))
        }
    }
}
