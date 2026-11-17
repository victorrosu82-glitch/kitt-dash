package ro.vix.kittdash.bord

import org.junit.Assert.assertEquals
import org.junit.Test

class FelEcranTest {
    @Test
    fun butonulParcurgeToateEcraneleSiOIaDeLaCapat() {
        assertEquals(FelEcran.VITEZA, FelEcran.BORD.urmatorul())
        assertEquals(FelEcran.ENERGIE, FelEcran.VITEZA.urmatorul())
        assertEquals(FelEcran.CONSOLA, FelEcran.ENERGIE.urmatorul())
        assertEquals(FelEcran.BORD, FelEcran.CONSOLA.urmatorul())
    }

    @Test
    fun unNumeNecunoscutDuceLaBord() {
        assertEquals(FelEcran.BORD, FelEcran.dupaNume(null))
        assertEquals(FelEcran.BORD, FelEcran.dupaNume("ALTCEVA"))
        assertEquals(FelEcran.ENERGIE, FelEcran.dupaNume("ENERGIE"))
    }
}
