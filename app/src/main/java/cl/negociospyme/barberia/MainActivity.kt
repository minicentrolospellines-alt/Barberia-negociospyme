package cl.negociospyme.barberia

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp

private val Fondo = Color(0xFF111111)
private val Tarjeta = Color(0xFF1B1B1B)
private val Dorado = Color(0xFFD4AF37)
private val Gris = Color(0xFFAAAAAA)

class MainActivity : ComponentActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Dorado,
                    background = Fondo,
                    surface = Tarjeta
                )
            ) {
                BarberiaApp()
            }
        }
    }
}

@Composable
fun BarberiaApp() {

    var pantalla by remember { mutableStateOf("login") }

    when (pantalla) {

        "login" -> LoginScreen(
            onLogin = {
                pantalla = "inicio"
            }
        )

        "inicio" -> InicioScreen(
            onAbrir = {
                pantalla = it
            },
            onSalir = {
                pantalla = "login"
            }
        )

        "agenda" -> PantallaSimple(
            titulo = "Agenda",
            contenido = listOf(
                "13:30 - Carlos González",
                "Corte + barba - Diego",
                "",
                "14:15 - Martín Pérez",
                "Corte clásico - Sebastián",
                "",
                "15:00 - Felipe Soto",
                "Degradado - Diego"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "clientes" -> PantallaSimple(
            titulo = "Clientes",
            contenido = listOf(
                "Carlos González - 8 visitas",
                "Martín Pérez - 5 visitas",
                "Felipe Soto - 11 visitas",
                "Nicolás Rojas - 3 visitas"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "caja" -> PantallaSimple(
            titulo = "Caja",
            contenido = listOf(
                "Ventas de hoy: $84.000",
                "",
                "Efectivo: $36.000",
                "Transferencia: $32.000",
                "Débito / Crédito: $16.000"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "barberos" -> PantallaSimple(
            titulo = "Barberos",
            contenido = listOf(
                "Diego - Activo - Comisión 40%",
                "Sebastián - Activo - Comisión 40%",
                "Andrés - Activo - Comisión 45%"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "servicios" -> PantallaSimple(
            titulo = "Servicios",
            contenido = listOf(
                "Corte clásico - $12.000",
                "Degradado - $14.000",
                "Barba - $8.000",
                "Corte + barba - $18.000"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "reportes" -> PantallaSimple(
            titulo = "Reportes",
            contenido = listOf(
                "Ventas del día",
                "Ventas por barbero",
                "Servicios más vendidos",
                "Comisiones",
                "Clientes frecuentes"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "qr" -> PantallaSimple(
            titulo = "QR Reservas",
            contenido = listOf(
                "Aquí aparecerá el QR de la barbería.",
                "",
                "Los clientes podrán escanearlo para reservar una hora.",
                "",
                "También podremos compartir el enlace por WhatsApp."
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )

        "configuracion" -> PantallaSimple(
            titulo = "Configuración",
            contenido = listOf(
                "Datos de la barbería",
                "Horarios",
                "Logo",
                "Dirección",
                "Recordatorios",
                "Plan y suscripción"
            ),
            onVolver = {
                pantalla = "inicio"
            }
        )
    }
}

@Composable
fun LoginScreen(
    onLogin: () -> Unit
) {

    var correo by remember {
        mutableStateOf("admin@barberia.cl")
    }

    var clave by remember {
        mutableStateOf("123456")
    }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {

        Text(
            text = "BARBERÍA",
            color = Dorado,
            fontSize = 36.sp,
            fontWeight = FontWeight.Black
        )

        Text(
            text = "NegociosPyme",
            color = Color.White,
            fontSize = 18.sp
        )

        Spacer(
            modifier = Modifier.height(35.dp)
        )

        OutlinedTextField(
            value = correo,
            onValueChange = {
                correo = it
            },
            label = {
                Text("Correo")
            },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(12.dp)
        )

        OutlinedTextField(
            value = clave,
            onValueChange = {
                clave = it
            },
            label = {
                Text("Contraseña")
            },
            visualTransformation = PasswordVisualTransformation(),
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Button(
            onClick = onLogin,
            modifier = Modifier
                .fillMaxWidth()
                .height(55.dp),
            colors = ButtonDefaults.buttonColors(
                containerColor = Dorado,
                contentColor = Color.Black
            )
        ) {

            Text(
                text = "INGRESAR",
                fontWeight = FontWeight.Bold
            )
        }

        Spacer(
            modifier = Modifier.height(15.dp)
        )

        Text(
            text = "Versión inicial de prueba",
            color = Gris,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        )
    }
}

@Composable
fun InicioScreen(
    onAbrir: (String) -> Unit,
    onSalir: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp)
    ) {

        Text(
            text = "Barbería Central",
            color = Color.White,
            fontSize = 27.sp,
            fontWeight = FontWeight.Bold
        )

        Text(
            text = "Panel de administración",
            color = Gris
        )

        Spacer(
            modifier = Modifier.height(22.dp)
        )

        Row(
            modifier = Modifier.fillMaxWidth(),
            horizontalArrangement = Arrangement.spacedBy(
                10.dp
            )
        ) {

            ResumenCard(
                numero = "8",
                texto = "Citas",
                modifier = Modifier.weight(1f)
            )

            ResumenCard(
                numero = "$84.000",
                texto = "Ventas",
                modifier = Modifier.weight(1f)
            )

            ResumenCard(
                numero = "3",
                texto = "Barberos",
                modifier = Modifier.weight(1f)
            )
        }

        Spacer(
            modifier = Modifier.height(25.dp)
        )

        Text(
            text = "Gestión",
            color = Color.White,
            fontSize = 20.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(10.dp)
        )

        BotonMenu(
            titulo = "📅 Agenda",
            subtitulo = "Reservas y horarios"
        ) {
            onAbrir("agenda")
        }

        BotonMenu(
            titulo = "👥 Clientes",
            subtitulo = "Historial de clientes"
        ) {
            onAbrir("clientes")
        }

        BotonMenu(
            titulo = "💰 Caja",
            subtitulo = "Ventas y medios de pago"
        ) {
            onAbrir("caja")
        }

        BotonMenu(
            titulo = "✂️ Barberos",
            subtitulo = "Equipo y comisiones"
        ) {
            onAbrir("barberos")
        }

        BotonMenu(
            titulo = "🧴 Servicios",
            subtitulo = "Servicios, valores y duración"
        ) {
            onAbrir("servicios")
        }

        BotonMenu(
            titulo = "📊 Reportes",
            subtitulo = "Ventas y estadísticas"
        ) {
            onAbrir("reportes")
        }

        BotonMenu(
            titulo = "📱 QR Reservas",
            subtitulo = "Reservas por QR o enlace"
        ) {
            onAbrir("qr")
        }

        BotonMenu(
            titulo = "⚙️ Configuración",
            subtitulo = "Datos y ajustes del negocio"
        ) {
            onAbrir("configuracion")
        }

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        OutlinedButton(
            onClick = onSalir,
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Cerrar sesión")
        }

        Spacer(
            modifier = Modifier.height(25.dp)
        )

        Text(
            text = "Barbería NegociosPyme",
            color = Dorado,
            fontSize = 13.sp,
            modifier = Modifier.align(
                Alignment.CenterHorizontally
            )
        )
    }
}

@Composable
fun ResumenCard(
    numero: String,
    texto: String,
    modifier: Modifier = Modifier
) {

    Surface(
        modifier = modifier,
        color = Tarjeta,
        shape = RoundedCornerShape(15.dp)
    ) {

        Column(
            modifier = Modifier.padding(12.dp)
        ) {

            Text(
                text = numero,
                color = Dorado,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Text(
                text = texto,
                color = Gris,
                fontSize = 12.sp
            )
        }
    }
}

@Composable
fun BotonMenu(
    titulo: String,
    subtitulo: String,
    onClick: () -> Unit
) {

    Surface(
        modifier = Modifier
            .fillMaxWidth()
            .padding(vertical = 5.dp)
            .clickable {
                onClick()
            },
        color = Tarjeta,
        shape = RoundedCornerShape(15.dp)
    ) {

        Column(
            modifier = Modifier.padding(17.dp)
        ) {

            Text(
                text = titulo,
                color = Color.White,
                fontSize = 18.sp,
                fontWeight = FontWeight.Bold
            )

            Spacer(
                modifier = Modifier.height(3.dp)
            )

            Text(
                text = subtitulo,
                color = Gris,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun PantallaSimple(
    titulo: String,
    contenido: List<String>,
    onVolver: () -> Unit
) {

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .verticalScroll(
                rememberScrollState()
            )
            .padding(20.dp)
    ) {

        Text(
            text = "← Volver",
            color = Dorado,
            fontSize = 17.sp,
            modifier = Modifier.clickable {
                onVolver()
            }
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        Text(
            text = titulo,
            color = Color.White,
            fontSize = 30.sp,
            fontWeight = FontWeight.Bold
        )

        Spacer(
            modifier = Modifier.height(20.dp)
        )

        contenido.forEach { texto ->

            if (texto.isBlank()) {

                Spacer(
                    modifier = Modifier.height(10.dp)
                )

            } else {

                Surface(
                    modifier = Modifier
                        .fillMaxWidth()
                        .padding(vertical = 5.dp),
                    color = Tarjeta,
                    shape = RoundedCornerShape(14.dp)
                ) {

                    Text(
                        text = texto,
                        color = Color.White,
                        fontSize = 16.sp,
                        modifier = Modifier.padding(16.dp)
                    )
                }
            }
        }
    }
}
