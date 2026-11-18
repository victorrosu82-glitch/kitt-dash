package ro.vix.kittdash.bord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Citire
import ro.vix.kittdash.obd.Marime
import ro.vix.kittdash.obd.ModHibrid
import ro.vix.kittdash.obd.StareConexiune

/**
 * Deciziile bordului se pot gresi fara sa se vada pe ecran - de pilda bara
 * de recuperare ramanand goala la franare. De aceea se probeaza aici.
 */
class LogicaBordTest {

    // --- ceasul de viteza ----------------------------------------------

    @Test
    fun `masina oprita aprinde doar prima bara`() {
        val aprinse = LogicaBord.bareAprinse(0f)
        assertEquals(0, aprinse)
        assertEquals(LogicaBord.CuloareBara.VARF, LogicaBord.culoareBara(0, aprinse, 0f))
        assertEquals(LogicaBord.CuloareBara.STINSA, LogicaBord.culoareBara(1, aprinse, 0f))
        assertEquals(LogicaBord.CuloareBara.STINSA, LogicaBord.culoareBara(53, aprinse, 0f))
    }

    @Test
    fun `la 180 km pe ora ceasul e plin`() {
        assertEquals(53, LogicaBord.bareAprinse(180f))
    }

    @Test
    fun `peste capatul ceasului nu se trece`() {
        assertEquals(53, LogicaBord.bareAprinse(240f))
    }

    @Test
    fun `la 90 km pe ora ceasul e pe jumatate`() {
        assertEquals(27, LogicaBord.bareAprinse(90f))
    }

    @Test
    fun `varful ceasului e mai deschis decat restul`() {
        val aprinse = LogicaBord.bareAprinse(60f)
        assertEquals(LogicaBord.CuloareBara.VARF, LogicaBord.culoareBara(aprinse, aprinse, 60f))
        assertEquals(LogicaBord.CuloareBara.ROSIE, LogicaBord.culoareBara(aprinse - 1, aprinse, 60f))
    }

    @Test
    fun `ultimele bare ale ceasului sunt portocalii`() {
        val aprinse = LogicaBord.bareAprinse(180f)
        assertEquals(LogicaBord.CuloareBara.PORTOCALIE, LogicaBord.culoareBara(50, aprinse, 180f))
        assertEquals(LogicaBord.CuloareBara.ROSIE, LogicaBord.culoareBara(40, aprinse, 180f))
    }

    // --- bateria HV -----------------------------------------------------

    @Test
    fun `bateria la 62 la suta umple opt trepte si jumatate`() {
        val trepte = LogicaBord.trepteBaterie(62f)
        assertEquals(8.68f, trepte, 0.01f)
        assertEquals(LogicaBord.CuloareBaterie.AMBRA, LogicaBord.culoareBaterie(8, trepte))
        assertEquals(LogicaBord.CuloareBaterie.STINSA, LogicaBord.culoareBaterie(9, trepte))
    }

    @Test
    fun `primele trei trepte ale bateriei sunt rosii`() {
        val trepte = LogicaBord.trepteBaterie(62f)
        assertEquals(LogicaBord.CuloareBaterie.ROSIE, LogicaBord.culoareBaterie(0, trepte))
        assertEquals(LogicaBord.CuloareBaterie.ROSIE, LogicaBord.culoareBaterie(2, trepte))
        assertEquals(LogicaBord.CuloareBaterie.AMBRA, LogicaBord.culoareBaterie(3, trepte))
    }

    @Test
    fun `fara date despre baterie nu se aprinde nicio treapta`() {
        val trepte = LogicaBord.trepteBaterie(null)
        assertEquals(LogicaBord.CuloareBaterie.STINSA, LogicaBord.culoareBaterie(0, trepte))
    }

    // --- puterea in doua sensuri ----------------------------------------

    @Test
    fun `tractiunea urca si recuperarea sta stinsa`() {
        val sus = LogicaBord.trepteSus(11f)
        val jos = LogicaBord.trepteJos(11f)
        assertEquals(3.5f, sus, 0.01f)
        assertEquals(0f, jos, 0.001f)
        assertEquals(LogicaBord.CuloarePutere.AMBRA, LogicaBord.culoarePutereSus(0, sus))
        assertEquals(LogicaBord.CuloarePutere.PORTOCALIE, LogicaBord.culoarePutereSus(3, sus))
        assertEquals(LogicaBord.CuloarePutere.STINSA, LogicaBord.culoarePutereJos(0, jos))
    }

    @Test
    fun `recuperarea coboara in cyan si tractiunea sta stinsa`() {
        // -3,4 kW: exact cat s-a masurat cand motorul incarca bateria.
        val sus = LogicaBord.trepteSus(-3.38f)
        val jos = LogicaBord.trepteJos(-3.38f)
        assertEquals(0f, sus, 0.001f)
        assertTrue("recuperarea trebuie sa aprinda ceva", jos > 0.9f)
        assertEquals(LogicaBord.CuloarePutere.CYAN, LogicaBord.culoarePutereJos(0, jos))
        assertEquals(LogicaBord.CuloarePutere.STINSA, LogicaBord.culoarePutereSus(0, sus))
    }

    @Test
    fun `puterea zero lasa amandoua barele stinse`() {
        assertEquals(0f, LogicaBord.trepteSus(0f), 0.001f)
        assertEquals(0f, LogicaBord.trepteJos(0f), 0.001f)
    }

    @Test
    fun `varful de putere aprinde treptele rosii`() {
        val sus = LogicaBord.trepteSus(22f)
        assertEquals(LogicaBord.CuloarePutere.ROSIE, LogicaBord.culoarePutereSus(6, sus))
    }

    // --- scanerul --------------------------------------------------------

    @Test
    fun `scanerul se intoarce la capat`() {
        assertEquals(0f, LogicaBord.pozitiaScanerului(0f), 0.01f)
        assertEquals(29f, LogicaBord.pozitiaScanerului(29f), 0.01f)
        assertEquals(19f, LogicaBord.pozitiaScanerului(39f), 0.01f)
        assertEquals(1f, LogicaBord.directiaScanerului(10f), 0.01f)
        assertEquals(-1f, LogicaBord.directiaScanerului(40f), 0.01f)
    }

    @Test
    fun `casuta pe care sta scanerul e cea mai aprinsa`() {
        val pe = LogicaBord.stralucireScaner(10, 10f, 1f)
        val langa = LogicaBord.stralucireScaner(11, 10f, 1f)
        val departe = LogicaBord.stralucireScaner(20, 10f, 1f)
        assertEquals(1f, pe, 0.001f)
        assertTrue("vecina e mai stinsa", langa < pe)
        assertTrue("departe e aproape stinsa", departe < 0.3f)
    }

    @Test
    fun `dara ramane in urma scanerului`() {
        // Merge spre dreapta: casuta din stanga lui e mai aprinsa decat cea
        // din dreapta, la aceeasi departare.
        val inUrma = LogicaBord.stralucireScaner(7, 10f, 1f)
        val inFata = LogicaBord.stralucireScaner(13, 10f, 1f)
        assertTrue("dara ($inUrma) trebuie sa fie mai aprinsa decat fata ($inFata)",
            inUrma > inFata)
    }

    // --- netezirea -------------------------------------------------------

    @Test
    fun `valoarea desenata alearga dupa cea citita, fara s-o depaseasca`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        val masina = CarState(
            conexiune = StareConexiune.CONECTAT,
            valori = mapOf(Marime.VITEZA to Citire(100.0, 0L)),
        )
        var ultima = 0f
        repeat(120) {
            ceas += 16_000_000L // un cadru de ecran
            val stare = netezitor.urmatoareaStare(masina)
            assertTrue("nu se depaseste tinta", stare.vitezaKmh <= 100.1f)
            assertTrue("se apropie mereu", stare.vitezaKmh >= ultima - 0.01f)
            ultima = stare.vitezaKmh
        }
        assertTrue("dupa doua secunde trebuie sa fie aproape, a ajuns la $ultima", ultima > 95f)
    }

    @Test
    fun `primul cadru nu sare direct la valoarea citita`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        ceas += 16_000_000L
        val stare = netezitor.urmatoareaStare(
            CarState(valori = mapOf(Marime.VITEZA to Citire(100.0, 0L))),
        )
        assertTrue("acul porneste lin, a sarit la ${stare.vitezaKmh}", stare.vitezaKmh < 20f)
    }

    @Test
    fun `fara citire valoarea ramane unde era, nu cade la zero`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        val cuViteza = CarState(valori = mapOf(Marime.VITEZA to Citire(80.0, 0L)))
        repeat(200) {
            ceas += 16_000_000L
            netezitor.urmatoareaStare(cuViteza)
        }
        ceas += 16_000_000L
        val dupa = netezitor.urmatoareaStare(CarState())
        assertTrue("viteza nu trebuie sa cada brusc, a ajuns la ${dupa.vitezaKmh}",
            dupa.vitezaKmh > 70f)
    }

    @Test
    fun `modulatorul de voce creste cu puterea, incotro ar merge ea`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        val cuPutere = CarState(valori = mapOf(Marime.HV_PUTERE to Citire(-20.0, 0L)))
        repeat(200) {
            ceas += 16_000_000L
            netezitor.urmatoareaStare(cuPutere)
        }
        ceas += 16_000_000L
        val stare = netezitor.urmatoareaStare(cuPutere)
        assertTrue("recuperarea puternica trebuie sa umple modulatorul, e la ${stare.vocePlin}",
            stare.vocePlin > 0.6f)
    }

    // --- banda cu melodia -----------------------------------------------

    @Test
    fun `melodia gasita la pornire nu se anunta`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas }, melodiaAcum = { "Saliva" })
        repeat(5) {
            ceas += 1_100_000_000L
            val stare = netezitor.urmatoareaStare(CarState())
            assertNull(
                "nu tu ai pus-o, doar ai urcat peste ea: ${stare.melodie}",
                stare.melodie,
            )
        }
    }

    @Test
    fun `melodia schimbata se anunta o singura data`() {
        var ceas = 0L
        var melodia = "Saliva"
        val netezitor = Netezitor(ceas = { ceas }, melodiaAcum = { melodia })

        // Prima intrebare doar afla ce canta.
        ceas += 1_100_000_000L
        netezitor.urmatoareaStare(CarState())

        melodia = "Stargazing"
        ceas += 1_100_000_000L
        val dupaSchimbare = netezitor.urmatoareaStare(CarState())
        assertEquals("Stargazing", dupaSchimbare.melodie)

        // Dupa ce trece banda, modulatorul isi ia locul inapoi.
        var ultima: StareBord? = null
        repeat(20) {
            ceas += 600_000_000L
            ultima = netezitor.urmatoareaStare(CarState())
        }
        assertNull("banda trebuie sa treaca, nu sa ramana", ultima!!.melodie)
    }

    @Test
    fun `banda strabate tot drumul, de la un capat la altul`() {
        var ceas = 0L
        var melodia: String? = "unu"
        val netezitor = Netezitor(ceas = { ceas }, melodiaAcum = { melodia })
        ceas += 1_100_000_000L
        netezitor.urmatoareaStare(CarState())

        melodia = "doi"
        ceas += 1_100_000_000L
        val inceput = netezitor.urmatoareaStare(CarState())
        assertEquals("porneste de la zero", 0f, inceput.melodieProgres, 0.05f)

        ceas += (Netezitor.SECUNDE_MELODIE * 1_000_000_000L / 2).toLong()
        val mijloc = netezitor.urmatoareaStare(CarState())
        assertEquals("la jumatatea drumului", 0.5f, mijloc.melodieProgres, 0.1f)
    }

    @Test
    fun `fara acces la notificari bordul nu anunta nimic`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas }, melodiaAcum = { null })
        repeat(10) {
            ceas += 1_100_000_000L
            assertNull(netezitor.urmatoareaStare(CarState()).melodie)
        }
    }

    @Test
    fun `temperatura de afara ajunge pe bord`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        ceas += 16_000_000L
        val stare = netezitor.urmatoareaStare(
            CarState(valori = mapOf(Marime.TEMP_EXTERIOARA to Citire(11.0, 1L))),
        )
        assertEquals(11f, stare.tempExterioara!!, 0.01f)
    }

    @Test
    fun `starea bordului duce mai departe modul masinii`() {
        var ceas = 0L
        val netezitor = Netezitor(ceas = { ceas })
        ceas += 16_000_000L
        val stare = netezitor.urmatoareaStare(
            CarState(
                conexiune = StareConexiune.CONECTAT,
                valori = mapOf(
                    Marime.TURATIE to Citire(1400.0, 0L),
                    Marime.HV_PUTERE to Citire(-8.0, 0L),
                    Marime.VITEZA to Citire(60.0, 0L),
                ),
            ),
        )
        assertEquals(ModHibrid.REGEN, stare.mod)
        assertTrue(stare.areLegatura)
    }
}
