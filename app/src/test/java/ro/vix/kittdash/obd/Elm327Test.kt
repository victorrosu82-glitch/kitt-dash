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

    private fun decodat(pid: ObdPid, vararg octeti: Int): Map<Marime, Double> =
        pid.marimi.associate { (marime, formula) -> marime to formula(octeti) }

    @Test
    fun `turatia din 1AF8 este 1726 rotatii`() {
        assertEquals(1726.0, decodat(ObdPid.TURATIE, 0x1A, 0xF8)[Marime.TURATIE]!!, 0.01)
    }

    @Test
    fun `viteza este octetul ca atare`() {
        assertEquals(90.0, decodat(ObdPid.VITEZA, 90)[Marime.VITEZA]!!, 0.01)
    }

    @Test
    fun `temperatura lichidului scade 40`() {
        assertEquals(48.0, decodat(ObdPid.TEMP_LICHID, 88)[Marime.TEMP_LICHID]!!, 0.01)
    }

    @Test
    fun `tensiunea modulului e in milivolti`() {
        assertEquals(
            13.9, decodat(ObdPid.TENSIUNE_MODUL, 0x36, 0x4C)[Marime.TENSIUNE_MODUL]!!, 0.001,
        )
    }

    // --- hibrid: octetii adevarati, cititi de pe masina lui Victor -------

    @Test
    fun `bateria HV din 9D este 61 la suta`() {
        assertEquals(61.6, decodat(ObdPid.BATERIE_HV, 0x9D)[Marime.BATERIE_HV]!!, 0.1)
    }

    @Test
    fun `PID-ul 9A da tensiunea curentul si puterea`() {
        // 07 00 44 00 00 0D - raspunsul masinii, stand pe loc, la 22:14.
        val v = decodat(ObdPid.BATERIE_HV_ELECTRIC, 0x07, 0x00, 0x44, 0x00, 0x00, 0x0D)
        // Dr Prius, citind aceeasi masina in aceleasi minute: 274,87 V si
        // "DISCHARGING 1.3 amp".
        assertEquals(272.0, v[Marime.HV_TENSIUNE]!!, 0.01)
        assertEquals(1.3, v[Marime.HV_CURENT]!!, 0.01)
        assertEquals(0.354, v[Marime.HV_PUTERE]!!, 0.001)
        assertEquals(7.0, v[Marime.HV_STARE]!!, 0.01)
    }

    @Test
    fun `curentul negativ inseamna baterie care se incarca`() {
        // 07 00 47 00 FF 89 - clipa in care a pornit motorul termic si a
        // inceput sa incarce bateria.
        val v = decodat(ObdPid.BATERIE_HV_ELECTRIC, 0x07, 0x00, 0x47, 0x00, 0xFF, 0x89)
        assertEquals(284.0, v[Marime.HV_TENSIUNE]!!, 0.01)
        assertEquals(-11.9, v[Marime.HV_CURENT]!!, 0.01)
        assertTrue("puterea trebuie sa fie negativa la incarcare",
            v[Marime.HV_PUTERE]!! < 0.0)
        assertEquals(-3.38, v[Marime.HV_PUTERE]!!, 0.01)
    }

    @Test
    fun `varful de la pornirea motorului e pozitiv`() {
        // 07 00 42 80 00 C6 - demarorul tragand din bateria HV.
        val v = decodat(ObdPid.BATERIE_HV_ELECTRIC, 0x07, 0x00, 0x42, 0x80, 0x00, 0xC6)
        assertEquals(19.8, v[Marime.HV_CURENT]!!, 0.01)
        // Tensiunea cade la 266 V sub sarcina demarorului, de la 277 V.
        assertEquals(266.0, v[Marime.HV_TENSIUNE]!!, 0.01)
        assertEquals(5.27, v[Marime.HV_PUTERE]!!, 0.05)
    }

    // --- raspunsuri pe mai multe cadre ----------------------------------

    @Test
    fun `raspunsul pe mai multe cadre nu inghite cifra cadrului`() {
        // Asa vine de la adaptor. Lipite de-a valma, cifra "1" a cadrului
        // al doilea intra in date si curentul iese 409 A in loc de 1,3 A.
        val o = Elm327.octetiDin("008 0:419A07004580 1:000D0000000000", "419A")!!
        assertEquals(6, o.size)
        assertEquals(listOf(0x07, 0x00, 0x45, 0x80, 0x00, 0x0D), o.toList())
    }

    @Test
    fun `cadrele se decodeaza in valori bune`() {
        val o = Elm327.octetiDin("008 0:419A07004700 1:FF890000000000", "419A")!!
        val v = ObdPid.BATERIE_HV_ELECTRIC.marimi.associate { (m, f) -> m to f(o) }
        assertEquals(284.0, v[Marime.HV_TENSIUNE]!!, 0.01)
        assertEquals(-11.9, v[Marime.HV_CURENT]!!, 0.01)
    }

    @Test
    fun `lungimea anuntata taie umplutura de zerouri`() {
        // Blocul Toyota 2101, cu toate cele patru cadre: 6+7+7+7 = 27 de
        // octeti trimisi, dar numai 0x18 = 24 inseamna ceva. Restul e
        // umplutura cu care adaptorul completeaza ultimul cadru.
        val o = Elm327.octetiDin(
            "018 0:610100654B2D 1:655D00000007AC 2:2C2851AC108522 3:6237EC9D000000",
            "6101",
        )!!
        assertEquals("24 de octeti, din care 2 sunt antetul", 22, o.size)
        // Ultimul octet util e acelasi cu PID-ul standard 5B: 9D = 61,6%.
        assertEquals(0x9D, o[21])
    }

    @Test
    fun `curentul zero al masinii oprite iese exact zero`() {
        // 07 00 47 80 00 00 - cu sistemul hibrid oprit.
        val v = decodat(ObdPid.BATERIE_HV_ELECTRIC, 0x07, 0x00, 0x47, 0x80, 0x00, 0x00)
        assertEquals(0.0, v[Marime.HV_CURENT]!!, 0.001)
        assertEquals(0.0, v[Marime.HV_PUTERE]!!, 0.001)
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

        val viteza = elm.citeste(ObdPid.VITEZA)[Marime.VITEZA]!!
        assertTrue("viteza intre 0 si 200, a iesit $viteza", viteza in 0.0..200.0)

        val turatie = elm.citeste(ObdPid.TURATIE)[Marime.TURATIE]!!
        assertTrue("turatia sub 6000, a iesit $turatie", turatie in 0.0..6000.0)

        val temp = elm.citeste(ObdPid.TEMP_LICHID)[Marime.TEMP_LICHID]!!
        assertTrue("temperatura intre 0 si 120, a iesit $temp", temp in 0.0..120.0)

        legatura.close()
    }

    @Test
    fun `simulatorul da si datele bateriei HV`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()

        val procent = elm.citeste(ObdPid.BATERIE_HV)[Marime.BATERIE_HV]!!
        assertTrue("bateria intre 30 si 85, a iesit $procent", procent in 30.0..85.0)

        val electric = elm.citeste(ObdPid.BATERIE_HV_ELECTRIC)
        val tensiune = electric[Marime.HV_TENSIUNE]!!
        val putere = electric[Marime.HV_PUTERE]!!
        assertTrue("tensiunea HV intre 240 si 300, a iesit $tensiune", tensiune in 240.0..300.0)
        // Bateria masinii da cel mult ~22 kW si primeste cel mult ~24 kW.
        assertTrue("puterea intre -25 si 25 kW, a iesit $putere", putere in -25.0..25.0)

        legatura.close()
    }

    @Test
    fun `PID-ul care lipseste da null`() {
        val legatura = SimulatorObdLink(intarziereMs = 0)
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()
        assertTrue(elm.citeste(ObdPid.NIVEL_COMBUSTIBIL).isEmpty())
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
    fun `simulatorul se misca in timp`() {
        var ceas = 0L
        val legatura = SimulatorObdLink(intarziereMs = 0, ceas = { ceas })
        legatura.deschide()
        val elm = Elm327(legatura)
        elm.initializeaza()

        val citiri = mutableListOf<Double>()
        repeat(40) {
            ceas += 1_000
            elm.citeste(ObdPid.VITEZA)[Marime.VITEZA]?.let { citiri.add(it) }
        }
        assertTrue("viteza trebuie sa se schimbe, nu sa stea", citiri.distinct().size > 3)
        legatura.close()
    }
}
