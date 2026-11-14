package ro.vix.kittdash.muzica

import android.service.notification.NotificationListenerService

/**
 * Nu asculta nimic si nu face nimic.
 *
 * Exista doar fiindca Android cere un serviciu de acest fel ca sa dea voie
 * cuiva sa se uite la sesiunile media. Notificarile nu ne intereseaza: din
 * permisiunea asta folosim doar dreptul de a intreba ce melodie cânta.
 */
class AscultatorNotificari : NotificationListenerService()
