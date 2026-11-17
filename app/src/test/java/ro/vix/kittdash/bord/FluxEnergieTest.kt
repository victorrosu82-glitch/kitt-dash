package ro.vix.kittdash.bord

import org.junit.Assert.assertEquals
import org.junit.Assert.assertFalse
import org.junit.Assert.assertTrue
import org.junit.Test

class FluxEnergieTest {

    @Test
    fun numaiPeBaterieTrageFataFaraMotor() {
        val f = FluxEnergie.din(turatie = 0f, putereKw = 9f, vitezaKmh = 28f)
        assertFalse(f.motor)
        assertTrue(f.fata)
        assertFalse(f.spate)
        assertFalse(f.inBaterie)
        assertTrue(f.rotiFata)
    }

    @Test
    fun acceleratiePuternicaAprindeSiSpatele() {
        val f = FluxEnergie.din(turatie = 2100f, putereKw = 18f, vitezaKmh = 74f)
        assertTrue(f.motorLaRoti)
        assertTrue(f.fata)
        assertTrue(f.spate)
        assertFalse(f.generator)
    }

    @Test
    fun motorulCareIncarcaBateriaEGenerator() {
        val f = FluxEnergie.din(turatie = 1600f, putereKw = -6f, vitezaKmh = 62f)
        assertTrue(f.generator)
        assertTrue(f.motorLaRoti)
        assertTrue(f.inBaterie)
        assertFalse(f.recuperare)
        assertFalse(f.spate)
    }

    @Test
    fun franareaRecupereazaPeAmbeleOsii() {
        val f = FluxEnergie.din(turatie = 0f, putereKw = -21f, vitezaKmh = 46f)
        assertTrue(f.recuperare)
        assertTrue(f.fata)
        assertTrue(f.spate)
        assertFalse(f.generator)
    }

    @Test
    fun pePlocMotorulIncarcaFaraRoti() {
        // la semafor, motorul pornit ca sa incarce bateria
        val f = FluxEnergie.din(turatie = 1100f, putereKw = -4f, vitezaKmh = 0f)
        assertTrue(f.generator)
        assertFalse(f.motorLaRoti)
        assertFalse(f.rotiFata)
    }

    @Test
    fun consumulDeBordNuEsteTractiune() {
        val f = FluxEnergie.din(turatie = 0f, putereKw = 0.4f, vitezaKmh = 0f)
        assertEquals(FluxEnergie(false, false, false, false, false, false, false), f)
    }
}
