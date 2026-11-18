package ro.vix.kittdash.bord

import android.graphics.Bitmap
import android.graphics.Canvas
import org.junit.Assert.assertTrue
import org.junit.Test
import org.junit.runner.RunWith
import org.robolectric.RobolectricTestRunner
import org.robolectric.annotation.Config
import org.robolectric.annotation.GraphicsMode
import ro.vix.kittdash.navigatie.InstructiuneNav
import ro.vix.kittdash.obd.ModHibrid
import java.io.File

/**
 * Deseneaza fiecare ecran nou pe o panza cat a Lexusului si il scrie ca
 * imagine in `app/build/ecrane/`, ca sa-l putem privi fara masina.
 * Verifica si ca desenul nu crapa cu valori lipsa.
 */
@RunWith(RobolectricTestRunner::class)
@GraphicsMode(GraphicsMode.Mode.NATIVE)
@Config(sdk = [34])
class DesenEcraneTest {

    private val dosar = File("build/ecrane").apply { mkdirs() }

    private fun stare(
        viteza: Float, rpm: Float, kw: Float, soc: Float, mod: ModHibrid,
    ) = StareBord(
        vitezaKmh = viteza, turatie = rpm, putereKw = kw, socProcent = soc,
        tempLichid = 88f, tempAer = 18f, tempExterioara = 7f, tensiune12 = 14.1f,
        hvTensiune = 243f, hvCurent = kw * 1000f / 243f, clapeta = 31f, sarcina = 38f,
        tempCatalizator = 540f, consumLh = 3.4f, mod = mod, areLegatura = true,
        scanerFaza = 21f, timpS = 1.3f,
    )

    private fun deseneaza(nume: String, fel: FelEcran, s: StareBord, nav: InstructiuneNav? = null) {
        val imagine = Bitmap.createBitmap(1190, 436, Bitmap.Config.ARGB_8888)
        Ecrane().deseneaza(Canvas(imagine), 1190f, 436f, s, nav, fel)
        File(dosar, "$nume.png").outputStream().use { imagine.compress(Bitmap.CompressFormat.PNG, 100, it) }
        assertTrue(File(dosar, "$nume.png").length() > 0)
    }

    @Test
    fun viteza() {
        deseneaza("viteza", FelEcran.VITEZA, stare(87f, 2100f, 14f, 61f, ModHibrid.HYBRID),
            InstructiuneNav("WAZE", "300 m", "Calea Exemplu", null))
    }

    @Test
    fun energie() {
        deseneaza("energie-accel", FelEcran.ENERGIE, stare(74f, 2100f, 18f, 58f, ModHibrid.HYBRID))
        deseneaza("energie-ev", FelEcran.ENERGIE, stare(28f, 0f, 9f, 61f, ModHibrid.EV))
        deseneaza("energie-incarca", FelEcran.ENERGIE, stare(62f, 1600f, -6f, 47f, ModHibrid.HYBRID))
        deseneaza("energie-regen", FelEcran.ENERGIE, stare(46f, 0f, -21f, 63f, ModHibrid.REGEN))
        // ca in poza din 18.11.2026: pe loc, motorul incarca bateria
        deseneaza("energie-pe-loc", FelEcran.ENERGIE, stare(0f, 1200f, -3f, 52f, ModHibrid.HYBRID))
    }

    @Test
    fun consola() {
        deseneaza("consola", FelEcran.CONSOLA, stare(74f, 2100f, 18f, 58f, ModHibrid.HYBRID))
    }

    @Test
    fun faraMasinaNuCrapa() {
        for (fel in FelEcran.entries) deseneaza("gol-${fel.name.lowercase()}", fel, StareBord())
    }
}
