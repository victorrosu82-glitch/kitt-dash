package ro.vix.kittdash.muzica

import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.media.session.MediaSessionManager
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
     */
    fun melodia(): Melodie? {
        if (!areVoie()) return null
        val manager = context.getSystemService(MediaSessionManager::class.java) ?: return null
        val sesiuni = try {
            manager.getActiveSessions(numeAscultator)
        } catch (e: SecurityException) {
            Log.w(TAG, "Nu am voie la sesiunile media: ${e.message}")
            return null
        }
        for (sesiune in sesiuni) {
            val stare = sesiune.playbackState?.state
            if (stare != PlaybackState.STATE_PLAYING) continue
            val date = sesiune.metadata ?: continue
            val titlu = date.getString(android.media.MediaMetadata.METADATA_KEY_TITLE)
                ?.takeIf { it.isNotBlank() } ?: continue
            val artist = date.getString(android.media.MediaMetadata.METADATA_KEY_ARTIST)
                ?: date.getString(android.media.MediaMetadata.METADATA_KEY_ALBUM_ARTIST)
            return Melodie(titlu, artist)
        }
        return null
    }

    private companion object {
        const val TAG = "Muzica"
    }
}
