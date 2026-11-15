package ro.vix.kittdash

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ro.vix.kittdash.obd.ObdService

class KittApp : Application() {

    override fun onCreate() {
        super.onCreate()
        // Intai cutia neagra: daca pica ceva mai jos, vrem sa se vada.
        Jurnal.prindeCaderile(this)
        Jurnal.scrie(this, "Aplicatia porneste")
        val canal = NotificationChannel(
            ObdService.CANAL,
            getString(R.string.obd_channel_name),
            NotificationManager.IMPORTANCE_LOW,
        ).apply {
            description = getString(R.string.obd_channel_desc)
            setShowBadge(false)
        }
        getSystemService(NotificationManager::class.java).createNotificationChannel(canal)
    }
}
