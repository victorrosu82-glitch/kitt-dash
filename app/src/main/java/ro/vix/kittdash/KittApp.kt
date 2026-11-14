package ro.vix.kittdash

import android.app.Application
import android.app.NotificationChannel
import android.app.NotificationManager
import ro.vix.kittdash.obd.ObdService

class KittApp : Application() {

    override fun onCreate() {
        super.onCreate()
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
