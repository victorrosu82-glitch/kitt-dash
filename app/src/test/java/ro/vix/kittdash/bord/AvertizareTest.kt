package ro.vix.kittdash.bord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Citire
import ro.vix.kittdash.obd.Marime
import ro.vix.kittdash.obd.StareConexiune

class AvertizareTest {

    private fun masina(vararg valori: Pair<Marime, Double>, laMs: Long = 1_000L) = CarState(
        conexiune = StareConexiune.CONECTAT,
        valori = valori.associate { it.first to Citire(it.second, laMs) },
    )

    @Test
    fun `temperaturile obisnuite nu sperie pe nimeni`() {
        val stare = masina(
            Marime.TEMP_LICHID to 88.0,
            Marime.BATERIE_HV to 62.0,
            Marime.TENSIUNE_MODUL to 14.3,
        )
        assertTrue(Avertizare.din(stare, 1_000L).isEmpty())
    }

    @Test
    fun `motorul fierbinte se anunta`() {
        val stare = masina(Marime.TEMP_LICHID to 108.0)
        assertEquals(listOf(Avertizare.MOTOR_FIERBINTE), Avertizare.din(stare, 1_000L))
    }

    @Test
    fun `bateria HV scazuta se anunta`() {
        val stare = masina(Marime.BATERIE_HV to 30.0)
        assertEquals(listOf(Avertizare.BATERIE_HV_SCAZUTA), Avertizare.din(stare, 1_000L))
    }

    @Test
    fun `tensiunea mica de bord se anunta`() {
        val stare = masina(Marime.TENSIUNE_MODUL to 11.4)
        assertEquals(listOf(Avertizare.TENSIUNE_BORD_MICA), Avertizare.din(stare, 1_000L))
    }

    @Test
    fun `cele grave trec inaintea celorlalte`() {
        val stare = masina(
            Marime.TEMP_LICHID to 110.0,
            Marime.BATERIE_HV to 28.0,
        )
        val gasite = Avertizare.din(stare, 1_000L)
        assertEquals("motorul fierbinte e mai grav", Avertizare.MOTOR_FIERBINTE, gasite.first())
        assertTrue(gasite.contains(Avertizare.BATERIE_HV_SCAZUTA))
    }

    @Test
    fun `datele vechi se vad ca atare`() {
        val stare = masina(Marime.VITEZA to 50.0, laMs = 1_000L)
        // Dupa 15 secunde fara o citire noua, adaptorul a amutit.
        val gasite = Avertizare.din(stare, 16_000L)
        assertTrue(gasite.contains(Avertizare.ADAPTOR_TACE))
    }

    @Test
    fun `datele proaspete nu sunt vechi`() {
        val stare = masina(Marime.VITEZA to 50.0, laMs = 1_000L)
        assertTrue(Avertizare.din(stare, 3_000L).isEmpty())
    }

    @Test
    fun `fara legatura nu avertizam nimic`() {
        // Valorile raman pe ecran dupa deconectare, dar sunt de ieri: n-are
        // rost sa speriem pe cineva cu ele.
        val stare = CarState(
            conexiune = StareConexiune.DECONECTAT,
            valori = mapOf(Marime.TEMP_LICHID to Citire(130.0, 1_000L)),
        )
        assertTrue(Avertizare.din(stare, 2_000L).isEmpty())
    }

    @Test
    fun `pragurile sunt cele potrivite masinii`() {
        // Un NX 300h merge la 85-90 de grade; 105 e deja cald rau.
        assertTrue(Avertizare.din(masina(Marime.TEMP_LICHID to 95.0), 1_000L).isEmpty())
        assertTrue(Avertizare.din(masina(Marime.TEMP_LICHID to 105.0), 1_000L).isNotEmpty())
        // Hibridul tine bateria intre 40 si 80 la suta.
        assertTrue(Avertizare.din(masina(Marime.BATERIE_HV to 40.0), 1_000L).isEmpty())
        assertTrue(Avertizare.din(masina(Marime.BATERIE_HV to 35.0), 1_000L).isNotEmpty())
    }
}
