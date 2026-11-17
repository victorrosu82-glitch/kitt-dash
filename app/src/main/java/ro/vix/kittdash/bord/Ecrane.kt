package ro.vix.kittdash.bord

import android.graphics.Canvas
import ro.vix.kittdash.navigatie.InstructiuneNav
import kotlin.math.min

/**
 * Alege ce ecran se deseneaza: bordul obisnuit sau unul din cele noi.
 *
 * Bordul obisnuit isi face singur asezarea (si panoul Waze). Ecranele noi
 * sunt desenate pe caietul de 1190 x 460 al Lexusului si aici doar se
 * potrivesc pe panza primita, pastrandu-si proportia.
 */
class Ecrane {

    private val bord = BordKitt()
    private val pensula = Pensula()
    private val viteza = EcranViteza(pensula)
    private val energie = EcranEnergie(pensula)
    private val consola = EcranConsola(pensula)

    fun deseneaza(
        canvas: Canvas,
        latime: Float,
        inaltime: Float,
        stare: StareBord,
        nav: InstructiuneNav?,
        fel: FelEcran,
    ) {
        if (fel == FelEcran.BORD) {
            bord.deseneaza(canvas, latime, inaltime, stare, nav)
            return
        }
        canvas.drawColor(Pensula.FUNDAL)
        if (latime <= 0f || inaltime <= 0f) return

        val scara = min(latime / Pensula.LATIME, inaltime / Pensula.INALTIME)
        val salvat = canvas.save()
        canvas.translate(
            (latime - Pensula.LATIME * scara) / 2f,
            (inaltime - Pensula.INALTIME * scara) / 2f,
        )
        canvas.scale(scara, scara)
        when (fel) {
            FelEcran.VITEZA -> viteza.deseneaza(canvas, stare, nav)
            FelEcran.ENERGIE -> energie.deseneaza(canvas, stare)
            FelEcran.CONSOLA -> consola.deseneaza(canvas, stare)
            FelEcran.BORD -> Unit
        }
        canvas.restoreToCount(salvat)

        if (stare.noapte) canvas.drawColor(BordKitt.PERDEA_NOAPTE)
    }
}
