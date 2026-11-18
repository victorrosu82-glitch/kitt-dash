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
    /** Daca da, dupa initializare face proba de baterie (2 minute), o data. */
    faceProba: Boolean = false,
    /** Daca da, dupa initializare face proba ghidata (mod de condus, clima, MG), o data. */
    faceGhidata: Boolean = false,
    /** Scrie un raport (fel, text) si spune unde (sau null daca n-a putut). */
    private val scrieExplorare: (String, String) -> String? = { _, _ -> null },
) {

    private var deExplorat = faceExplorare
    private var deProbat = faceProba
    private var deGhidat = faceGhidata

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
                if (deProbat) {
                    probeaza(link)
                    elm.uitaAntetul()
                }
                if (deGhidat) {
                    ghideaza(link)
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
        val unde = scrieExplorare("explorare", raport)
        depozit.actualizeaza { it.copy(explorare = null, explorareProgres = 1f, fisierExplorare = unde) }
        Log.i(TAG, "Explorare terminata, raport in $unde")
    }

    private fun probeaza(link: ObdLink) {
        val raport = ProbaBaterie(
            link,
            progres = { text, f -> depozit.actualizeaza { it.copy(explorare = text, explorareProgres = f) } },
        ).ruleaza()
        deProbat = false
        val unde = scrieExplorare("proba-baterie", raport)
        depozit.actualizeaza { it.copy(explorare = null, explorareProgres = 1f, fisierExplorare = unde) }
        Log.i(TAG, "Proba de baterie terminata, raport in $unde")
    }

    private fun ghideaza(link: ObdLink) {
        val raport = ProbaGhidata(
            link,
            progres = { text, f -> depozit.actualizeaza { it.copy(explorare = text, explorareProgres = f) } },
        ).ruleaza()
        deGhidat = false
        val unde = scrieExplorare("proba-ghidata", raport)
        depozit.actualizeaza { it.copy(explorare = null, explorareProgres = 1f, fisierExplorare = unde) }
        Log.i(TAG, "Proba ghidata terminata, raport in $unde")
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

        // Cererile cu mai multe PID-uri deodata. Daca masina sau adaptorul nu
        // le inteleg, ne intoarcem la cate una si nu mai incercam.
        var grupuriMerg = true
        var pasHibrid = 0
        var drum = Drum()
        var ultimulDrumMs = ceas()

        while (coroutineContext.isActive) {
            val acum = ceas()
            var amCerutCeva = false

            val datorate = ObdPid.entries.filter { it in deCerut && acum >= (urmatoarea[it] ?: 0L) }
            // grupate pe calculator, ca sa nu schimbam adresa de mai multe ori pe tur
            for ((_, deAcelasi) in datorate.groupBy { it.antet }) {
                val bucati = if (grupuriMerg) deAcelasi.chunked(GRUP_MAXIM) else deAcelasi.map { listOf(it) }
                for (bucata in bucati) {
                    var aTacut = false
                    val rezultat: Map<ObdPid, Map<Marime, Double>> = try {
                        val r = citesteBucata(elm, bucata, grupuriMerg)
                        if (r == null) {
                            grupuriMerg = false
                            Log.i(TAG, "Masina nu raspunde la mai multe PID-uri deodata; le cer pe rand")
                            bucata.associateWith { elm.citeste(it) }
                        } else {
                            r
                        }.also { taceriLaRand = 0 }
                    } catch (e: ObdException) {
                        aTacut = true
                        taceriLaRand++
                        if (taceriLaRand >= TACERI_MAXIME) {
                            throw ObdException(
                                "Adaptorul a tacut de $taceriLaRand ori la rand: ${e.message}", e,
                            )
                        }
                        Log.d(TAG, "${bucata.joinToString { it.eticheta }}: ${e.message} (tacerea $taceriLaRand)")
                        emptyMap()
                    }
                    amCerutCeva = true
                    val dupa = ceas()
                    for (pid in bucata) {
                        urmatoarea[pid] = dupa + pid.perioadaMs
                        val valori = rezultat[pid].orEmpty()
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
                                contorRaspunsuri++
                                depozit.pune(valori, dupa)
                            }
                        }
                    }
                }
            }

            // Blocurile bateriei, numai cat le arata ecranul HIBRID: cate unul
            // pe tur, prin rotatie, ca viteza si turatia sa curga neoprite.
            // Tensiunile vin la fiecare al treilea pas, adica la ~1 s.
            if (CereriRare.vreaHibrid()) {
                val pas = PASI_HIBRID[pasHibrid % PASI_HIBRID.size]
                pasHibrid++
                try {
                    val vechi = depozit.stare.value.hibrid ?: DateHibrid()
                    val nou = citestePasHibrid(elm, pas, vechi)
                    depozit.actualizeaza { st ->
                        val curent = st.valoare(Marime.HV_CURENT)?.toFloat()
                        val repaus = SanatateBaterie.diferentaInRepaus(nou, curent) ?: st.diferentaRepausMv
                        st.copy(
                            hibrid = nou,
                            diferentaRepausMv = repaus,
                            sanatate = SanatateBaterie.evalueaza(nou, curent, repaus),
                        )
                    }
                    taceriLaRand = 0
                } catch (e: ObdException) {
                    Log.d(TAG, "Blocul bateriei $pas: ${e.message}")
                }
                amCerutCeva = true
            }

            // Drumul de acum, socotit din ce avem deja.
            val acumDrum = ceas()
            drum = drum.cu(depozit.stare.value, (acumDrum - ultimulDrumMs) / 1000.0)
            ultimulDrumMs = acumDrum
            if (drum != depozit.stare.value.drum) depozit.actualizeaza { it.copy(drum = drum) }

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

    /**
     * O bucata de PID-uri: cate unul, sau toate intr-o singura intrebare.
     * Intoarce null daca intrebarea comuna n-a fost inteleasa.
     */
    private fun citesteBucata(elm: Elm327, bucata: List<ObdPid>, grupuri: Boolean): Map<ObdPid, Map<Marime, Double>>? {
        if (bucata.size == 1 || !grupuri) return bucata.associateWith { elm.citeste(it) }
        val r = elm.citesteGrup(bucata) ?: return null
        // un raspuns fara niciun PID cerut = intrebarea n-a fost inteleasa
        return if (r.values.all { it.isEmpty() }) null else r
    }

    /** Un singur bloc al calculatorului hibrid, pus peste ce stiam. Vezi [DateHibrid]. */
    private fun citestePasHibrid(elm: Elm327, pas: Int, d: DateHibrid): DateHibrid {
        val h = "7E2"
        val acum = ceas()
        return when (pas) {
            0x81 -> DateHibrid.tensiuni(elm.citesteBloc(h, pas)).takeIf { it.isNotEmpty() }
                ?.let { d.copy(tensiuni = it, laMs = acum) } ?: d
            0x95 -> DateHibrid.rezistente(elm.citesteBloc(h, pas)).takeIf { it.isNotEmpty() }
                ?.let { d.copy(rezistente = it) } ?: d
            0x87 -> DateHibrid.temperaturi(elm.citesteBloc(h, pas)).takeIf { it.isNotEmpty() }
                ?.let { d.copy(temperaturiBaterie = it) } ?: d
            REZERVOR -> DateHibrid.rezervor(elm.citesteBloc("7C0", 0x29))
                ?.let { d.copy(rezervorLitri = it) } ?: d
            else -> {
                val eticheta = ETICHETE_SISTEM[pas] ?: return d
                val t = DateHibrid.temperaturaSimpla(elm.citesteBloc(h, pas)) ?: return d
                val alte = d.temperaturiSistem.filter { it.first != eticheta }
                val toate = (alte + (eticheta to t))
                    .sortedBy { (e, _) -> ETICHETE_SISTEM.values.indexOf(e) }
                d.copy(temperaturiSistem = toate)
            }
        }
    }

    companion object {
        private const val TAG = "ObdPoller"
        private const val PAUZA_MINIMA_MS = 1_000L
        private const val PAUZA_MAXIMA_MS = 30_000L
        private const val TURA_MINIMA_MS = 20L
        private const val RATARI_MAXIME = 3
        private const val TACERI_MAXIME = 5

        /** Cate PID-uri punem intr-o intrebare: 3 incap intr-un singur cadru CAN. */
        const val GRUP_MAXIM = 3

        /** Rezervorul sta la alt calculator (7C0); aici doar un semn in rotatie. */
        private const val REZERVOR = -1

        /** Ordinea blocurilor pe ecranul HIBRID: tensiunile la fiecare al treilea pas. */
        val PASI_HIBRID = listOf(0x81, 0x95, 0x87, 0x81, 0x61, 0x62, 0x81, 0x63, 0x70, 0x81, REZERVOR, 0x87)

        private val ETICHETE_SISTEM = linkedMapOf(0x61 to "MG1?", 0x62 to "MG2?", 0x63 to "INVERTOR?", 0x70 to "CONVERTOR?")
    }
}
