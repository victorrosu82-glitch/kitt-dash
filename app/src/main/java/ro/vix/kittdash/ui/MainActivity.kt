package ro.vix.kittdash.ui

import android.Manifest
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
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
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import androidx.lifecycle.viewmodel.compose.viewModel
import ro.vix.kittdash.obd.CarState
import ro.vix.kittdash.obd.ObdPid
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
        setContent {
            MaterialTheme {
                Surface(color = FUNDAL, modifier = Modifier.fillMaxSize()) {
                    EcranDeTest()
                }
            }
        }
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
            text = "etapa 1 - legatura cu masina",
            color = ROSU_STINS,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
        )

        Spacer(Modifier.height(16.dp))
        PanouStare(stare)

        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            if (stare.conexiune == StareConexiune.DECONECTAT) {
                Button(
                    onClick = { vm.adaptorProbabil()?.let(vm::conecteaza) },
                    colors = ButtonDefaults.buttonColors(containerColor = ROSU),
                    enabled = adaptoare.isNotEmpty(),
                ) { Text("Conecteaza") }
                OutlinedButton(onClick = vm::conecteazaSimulator) {
                    Text("Simulator", color = CYAN)
                }
            } else {
                OutlinedButton(onClick = vm::deconecteaza) {
                    Text("Opreste", color = AMBRA)
                }
            }
            OutlinedButton(onClick = { adaptoare = vm.adaptoare() }) {
                Text("Caut adaptoare", color = ROSU_STINS)
            }
        }

        if (stare.conexiune == StareConexiune.DECONECTAT) {
            Spacer(Modifier.height(12.dp))
            Text(
                text = if (adaptoare.isEmpty()) {
                    "Niciun aparat imperecheat. Imperecheaza adaptorul din " +
                        "Setari > Bluetooth (cod 1234), apoi apasa \"Caut adaptoare\"."
                } else {
                    "Imperecheate: " + adaptoare.joinToString { it.nume }
                },
                color = ROSU_STINS,
                fontSize = 12.sp,
                fontFamily = FontFamily.Monospace,
            )
        }

        Spacer(Modifier.height(16.dp))
        ListaValori(stare)
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
        stare.mesajEroare?.let { Rand("Ultima eroare", it, AMBRA) }
        if (stare.erori > 0) Rand("Reconectari", "${stare.erori}", AMBRA)
        if (stare.simulator) Rand("Atentie", "date din simulator, nu din masina", AMBRA)
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
        items(ObdPid.entries.filter { it in stare.valori.keys }) { pid ->
            val citire = stare.valori.getValue(pid)
            val veche = citire.vecheDeMs(acum) > 5_000
            Row(
                modifier = Modifier
                    .fillMaxWidth()
                    .background(PANOU, RoundedCornerShape(4.dp))
                    .padding(horizontal = 10.dp, vertical = 8.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                Text(
                    text = pid.eticheta,
                    color = if (veche) ROSU_STINS else ROSU,
                    fontSize = 13.sp,
                    fontFamily = FontFamily.Monospace,
                    modifier = Modifier.weight(1f),
                )
                Text(
                    text = "%.1f %s".format(citire.valoare, pid.unitate),
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
    Row(modifier = Modifier.fillMaxWidth()) {
        Text(
            text = eticheta,
            color = ROSU_STINS,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
            modifier = Modifier.weight(1f),
        )
        Text(
            text = valoare,
            color = culoare,
            fontSize = 12.sp,
            fontFamily = FontFamily.Monospace,
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
