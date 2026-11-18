package ro.vix.kittdash.obd

import android.util.Log
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.delay
import kotlinx.coroutines.isActive
import kotlinx.coroutines.withContext
import kotlin.coroutines.coroutineContext

/**
 * Bucla de interogare. Deschide legatura, initializeaza adaptorul si apoi
 * cere PID-urile la nesfarsit, publicand fiecare valoare in [depozit].
 * Daca legatura pica, incearca din nou - cu pauze din ce in ce mai lungi,
 * ca sa nu macine bateria telefonului cand masina e oprita.
 *
 * Un ELM327 da cam 10-20 de raspunsuri pe secunda in total, deci nu cerem
 * totul la fel de des: viteza si turatia la fiecare tura, temperaturile din
 * doua in doua secunde. Perioadele sunt in [ObdPid.perioadaMs].
 */
class ObdPoller(
    private val depozit: CarStateRepository,
    private val faLegatura: () -> ObdLink,
    private val ceas: () -> Long = { System.currentTimeMillis() },
    /** Daca da, dupa initializare intreaba o data calculatoarele hibride. */
    private val faceSondaHibrida: Boolean = false,
    /** Unde se scriu raspunsurile brute ale sondei, ca sa nu se piarda. */
    private val scrieSonda: (List<RandSonda>) -> Unit = {},
    /** Daca da, dupa initializare exploreaza toata masina, o singura data. */
    faceExplorare: Boolean = false,
    /** Scrie raportul explorarii si spune unde (sau null daca n-a putut). */
    private val scrieExplorare: (String) -> String? = { null },
) {

    private var deExplorat = faceExplorare

    /** Ruleaza pana la anularea corutinei. */
    suspend fun ruleaza() = withContext(Dispatchers.IO) {
        var pauzaMs = PAUZA_MINIMA_MS
        while (coroutineContext.isActive) {
            var legatura: ObdLink? = null
            try {
                val link = faLegatura()
                legatura = link
                depozit.actualizeaza {
                    it.copy(
                        conexiune = StareConexiune.SE_CONECTEAZA,
                        numeAdaptor = link.nume,
                        simulator = link.esteSimulator,
                        mesajEroare = null,
                    )
                }
                link.deschide()

                depozit.actualizeaza { it.copy(conexiune = StareConexiune.SE_INITIALIZEAZA) }
                val elm = Elm327(link)
                elm.initializeaza()

                val anunt = elm.pidSuportate()
                val suportate = ObdPid.entries.filter { it.pid in anunt }.toSet()
                depozit.actualizeaza {
                    it.copy(
                        conexiune = StareConexiune.CONECTAT,
                        versiuneElm = elm.versiune,
                        protocol = elm.protocol,
                        pidSuportate = suportate,
                        erori = 0,
                    )
                }
                Log.i(TAG, "Conectat la ${link.nume}, ${suportate.size} PID-uri utile")

                if (faceSondaHibrida) {
                    val randuri = SondaHibrid(elm).ruleaza()
                    depozit.actualizeaza { it.copy(sonda = randuri) }
                    scrieSonda(randuri)
                    // Sonda a umblat la ATSH; bucla trebuie sa-l puna la loc.
                    elm.uitaAntetul()
                    Log.i(TAG, "Sonda hibrida: ${randuri.count { r -> r.areDate }} " +
                        "raspunsuri cu date din ${randuri.size} cereri")
                }

                if (deExplorat) {
                    exploreaza(link)
                    elm.uitaAntetul()
                }

                pauzaMs = PAUZA_MINIMA_MS
                interogheazaLaNesfarsit(elm, suportate)
            } catch (e: CancellationException) {
                throw e
            } catch (e: Exception) {
                Log.w(TAG, "Legatura a picat: ${e.message}")
                depozit.actualizeaza {
                    it.copy(
                        conexiune = StareConexiune.EROARE,
                        mesajEroare = e.message ?: e::class.simpleName,
                        erori = it.erori + 1,
                    )
                }
            } finally {
                try {
                    legatura?.close()
                } catch (_: Exception) {
                }
            }

            if (!coroutineContext.isActive) break
            delay(pauzaMs)
            pauzaMs = (pauzaMs * 2).coerceAtMost(PAUZA_MAXIMA_MS)
        }
    }

    private fun exploreaza(link: ObdLink) {
        val raport = ExploratorMasina(
            link,
            progres = { text, f ->
                depozit.actualizeaza {
                    it.copy(explorare = text, explorareProgres = if (f < 0f) it.explorareProgres else f)
                }
            },
        ).ruleaza()
        // O singura data: daca legatura cade si se reface, nu o luam de la capat.
        deExplorat = false
        val unde = scrieExplorare(raport)
        depozit.actualizeaza { it.copy(explorare = null, explorareProgres = 1f, fisierExplorare = unde) }
        Log.i(TAG, "Explorare terminata, raport in $unde")
    }

    /**
     * Cere PID-urile dupa perioada fiecaruia. Iese prin excepție cand
     * legatura pica, ca sa reia bucla de afara cu o reconectare.
     */
    private suspend fun interogheazaLaNesfarsit(elm: Elm327, suportate: Set<ObdPid>) {
        // Daca masina nu anunta un PID, il cerem oricum o data: anunturile
        // mint uneori, mai ales la Toyota. Ce da NO DATA de 3 ori, iese.
        val deCerut = (suportate + ObdPid.RAPIDE).toMutableSet()
        val urmatoarea = mutableMapOf<ObdPid, Long>()
        val ratari = mutableMapOf<ObdPid, Int>()
        var contorRaspunsuri = 0
        var inceputMasurare = ceas()
        // Un raspuns intarziat nu inseamna ca adaptorul a murit. Rupem
        // legatura abia dupa mai multe la rand.
        var taceriLaRand = 0

        while (coroutineContext.isActive) {
            val acum = ceas()
            var amCerutCeva = false

            for (pid in ObdPid.entries) {
                if (pid !in deCerut) continue
                if (acum < (urmatoarea[pid] ?: 0L)) continue

                var aTacut = false
                val valori = try {
                    val v = elm.citeste(pid)
                    taceriLaRand = 0
                    v
                } catch (e: ObdException) {
                    aTacut = true
                    taceriLaRand++
                    if (taceriLaRand >= TACERI_MAXIME) {
                        throw ObdException(
                            "Adaptorul a tacut de $taceriLaRand ori la rand: ${e.message}", e,
                        )
                    }
                    Log.d(TAG, "${pid.eticheta}: ${e.message} (tacerea $taceriLaRand)")
                    emptyMap()
                }
                contorRaspunsuri++
                amCerutCeva = true
                urmatoarea[pid] = ceas() + pid.perioadaMs

                when {
                    // Tacerea e vina legaturii, nu a PID-ului: nu-l scoatem
                    // din lista pentru ca adaptorul a intarziat o data.
                    aTacut -> Unit
                    valori.isEmpty() -> {
                        val n = (ratari[pid] ?: 0) + 1
                        ratari[pid] = n
                        if (n >= RATARI_MAXIME) {
                            deCerut.remove(pid)
                            Log.i(TAG, "${pid.eticheta}: masina nu da PID-ul, nu-l mai cer")
                        }
                    }
                    else -> {
                        ratari[pid] = 0
                        depozit.pune(valori, ceas())
                    }
                }
            }

            // Media raspunsurilor pe secunda, reinnoita din secunda in secunda.
            val trecut = ceas() - inceputMasurare
            if (trecut >= 1000) {
                val rata = contorRaspunsuri * 1000.0 / trecut
                depozit.actualizeaza { it.copy(raspunsuriPeSecunda = rata) }
                contorRaspunsuri = 0
                inceputMasurare = ceas()
            }

            if (!amCerutCeva) delay(TURA_MINIMA_MS)
        }
    }

    companion object {
        private const val TAG = "ObdPoller"
        private const val PAUZA_MINIMA_MS = 1_000L
        private const val PAUZA_MAXIMA_MS = 30_000L
        private const val TURA_MINIMA_MS = 20L
        private const val RATARI_MAXIME = 3
        private const val TACERI_MAXIME = 5
    }
}
