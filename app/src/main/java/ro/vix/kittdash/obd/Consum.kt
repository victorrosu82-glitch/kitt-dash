package ro.vix.kittdash.obd

/**
 * Consumul instantaneu, socotit din aerul care intra in motor.
 *
 * Masina nu da consumul (PID-ul 5E nu e anuntat), dar da debitul de aer.
 * Motorul arde benzina in proportia 14,7 kg de aer la 1 kg de benzina, iar
 * un litru de benzina cantareste cam 745 g. Asadar:
 *
 *     litri pe ora = grame de aer pe secunda * 3600 / 14,7 / 745
 *
 * E o socoteala, nu o masura: la plina sarcina motorul imbogateste putin
 * amestecul, deci consumul real e atunci ceva mai mare decat cel aratat.
 */
object Consum {
    const val AER_PE_BENZINA = 14.7
    const val GRAME_PE_LITRU = 745.0

    fun litriPeOra(debitAerGs: Double): Double =
        debitAerGs * 3600.0 / AER_PE_BENZINA / GRAME_PE_LITRU
}
