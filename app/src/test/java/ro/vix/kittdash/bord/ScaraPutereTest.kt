package ro.vix.kittdash.bord

import org.junit.Assert.assertEquals
import org.junit.Test

class ScaraPutereTest {
    @Test
    fun puterileMiciSeVad() {
        // 3 kW, cat se vede de obicei in oras, umple o treime din jumatate de bara
        assertEquals(0.346f, ScaraPutere.fractie(3f), 0.001f)
        assertEquals(0.346f, ScaraPutere.fractie(-3f), 0.001f)
    }

    @Test
    fun capatulEStrans() {
        assertEquals(1f, ScaraPutere.fractie(25f), 0f)
        assertEquals(1f, ScaraPutere.fractie(40f), 0f)
        assertEquals(0f, ScaraPutere.fractie(0f), 0f)
    }
}
