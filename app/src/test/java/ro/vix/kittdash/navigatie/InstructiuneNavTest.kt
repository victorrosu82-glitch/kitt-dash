package ro.vix.kittdash.navigatie

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Test

/**
 * Distanta vine ca text scris de Waze ("70 m"). Daca o intelegem gresit,
 * bara de apropiere minte si sageata clipeste cand nu trebuie.
 */
class InstructiuneNavTest {

    @Test
    fun `metri si kilometri, cu virgula sau punct`() {
        assertEquals(70f, Distanta.inMetri("70 m")!!, 0.01f)
        assertEquals(1200f, Distanta.inMetri("1,2 km")!!, 0.01f)
        assertEquals(1200f, Distanta.inMetri("1.2 km")!!, 0.01f)
        assertEquals(300f, Distanta.inMetri("300m")!!, 0.01f)
    }

    @Test
    fun `text fara distanta nu e citit ca zero`() {
        assertNull(Distanta.inMetri("Acum"))
        assertNull(Distanta.inMetri("Str. Balteni"))
    }

    @Test
    fun `cifra si unitatea se despart pentru desen`() {
        assertEquals("70" to "M", Distanta.desparte("70 m"))
        assertEquals("1,2" to "KM", Distanta.desparte("1,2 km"))
        assertEquals("Acum" to "", Distanta.desparte("Acum"))
    }

    @Test
    fun `notificarea fara distanta nu e manevra`() {
        assertEquals("70 m" to "Str. A", Distanta.alege("70 m", "Str. A"))
        assertEquals("1,2 km" to "Str. A", Distanta.alege("Str. A", "1,2 km"))
        assertNull(Distanta.alege("Strada B", null))
        assertNull(Distanta.alege(null, null))
    }

    private fun nav(distanta: String, strada: String?, start: Float?) =
        InstructiuneNav("WAZE", distanta, strada, null, start)

    @Test
    fun `apropierea creste spre manevra`() {
        assertEquals(0f, nav("300 m", "A", 300f).apropiere!!, 0.001f)
        assertEquals(0.5f, nav("150 m", "A", 300f).apropiere!!, 0.001f)
        assertEquals(1f, nav("0 m", "A", 300f).apropiere!!, 0.001f)
    }

    @Test
    fun `manevra noua o ia de la capat, aceeasi manevra isi pastreaza inceputul`() {
        val veche = nav("120 m", "Str. A", 300f)
        assertEquals(300f, InstructiuneNav.inceput(veche, "Str. A", 90f)!!, 0.01f)
        assertEquals(800f, InstructiuneNav.inceput(veche, "Str. B", 800f)!!, 0.01f)
        // traseu recalculat: distanta a crescut pe aceeasi strada
        assertEquals(500f, InstructiuneNav.inceput(veche, "Str. A", 500f)!!, 0.01f)
        assertEquals(400f, InstructiuneNav.inceput(null, "Str. A", 400f)!!, 0.01f)
    }
}
