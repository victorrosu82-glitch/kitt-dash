package ro.vix.kittdash.bord

import androidx.compose.foundation.Canvas
import androidx.compose.foundation.isSystemInDarkTheme
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberUpdatedState
import androidx.compose.runtime.setValue
import androidx.compose.runtime.withFrameNanos
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.drawscope.drawIntoCanvas
import androidx.compose.ui.graphics.nativeCanvas
import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.content.pm.ActivityInfo
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalView
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsCompat
import androidx.core.view.WindowInsetsControllerCompat
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.LaunchedEffect
import ro.vix.kittdash.muzica.Muzica
import ro.vix.kittdash.obd.CarState

/**
 * Bordul KITT pe ecranul telefonului.
 *
 * Deseneaza la fiecare cadru al ecranului, nu la fiecare citire din masina:
 * citirile vin de doua ori pe secunda, iar intre ele [Netezitor] duce
 * valorile lin spre tinta. Asa acul se misca frumos chiar daca adaptorul e
 * incet.
 *
 * Desenatorul e acelasi care va desena si pe ecranul masinii - aici doar ii
 * dam panza din Compose.
 */
/** Scoate activitatea din context, ca sa-i putem cere ecranul pe lat. */
private tailrec fun Context.activitate(): Activity? = when (this) {
    is Activity -> this
    is ContextWrapper -> baseContext.activitate()
    else -> null
}

@Composable
fun EcranBord(
    masina: CarState,
    modifier: Modifier = Modifier,
    laAtingere: (() -> Unit)? = null,
) {
    val context = LocalContext.current
    val bord = remember { BordKitt() }
    val muzica = remember { Muzica(context) }
    var stare by remember { mutableStateOf(StareBord()) }

    // Bucla de desen porneste o singura data si tine pana la iesire, deci
    // trebuie sa citeasca de fiecare data ULTIMA stare a masinii si ULTIMA
    // tema. Fara asta ramane agatata de ce era in clipa pornirii.
    val masinaAcum by rememberUpdatedState(masina)
    val noapteAcum by rememberUpdatedState(isSystemInDarkTheme())

    val netezitor = remember {
        Netezitor(
            melodiaAcum = { muzica.melodia()?.text },
            esteNoapte = { noapteAcum },
        )
    }

    // Un cadru nou la fiecare cadru al ecranului.
    LaunchedEffect(Unit) {
        while (true) {
            withFrameNanos { }
            stare = netezitor.urmatoareaStare(masinaAcum)
        }
    }

    // Bordul e facut pe lat, ca ecranul masinii. Pe telefon il intoarcem,
    // altfel ramane o fasie in mijlocul ecranului. Si dam deoparte barele
    // Android: ceasul telefonului acoperea tocmai numele masinii, iar
    // butoanele de navigare stateau peste bara de putere.
    val activitate = context.activitate()
    val vedere = LocalView.current
    DisposableEffect(activitate) {
        val inainte = activitate?.requestedOrientation
        activitate?.requestedOrientation = ActivityInfo.SCREEN_ORIENTATION_SENSOR_LANDSCAPE

        val fereastra = activitate?.window
        val carma = fereastra?.let { WindowCompat.getInsetsController(it, vedere) }
        carma?.apply {
            systemBarsBehavior =
                WindowInsetsControllerCompat.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            hide(WindowInsetsCompat.Type.systemBars())
        }

        onDispose {
            if (inainte != null) activitate.requestedOrientation = inainte
            carma?.show(WindowInsetsCompat.Type.systemBars())
        }
    }

    // Cat e bordul pe ecran, ecranul nu se stinge: stai la volan, nu atingi
    // telefonul, si n-are rost sa se stinga tocmai cand te uiti la el.
    DisposableEffect(Unit) {
        vedere.keepScreenOn = true
        onDispose { vedere.keepScreenOn = false }
    }

    Box(
        modifier = modifier
            .fillMaxSize()
            .then(if (laAtingere != null) Modifier.clickable { laAtingere() } else Modifier),
    ) {
        Canvas(modifier = Modifier.fillMaxSize()) {
            drawIntoCanvas { panza ->
                bord.deseneaza(panza.nativeCanvas, size.width, size.height, stare)
            }
        }
    }
}
