package ro.vix.kittdash.obd

import org.junit.Assert.assertEquals
import org.junit.Test

/**
 * Modul se scoate din turatie si din semnul puterii bateriei. Cifrele de
 * mai jos sunt cele masurate pe masina lui Victor pe 14.11.2026.
 */
class ModHibridTest {

    @Test
    fun `motor oprit si masina in miscare inseamna EV`() {
        assertEquals(ModHibrid.EV, ModHibrid.din(turatie = 0.0, putereKw = 4.2, vitezaKmh = 35.0))
    }

    @Test
    fun `motor pornit inseamna HYBRID`() {
        assertEquals(
            ModHibrid.HYBRID,
            ModHibrid.din(turatie = 1248.0, putereKw = 0.4, vitezaKmh = 50.0),
        )
    }

    @Test
    fun `bateria care primeste curent in mers inseamna REGEN`() {
        assertEquals(
            ModHibrid.REGEN,
            ModHibrid.din(turatie = 0.0, putereKw = -3.38, vitezaKmh = 35.0),
        )
    }

    @Test
    fun `pe loc, motorul care incarca bateria e HYBRID, nu REGEN`() {
        // -11,9 A la 284 V = -3,4 kW: masurat cand a pornit motorul termic ca
        // sa incarce bateria, cu masina pe loc. Asta nu e recuperare.
        assertEquals(
            ModHibrid.HYBRID,
            ModHibrid.din(turatie = 1248.0, putereKw = -3.38, vitezaKmh = 0.0),
        )
    }

    @Test
    fun `regenerarea bate motorul pornit`() {
        val mod = ModHibrid.din(turatie = 1400.0, putereKw = -8.0, vitezaKmh = 60.0)
        assertEquals("franarea cu recuperare se vede si cu motorul pornit", ModHibrid.REGEN, mod)
    }

    @Test
    fun `masina care sta cu sistemul pornit arata READY`() {
        // 1,3 A la 272 V = 0,35 kW - consumul de bord, nu o incarcare.
        assertEquals(
            ModHibrid.READY,
            ModHibrid.din(turatie = 0.0, putereKw = 0.354, vitezaKmh = 0.0),
        )
    }

    @Test
    fun `fara date nu inventam un mod`() {
        assertEquals(
            ModHibrid.NECUNOSCUT,
            ModHibrid.din(turatie = null, putereKw = null, vitezaKmh = 0.0),
        )
    }

    @Test
    fun `citeste modul direct din starea masinii`() {
        val stare = CarState(
            valori = mapOf(
                Marime.TURATIE to Citire(0.0, 1L),
                Marime.HV_PUTERE to Citire(-5.0, 1L),
                Marime.VITEZA to Citire(42.0, 1L),
            ),
        )
        assertEquals(ModHibrid.REGEN, ModHibrid.din(stare))
    }

    @Test
    fun `pe loc motorul care incarca bateria nu e REGEN`() {
        // 18.11.2026, in Lexus: 0 km/h, 1200 rot/min, -3 kW
        assertEquals(ModHibrid.HYBRID, ModHibrid.din(turatie = 1200.0, putereKw = -3.0, vitezaKmh = 0.0))
    }
}
