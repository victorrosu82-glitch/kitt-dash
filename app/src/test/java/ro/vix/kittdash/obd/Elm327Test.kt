package ro.vix.kittdash.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/**
 * Teste pe decodarea raspunsurilor si pe dialogul cu adaptorul, folosind
 * simulatorul. Ruleaza pe PC, fara telefon si fara masina.
 */
class Elm327Test {

    // --- decodarea raspunsului brut -----------------------------------

    @Test
    fun `scoate octetii dintr-un raspuns simplu`() {
        val o = Elm327.octetiDin("410D1F", "410D")
        assertEquals(1, o!!.size)
        assertEquals(0x1F, o[0])
    }

    @Test
    fun `merge si cu spatii si cu sfarsituri de linie`() {
        val o = Elm327.octetiDin("41 0C 1A F8\r\r", "410C")
        assertEquals(listOf(0x1A, 0xF8), o!!.toList())
    }

    @Test
    fun `sare peste ecoul comenzii`() {
        val o = Elm327.octetiDin("010D\r410D3C\r\r", "410D")
        assertEquals(0x3C, o!![0])
    }

    @Test
    fun `NO DATA da null`() {
        assertNull(Elm327.octetiDin("NO DATA\r\r", "412F"))
    }

    @Test
    fun `antet lipsa da null`() {
        assertNull(Elm327.octetiDin("41051E", "410D"))
    }

    // --- formulele -----------------------------------------------------

    @Test
    fun `turatia din 1AF8 este 1726 rotatii`() {
        assertEquals(1726.0, ObdPid.TURATIE.decodeaza(intArrayOf(0x1A, 0xF8)), 0.01)
    }

    @Test
    fun `viteza este octetul ca atare`() {
        assertEquals(90.0, ObdPid.VITEZA.decodeaza(intArrayOf(90)), 0.01)
    }

    @Test
    fun `temperatura lichidului scade 40`() {
        assertEquals(48.0, ObdPid.TEMP_LICHID.decodeaza(intArrayOf(88)), 0.01)
    }

    @Test
    fun `tensiunea modulului e in milivolti`() {
        assertEquals(13.9, ObdPid.TENSIUNE_MODUL.decodeaza(intArrayOf(0x36, 0x4C)), 0.001)
    }

    @Test
    fun `cererea si antetul sunt formate corect`() {
        assertEquals("010D", ObdPid.VITEZA.cerere)
        assertEquals("410D", ObdPid.VITEZA.antetRaspuns)
        assertEquals("012F", ObdPid.NIVEL_COMBUSTIBIL.cerere)
    }

    // --- dialogul cu adaptorul, pe simulator ---------------------------

    @Test
    fun `initializarea reuseste pe simulator`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()
        assertTrue("versiunea trebuie citita", elm.versiune!!.contains("ELM327"))
        assertTrue("protocolul trebuie citit", elm.protocol!!.contains("ISO"))
        legatura.close()
    }

    @Test
    fun `citeste valori plauzibile de la simulator`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()

        val viteza = elm.citeste(ObdPid.VITEZA)!!
        assertTrue("viteza intre 0 si 200, a ieșit $viteza", viteza in 0.0..200.0)

        val turatie = elm.citeste(ObdPid.TURATIE)!!
        assertTrue("turatia sub 6000, a ieșit $turatie", turatie in 0.0..6000.0)

        val temp = elm.citeste(ObdPid.TEMP_LICHID)!!
        assertTrue("temperatura intre 0 si 120, a ieșit $temp", temp in 0.0..120.0)

        legatura.close()
    }

    @Test
    fun `PID-ul care lipseste da null`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()
        assertNull(elm.citeste(ObdPid.NIVEL_COMBUSTIBIL))
        legatura.close()
    }

    @Test
    fun `simulatorul anunta PID-urile standard`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()
        val suportate = elm.pidSuportate()
        assertTrue("viteza trebuie anuntata", ObdPid.VITEZA.pid in suportate)
        assertTrue("turatia trebuie anuntata", ObdPid.TURATIE.pid in suportate)
        legatura.close()
    }

    @Test
    fun `simulatorul se mişca in timp`() {
        var ceas = 0L
        val legatura = SimulatorObdLink(intarziereMs = 0, ceas = { ceas })
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()

        val citiri = mutableListOf<Double>()
        repeat(40) {
            ceas += 1_000
            elm.citeste(ObdPid.VITEZA)?.let { citiri.add(it) }
        }
        assertTrue("viteza trebuie sa se schimbe, nu sa stea", citiri.distinct().size > 3)
        legatura.close()
    }
}
