package ro.vix.kittdash.obd

import android.annotation.SuppressLint
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothSocket
import java.io.IOException
import java.io.InputStream
import java.io.OutputStream
import java.util.UUID

/**
 * Legatura reala: Bluetooth clasic, RFCOMM, profilul port serial (SPP).
 * Adaptorul trebuie imperecheat intai din setarile telefonului.
 */
@SuppressLint("MissingPermission")
class BluetoothObdLink(
    private val dispozitiv: BluetoothDevice,
) : ObdLink {

    private var socket: BluetoothSocket? = null
    private var fluxIesire: OutputStream? = null
    private var fluxIntrare: InputStream? = null

    override val nume: String
        get() = dispozitiv.name ?: dispozitiv.address

    override fun deschide() {
        inchideLiniste()
        val s = try {
            dispozitiv.createRfcommSocketToServiceRecord(UUID_SPP)
        } catch (e: IOException) {
            throw ObdException("Nu pot crea legatura cu ${dispozitiv.address}", e)
        }
        try {
            s.connect()
        } catch (e: IOException) {
            try {
                s.close()
            } catch (_: IOException) {
            }
            throw ObdException(
                "Adaptorul nu raspunde. Verifica daca e in mufa OBD si imperecheat.", e,
            )
        }
        socket = s
        fluxIesire = s.outputStream
        fluxIntrare = s.inputStream
    }

    override fun comanda(comanda: String, timeoutMs: Long): String {
        val out = fluxIesire ?: throw ObdException("Legatura nu e deschisa")
        val inp = fluxIntrare ?: throw ObdException("Legatura nu e deschisa")
        try {
            // Golim ce a mai ramas de la o comanda anterioara intrerupta.
            while (inp.available() > 0) {
                inp.read()
            }
            out.write((comanda + "\r").toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (e: IOException) {
            throw ObdException("Nu pot scrie catre adaptor", e)
        }
        return citestePanaLaPrompt(inp, timeoutMs)
    }

    override fun asculta(durataMs: Long): String {
        val out = fluxIesire ?: throw ObdException("Legatura nu e deschisa")
        val inp = fluxIntrare ?: throw ObdException("Legatura nu e deschisa")
        val sb = StringBuilder()
        try {
            while (inp.available() > 0) inp.read()
            out.write("ATMA\r".toByteArray(Charsets.US_ASCII))
            out.flush()
            val sfarsit = System.currentTimeMillis() + durataMs
            while (System.currentTimeMillis() < sfarsit && sb.length < ASCULTARE_MAXIMA) {
                if (inp.available() <= 0) {
                    Thread.sleep(4)
                    continue
                }
                val c = inp.read()
                if (c < 0) throw ObdException("Legatura s-a inchis")
                // Daca adaptorul se opreste singur (BUFFER FULL), iese la prompt.
                if (c == PROMPT) return sb.toString()
                sb.append(c.toChar())
            }
            // Orice caracter trimis opreste ascultarea; apoi vine promptul.
            out.write("\r".toByteArray(Charsets.US_ASCII))
            out.flush()
        } catch (e: IOException) {
            throw ObdException("Legatura s-a intrerupt in ascultare", e)
        }
        sb.append(citestePanaLaPrompt(inp, 2_000L))
        return sb.toString()
    }

    private fun citestePanaLaPrompt(inp: InputStream, timeoutMs: Long): String {
        val sb = StringBuilder()
        val limita = System.currentTimeMillis() + timeoutMs
        while (System.currentTimeMillis() < limita) {
            if (inp.available() <= 0) {
                // Adaptorul e lent prin natura lui; asteptam scurt si reincercam.
                Thread.sleep(4)
                continue
            }
            val c = try {
                inp.read()
            } catch (e: IOException) {
                throw ObdException("Legatura s-a intrerupt", e)
            }
            if (c < 0) throw ObdException("Legatura s-a inchis")
            if (c == PROMPT) return sb.toString()
            sb.append(c.toChar())
        }
        throw ObdException("Adaptorul nu a raspuns in ${timeoutMs} ms")
    }

    override fun close() = inchideLiniste()

    private fun inchideLiniste() {
        try {
            socket?.close()
        } catch (_: IOException) {
        }
        socket = null
        fluxIesire = null
        fluxIntrare = null
    }

    companion object {
        /** UUID-ul standard pentru profilul port serial. */
        val UUID_SPP: UUID = UUID.fromString("00001101-0000-1000-8000-00805F9B34FB")
        private const val PROMPT = '>'.code

        /** Cat strangem cel mult din ascultare: ajunge pentru cateva secunde. */
        private const val ASCULTARE_MAXIMA = 200_000
    }
}
