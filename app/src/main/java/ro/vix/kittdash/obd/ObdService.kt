package ro.vix.kittdash.obd

import android.app.Notification
import android.app.PendingIntent
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothManager
import android.content.Context
import android.content.Intent
import android.content.pm.ServiceInfo
import android.os.Build
import androidx.lifecycle.LifecycleService
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.launch
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

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        super.onStartCommand(intent, flags, startId)

        pornesteInPrimPlan()

        val adresa = intent?.getStringExtra(EXTRA_ADRESA)
        val simulator = intent?.getBooleanExtra(EXTRA_SIMULATOR, false) ?: false

        bucla?.cancel()
        bucla = lifecycleScope.launch {
            val poller = ObdPoller(
                depozit = CarStateRepository.INSTANTA,
                faLegatura = { construiesteLegatura(adresa, simulator) },
            )
            poller.ruleaza()
        }
        return START_STICKY
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
        const val CANAL = "obd"
        private const val ID_NOTIFICARE = 1
        private const val EXTRA_ADRESA = "adresa"
        private const val EXTRA_SIMULATOR = "simulator"

        /** Porneste serviciul pe adaptorul cu adresa data. */
        fun porneste(context: Context, adresa: String) {
            val intent = Intent(context, ObdService::class.java)
                .putExtra(EXTRA_ADRESA, adresa)
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
