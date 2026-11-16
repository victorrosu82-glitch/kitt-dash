package ro.vix.kittdash.navigatie

import android.app.Notification
import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.drawable.BitmapDrawable
import android.graphics.drawable.Icon
import android.service.notification.StatusBarNotification
import android.util.Log
import androidx.car.app.notification.CarAppExtender

/**
 * Singurul loc unde sta instructiunea de navigatie de acum.
 *
 * O scrie ascultatorul de notificari, o citeste desenatorul bordului de 30 de
 * ori pe secunda; de aceea e un simplu camp volatil, nu un flux.
 */
object Navigatie {

    @Volatile
    var acum: InstructiuneNav? = null
        private set

    /** Cheia notificarii din care vine [acum], ca s-o stergem cand dispare. */
    @Volatile
    private var cheie: String? = null

    /** Sa scriem in cutia neagra marimea sagetii o singura data. */
    @Volatile
    private var amSpusSageata = false

    fun primeste(context: Context, sbn: StatusBarNotification) {
        if (!esteNavigatie(context, sbn)) return
        val n = sbn.notification
        val extras = n.extras
        val (distanta, strada) = Distanta.alege(
            extras.getCharSequence(Notification.EXTRA_TITLE)?.toString()?.trim()?.takeIf { it.isNotEmpty() },
            extras.getCharSequence(Notification.EXTRA_TEXT)?.toString()?.trim()?.takeIf { it.isNotEmpty() },
        ) ?: return
        val sageata = sageata(context, n)
        if (sageata != null && !amSpusSageata) {
            amSpusSageata = true
            ro.vix.kittdash.Jurnal.scrie(
                context,
                "Navigatie de la ${sbn.packageName}: sageata ${sageata.width}x${sageata.height}",
            )
        }
        cheie = sbn.key
        acum = InstructiuneNav(
            sursa = numeSursa(sbn.packageName),
            distanta = distanta,
            strada = strada,
            sageata = sageata,
            metriLaInceput = InstructiuneNav.inceput(acum, strada, Distanta.inMetri(distanta)),
        )
    }

    fun dispare(sbn: StatusBarNotification) {
        if (sbn.key == cheie) {
            cheie = null
            acum = null
        }
    }

    /**
     * Notificarile de navigatie: categoria `navigation`, tinute pe ecran
     * (ongoing). Asa le pun si Waze, si Google Maps. Pe ale noastre le sarim.
     */
    private fun esteNavigatie(context: Context, sbn: StatusBarNotification): Boolean {
        if (sbn.packageName == context.packageName) return false
        val n = sbn.notification
        val tinuta = n.flags and Notification.FLAG_ONGOING_EVENT != 0
        return n.category == Notification.CATEGORY_NAVIGATION && tinuta
    }

    /**
     * Sageata manevrei. Waze o pune si in extensia de masina a notificarii,
     * si ca imagine mare; o luam pe cea mai mare dintre ele.
     */
    private fun sageata(context: Context, n: Notification): Bitmap? {
        val dinMasina = try {
            CarAppExtender(n).largeIcon
        } catch (e: Exception) {
            null
        }
        val mare = try {
            n.getLargeIcon()?.let { inImagine(context, it) }
        } catch (e: Exception) {
            Log.w(TAG, "Nu pot citi sageata: ${e.message}")
            null
        }
        return listOfNotNull(dinMasina, mare).maxByOrNull { it.width * it.height }
    }

    private fun inImagine(context: Context, icon: Icon): Bitmap? {
        val d = icon.loadDrawable(context) ?: return null
        if (d is BitmapDrawable && d.bitmap != null) return d.bitmap
        val l = d.intrinsicWidth.takeIf { it > 0 } ?: 96
        val h = d.intrinsicHeight.takeIf { it > 0 } ?: 96
        val b = Bitmap.createBitmap(l, h, Bitmap.Config.ARGB_8888)
        val c = Canvas(b)
        d.setBounds(0, 0, l, h)
        d.draw(c)
        return b
    }

    private fun numeSursa(pachet: String): String = when {
        pachet.startsWith("com.waze") -> "WAZE"
        pachet == "com.google.android.apps.maps" -> "MAPS"
        else -> pachet.substringAfterLast('.').uppercase()
    }

    private const val TAG = "Navigatie"
}
