package ro.vix.kittdash.ui

import android.Manifest
import android.content.Intent
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.PaddingValues
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.Button
import androidx.compose.material3.ButtonDefaults
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.Surface
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontFamily
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.style.TextAlign
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import kotlin.math.abs
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.Marime
import ro.vix.kittdash.obd.ModHibrid
import ro.vix.kittdash.obd.ObdService
import ro.vix.kittdash.obd.RandSonda
import ro.vix.kittdash.obd.StareConexiune

/** Culorile KITT, aceleasi ca in macheta. */
private val ROSU = Color(0xFFFF2A1A)
private val ROSU_STINS = Color(0xFF8A1A12)
private val AMBRA = Color(0xFFFFB020)
private val CYAN = Color(0xFF28E0E0)
private val FUNDAL = Color(0xFF050505)
private val PANOU = Color(0xFF101010)

/**
 * Ecranul de test al etapei 1: nicio frumusete, doar cifrele brute, ca sa
 * vedem ca legatura cu masina merge. Bordul KITT vine in etapa 3.
 */
class MainActivity : ComponentActivity() {

    private val cerePermisiuni = registerForActivityResult(
        ActivityResultContracts.RequestMultiplePermissions(),
    ) { /* rezultatul se vede in lista de adaptoare */ }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        cerePermisiunileNecesare()
        pornesteDinIntent(intent)
        setContent {
            MaterialTheme {
                Surface(color = FUNDAL, modifier = Modifier.fillMaxSize()) {
                    EcranDeTest()
                }
            }
        }
    }

    /**
     * Pornire din linia de comanda, pentru probele pe masina:
     *
     *   adb shell am start -n ro.vix.kittdash/.ui.MainActivity \
     *       --es adresa 66:1E:87:02:48:A7 --ez sonda true
     *
     * Fara asta, probele depindeau de atins butoane la coordonate ghicite,
     * iar cand ecranul se stingea atingerile se pierdeau.
     */
    private fun pornesteDinIntent(intent: Intent?) {
        val adresa = intent?.getStringExtra("adresa") ?: return
        val sonda = intent.getBooleanExtra("sonda", false)
        ObdService.porneste(this, adresa, sonda)
    }

    private fun cerePermisiunileNecesare() {
        val cerute = buildList {
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                add(Manifest.permission.BLUETOOTH_CONNECT)
                add(Manifest.permission.BLUETOOTH_SCAN)
            }
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                add(Manifest.permission.POST_NOTIFICATIONS)
            }
        }
        if (cerute.isNotEmpty()) cerePermisiuni.launch(cerute.toTypedArray())
    }
}

@Composable
private fun EcranDeTest(vm: MainViewModel = viewModel()) {
    val stare by vm.stare.collectAsStateWithLifecycle()
    var adaptoare by remember { mutableStateOf(vm.adaptoare()) }
    var cuSonda by remember { mutableStateOf(false) }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .systemBarsPadding()
            .padding(16.dp),
    ) {
        Text(
            text = "K I T T   D A S H",
            color = ROSU,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        Text(
            text = "etapa 2 - datele hibride",
            color = ROSU_STINS,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(16.dp))
        PanouStare(stare)

        Spacer(Modifier.height(12.dp))
        // Trei butoane nu incap pe un rand de telefon: fiecare primeste o
        // parte egala din latime si un text scurt.
        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(8.dp),
        ) {
            if (stare.conexiune == StareConexiune.DECONECTAT) {
                OutlinedButton(
                    onClick = vm::conecteazaSimulator,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Simulator", color = CYAN, maxLines = 1, fontSize = 13.sp) }
            } else {
                OutlinedButton(
                    onClick = vm::deconecteaza,
                    contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                    modifier = Modifier.weight(1f),
                ) { Text("Opreste", color = AMBRA, maxLines = 1, fontSize = 13.sp) }
            }
            OutlinedButton(
                onClick = { adaptoare = vm.adaptoare() },
                contentPadding = PaddingValues(horizontal = 8.dp, vertical = 10.dp),
                modifier = Modifier.weight(1f),
            ) { Text("Caut", color = ROSU_STINS, maxLines = 1, fontSize = 13.sp) }
        }

        if (stare.conexiune == StareConexiune.DECONECTAT) {
            Spacer(Modifier.height(12.dp))
            if (adaptoare.isEmpty()) {
                Text(
                    text = "Niciun aparat imperecheat. Porneste Bluetooth-ul si " +
                        "imperecheaza adaptorul din Setari > Bluetooth (cod 1234), " +
                        "apoi apasa \"Caut\".",
                    color = ROSU_STINS,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
            } else {
                Row(
                    modifier = Modifier
                        .fillMaxWidth()
                        .clickable { cuSonda = !cuSonda }
                        .padding(vertical = 4.dp),
                    verticalAlignment = Alignment.CenterVertically,
                ) {
                    Text(
                        text = if (cuSonda) "[x]" else "[ ]",
                        color = if (cuSonda) CYAN else ROSU_STINS,
                        fontSize = 14.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                    Spacer(Modifier.width(8.dp))
                    Text(
                        text = "intreaba si calculatoarele hibride",
                        color = if (cuSonda) CYAN else ROSU_STINS,
                        fontSize = 12.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                Spacer(Modifier.height(6.dp))
                Text(
                    text = "Alege adaptorul:",
                    color = ROSU_STINS,
                    fontSize = 12.sp,
                    fontFamily = FontFamily.Monospace,
                )
                Spacer(Modifier.height(6.dp))
                ListaAdaptoare(adaptoare, vm, cuSonda)
            }
        } else {
            if (stare.sonda.isNotEmpty()) {
                Spacer(Modifier.height(12.dp))
                RezultatSonda(stare.sonda)
            }
            Spacer(Modifier.height(16.dp))
            ListaValori(stare)
        }
    }
}

@Composable
private fun PanouStare(stare: CarState) {
    val culoare = when (stare.conexiune) {
        StareConexiune.CONECTAT -> CYAN
        StareConexiune.EROARE -> AMBRA
        StareConexiune.DECONECTAT -> ROSU_STINS
        else -> ROSU
    }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANOU, RoundedCornerShape(6.dp))
            .padding(12.dp),
        verticalArrangement = Arrangement.spacedBy(2.dp),
    ) {
        Rand("Stare", textStare(stare.conexiune), culoare)
        stare.numeAdaptor?.let { Rand("Adaptor", it, ROSU) }
        stare.versiuneElm?.let { Rand("Versiune", it, ROSU_STINS) }
        stare.protocol?.let { Rand("Protocol", it, ROSU_STINS) }
        if (stare.conectat) {
            Rand("Viteza de citire", "%.1f raspunsuri/s".format(stare.raspunsuriPeSecunda), CYAN)
            Rand("PID-uri utile", "${stare.pidSuportate.size}", ROSU_STINS)
        }
        if (stare.areDateHibride) {
            val mod = ModHibrid.din(stare)
            Rand(
                "Mod",
                mod.eticheta,
                when (mod) {
                    ModHibrid.REGEN -> CYAN
                    ModHibrid.EV -> CYAN
                    ModHibrid.HYBRID -> AMBRA
                    else -> ROSU_STINS
                },
            )
            // Puterea cu semn e greu de citit dintr-o cifra: spunem in
            // cuvinte incotro merge curentul.
            stare.valoare(Marime.HV_PUTERE)?.let { kw ->
                val incotro = when {
                    kw <= -ModHibrid.PRAG_REGEN_KW -> "spre baterie"
                    kw >= ModHibrid.PRAG_REGEN_KW -> "spre roti"
                    else -> "in echilibru"
                }
                Rand(
                    "Putere",
                    "%.1f kW %s".format(abs(kw), incotro),
                    if (kw < 0) CYAN else ROSU,
                )
            }
        }
        stare.mesajEroare?.let { Rand("Ultima eroare", it, AMBRA) }
        if (stare.erori > 0) Rand("Reconectari", "${stare.erori}", AMBRA)
        if (stare.simulator) Rand("Atentie", "date din simulator, nu din masina", AMBRA)
    }
}

/**
 * Aparatele imperecheate, de atins ca sa se conecteze. Nu ghicim noi: prima
 * oara aplicatia a ales singura primul aparat in ordine alfabetica si s-a
 * legat la ce nu trebuia. Cele care par adaptoare OBD sunt insemnate.
 */
@Composable
private fun ListaAdaptoare(adaptoare: List<Adaptor>, vm: MainViewModel, cuSonda: Boolean) {
    LazyColumn(verticalArrangement = Arrangement.spacedBy(6.dp)) {
        items(adaptoare) { adaptor ->
            val pare = vm.pareAdaptorObd(adaptor)
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PANOU, RoundedCornerShape(6.dp))
                    .clickable { vm.conecteaza(adaptor, cuSonda) }
                    .padding(horizontal = 12.dp, vertical = 12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Column(modifier = Modifier.weight(1f)) {
                    Text(
                        text = adaptor.nume,
                        color = if (pare) ROSU else ROSU_STINS,
                        fontSize = 15.sp,
                        fontWeight = if (pare) FontWeight.Bold else FontWeight.Normal,
                        fontFamily = FontFamily.Monospace,
                    )
                    Text(
                        text = adaptor.adresa,
                        color = ROSU_STINS,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
                if (pare) {
                    Text(
                        text = "pare adaptor OBD",
                        color = CYAN,
                        fontSize = 11.sp,
                        fontFamily = FontFamily.Monospace,
                    )
                }
            }
        }
    }
}

/** Raspunsurile brute ale calculatoarelor hibride, pe scurt. */
@Composable
private fun RezultatSonda(randuri: List<RandSonda>) {
    val cuDate = randuri.count { it.areDate }
    Column(
        modifier = Modifier
            .fillMaxWidth()
            .background(PANOU, RoundedCornerShape(6.dp))
            .padding(10.dp),
        verticalArrangement = Arrangement.spacedBy(3.dp),
    ) {
        Text(
            text = "Sonda hibrida: $cuDate din ${randuri.size} cereri au dat date",
            color = if (cuDate > 0) CYAN else AMBRA,
            fontSize = 12.sp,
            fontWeight = FontWeight.Bold,
            fontFamily = FontFamily.Monospace,
        )
        for (rand in randuri) {
            Text(
                text = "${rand.adresa} ${rand.cerere}  ${rand.raspuns.take(60)}",
                color = if (rand.areDate) ROSU else ROSU_STINS,
                fontSize = 10.sp,
                fontFamily = FontFamily.Monospace,
            )
        }
    }
}

@Composable
private fun ListaValori(stare: CarState) {
    if (stare.valori.isEmpty()) {
        Text(
            text = "Nicio valoare inca.",
            color = ROSU_STINS,
            fontFamily = FontFamily.Monospace,
            fontSize = 13.sp,
        )
        return
    }
    val acum = System.currentTimeMillis()
    LazyColumn(verticalArrangement = Arrangement.spacedBy(4.dp)) {
        items(Marime.entries.filter { it in stare.valori.keys }) { marime ->
            val citire = stare.valori.getValue(marime)
            val veche = citire.vecheDeMs(acum) > 5_000
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PANOU, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = marime.eticheta,
                    color = if (veche) ROSU_STINS else ROSU,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f %s".format(citire.valoare, marime.unitate),
                    color = if (veche) ROSU_STINS else CYAN,
                    fontSize = 15.sp,
                    fontWeight = FontWeight.Bold,
                    fontFamily = FontFamily.Monospace,
                )
            }
        }
    }
}

@Composable
private fun Rand(eticheta: String, valoare: String, culoare: Color) {
    Row(
        modifier = Modifier.fillMaxWidth(),
        verticalAlignment = Alignment.Top,
    ) {
        // Eticheta sta pe o latime fixa, ca valorile lungi (protocolul) sa
        // aiba loc sa se rupa in randuri, nu sa rupa eticheta in doua.
        Text(
            text = eticheta,
            color = ROSU_STINS,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            maxLines = 1,
            modifier = Modifier.width(118.dp),
        )
        Text(
            text = valoare,
            color = culoare,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            textAlign = TextAlign.End,
            modifier = Modifier.weight(1f),
        )
    }
}

private fun textStare(s: StareConexiune): String = when (s) {
    StareConexiune.DECONECTAT -> "deconectat"
    StareConexiune.SE_CONECTEAZA -> "se conecteaza..."
    StareConexiune.SE_INITIALIZEAZA -> "pregatesc adaptorul..."
    StareConexiune.CONECTAT -> "CONECTAT"
    StareConexiune.EROARE -> "eroare, reiau"
}
