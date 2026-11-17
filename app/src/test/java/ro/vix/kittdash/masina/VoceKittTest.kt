package ro.vix.kittdash.masina

import org.junit.Assert.assertEquals
import org.junit.Test
import ro.vix.kittdash.navigatie.InstructiuneNav
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Citire
import ro.vix.kittdash.obd.Marime
import ro.vix.kittdash.obd.StareConexiune

class VoceKittTest {

    private fun legat(vararg v: Pair<Marime, Double>) = CarState(
        conexiune = StareConexiune.CONECTAT,
        valori = v.associate { (m, x) -> m to Citire(x, 0L) },
    )

    @Test
    fun faraMasinaSpuneCaNuELegat() {
        assertEquals(
            "Nu sunt legat la mașină. Apasă Conectează.",
            VoceKitt.rezumat(CarState(), null),
        )
    }

    @Test
    fun spuneVitezaBateriaSiMotorul() {
        val stare = legat(
            Marime.VITEZA to 52.4,
            Marime.BATERIE_HV to 61.6,
            Marime.TEMP_LICHID to 85.0,
        )
        assertEquals(
            "Viteza 52 de kilometri pe oră. Bateria hibridă 62 la sută. Motorul are 85 de grade.",
            VoceKitt.rezumat(stare, null),
        )
    }

    @Test
    fun citesteDistantaManevreiInCuvinte() {
        val nav = InstructiuneNav("WAZE", "1,2 km", "Str. Exemplu", null)
        assertEquals(
            "Viteza 30 de kilometri pe oră. Următoarea manevră peste 1,2 kilometri, pe Str. Exemplu.",
            VoceKitt.rezumat(legat(Marime.VITEZA to 30.0), nav),
        )
        val aproape = InstructiuneNav("WAZE", "70 m", null, null)
        assertEquals(
            "Următoarea manevră peste 70 metri.",
            VoceKitt.rezumat(legat(), aproape),
        )
    }
}
