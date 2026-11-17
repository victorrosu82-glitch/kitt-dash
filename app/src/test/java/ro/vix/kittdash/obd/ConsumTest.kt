package ro.vix.kittdash.obd

import org.junit.Assert.assertEquals
import org.junit.Test

class ConsumTest {
    @Test
    fun treiGramePeSecundaInseamnaCamUnLitruPeOra() {
        // 3 g/s * 3600 / 14,7 / 745 = 0,986 l/h
        assertEquals(0.986, Consum.litriPeOra(3.0), 0.001)
    }

    @Test
    fun motorOpritNuConsuma() {
        assertEquals(0.0, Consum.litriPeOra(0.0), 0.0)
    }
}
