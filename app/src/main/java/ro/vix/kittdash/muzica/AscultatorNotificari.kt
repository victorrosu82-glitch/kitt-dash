package ro.vix.kittdash.muzica

import android.service.notification.NotificationListenerService
import android.service.notification.StatusBarNotification
import android.util.Log
import ro.vix.kittdash.navigatie.Navigatie

/**
 * Accesul la notificari, dat o singura data de user din Setari.
 *
 * Il folosim pentru doua lucruri:
 *  - sa intrebam ce melodie canta (Android cere un serviciu ca asta pentru
 *    sesiunile media - vezi [Muzica]);
 *  - sa citim instructiunea de navigatie pe care Waze sau Google Maps o tin
 *    in notificare cat timp navigheaza (vezi [Navigatie]).
 * Celelalte notificari nu ne intereseaza si nu le pastram.
 */
class AscultatorNotificari : NotificationListenerService() {

    override fun onListenerConnected() {
        // Daca navigatia era deja pornita cand ne-am legat, o luam de acum.
        try {
            activeNotifications?.forEach { Navigatie.primeste(this, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot citi notificarile de acum: ${e.message}")
        }
    }

    override fun onNotificationPosted(sbn: StatusBarNotification) {
        Navigatie.primeste(this, sbn)
    }

    override fun onNotificationRemoved(sbn: StatusBarNotification) {
        Navigatie.dispare(sbn)
    }

    private companion object {
        const val TAG = "AscultatorNotificari"
    }
}
