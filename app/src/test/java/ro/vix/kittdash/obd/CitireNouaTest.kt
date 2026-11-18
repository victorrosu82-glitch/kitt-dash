package ro.vix.kittdash.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

/** Intrebarile cu mai multe PID-uri, blocurile bateriei, drumul si proba. */
class CitireNouaTest {

    private class Fals(private val raspunsuri: Map<String, String>) : ObdLink {
        override val nume = "fals"
        val trimise = mutableListOf<String>()
        override fun deschide() {}
        override fun close() {}
        override fun comanda(comanda: String, timeoutMs: Long): String {
            trimise += comanda
            return raspunsuri[comanda] ?: if (comanda.startsWith("AT")) "OK" else "NO DATA"
        }
    }

    @Test
    fun `intrebarea cu trei PID-uri da trei valori`() {
        val elm = Elm327(Fals(mapOf("010D0C11" to "410D2A0C1A2011FF\r\r")))
        val r = elm.citesteGrup(listOf(ObdPid.VITEZA, ObdPid.TURATIE, ObdPid.POZITIE_CLAPETA))!!
        assertEquals(42.0, r[ObdPid.VITEZA]!![Marime.VITEZA]!!, 0.0)
        assertEquals(1672.0, r[ObdPid.TURATIE]!![Marime.TURATIE]!!, 0.0)
        assertEquals(100.0, r[ObdPid.POZITIE_CLAPETA]!![Marime.CLAPETA]!!, 0.0)
    }

    @Test
    fun `intrebarea comuna neinteleasa da null`() {
        val elm = Elm327(Fals(mapOf("010D0C" to "NO DATA\r\r")))
        assertNull(elm.citesteGrup(listOf(ObdPid.VITEZA, ObdPid.TURATIE)))
    }

    @Test
    fun `simulatorul raspunde la intrebarea comuna`() {
        val sim = SimulatorObdLink()
        sim.deschide()
        val elm = Elm327(sim)
        elm.initializeaza()
        val r = elm.citesteGrup(listOf(ObdPid.VITEZA, ObdPid.TURATIE, ObdPid.SARCINA_MOTOR))
        assertNotNull(r)
        assertEquals(3, r!!.size)
    }

    @Test
    fun `blocurile bateriei din simulator dau cele 17 tensiuni`() {
        val sim = SimulatorObdLink()
        sim.deschide()
        val elm = Elm327(sim)
        elm.initializeaza()
        val v = DateHibrid.tensiuni(elm.citesteBloc("7E2", 0x81))
        assertEquals(17, v.size)
        // suma ~ tensiunea bateriei citita in aceeasi explorare (~296 V)
        assertEquals(295.8, v.sum().toDouble(), 1.0)
        val t = DateHibrid.temperaturi(elm.citesteBloc("7E2", 0x87))
        assertEquals(7, t.size)
        assertEquals(18.5, t[0].toDouble(), 0.1)
        assertEquals(36.5, DateHibrid.rezervor(elm.citesteBloc("7C0", 0x29))!!.toDouble(), 0.01)
    }

    @Test
    fun `cel mai slab si cel mai tare bloc`() {
        val h = DateHibrid(tensiuni = listOf(17.3f, 17.1f, 17.4f))
        assertEquals(1, h.celMaiSlab)
        assertEquals(2, h.celMaiTare)
        assertEquals(0.3f, h.diferentaV!!, 0.001f)
    }

    private fun stare(viteza: Double, turatie: Double, kw: Double, aer: Double) = CarState(
        valori = mapOf(
            Marime.VITEZA to Citire(viteza, 0), Marime.TURATIE to Citire(turatie, 0),
            Marime.HV_PUTERE to Citire(kw, 0), Marime.DEBIT_AER to Citire(aer, 0),
        ),
    )

    @Test
    fun `drumul aduna km, timpul pe baterie, recuperarea si consumul`() {
        var d = Drum()
        // 60 s la 60 km/h pe baterie, franand cu -10 kW
        repeat(60) { d = d.cu(stare(60.0, 0.0, -10.0, 0.0), 1.0) }
        // 60 s la 60 km/h cu motorul, 6 g/s aer
        repeat(60) { d = d.cu(stare(60.0, 1500.0, 5.0, 6.0), 1.0) }
        assertEquals(2.0, d.km, 0.001)
        assertEquals(50.0, d.procentBaterie!!, 0.001)
        assertEquals(10.0 * 60 / 3600, d.kWhRecuperat, 0.0001)
        // 6 g/s = 1,97 l/h; un minut = 0,0329 l; la 2 km = 1,64 l/100
        assertEquals(1.64, d.litriLa100!!, 0.01)
    }

    @Test
    fun `o pauza lunga nu se socoteste in drum`() {
        val d = Drum().cu(stare(60.0, 0.0, 0.0, 0.0), 30.0)
        assertEquals(0.0, d.km, 0.0)
    }

    @Test
    fun `proba scoate octetii din raspunsul cu antete si cadre`() {
        val r = "7EA 10 10 61 87 44 8A 4E F5 7EA 21 50 5C 4C A1 4C 19 4F 7EA 22 9C 4E 51 00 00 00 00"
        val o = ProbaBaterie.octeti(r, "6187")!!
        assertEquals(14, o.size)
        assertEquals(0x44, o[0])
        assertEquals(0x51, o[13])
    }

    @Test
    fun `proba trimite numai cereri de citire si scrie valorile socotite`() {
        val fals = Fals(
            mapOf(
                "2181" to "7EA 10 28 61 81 37 BE 37 BE 7EA 21 37 6C 37 7C 37 A5 37 7EA 22 A5 37 7C 37 BE 37 F7 " +
                    "7EA 23 38 20 37 A5 37 95 37 7EA 24 95 37 A5 37 6C 37 7C 7EA 25 37 BE AE 87 0B 86 00",
                "2129" to "7C8 03 61 29 49",
            ),
        )
        var t = 0L
        val raport = ProbaBaterie(fals, ceas = { t }, asteapta = { t += it }, durataMs = 4_000L).ruleaza()
        assertTrue(fals.trimise.all { ExploratorMasina.esteDoarCitire(it) })
        assertTrue(raport, raport.contains("= tensiuni (V): 17.4"))
        assertTrue(raport, raport.contains("= rezervor (A/2): 36.5 l"))
        assertTrue(raport.contains("--- turul 2"))
    }
}
