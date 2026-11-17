package ro.vix.kittdash.obd

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertNull
import org.junit.Assert.assertTrue
import org.junit.Test

class ExploratorMasinaTest {

    /**
     * Un adaptor fals cu cateva calculatoare: motorul (7E0, standard OBD),
     * hibridul (7E2, blocuri 2101 si 2106), ABS-ul (7B0, stie serviciul 21
     * dar numai blocul 03), un calculator care nu stie serviciul 21 (7C4) si
     * unul de caroserie la 750/40. Raspunde cu antete, ca un ELM327 cu ATH1.
     */
    private class AdaptorFals : ObdLink {
        override val nume = "fals"
        val trimise = mutableListOf<String>()
        private var antet = "7E0"
        private var extins: String? = null

        override fun deschide() {}
        override fun close() {}

        override fun comanda(comanda: String, timeoutMs: Long): String {
            trimise += comanda
            val c = comanda.uppercase()
            when {
                c.startsWith("ATSH") -> { antet = c.removePrefix("ATSH"); return "OK" }
                c == "ATCEA" -> { extins = null; return "OK" }
                c.startsWith("ATCEA") -> { extins = c.removePrefix("ATCEA"); return "OK" }
                c.startsWith("AT") -> return "OK"
            }
            val cine = if (extins != null) "$antet/$extins" else antet
            return when (cine to c) {
                "7DF" to "0100" -> "7E8 06 41 00 98 18 00 13\r7EA 06 41 00 80 00 00 01"
                "7E0" to "0100" -> "7E8 06 41 00 98 18 00 13"
                "7E0" to "010D" -> "7E8 03 41 0D 2A"
                "7E0" to "0900" -> "7E8 06 49 00 40 00 00 00"
                "7E0" to "0902" -> "7E8 10 14 49 02 01 4A 54 4A\r7E8 21 42 41 52 42 5A 33\r7E8 22 30 32 30 31 32 33 34"
                "7E0" to "2101" -> "7E8 03 7F 21 12"
                "7E2" to "2101" -> "7EA 10 1A 61 01 00 00 03 E8\r7EA 21 12 B4 7F D8 00 40 2C"
                "7E2" to "2106" -> "7EA 05 61 06 01 02 03"
                "7B0" to "2101" -> "7B8 03 7F 21 12"
                "7B0" to "2103" -> "7B8 04 61 03 11 22"
                "7C4" to "2101" -> "7CC 03 7F 21 11"
                "750/40" to "2101" -> "758 40 04 61 01 AA BB"
                else -> if (c.startsWith("01") && antet == "7E0") "7E8 03 7F 01 12" else "NO DATA"
            }
        }

        override fun asculta(durataMs: Long): String = "3B7 08 00 01 02\r0B4 08 00 00 00 00 2A"
    }

    @Test
    fun gasesteCalculatoareleSiBlocurile() {
        val fals = AdaptorFals()
        val raport = ExploratorMasina(fals, ceas = { 0L }, tururi = 2).ruleaza()

        assertTrue(raport, raport.contains("Rezumat: 5 calculatoare gasite"))
        for (adresa in listOf("7E0", "7E2", "7B0", "7C4", "750/40")) {
            assertTrue("lipseste $adresa", raport.contains("  ${adresa.padEnd(8)} raspunde"))
        }
        assertTrue(raport.contains("2106 -> 7EA 05 61 06 01 02 03"))
        assertTrue(raport.contains("2103 -> 7B8 04 61 03 11 22"))
        assertTrue("seria de sasiu", raport.contains("0902 -> 7E8 10 14 49 02"))
        assertTrue("ascultarea", raport.contains("3B7 08 00 01 02"))
        // 7C4 nu stie serviciul 21: nu-i cerem cele 255 de blocuri degeaba
        val dupa7C4 = fals.trimise.dropWhile { it != "ATSH7C4" }.drop(1).takeWhile { !it.startsWith("ATSH") }
        assertFalse(dupa7C4.contains("2102"))
    }

    @Test
    fun nuTrimiteNimicCeScrieInMasina() {
        val fals = AdaptorFals()
        ExploratorMasina(fals, ceas = { 0L }, tururi = 1).ruleaza()
        val nepermise = fals.trimise.filterNot { ExploratorMasina.esteDoarCitire(it) }
        assertEquals(emptyList<String>(), nepermise)
        for (c in fals.trimise.filterNot { it.startsWith("AT") }) {
            assertTrue("serviciu nepermis: $c", c.take(2) in setOf("01", "09", "21", "22", "1A"))
        }
    }

    @Test
    fun refuzaCererileCareScriuSauSterg() {
        for (c in listOf("04", "14FF00", "2E0101", "3101", "1003", "1101", "27", "3B01", "ATWS", "ATPP0ESV")) {
            assertFalse(c, ExploratorMasina.esteDoarCitire(c))
        }
        for (c in listOf("0100", "0902", "2101", "22F190", "1A80", "ATSH7E2", "ATCEA40", "ATMA")) {
            assertTrue(c, ExploratorMasina.esteDoarCitire(c))
        }
    }

    @Test
    fun citesteCineARaspunsSiMasca() {
        assertEquals("7EA", ExploratorMasina.cineARaspuns("7EA 06 41 00 80 00 00 01"))
        assertNull(ExploratorMasina.cineARaspuns("NO DATA"))
        assertNull(ExploratorMasina.cineARaspuns("?"))
        assertEquals(0x98180013L, ExploratorMasina.masca("7E8 06 41 00 98 18 00 13", "4100"))
        assertTrue(ExploratorMasina.refuzaServiciul("7CC 03 7F 21 11", "21"))
        assertFalse(ExploratorMasina.refuzaServiciul("7B8 03 7F 21 12", "21"))
    }
}
