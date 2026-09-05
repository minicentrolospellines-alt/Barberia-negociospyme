package cl.negociospyme.barberia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState) {
            setContent {
                BarberiaApp()
            }
        }
    }
}

@Composable
fun BarberiaApp() {
    var login by remember { mutableStateOf(false) }

    if (!login) {
        LoginScreen {
            login = true
        }
    } else {
        InicioScreen()
    }
}

@Composable
fun LoginScreen(onLogin: () -> Unit) {
    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Color(0xFF111111))
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            "BARBERÍA",
            color = Color(0xFFD4AF37),
            fontSize = 36.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "NegociosPyme",
            color = Color.White,
            fontSize = 18.sp
        )

        Spacer(modifier = Modifier.height(35.dp))

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp)
        ) {
            Text("INGRESAR A DEMO")
        }
    }
}

@Composable
fun InicioScreen() {

    val dorado = Color(0xFFD4AF37)
    val fondo = Color(0xFF111111)
    val tarjeta = Color(0xFF1B1B1B)

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(fondo)
            .padding(20.dp)
    ) {

        Text(
            "Barbería Central",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Panel de administración",
            color = Color.Gray
        )

        Spacer(modifier = Modifier.height(25.dp))

        Text(
            "8 citas hoy",
            color = dorado,
            fontSize = 22.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            "Ventas del día: $84.000",
            color = Color.White
        )

        Spacer(modifier = Modifier.height(25.dp))

        val opciones = listOf(
            "📅 Agenda",
            "👥 Clientes",
            "💰 Caja",
            "✂️ Barberos",
            "🧴 Servicios",
            "📊 Reportes",
            "📱 QR Reservas",
            "⚙️ Configuración"
        )

        opciones.forEach { opcion ->

            Surface(
                modifier = Modifier
                    .fillMaxWidth()
                    .padding(vertical = 5.dp),
                color = tarjeta,
                shape = RoundedCornerShape(15.dp)
            ) {

                Text(
                    text = opcion,
                    color = Color.White,
                    fontSize = 18.sp,
                    modifier = Modifier.padding(18.dp)
                )
            }
        }
    }
}
