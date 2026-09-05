package cl.negociospyme.barberia

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Fondo = Color(0xFF101010)
private val Tarjeta = Color(0xFF1A1A1A)
private val Tarjeta2 = Color(0xFF222222)
private val Dorado = Color(0xFFD4AF37)
private val DoradoSuave = Color(0xFFFFE7A0)
private val Gris = Color(0xFFAAAAAA)
private val Verde = Color(0xFF4CAF50)
private val Rojo = Color(0xFFE85D5D)

data class Appointment(
    val id: Long,
    val client: String,
    val phone: String,
    val service: String,
    val barber: String,
    val date: String,
    val time: String,
    val status: String,
    val price: Int
)

data class ClientItem(
    val id: Long,
    val name: String,
    val phone: String,
    val visits: Int,
    val spent: Int,
    val notes: String
)

data class BarberItem(
    val id: Long,
    val name: String,
    val commission: Int,
    val active: Boolean
)

data class ServiceItem(
    val id: Long,
    val name: String,
    val price: Int,
    val duration: Int,
    val active: Boolean
)

data class SaleItem(
    val id: Long,
    val client: String,
    val service: String,
    val barber: String,
    val amount: Int,
    val payment: String,
    val date: String
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent {
            MaterialTheme(
                colorScheme = darkColorScheme(
                    primary = Dorado,
                    secondary = DoradoSuave,
                    background = Fondo,
                    surface = Tarjeta,
                    onPrimary = Color.Black,
                    onBackground = Color.White,
                    onSurface = Color.White
                )
            ) {
                BarberiaApp()
            }
        }
    }
}

@Composable
fun BarberiaApp() {
    val context = androidx.compose.ui.platform.LocalContext.current

    var loggedIn by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("inicio") }
    var shopName by remember { mutableStateOf(LocalStore.getShopName(context)) }
    var shopPhone by remember { mutableStateOf(LocalStore.getShopPhone(context)) }

    val appointments = remember(context) {
        mutableStateListOf<Appointment>().also { it.addAll(LocalStore.loadAppointments(context)) }
    }
    val clients = remember(context) {
        mutableStateListOf<ClientItem>().also { it.addAll(LocalStore.loadClients(context)) }
    }
    val barbers = remember(context) {
        mutableStateListOf<BarberItem>().also { it.addAll(LocalStore.loadBarbers(context)) }
    }
    val services = remember(context) {
        mutableStateListOf<ServiceItem>().also { it.addAll(LocalStore.loadServices(context)) }
    }
    val sales = remember(context) {
        mutableStateListOf<SaleItem>().also { it.addAll(LocalStore.loadSales(context)) }
    }

    if (!loggedIn) {
        LoginScreen(shopName = shopName, onLogin = { loggedIn = true })
        return
    }

    when (screen) {
        "inicio", "agenda", "caja", "clientes", "mas" -> {
            Scaffold(
                containerColor = Fondo,
                bottomBar = {
                    NavigationBar(containerColor = Tarjeta) {
                        BottomItem("inicio", "Inicio", Icons.Default.Home, screen) { screen = "inicio" }
                        BottomItem("agenda", "Agenda", Icons.Default.CalendarMonth, screen) { screen = "agenda" }
                        BottomItem("caja", "Caja", Icons.Default.PointOfSale, screen) { screen = "caja" }
                        BottomItem("clientes", "Clientes", Icons.Default.People, screen) { screen = "clientes" }
                        BottomItem("mas", "Más", Icons.Default.MoreHoriz, screen) { screen = "mas" }
                    }
                }
            ) { padding ->
                Box(Modifier.padding(padding)) {
                    when (screen) {
                        "inicio" -> DashboardScreen(
                            shopName = shopName,
                            appointments = appointments,
                            barbers = barbers,
                            sales = sales,
                            onOpenAgenda = { screen = "agenda" },
                            onOpenCaja = { screen = "caja" }
                        )

                        "agenda" -> AgendaScreen(
                            appointments = appointments,
                            clients = clients,
                            services = services,
                            barbers = barbers,
                            onAdd = { appt ->
                                appointments.add(appt)
                                LocalStore.saveAppointments(context, appointments)
                                if (clients.none { it.phone == appt.phone && appt.phone.isNotBlank() } && appt.client.isNotBlank()) {
                                    clients.add(
                                        ClientItem(
                                            id = System.currentTimeMillis(),
                                            name = appt.client,
                                            phone = appt.phone,
                                            visits = 0,
                                            spent = 0,
                                            notes = ""
                                        )
                                    )
                                    LocalStore.saveClients(context, clients)
                                }
                            },
                            onCancel = { appt ->
                                val index = appointments.indexOfFirst { it.id == appt.id }
                                if (index >= 0) {
                                    appointments[index] = appointments[index].copy(status = "Cancelada")
                                    LocalStore.saveAppointments(context, appointments)
                                }
                            },
                            onCharge = { appt, payment ->
                                val sale = SaleItem(
                                    id = System.currentTimeMillis(),
                                    client = appt.client,
                                    service = appt.service,
                                    barber = appt.barber,
                                    amount = appt.price,
                                    payment = payment,
                                    date = today()
                                )
                                sales.add(0, sale)
                                LocalStore.saveSales(context, sales)

                                val apptIndex = appointments.indexOfFirst { it.id == appt.id }
                                if (apptIndex >= 0) {
                                    appointments[apptIndex] = appointments[apptIndex].copy(status = "Atendida")
                                    LocalStore.saveAppointments(context, appointments)
                                }

                                val clientIndex = clients.indexOfFirst {
                                    (appt.phone.isNotBlank() && it.phone == appt.phone) || it.name.equals(appt.client, true)
                                }
                                if (clientIndex >= 0) {
                                    val old = clients[clientIndex]
                                    clients[clientIndex] = old.copy(
                                        visits = old.visits + 1,
                                        spent = old.spent + appt.price
                                    )
                                } else {
                                    clients.add(
                                        ClientItem(
                                            id = System.currentTimeMillis(),
                                            name = appt.client,
                                            phone = appt.phone,
                                            visits = 1,
                                            spent = appt.price,
                                            notes = ""
                                        )
                                    )
                                }
                                LocalStore.saveClients(context, clients)
                            }
                        )

                        "caja" -> CajaScreen(
                            sales = sales,
                            clients = clients,
                            services = services,
                            barbers = barbers,
                            onAddSale = { sale ->
                                sales.add(0, sale)
                                LocalStore.saveSales(context, sales)
                                val clientIndex = clients.indexOfFirst { it.name.equals(sale.client, true) }
                                if (clientIndex >= 0) {
                                    val old = clients[clientIndex]
                                    clients[clientIndex] = old.copy(visits = old.visits + 1, spent = old.spent + sale.amount)
                                    LocalStore.saveClients(context, clients)
                                }
                            }
                        )

                        "clientes" -> ClientsScreen(
                            clients = clients,
                            onAdd = { client ->
                                clients.add(0, client)
                                LocalStore.saveClients(context, clients)
                            }
                        )

                        "mas" -> MoreScreen(
                            onBarbers = { screen = "barberos" },
                            onServices = { screen = "servicios" },
                            onQr = { screen = "qr" },
                            onSettings = { screen = "config" },
                            onLogout = { loggedIn = false }
                        )
                    }
                }
            }
        }

        "barberos" -> BarbersScreen(
            barbers = barbers,
            onBack = { screen = "mas" },
            onAdd = { barber ->
                barbers.add(barber)
                LocalStore.saveBarbers(context, barbers)
            },
            onToggle = { barber ->
                val index = barbers.indexOfFirst { it.id == barber.id }
                if (index >= 0) {
                    barbers[index] = barber.copy(active = !barber.active)
                    LocalStore.saveBarbers(context, barbers)
                }
            }
        )

        "servicios" -> ServicesScreen(
            services = services,
            onBack = { screen = "mas" },
            onAdd = { service ->
                services.add(service)
                LocalStore.saveServices(context, services)
            },
            onToggle = { service ->
                val index = services.indexOfFirst { it.id == service.id }
                if (index >= 0) {
                    services[index] = service.copy(active = !service.active)
                    LocalStore.saveServices(context, services)
                }
            }
        )

        "qr" -> QrScreen(onBack = { screen = "mas" })

        "config" -> SettingsScreen(
            shopName = shopName,
            shopPhone = shopPhone,
            onBack = { screen = "mas" },
            onSave = { name, phone ->
                shopName = name
                shopPhone = phone
                LocalStore.setShopName(context, name)
                LocalStore.setShopPhone(context, phone)
            }
        )
    }
}

@Composable
private fun RowScope.BottomItem(
    route: String,
    label: String,
    icon: ImageVector,
    current: String,
    onClick: () -> Unit
) {
    NavigationBarItem(
        selected = current == route,
        onClick = onClick,
        icon = { Icon(icon, contentDescription = null) },
        label = { Text(label, fontSize = 11.sp) },
        colors = NavigationBarItemDefaults.colors(
            selectedIconColor = Color.Black,
            selectedTextColor = Dorado,
            indicatorColor = Dorado,
            unselectedIconColor = Gris,
            unselectedTextColor = Gris
        )
    )
}

@Composable
fun LoginScreen(shopName: String, onLogin: () -> Unit) {
    var email by remember { mutableStateOf("admin@barberia.cl") }
    var password by remember { mutableStateOf("123456") }

    Column(
        modifier = Modifier
            .fillMaxSize()
            .background(Fondo)
            .padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = Dorado, shape = RoundedCornerShape(20.dp)) {
            Icon(
                Icons.Default.ContentCut,
                contentDescription = null,
                tint = Color.Black,
                modifier = Modifier.padding(16.dp).size(38.dp)
            )
        }
        Spacer(Modifier.height(20.dp))
        Text("BARBERÍA", color = Dorado, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(shopName, color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))

        OutlinedTextField(
            value = email,
            onValueChange = { email = it },
            label = { Text("Correo") },
            leadingIcon = { Icon(Icons.Default.Email, null) },
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(
            value = password,
            onValueChange = { password = it },
            label = { Text("Contraseña") },
            leadingIcon = { Icon(Icons.Default.Lock, null) },
            visualTransformation = PasswordVisualTransformation(),
            singleLine = true,
            modifier = Modifier.fillMaxWidth()
        )
        Spacer(Modifier.height(20.dp))
        Button(
            onClick = onLogin,
            modifier = Modifier.fillMaxWidth().height(54.dp),
            colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black),
            shape = RoundedCornerShape(15.dp)
        ) {
            Text("INGRESAR", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("v2 local · los datos quedan guardados en este teléfono", color = Gris, fontSize = 12.sp)
    }
}

@Composable
fun DashboardScreen(
    shopName: String,
    appointments: List<Appointment>,
    barbers: List<BarberItem>,
    sales: List<SaleItem>,
    onOpenAgenda: () -> Unit,
    onOpenCaja: () -> Unit
) {
    val today = today()
    val todayAppointments = appointments.count { it.date == today && it.status != "Cancelada" }
    val todaySales = sales.filter { it.date == today }.sumOf { it.amount }
    val activeBarbers = barbers.count { it.active }
    val next = appointments
        .filter { it.date == today && it.status == "Pendiente" }
        .sortedBy { it.time }
        .firstOrNull()

    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Fondo),
        contentPadding = PaddingValues(18.dp),
        verticalArrangement = Arrangement.spacedBy(14.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Panel de administración", color = Gris, fontSize = 13.sp)
                    Text(shopName, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Surface(color = Dorado, shape = RoundedCornerShape(18.dp)) {
                    Icon(Icons.Default.ContentCut, null, tint = Color.Black, modifier = Modifier.padding(13.dp).size(28.dp))
                }
            }
        }

        item {
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(10.dp)) {
                StatCard(todayAppointments.toString(), "Citas hoy", Modifier.weight(1f))
                StatCard(money(todaySales), "Ventas", Modifier.weight(1f))
                StatCard(activeBarbers.toString(), "Barberos", Modifier.weight(1f))
            }
        }

        item {
            Text("Próxima cita", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            if (next == null) {
                EmptyCard("No hay citas pendientes para hoy.")
            } else {
                Surface(
                    modifier = Modifier.fillMaxWidth().clickable { onOpenAgenda() },
                    color = Tarjeta,
                    shape = RoundedCornerShape(18.dp)
                ) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Dorado.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) {
                            Text(next.time, color = Dorado, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp))
                        }
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(next.client, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${next.service} · ${next.barber}", color = Gris, fontSize = 13.sp)
                        }
                        Icon(Icons.Default.ChevronRight, null, tint = Dorado)
                    }
                }
            }
        }

        item {
            Text("Accesos rápidos", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold)
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(12.dp)) {
                QuickCard("Agenda", "Nueva cita", Icons.Default.CalendarMonth, Modifier.weight(1f), onOpenAgenda)
                QuickCard("Caja", "Registrar venta", Icons.Default.PointOfSale, Modifier.weight(1f), onOpenCaja)
            }
        }

        item {
            Surface(color = Dorado.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp)) {
                Row(Modifier.fillMaxWidth().padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Icon(Icons.Default.QrCode2, null, tint = Dorado, modifier = Modifier.size(34.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Reservas por QR", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("Módulo preparado para conectar la reserva web en la siguiente etapa.", color = Gris, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Tarjeta, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(13.dp)) {
            Text(value, color = Dorado, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1)
            Text(label, color = Gris, fontSize = 11.sp)
        }
    }
}

@Composable
fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable { onClick() }, color = Tarjeta, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) {
            Icon(icon, null, tint = Dorado, modifier = Modifier.size(30.dp))
            Spacer(Modifier.height(14.dp))
            Text(title, color = Color.White, fontWeight = FontWeight.Bold)
            Text(subtitle, color = Gris, fontSize = 12.sp)
        }
    }
}

@Composable
fun EmptyCard(text: String) {
    Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
        Text(text, color = Gris, modifier = Modifier.padding(18.dp))
    }
}

@Composable
fun AgendaScreen(
    appointments: List<Appointment>,
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    onAdd: (Appointment) -> Unit,
    onCancel: (Appointment) -> Unit,
    onCharge: (Appointment, String) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf<Appointment?>(null) }

    Scaffold(
        containerColor = Fondo,
        topBar = { SectionHeader("Agenda", "Reservas y horarios") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        }
    ) { padding ->
        val sorted = appointments.sortedWith(compareBy<Appointment> { it.date }.thenBy { it.time })
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (sorted.isEmpty()) item { EmptyCard("Todavía no hay citas. Presiona + para crear la primera.") }
            items(sorted, key = { it.id }) { appt ->
                AppointmentCard(
                    appt = appt,
                    onCancel = { onCancel(appt) },
                    onCharge = { charging = appt }
                )
            }
        }
    }

    if (showAdd) {
        NewAppointmentDialog(
            clients = clients,
            services = services.filter { it.active },
            barbers = barbers.filter { it.active },
            onDismiss = { showAdd = false },
            onSave = {
                onAdd(it)
                showAdd = false
            }
        )
    }

    charging?.let { appt ->
        PaymentDialog(
            title = "Cobrar ${appt.client}",
            amount = appt.price,
            onDismiss = { charging = null },
            onConfirm = { payment ->
                onCharge(appt, payment)
                charging = null
            }
        )
    }
}

@Composable
fun AppointmentCard(appt: Appointment, onCancel: () -> Unit, onCharge: () -> Unit) {
    val statusColor = when (appt.status) {
        "Atendida" -> Verde
        "Cancelada" -> Rojo
        else -> Dorado
    }
    Surface(color = Tarjeta, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) {
                    Text(appt.time, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp))
                }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(appt.client, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${appt.date} · ${appt.service}", color = Gris, fontSize = 12.sp)
                }
                Text(money(appt.price), color = Dorado, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            Text("Barbero: ${appt.barber}", color = Gris, fontSize = 13.sp)
            if (appt.phone.isNotBlank()) Text("Tel: ${appt.phone}", color = Gris, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appt.status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (appt.status == "Pendiente") {
                    TextButton(onClick = onCancel) { Text("Cancelar", color = Rojo) }
                    Button(
                        onClick = onCharge,
                        colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
                    ) { Text("Cobrar") }
                }
            }
        }
    }
}

@Composable
fun NewAppointmentDialog(
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit
) {
    var client by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var serviceName by remember { mutableStateOf(services.firstOrNull()?.name ?: "") }
    var barberName by remember { mutableStateOf(barbers.firstOrNull()?.name ?: "") }
    var date by remember { mutableStateOf(today()) }
    var time by remember { mutableStateOf("10:00") }
    val selectedService = services.firstOrNull { it.name == serviceName }
    val price = selectedService?.price ?: 0

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva cita") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono / WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectField("Servicio", serviceName, services.map { it.name }) { serviceName = it }
                SelectField("Barbero", barberName, barbers.map { it.name }) { barberName = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (dd/MM/yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Hora (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("Valor: ${money(price)}", color = Dorado, fontWeight = FontWeight.Bold)
                if (clients.isNotEmpty()) Text("Tip: si el cliente ya existe, puedes escribir el mismo nombre y teléfono.", color = Gris, fontSize = 11.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (client.isNotBlank() && serviceName.isNotBlank() && barberName.isNotBlank()) {
                        onSave(
                            Appointment(
                                id = System.currentTimeMillis(),
                                client = client.trim(),
                                phone = phone.trim(),
                                service = serviceName,
                                barber = barberName,
                                date = date.trim(),
                                time = time.trim(),
                                status = "Pendiente",
                                price = price
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CajaScreen(
    sales: List<SaleItem>,
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    onAddSale: (SaleItem) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    val todaySales = sales.filter { it.date == today() }
    val total = todaySales.sumOf { it.amount }
    val cash = todaySales.filter { it.payment == "Efectivo" }.sumOf { it.amount }
    val transfer = todaySales.filter { it.payment == "Transferencia" }.sumOf { it.amount }
    val card = todaySales.filter { it.payment == "Débito / Crédito" }.sumOf { it.amount }

    Scaffold(
        containerColor = Fondo,
        topBar = { SectionHeader("Caja", "Ventas y medios de pago") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) {
                Icon(Icons.Default.AddShoppingCart, null, tint = Color.Black)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(12.dp)
        ) {
            item {
                Text("Total de hoy", color = Gris)
                Text(money(total), color = Dorado, fontSize = 36.sp, fontWeight = FontWeight.Black)
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMoneyCard("Efectivo", cash, Modifier.weight(1f))
                    MiniMoneyCard("Transfer.", transfer, Modifier.weight(1f))
                    MiniMoneyCard("Tarjeta", card, Modifier.weight(1f))
                }
            }
            item { Text("Movimientos", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            if (sales.isEmpty()) item { EmptyCard("Todavía no hay ventas registradas.") }
            items(sales, key = { it.id }) { sale ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ReceiptLong, null, tint = Dorado)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(sale.client.ifBlank { "Venta directa" }, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${sale.service} · ${sale.barber}", color = Gris, fontSize = 12.sp)
                            Text("${sale.date} · ${sale.payment}", color = Gris, fontSize = 11.sp)
                        }
                        Text(money(sale.amount), color = Dorado, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewSaleDialog(
            clients = clients,
            services = services.filter { it.active },
            barbers = barbers.filter { it.active },
            onDismiss = { showAdd = false },
            onSave = {
                onAddSale(it)
                showAdd = false
            }
        )
    }
}

@Composable
fun MiniMoneyCard(label: String, value: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = Tarjeta, shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.padding(10.dp)) {
            Text(money(value), color = Dorado, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1)
            Text(label, color = Gris, fontSize = 10.sp)
        }
    }
}

@Composable
fun NewSaleDialog(
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    onDismiss: () -> Unit,
    onSave: (SaleItem) -> Unit
) {
    var client by remember { mutableStateOf(clients.firstOrNull()?.name ?: "Venta directa") }
    var serviceName by remember { mutableStateOf(services.firstOrNull()?.name ?: "Servicio") }
    var barberName by remember { mutableStateOf(barbers.firstOrNull()?.name ?: "Sin asignar") }
    var amountText by remember { mutableStateOf((services.firstOrNull()?.price ?: 0).toString()) }
    var payment by remember { mutableStateOf("Efectivo") }

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva venta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectField("Servicio", serviceName, services.map { it.name }) {
                    serviceName = it
                    amountText = (services.firstOrNull { s -> s.name == it }?.price ?: 0).toString()
                }
                SelectField("Barbero", barberName, barbers.map { it.name }) { barberName = it }
                OutlinedTextField(
                    amountText,
                    { amountText = it.filter(Char::isDigit) },
                    label = { Text("Monto") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
                SelectField("Pago", payment, listOf("Efectivo", "Transferencia", "Débito / Crédito")) { payment = it }
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val amount = amountText.toIntOrNull() ?: 0
                    if (amount > 0) {
                        onSave(
                            SaleItem(
                                id = System.currentTimeMillis(),
                                client = client.trim(),
                                service = serviceName,
                                barber = barberName,
                                amount = amount,
                                payment = payment,
                                date = today()
                            )
                        )
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun PaymentDialog(title: String, amount: Int, onDismiss: () -> Unit, onConfirm: (String) -> Unit) {
    var payment by remember { mutableStateOf("Efectivo") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Monto: ${money(amount)}", color = Dorado, fontSize = 20.sp, fontWeight = FontWeight.Bold)
                SelectField("Medio de pago", payment, listOf("Efectivo", "Transferencia", "Débito / Crédito")) { payment = it }
            }
        },
        confirmButton = {
            Button(onClick = { onConfirm(payment) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) {
                Text("Confirmar cobro")
            }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ClientsScreen(clients: List<ClientItem>, onAdd: (ClientItem) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val filtered = clients.filter {
        search.isBlank() || it.name.contains(search, true) || it.phone.contains(search, true)
    }

    Scaffold(
        containerColor = Fondo,
        topBar = { SectionHeader("Clientes", "Historial y fidelización") },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) {
                Icon(Icons.Default.PersonAdd, null, tint = Color.Black)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            item {
                OutlinedTextField(
                    value = search,
                    onValueChange = { search = it },
                    label = { Text("Buscar cliente") },
                    leadingIcon = { Icon(Icons.Default.Search, null) },
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
            if (filtered.isEmpty()) item { EmptyCard("No hay clientes para mostrar.") }
            items(filtered, key = { it.id }) { client ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Dorado.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) {
                                Icon(Icons.Default.Person, null, tint = Dorado, modifier = Modifier.padding(10.dp))
                            }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(client.name, color = Color.White, fontWeight = FontWeight.Bold)
                                Text(client.phone.ifBlank { "Sin teléfono" }, color = Gris, fontSize = 12.sp)
                            }
                            Text("${client.visits} visitas", color = Dorado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp))
                        Text("Gasto acumulado: ${money(client.spent)}", color = Gris, fontSize = 13.sp)
                        if (client.notes.isNotBlank()) Text(client.notes, color = Gris, fontSize = 12.sp)
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewClientDialog(
            onDismiss = { showAdd = false },
            onSave = {
                onAdd(it)
                showAdd = false
            }
        )
    }
}

@Composable
fun NewClientDialog(onDismiss: () -> Unit, onSave: (ClientItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var notes by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo cliente") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(ClientItem(System.currentTimeMillis(), name.trim(), phone.trim(), 0, 0, notes.trim()))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun MoreScreen(
    onBarbers: () -> Unit,
    onServices: () -> Unit,
    onQr: () -> Unit,
    onSettings: () -> Unit,
    onLogout: () -> Unit
) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Fondo),
        contentPadding = PaddingValues(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item { SectionHeader("Más", "Administración de la barbería") }
        item { MoreCard(Icons.Default.ContentCut, "Barberos", "Equipo, estado y comisión", onBarbers) }
        item { MoreCard(Icons.Default.Storefront, "Servicios", "Precios y duración", onServices) }
        item { MoreCard(Icons.Default.QrCode2, "QR Reservas", "Enlace público de reservas", onQr) }
        item { MoreCard(Icons.Default.Settings, "Configuración", "Datos de la barbería", onSettings) }
        item { MoreCard(Icons.Default.Logout, "Cerrar sesión", "Salir de esta cuenta", onLogout) }
    }
}

@Composable
fun MoreCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = Tarjeta, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(icon, null, tint = Dorado, modifier = Modifier.size(28.dp))
            Spacer(Modifier.width(14.dp))
            Column(Modifier.weight(1f)) {
                Text(title, color = Color.White, fontWeight = FontWeight.Bold)
                Text(subtitle, color = Gris, fontSize = 12.sp)
            }
            Icon(Icons.Default.ChevronRight, null, tint = Gris)
        }
    }
}

@Composable
fun BarbersScreen(
    barbers: List<BarberItem>,
    onBack: () -> Unit,
    onAdd: (BarberItem) -> Unit,
    onToggle: (BarberItem) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Fondo,
        topBar = { BackHeader("Barberos", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) {
                Icon(Icons.Default.PersonAdd, null, tint = Color.Black)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (barbers.isEmpty()) item { EmptyCard("Agrega el primer barbero.") }
            items(barbers, key = { it.id }) { barber ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, null, tint = if (barber.active) Dorado else Gris)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(barber.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("Comisión ${barber.commission}%", color = Gris, fontSize = 12.sp)
                        }
                        Switch(checked = barber.active, onCheckedChange = { onToggle(barber) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewBarberDialog(onDismiss = { showAdd = false }, onSave = {
            onAdd(it)
            showAdd = false
        })
    }
}

@Composable
fun NewBarberDialog(onDismiss: () -> Unit, onSave: (BarberItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var commission by remember { mutableStateOf("40") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo barbero") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(
                    commission,
                    { commission = it.filter(Char::isDigit).take(3) },
                    label = { Text("Comisión %") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number),
                    singleLine = true,
                    modifier = Modifier.fillMaxWidth()
                )
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank()) onSave(BarberItem(System.currentTimeMillis(), name.trim(), (commission.toIntOrNull() ?: 0).coerceIn(0, 100), true))
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ServicesScreen(
    services: List<ServiceItem>,
    onBack: () -> Unit,
    onAdd: (ServiceItem) -> Unit,
    onToggle: (ServiceItem) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(
        containerColor = Fondo,
        topBar = { BackHeader("Servicios", onBack) },
        floatingActionButton = {
            FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) {
                Icon(Icons.Default.Add, null, tint = Color.Black)
            }
        }
    ) { padding ->
        LazyColumn(
            modifier = Modifier.fillMaxSize().padding(padding),
            contentPadding = PaddingValues(16.dp),
            verticalArrangement = Arrangement.spacedBy(10.dp)
        ) {
            if (services.isEmpty()) item { EmptyCard("Agrega el primer servicio.") }
            items(services, key = { it.id }) { service ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Storefront, null, tint = if (service.active) Dorado else Gris)
                        Spacer(Modifier.width(12.dp))
                        Column(Modifier.weight(1f)) {
                            Text(service.name, color = Color.White, fontWeight = FontWeight.Bold)
                            Text("${money(service.price)} · ${service.duration} min", color = Gris, fontSize = 12.sp)
                        }
                        Switch(checked = service.active, onCheckedChange = { onToggle(service) })
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewServiceDialog(onDismiss = { showAdd = false }, onSave = {
            onAdd(it)
            showAdd = false
        })
    }
}

@Composable
fun NewServiceDialog(onDismiss: () -> Unit, onSave: (ServiceItem) -> Unit) {
    var name by remember { mutableStateOf("") }
    var price by remember { mutableStateOf("") }
    var duration by remember { mutableStateOf("30") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nuevo servicio") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(price, { price = it.filter(Char::isDigit) }, label = { Text("Precio") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duración en minutos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    if (name.isNotBlank() && (price.toIntOrNull() ?: 0) > 0) {
                        onSave(ServiceItem(System.currentTimeMillis(), name.trim(), price.toIntOrNull() ?: 0, duration.toIntOrNull() ?: 30, true))
                    }
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Guardar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun QrScreen(onBack: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Fondo)) {
        BackHeader("QR Reservas", onBack)
        Column(Modifier.padding(20.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
                Icon(Icons.Default.QrCode2, null, tint = Color.Black, modifier = Modifier.padding(34.dp).size(150.dp))
            }
            Spacer(Modifier.height(20.dp))
            Text("Reserva online", color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            Text("https://barberia.negociospyme.cl/reservar", color = Dorado, fontSize = 13.sp)
            Spacer(Modifier.height(16.dp))
            Text(
                "En la siguiente etapa este módulo generará el QR real de cada barbería y recibirá reservas desde la web.",
                color = Gris,
                fontSize = 13.sp
            )
        }
    }
}

@Composable
fun SettingsScreen(
    shopName: String,
    shopPhone: String,
    onBack: () -> Unit,
    onSave: (String, String) -> Unit
) {
    var name by remember(shopName) { mutableStateOf(shopName) }
    var phone by remember(shopPhone) { mutableStateOf(shopPhone) }
    var saved by remember { mutableStateOf(false) }

    Column(Modifier.fillMaxSize().background(Fondo)) {
        BackHeader("Configuración", onBack)
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre de la barbería") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(
                onClick = {
                    if (name.isNotBlank()) {
                        onSave(name.trim(), phone.trim())
                        saved = true
                    }
                },
                modifier = Modifier.fillMaxWidth(),
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text("Guardar cambios") }
            if (saved) Text("Cambios guardados en este teléfono ✓", color = Verde, fontSize = 13.sp)
            Spacer(Modifier.height(8.dp))
            Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp)) {
                Column(Modifier.padding(16.dp)) {
                    Text("Versión 2", color = Dorado, fontWeight = FontWeight.Bold)
                    Text("Agenda, clientes, caja, barberos y servicios con almacenamiento local.", color = Gris, fontSize = 12.sp)
                }
            }
        }
    }
}

@Composable
fun SelectField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Gris, fontSize = 12.sp)
        Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(value.ifBlank { "Seleccionar" }, modifier = Modifier.weight(1f), color = Color.White)
                Icon(Icons.Default.ArrowDropDown, null)
            }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option ->
                    DropdownMenuItem(
                        text = { Text(option) },
                        onClick = {
                            onSelect(option)
                            expanded = false
                        }
                    )
                }
                if (options.isEmpty()) {
                    DropdownMenuItem(text = { Text("Sin opciones") }, onClick = { expanded = false })
                }
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Fondo).padding(horizontal = 18.dp, vertical = 14.dp)) {
        Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
        Text(subtitle, color = Gris, fontSize = 12.sp)
    }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(
        modifier = Modifier.fillMaxWidth().background(Fondo).padding(horizontal = 8.dp, vertical = 8.dp),
        verticalAlignment = Alignment.CenterVertically
    ) {
        IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Dorado) }
        Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold)
    }
}

private fun today(): String = SimpleDateFormat("dd/MM/yyyy", Locale.getDefault()).format(Date())

private fun money(value: Int): String {
    val nf = NumberFormat.getIntegerInstance(Locale("es", "CL"))
    return "$${nf.format(value)}"
}

object LocalStore {
    private const val PREF = "barberia_v2"
    private const val APPOINTMENTS = "appointments"
    private const val CLIENTS = "clients"
    private const val BARBERS = "barbers"
    private const val SERVICES = "services"
    private const val SALES = "sales"
    private const val SHOP_NAME = "shop_name"
    private const val SHOP_PHONE = "shop_phone"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getShopName(context: Context): String = prefs(context).getString(SHOP_NAME, "Barbería Central") ?: "Barbería Central"
    fun setShopName(context: Context, value: String) = prefs(context).edit().putString(SHOP_NAME, value).apply()
    fun getShopPhone(context: Context): String = prefs(context).getString(SHOP_PHONE, "") ?: ""
    fun setShopPhone(context: Context, value: String) = prefs(context).edit().putString(SHOP_PHONE, value).apply()

    fun loadAppointments(context: Context): List<Appointment> {
        val raw = prefs(context).getString(APPOINTMENTS, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                Appointment(1, "Carlos González", "+56911111111", "Corte + barba", "Diego", today(), "13:30", "Pendiente", 18000),
                Appointment(2, "Martín Pérez", "+56922222222", "Corte clásico", "Sebastián", today(), "15:00", "Pendiente", 12000)
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(
                        Appointment(
                            o.optLong("id"), o.optString("client"), o.optString("phone"),
                            o.optString("service"), o.optString("barber"), o.optString("date"),
                            o.optString("time"), o.optString("status", "Pendiente"), o.optInt("price")
                        )
                    )
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveAppointments(context: Context, list: List<Appointment>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("client", it.client); put("phone", it.phone); put("service", it.service)
                put("barber", it.barber); put("date", it.date); put("time", it.time); put("status", it.status); put("price", it.price)
            })
        }
        prefs(context).edit().putString(APPOINTMENTS, arr.toString()).apply()
    }

    fun loadClients(context: Context): List<ClientItem> {
        val raw = prefs(context).getString(CLIENTS, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                ClientItem(1, "Carlos González", "+56911111111", 8, 112000, "Cliente frecuente"),
                ClientItem(2, "Martín Pérez", "+56922222222", 5, 68000, "")
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ClientItem(o.optLong("id"), o.optString("name"), o.optString("phone"), o.optInt("visits"), o.optInt("spent"), o.optString("notes")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveClients(context: Context, list: List<ClientItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("name", it.name); put("phone", it.phone); put("visits", it.visits); put("spent", it.spent); put("notes", it.notes)
            })
        }
        prefs(context).edit().putString(CLIENTS, arr.toString()).apply()
    }

    fun loadBarbers(context: Context): List<BarberItem> {
        val raw = prefs(context).getString(BARBERS, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                BarberItem(1, "Diego", 40, true),
                BarberItem(2, "Sebastián", 40, true),
                BarberItem(3, "Andrés", 45, true)
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(BarberItem(o.optLong("id"), o.optString("name"), o.optInt("commission"), o.optBoolean("active", true)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveBarbers(context: Context, list: List<BarberItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("commission", it.commission); put("active", it.active) })
        }
        prefs(context).edit().putString(BARBERS, arr.toString()).apply()
    }

    fun loadServices(context: Context): List<ServiceItem> {
        val raw = prefs(context).getString(SERVICES, null)
        if (raw.isNullOrBlank()) {
            return listOf(
                ServiceItem(1, "Corte clásico", 12000, 30, true),
                ServiceItem(2, "Degradado", 14000, 45, true),
                ServiceItem(3, "Barba", 8000, 20, true),
                ServiceItem(4, "Corte + barba", 18000, 50, true)
            )
        }
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(ServiceItem(o.optLong("id"), o.optString("name"), o.optInt("price"), o.optInt("duration"), o.optBoolean("active", true)))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveServices(context: Context, list: List<ServiceItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("price", it.price); put("duration", it.duration); put("active", it.active) })
        }
        prefs(context).edit().putString(SERVICES, arr.toString()).apply()
    }

    fun loadSales(context: Context): List<SaleItem> {
        val raw = prefs(context).getString(SALES, null)
        if (raw.isNullOrBlank()) return emptyList()
        return runCatching {
            val arr = JSONArray(raw)
            buildList {
                for (i in 0 until arr.length()) {
                    val o = arr.getJSONObject(i)
                    add(SaleItem(o.optLong("id"), o.optString("client"), o.optString("service"), o.optString("barber"), o.optInt("amount"), o.optString("payment"), o.optString("date")))
                }
            }
        }.getOrDefault(emptyList())
    }

    fun saveSales(context: Context, list: List<SaleItem>) {
        val arr = JSONArray()
        list.forEach {
            arr.put(JSONObject().apply {
                put("id", it.id); put("client", it.client); put("service", it.service); put("barber", it.barber); put("amount", it.amount); put("payment", it.payment); put("date", it.date)
            })
        }
        prefs(context).edit().putString(SALES, arr.toString()).apply()
    }
}
