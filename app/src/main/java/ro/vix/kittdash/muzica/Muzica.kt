package ro.vix.kittdash.muzica

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.MediaMetadata
import android.media.session.MediaController
import android.media.session.MediaSessionManager
import android.os.Handler
import android.os.Looper
import android.media.session.PlaybackState
import android.provider.Settings
import android.util.Log

/** Melodia care cânta acum, din orice aplicație de muzică. */
data class Melodie(
    val titlu: String,
    val artist: String?,
) {
    /** Cum se scrie pe bord. */
    val text: String
        get() = if (artist.isNullOrBlank()) titlu else "$artist — $titlu"
}

/**
 * Citeste ce canta telefonul.
 *
 * Nu intreaba Spotify anume: intreaba Android ce sesiuni media sunt
 * pornite, asa ca merge la fel cu Spotify, YouTube Music sau radioul.
 *
 * Pentru asta Android cere acces la notificari - o permisiune pe care userul
 * o da o singura data, de mana, din Setari. Fara ea nu stim nimic si bordul
 * se poarta ca si cum n-ar canta nimic, fara sa se planga.
 */
class Muzica(private val context: Context) {

    private val numeAscultator = ComponentName(context, AscultatorNotificari::class.java)

    /** Userul ne-a dat acces la notificari? */
    fun areVoie(): Boolean {
        val permise = Settings.Secure.getString(
            context.contentResolver,
            "enabled_notification_listeners",
        ) ?: return false
        return permise.split(":").any {
            ComponentName.unflattenFromString(it)?.packageName == context.packageName
        }
    }

    /** Ecranul din Setari unde se da permisiunea. */
    fun intentSetari(): Intent =
        Intent(Settings.ACTION_NOTIFICATION_LISTENER_SETTINGS)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

    /**
     * Melodia din sesiunea care chiar cânta acum, sau null daca nu canta
     * nimic ori n-avem voie sa ne uitam.
     *
     * Nu mai intreaba telefonul la fiecare apel: la primul apel ne abonam la
     * sesiunile media si de atunci telefonul ne anunta singur cand se schimba
     * melodia sau cand porneste/se opreste. Apelul intoarce ce stim deja.
     * Daca abonarea nu merge, intrebam ca inainte.
     */
    fun melodia(): Melodie? {
        if (!areVoie()) return null
        if (!abonat) aboneaza()
        return if (abonat) curenta else citesteAcum(sesiuniActive() ?: return null)
    }

    /** Renunta la abonamente; de chemat cand ecranul se inchide. */
    fun inchide() {
        val manager = context.getSystemService(MediaSessionManager::class.java)
        ascultatorSesiuni?.let { manager?.removeOnActiveSessionsChangedListener(it) }
        ascultatorSesiuni = null
        for ((controler, cb) in urmarite) controler.unregisterCallback(cb)
        urmarite.clear()
        abonat = false
    }

    @Volatile
    private var curenta: Melodie? = null

    @Volatile
    private var abonat = false
    private var ascultatorSesiuni: MediaSessionManager.OnActiveSessionsChangedListener? = null
    private val urmarite = mutableListOf<Pair<MediaController, MediaController.Callback>>()
    private val mana = Handler(Looper.getMainLooper())

    private val laSchimbare = object : MediaController.Callback() {
        override fun onPlaybackStateChanged(state: PlaybackState?) = recalculeaza()
        override fun onMetadataChanged(metadata: MediaMetadata?) = recalculeaza()
    }

    private fun aboneaza() {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return
        try {
            val asc = MediaSessionManager.OnActiveSessionsChangedListener { sesiuni ->
                urmareste(sesiuni.orEmpty())
            }
            manager.addOnActiveSessionsChangedListener(asc, numeAscultator, mana)
            ascultatorSesiuni = asc
            urmareste(manager.getActiveSessions(numeAscultator))
            abonat = true
        } catch (e: SecurityException) {
            Log.w(TAG, "Nu ma pot abona la sesiunile media: ${e.message}")
        }
    }

    @Synchronized
    private fun urmareste(sesiuni: List<MediaController>) {
        for ((controler, cb) in urmarite) controler.unregisterCallback(cb)
        urmarite.clear()
        for (s in sesiuni) {
            s.registerCallback(laSchimbare, mana)
            urmarite += s to laSchimbare
        }
        curenta = citesteAcum(sesiuni)
    }

    @Synchronized
    private fun recalculeaza() {
        curenta = citesteAcum(urmarite.map { it.first })
    }

    private fun sesiuniActive(): List<MediaController>? {
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        return try {
            manager.getActiveSessions(numeAscultator)
        } catch (e: SecurityException) {
            Log.w(TAG, "Nu am voie la sesiunile media: ${e.message}")
            null
        }
    }

    private fun citesteAcum(sesiuni: List<MediaController>): Melodie? {
        for (sesiune in sesiuni) {
            val stare = sesiune.playbackState?.state
            if (stare != PlaybackState.STATE_PLAYING) continue
            val date = sesiune.metadata ?: continue
            val titlu = date.getString(MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: continue
            val artist = date.getString(MediaMetadata.METADATA_KEY_ARTIST)
                ?: date.getString(MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            return Melodie(titlu, artist)
        }
        return null
    }

    private companion object {
        const val TAG = "Muzica"
    }
}
