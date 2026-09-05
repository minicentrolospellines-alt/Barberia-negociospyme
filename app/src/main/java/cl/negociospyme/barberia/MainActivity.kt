package cl.negociospyme.barberia

import android.Manifest
import android.app.AlarmManager
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.graphics.Bitmap
import android.net.Uri
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
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
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.vector.ImageVector
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.app.NotificationCompat
import androidx.core.content.ContextCompat
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import org.json.JSONArray
import org.json.JSONObject
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale
import kotlin.math.max

private val Fondo = Color(0xFF101010)
private val Tarjeta = Color(0xFF1A1A1A)
private val Tarjeta2 = Color(0xFF222222)
private val Dorado = Color(0xFFD4AF37)
private val DoradoSuave = Color(0xFFFFE7A0)
private val Gris = Color(0xFFAAAAAA)
private val Verde = Color(0xFF4CAF50)
private val Rojo = Color(0xFFE85D5D)
private val Azul = Color(0xFF5B8DEF)

private const val DATE_PATTERN = "dd/MM/yyyy"
private const val TIME_PATTERN = "HH:mm"

data class Appointment(
    val id: Long,
    val client: String,
    val phone: String,
    val service: String,
    val barber: String,
    val date: String,
    val time: String,
    val status: String,
    val price: Int,
    val duration: Int = 30,
    val source: String = "Manual"
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
    val active: Boolean,
    val commissionOverride: Int = -1
)

data class SaleItem(
    val id: Long,
    val client: String,
    val service: String,
    val barber: String,
    val amount: Int,
    val payment: String,
    val date: String,
    val time: String = nowTime(),
    val serviceBase: Int = 0,
    val extra: Int = 0,
    val discount: Int = 0,
    val tip: Int = 0,
    val commissionAmount: Int = 0
)

data class ScheduleBlock(
    val id: Long,
    val barber: String,
    val date: String,
    val start: String,
    val end: String,
    val reason: String
)

data class CashClose(
    val id: Long,
    val date: String,
    val time: String,
    val total: Int,
    val cash: Int,
    val transfer: Int,
    val card: Int,
    val note: String
)

data class ChargeDraft(
    val payment: String,
    val extra: Int,
    val discount: Int,
    val tip: Int
)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        NotificationHelper.ensureChannel(this)
        if (Build.VERSION.SDK_INT >= 33 && checkSelfPermission(Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) {
            requestPermissions(arrayOf(Manifest.permission.POST_NOTIFICATIONS), 1001)
        }
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

class ReminderReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val client = intent.getStringExtra("client") ?: "Cliente"
        val time = intent.getStringExtra("time") ?: ""
        val barber = intent.getStringExtra("barber") ?: ""
        NotificationHelper.show(
            context,
            "Próxima cita",
            "$client a las $time${if (barber.isNotBlank()) " · $barber" else ""}",
            intent.getLongExtra("id", System.currentTimeMillis()).toInt()
        )
    }
}

object NotificationHelper {
    private const val CHANNEL_ID = "barberia_citas"

    fun ensureChannel(context: Context) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
            manager.createNotificationChannel(
                NotificationChannel(CHANNEL_ID, "Citas y reservas", NotificationManager.IMPORTANCE_HIGH).apply {
                    description = "Avisos de nuevas reservas y recordatorios de citas"
                }
            )
        }
    }

    fun show(context: Context, title: String, text: String, id: Int) {
        if (Build.VERSION.SDK_INT >= 33 && ContextCompat.checkSelfPermission(context, Manifest.permission.POST_NOTIFICATIONS) != PackageManager.PERMISSION_GRANTED) return
        ensureChannel(context)
        val manager = context.getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notification = NotificationCompat.Builder(context, CHANNEL_ID)
            .setSmallIcon(android.R.drawable.ic_dialog_info)
            .setContentTitle(title)
            .setContentText(text)
            .setPriority(NotificationCompat.PRIORITY_HIGH)
            .setAutoCancel(true)
            .build()
        manager.notify(id, notification)
    }

    fun newBooking(context: Context, appointment: Appointment) {
        show(context, "Nueva reserva", "${appointment.client} · ${appointment.date} ${appointment.time}", appointment.id.toInt())
    }

    fun scheduleReminder(context: Context, appointment: Appointment) {
        val whenMs = parseDateTime(appointment.date, appointment.time)?.time ?: return
        val trigger = whenMs - 60 * 60 * 1000L
        if (trigger <= System.currentTimeMillis()) return
        val intent = Intent(context, ReminderReceiver::class.java).apply {
            putExtra("id", appointment.id)
            putExtra("client", appointment.client)
            putExtra("time", appointment.time)
            putExtra("barber", appointment.barber)
        }
        val pending = PendingIntent.getBroadcast(
            context,
            appointment.id.hashCode(),
            intent,
            PendingIntent.FLAG_UPDATE_CURRENT or PendingIntent.FLAG_IMMUTABLE
        )
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.setAndAllowWhileIdle(AlarmManager.RTC_WAKEUP, trigger, pending)
    }

    fun cancelReminder(context: Context, appointmentId: Long) {
        val pending = PendingIntent.getBroadcast(
            context,
            appointmentId.hashCode(),
            Intent(context, ReminderReceiver::class.java),
            PendingIntent.FLAG_NO_CREATE or PendingIntent.FLAG_IMMUTABLE
        ) ?: return
        val alarm = context.getSystemService(Context.ALARM_SERVICE) as AlarmManager
        alarm.cancel(pending)
        pending.cancel()
    }
}

@Composable
fun BarberiaApp() {
    val context = LocalContext.current

    var loggedIn by remember { mutableStateOf(false) }
    var screen by remember { mutableStateOf("inicio") }
    var shopName by remember { mutableStateOf(LocalStore.getShopName(context)) }
    var shopPhone by remember { mutableStateOf(LocalStore.getShopPhone(context)) }
    var bookingLink by remember { mutableStateOf(LocalStore.getBookingLink(context)) }

    val appointments = remember(context) { mutableStateListOf<Appointment>().also { it.addAll(LocalStore.loadAppointments(context)) } }
    val clients = remember(context) { mutableStateListOf<ClientItem>().also { it.addAll(LocalStore.loadClients(context)) } }
    val barbers = remember(context) { mutableStateListOf<BarberItem>().also { it.addAll(LocalStore.loadBarbers(context)) } }
    val services = remember(context) { mutableStateListOf<ServiceItem>().also { it.addAll(LocalStore.loadServices(context)) } }
    val sales = remember(context) { mutableStateListOf<SaleItem>().also { it.addAll(LocalStore.loadSales(context)) } }
    val blocks = remember(context) { mutableStateListOf<ScheduleBlock>().also { it.addAll(LocalStore.loadBlocks(context)) } }
    val cashCloses = remember(context) { mutableStateListOf<CashClose>().also { it.addAll(LocalStore.loadCashCloses(context)) } }

    fun persistAppointment(appointment: Appointment, isNew: Boolean) {
        if (isNew) {
            appointments.add(appointment)
            NotificationHelper.newBooking(context, appointment)
        } else {
            val idx = appointments.indexOfFirst { it.id == appointment.id }
            if (idx >= 0) appointments[idx] = appointment
        }
        LocalStore.saveAppointments(context, appointments)
        NotificationHelper.cancelReminder(context, appointment.id)
        if (appointment.status != "Cancelada" && appointment.status != "Atendida") NotificationHelper.scheduleReminder(context, appointment)
        if (appointment.client.isNotBlank() && clients.none { appointment.phone.isNotBlank() && it.phone == appointment.phone }) {
            clients.add(ClientItem(System.currentTimeMillis(), appointment.client, appointment.phone, 0, 0, ""))
            LocalStore.saveClients(context, clients)
        }
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
                        "inicio" -> DashboardScreen(shopName, appointments, barbers, sales, { screen = "agenda" }, { screen = "caja" })

                        "agenda" -> AgendaScreen(
                            appointments = appointments,
                            clients = clients,
                            services = services,
                            barbers = barbers,
                            blocks = blocks,
                            onAdd = { persistAppointment(it, true) },
                            onUpdate = { persistAppointment(it, false) },
                            onCancel = { appt ->
                                val updated = appt.copy(status = "Cancelada")
                                persistAppointment(updated, false)
                                NotificationHelper.cancelReminder(context, appt.id)
                            },
                            onConfirm = { appt -> persistAppointment(appt.copy(status = "Confirmada"), false) },
                            onAddBlock = {
                                blocks.add(it)
                                LocalStore.saveBlocks(context, blocks)
                            },
                            onDeleteBlock = {
                                blocks.removeAll { b -> b.id == it.id }
                                LocalStore.saveBlocks(context, blocks)
                            },
                            onCharge = { appt, draft ->
                                val service = services.firstOrNull { it.name == appt.service }
                                val barber = barbers.firstOrNull { it.name == appt.barber }
                                val baseAfterDiscount = max(0, appt.price - draft.discount)
                                val total = max(0, appt.price + draft.extra - draft.discount) + draft.tip
                                val commission = commissionAmount(baseAfterDiscount, service, barber)
                                val sale = SaleItem(
                                    id = System.currentTimeMillis(),
                                    client = appt.client,
                                    service = appt.service,
                                    barber = appt.barber,
                                    amount = total,
                                    payment = draft.payment,
                                    date = today(),
                                    time = nowTime(),
                                    serviceBase = appt.price,
                                    extra = draft.extra,
                                    discount = draft.discount,
                                    tip = draft.tip,
                                    commissionAmount = commission
                                )
                                sales.add(0, sale)
                                LocalStore.saveSales(context, sales)
                                persistAppointment(appt.copy(status = "Atendida"), false)
                                NotificationHelper.cancelReminder(context, appt.id)

                                val clientIndex = clients.indexOfFirst { (appt.phone.isNotBlank() && it.phone == appt.phone) || it.name.equals(appt.client, true) }
                                if (clientIndex >= 0) {
                                    val old = clients[clientIndex]
                                    clients[clientIndex] = old.copy(visits = old.visits + 1, spent = old.spent + total)
                                } else {
                                    clients.add(ClientItem(System.currentTimeMillis(), appt.client, appt.phone, 1, total, ""))
                                }
                                LocalStore.saveClients(context, clients)
                            },
                            onWhatsAppReminder = { openWhatsAppReminder(context, it, shopName) }
                        )

                        "caja" -> CajaScreen(
                            sales = sales,
                            clients = clients,
                            services = services,
                            barbers = barbers,
                            cashCloses = cashCloses,
                            onAddSale = {
                                sales.add(0, it)
                                LocalStore.saveSales(context, sales)
                            },
                            onCloseCash = {
                                cashCloses.add(0, it)
                                LocalStore.saveCashCloses(context, cashCloses)
                            }
                        )

                        "clientes" -> ClientsScreen(clients) {
                            clients.add(it)
                            LocalStore.saveClients(context, clients)
                        }

                        "mas" -> MoreScreen(
                            onBarbers = { screen = "barberos" },
                            onServices = { screen = "servicios" },
                            onCommissions = { screen = "comisiones" },
                            onQr = { screen = "qr" },
                            onSettings = { screen = "config" },
                            onLogout = { loggedIn = false }
                        )
                    }
                }
            }
        }

        "barberos" -> BarbersScreen(barbers, { screen = "mas" }, {
            barbers.add(it); LocalStore.saveBarbers(context, barbers)
        }, { barber ->
            val index = barbers.indexOfFirst { it.id == barber.id }
            if (index >= 0) { barbers[index] = barber.copy(active = !barber.active); LocalStore.saveBarbers(context, barbers) }
        })

        "servicios" -> ServicesScreen(services, { screen = "mas" }, {
            services.add(it); LocalStore.saveServices(context, services)
        }, { service ->
            val index = services.indexOfFirst { it.id == service.id }
            if (index >= 0) { services[index] = service.copy(active = !service.active); LocalStore.saveServices(context, services) }
        })

        "comisiones" -> CommissionsScreen(sales, barbers, services, { screen = "mas" })

        "qr" -> QrScreen(
            shopName = shopName,
            bookingLink = bookingLink,
            services = services.filter { it.active },
            barbers = barbers.filter { it.active },
            appointments = appointments,
            blocks = blocks,
            onBack = { screen = "mas" },
            onSaveLink = {
                bookingLink = it
                LocalStore.setBookingLink(context, it)
            },
            onOnlineBooking = { persistAppointment(it.copy(source = "Online"), true) }
        )

        "config" -> SettingsScreen(shopName, shopPhone, { screen = "mas" }) { name, phone ->
            shopName = name; shopPhone = phone
            LocalStore.setShopName(context, name); LocalStore.setShopPhone(context, phone)
        }
    }
}

@Composable
private fun RowScope.BottomItem(route: String, label: String, icon: ImageVector, current: String, onClick: () -> Unit) {
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
        modifier = Modifier.fillMaxSize().background(Fondo).padding(24.dp),
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = Dorado, shape = RoundedCornerShape(20.dp)) {
            Icon(Icons.Default.ContentCut, null, tint = Color.Black, modifier = Modifier.padding(16.dp).size(38.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("BARBERÍA", color = Dorado, fontSize = 34.sp, fontWeight = FontWeight.Black)
        Text(shopName, color = Color.White, fontSize = 18.sp)
        Spacer(Modifier.height(28.dp))
        OutlinedTextField(email, { email = it }, label = { Text("Correo") }, leadingIcon = { Icon(Icons.Default.Email, null) }, singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(12.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Contraseña") }, leadingIcon = { Icon(Icons.Default.Lock, null) }, visualTransformation = PasswordVisualTransformation(), singleLine = true, modifier = Modifier.fillMaxWidth())
        Spacer(Modifier.height(20.dp))
        Button(onClick = onLogin, modifier = Modifier.fillMaxWidth().height(54.dp), colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black), shape = RoundedCornerShape(15.dp)) {
            Text("INGRESAR", fontWeight = FontWeight.Bold)
        }
        Spacer(Modifier.height(12.dp))
        Text("v3 · agenda, reservas, avisos, caja y comisiones", color = Gris, fontSize = 12.sp)
    }
}

@Composable
fun DashboardScreen(shopName: String, appointments: List<Appointment>, barbers: List<BarberItem>, sales: List<SaleItem>, onOpenAgenda: () -> Unit, onOpenCaja: () -> Unit) {
    val today = today()
    val todayAppointments = appointments.count { it.date == today && it.status != "Cancelada" }
    val todaySales = sales.filter { it.date == today }.sumOf { it.amount }
    val activeBarbers = barbers.count { it.active }
    val next = appointments.filter { it.date == today && it.status in listOf("Pendiente", "Confirmada") }.sortedBy { it.time }.firstOrNull()
    LazyColumn(modifier = Modifier.fillMaxSize().background(Fondo), contentPadding = PaddingValues(18.dp), verticalArrangement = Arrangement.spacedBy(14.dp)) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Column(Modifier.weight(1f)) {
                    Text("Panel de administración", color = Gris, fontSize = 13.sp)
                    Text(shopName, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold)
                }
                Surface(color = Dorado, shape = RoundedCornerShape(18.dp)) { Icon(Icons.Default.ContentCut, null, tint = Color.Black, modifier = Modifier.padding(13.dp).size(28.dp)) }
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
            if (next == null) EmptyCard("No hay citas pendientes para hoy.") else {
                Surface(modifier = Modifier.fillMaxWidth().clickable { onOpenAgenda() }, color = Tarjeta, shape = RoundedCornerShape(18.dp)) {
                    Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Dorado.copy(alpha = 0.15f), shape = RoundedCornerShape(12.dp)) { Text(next.time, color = Dorado, fontWeight = FontWeight.Bold, modifier = Modifier.padding(12.dp)) }
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
                    Icon(Icons.Default.NotificationsActive, null, tint = Dorado, modifier = Modifier.size(32.dp))
                    Spacer(Modifier.width(12.dp))
                    Column {
                        Text("Recordatorios activos", color = Color.White, fontWeight = FontWeight.Bold)
                        Text("La app programa un aviso local 1 hora antes de cada cita.", color = Gris, fontSize = 12.sp)
                    }
                }
            }
        }
    }
}

@Composable
fun StatCard(value: String, label: String, modifier: Modifier = Modifier) {
    Surface(modifier = modifier, color = Tarjeta, shape = RoundedCornerShape(16.dp)) {
        Column(Modifier.padding(13.dp)) { Text(value, color = Dorado, fontWeight = FontWeight.Bold, fontSize = 17.sp, maxLines = 1); Text(label, color = Gris, fontSize = 11.sp) }
    }
}

@Composable
fun QuickCard(title: String, subtitle: String, icon: ImageVector, modifier: Modifier, onClick: () -> Unit) {
    Surface(modifier = modifier.clickable { onClick() }, color = Tarjeta, shape = RoundedCornerShape(18.dp)) {
        Column(Modifier.padding(16.dp)) { Icon(icon, null, tint = Dorado, modifier = Modifier.size(30.dp)); Spacer(Modifier.height(14.dp)); Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Gris, fontSize = 12.sp) }
    }
}

@Composable
fun EmptyCard(text: String) {
    Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) { Text(text, color = Gris, modifier = Modifier.padding(18.dp)) }
}

@Composable
fun AgendaScreen(
    appointments: List<Appointment>,
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    blocks: List<ScheduleBlock>,
    onAdd: (Appointment) -> Unit,
    onUpdate: (Appointment) -> Unit,
    onCancel: (Appointment) -> Unit,
    onConfirm: (Appointment) -> Unit,
    onAddBlock: (ScheduleBlock) -> Unit,
    onDeleteBlock: (ScheduleBlock) -> Unit,
    onCharge: (Appointment, ChargeDraft) -> Unit,
    onWhatsAppReminder: (Appointment) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var showBlock by remember { mutableStateOf(false) }
    var charging by remember { mutableStateOf<Appointment?>(null) }
    var editing by remember { mutableStateOf<Appointment?>(null) }
    var filter by remember { mutableStateOf("Hoy") }

    val visible = appointments.filter {
        when (filter) {
            "Hoy" -> it.date == today()
            "7 días" -> isWithinDays(it.date, 7)
            else -> true
        }
    }.sortedWith(compareBy<Appointment> { parseDate(it.date)?.time ?: Long.MAX_VALUE }.thenBy { it.time })

    Scaffold(
        containerColor = Fondo,
        topBar = { SectionHeader("Agenda", "Diaria, semanal, bloqueos y estados") },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) { Icon(Icons.Default.Add, null, tint = Color.Black) } }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    listOf("Hoy", "7 días", "Todas").forEach { item ->
                        FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) })
                    }
                }
                Spacer(Modifier.height(8.dp))
                OutlinedButton(onClick = { showBlock = true }, modifier = Modifier.fillMaxWidth()) {
                    Icon(Icons.Default.Block, null); Spacer(Modifier.width(8.dp)); Text("Bloquear horario / descanso / feriado")
                }
            }
            val todayBlocks = blocks.filter { filter == "Todas" || (filter == "Hoy" && it.date == today()) || (filter == "7 días" && isWithinDays(it.date, 7)) }
            if (todayBlocks.isNotEmpty()) {
                item { Text("Horarios bloqueados", color = Color.White, fontWeight = FontWeight.Bold) }
                items(todayBlocks, key = { "b${it.id}" }) { block -> BlockCard(block) { onDeleteBlock(block) } }
            }
            item { Text("Citas", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp) }
            if (visible.isEmpty()) item { EmptyCard("No hay citas en este período. Presiona + para crear una.") }
            items(visible, key = { it.id }) { appt ->
                AppointmentCard(
                    appt = appt,
                    onConfirm = { onConfirm(appt) },
                    onEdit = { editing = appt },
                    onCancel = { onCancel(appt) },
                    onCharge = { charging = appt },
                    onWhatsApp = { onWhatsAppReminder(appt) }
                )
            }
        }
    }

    if (showAdd) {
        NewAppointmentDialog(
            clients = clients,
            services = services.filter { it.active },
            barbers = barbers.filter { it.active },
            appointments = appointments,
            blocks = blocks,
            initial = null,
            source = "Manual",
            onDismiss = { showAdd = false },
            onSave = { onAdd(it); showAdd = false }
        )
    }
    editing?.let { current ->
        NewAppointmentDialog(
            clients, services.filter { it.active }, barbers.filter { it.active }, appointments, blocks,
            initial = current, source = current.source,
            onDismiss = { editing = null },
            onSave = { onUpdate(it); editing = null }
        )
    }
    if (showBlock) {
        NewBlockDialog(barbers.filter { it.active }, { showBlock = false }) { onAddBlock(it); showBlock = false }
    }
    charging?.let { appt ->
        PaymentDialog("Cobrar ${appt.client}", appt.price, { charging = null }) { draft -> onCharge(appt, draft); charging = null }
    }
}

@Composable
fun AppointmentCard(appt: Appointment, onConfirm: () -> Unit, onEdit: () -> Unit, onCancel: () -> Unit, onCharge: () -> Unit, onWhatsApp: () -> Unit) {
    val statusColor = when (appt.status) { "Atendida" -> Verde; "Cancelada" -> Rojo; "Confirmada" -> Azul; else -> Dorado }
    Surface(color = Tarjeta, shape = RoundedCornerShape(17.dp), modifier = Modifier.fillMaxWidth()) {
        Column(Modifier.padding(15.dp)) {
            Row(verticalAlignment = Alignment.CenterVertically) {
                Surface(color = statusColor.copy(alpha = 0.14f), shape = RoundedCornerShape(10.dp)) { Text(appt.time, color = statusColor, fontWeight = FontWeight.Bold, modifier = Modifier.padding(horizontal = 10.dp, vertical = 8.dp)) }
                Spacer(Modifier.width(10.dp))
                Column(Modifier.weight(1f)) {
                    Text(appt.client, color = Color.White, fontWeight = FontWeight.Bold, fontSize = 16.sp)
                    Text("${appt.date} · ${appt.service} · ${appt.duration} min", color = Gris, fontSize = 12.sp)
                    Text("${appt.barber} · ${appt.source}", color = Gris, fontSize = 11.sp)
                }
                Text(money(appt.price), color = Dorado, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text(appt.status, color = statusColor, fontSize = 12.sp, fontWeight = FontWeight.Bold, modifier = Modifier.weight(1f))
                if (appt.phone.isNotBlank() && appt.status !in listOf("Atendida", "Cancelada")) IconButton(onClick = onWhatsApp) { Icon(Icons.Default.Message, null, tint = Verde) }
                if (appt.status !in listOf("Atendida", "Cancelada")) IconButton(onClick = onEdit) { Icon(Icons.Default.Edit, null, tint = Dorado) }
            }
            if (appt.status in listOf("Pendiente", "Confirmada")) {
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.End) {
                    if (appt.status == "Pendiente") TextButton(onClick = onConfirm) { Text("Confirmar", color = Azul) }
                    TextButton(onClick = onCancel) { Text("Cancelar", color = Rojo) }
                    Button(onClick = onCharge, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Cobrar") }
                }
            }
        }
    }
}

@Composable
fun BlockCard(block: ScheduleBlock, onDelete: () -> Unit) {
    Surface(color = Tarjeta2, shape = RoundedCornerShape(14.dp), modifier = Modifier.fillMaxWidth()) {
        Row(Modifier.padding(13.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.Block, null, tint = Rojo)
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("${block.barber} · ${block.date}", color = Color.White, fontWeight = FontWeight.Bold)
                Text("${block.start}–${block.end} · ${block.reason}", color = Gris, fontSize = 12.sp)
            }
            IconButton(onClick = onDelete) { Icon(Icons.Default.Delete, null, tint = Rojo) }
        }
    }
}

@Composable
fun NewBlockDialog(barbers: List<BarberItem>, onDismiss: () -> Unit, onSave: (ScheduleBlock) -> Unit) {
    var barber by remember { mutableStateOf(barbers.firstOrNull()?.name ?: "") }
    var date by remember { mutableStateOf(today()) }
    var start by remember { mutableStateOf("13:00") }
    var end by remember { mutableStateOf("14:00") }
    var reason by remember { mutableStateOf("Descanso") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Bloquear horario") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(9.dp)) {
                SelectField("Barbero", barber, barbers.map { it.name }) { barber = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha dd/MM/yyyy") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    OutlinedTextField(start, { start = it }, label = { Text("Desde") }, modifier = Modifier.weight(1f), singleLine = true)
                    OutlinedTextField(end, { end = it }, label = { Text("Hasta") }, modifier = Modifier.weight(1f), singleLine = true)
                }
                OutlinedTextField(reason, { reason = it }, label = { Text("Motivo") }, modifier = Modifier.fillMaxWidth())
            }
        },
        confirmButton = { Button(onClick = { if (barber.isNotBlank()) onSave(ScheduleBlock(System.currentTimeMillis(), barber, date.trim(), start.trim(), end.trim(), reason.trim())) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun NewAppointmentDialog(
    clients: List<ClientItem>,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    appointments: List<Appointment>,
    blocks: List<ScheduleBlock>,
    initial: Appointment?,
    source: String,
    onDismiss: () -> Unit,
    onSave: (Appointment) -> Unit
) {
    var client by remember(initial) { mutableStateOf(initial?.client ?: "") }
    var phone by remember(initial) { mutableStateOf(initial?.phone ?: "") }
    var serviceName by remember(initial, services) { mutableStateOf(initial?.service ?: services.firstOrNull()?.name.orEmpty()) }
    var barberName by remember(initial, barbers) { mutableStateOf(initial?.barber ?: barbers.firstOrNull()?.name.orEmpty()) }
    var date by remember(initial) { mutableStateOf(initial?.date ?: today()) }
    var time by remember(initial) { mutableStateOf(initial?.time ?: "10:00") }
    var error by remember { mutableStateOf("") }
    val selectedService = services.firstOrNull { it.name == serviceName }
    val price = selectedService?.price ?: initial?.price ?: 0
    val duration = selectedService?.duration ?: initial?.duration ?: 30

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(if (initial == null) if (source == "Online") "Reserva cliente" else "Nueva cita" else "Reprogramar cita") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono / WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectField("Servicio", serviceName, services.map { it.name }) { serviceName = it }
                SelectField("Barbero", barberName, barbers.map { it.name }) { barberName = it }
                OutlinedTextField(date, { date = it }, label = { Text("Fecha (dd/MM/yyyy)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                OutlinedTextField(time, { time = it }, label = { Text("Hora (HH:mm)") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                Text("${money(price)} · $duration min", color = Dorado, fontWeight = FontWeight.Bold)
                if (clients.isNotEmpty() && source != "Online") Text("Tip: puedes usar el mismo teléfono de un cliente existente.", color = Gris, fontSize = 11.sp)
                if (error.isNotBlank()) Text(error, color = Rojo, fontSize = 12.sp)
            }
        },
        confirmButton = {
            Button(
                onClick = {
                    val candidate = Appointment(
                        id = initial?.id ?: System.currentTimeMillis(), client = client.trim(), phone = phone.trim(), service = serviceName,
                        barber = barberName, date = date.trim(), time = time.trim(), status = initial?.status ?: "Pendiente", price = price,
                        duration = duration, source = source
                    )
                    error = when {
                        client.isBlank() -> "Ingresa el nombre del cliente."
                        serviceName.isBlank() || barberName.isBlank() -> "Selecciona servicio y barbero."
                        parseDateTime(candidate.date, candidate.time) == null -> "Revisa fecha y hora. Usa dd/MM/yyyy y HH:mm."
                        appointmentHasConflict(candidate, appointments, blocks, initial?.id) -> "Ese barbero ya tiene una cita o bloqueo que se cruza con este horario."
                        else -> ""
                    }
                    if (error.isBlank()) onSave(candidate)
                },
                colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)
            ) { Text(if (initial == null) "Guardar" else "Actualizar") }
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
    cashCloses: List<CashClose>,
    onAddSale: (SaleItem) -> Unit,
    onCloseCash: (CashClose) -> Unit
) {
    var showAdd by remember { mutableStateOf(false) }
    var showClose by remember { mutableStateOf(false) }
    val todaySales = sales.filter { it.date == today() }
    val total = todaySales.sumOf { it.amount }
    val cash = todaySales.filter { it.payment == "Efectivo" }.sumOf { it.amount }
    val transfer = todaySales.filter { it.payment == "Transferencia" }.sumOf { it.amount }
    val card = todaySales.filter { it.payment == "Débito / Crédito" }.sumOf { it.amount }
    val lastClose = cashCloses.firstOrNull { it.date == today() }

    Scaffold(
        containerColor = Fondo,
        topBar = { SectionHeader("Caja", "Ventas, descuentos, propinas y cierre") },
        floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) { Icon(Icons.Default.AddShoppingCart, null, tint = Color.Black) } }
    ) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            item {
                Row(verticalAlignment = Alignment.CenterVertically) {
                    Column(Modifier.weight(1f)) { Text("Total de hoy", color = Gris); Text(money(total), color = Dorado, fontSize = 36.sp, fontWeight = FontWeight.Black) }
                    OutlinedButton(onClick = { showClose = true }) { Icon(Icons.Default.Lock, null); Spacer(Modifier.width(6.dp)); Text("Cerrar caja") }
                }
                Spacer(Modifier.height(10.dp))
                Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                    MiniMoneyCard("Efectivo", cash, Modifier.weight(1f)); MiniMoneyCard("Transfer.", transfer, Modifier.weight(1f)); MiniMoneyCard("Tarjeta", card, Modifier.weight(1f))
                }
            }
            if (lastClose != null) item {
                Surface(color = Verde.copy(alpha = 0.12f), shape = RoundedCornerShape(15.dp)) {
                    Column(Modifier.fillMaxWidth().padding(14.dp)) {
                        Text("Último cierre de hoy · ${lastClose.time}", color = Verde, fontWeight = FontWeight.Bold)
                        Text("Total ${money(lastClose.total)} · Efectivo ${money(lastClose.cash)}", color = Gris, fontSize = 12.sp)
                        if (lastClose.note.isNotBlank()) Text(lastClose.note, color = Gris, fontSize = 12.sp)
                    }
                }
            }
            item { Text("Movimientos", color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold) }
            if (sales.isEmpty()) item { EmptyCard("Todavía no hay ventas registradas.") }
            items(sales, key = { it.id }) { sale ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Icon(Icons.Default.ReceiptLong, null, tint = Dorado); Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) {
                                Text(sale.client.ifBlank { "Venta directa" }, color = Color.White, fontWeight = FontWeight.Bold)
                                Text("${sale.service} · ${sale.barber}", color = Gris, fontSize = 12.sp)
                                Text("${sale.date} ${sale.time} · ${sale.payment}", color = Gris, fontSize = 11.sp)
                            }
                            Text(money(sale.amount), color = Dorado, fontWeight = FontWeight.Bold)
                        }
                        if (sale.extra > 0 || sale.discount > 0 || sale.tip > 0) {
                            Spacer(Modifier.height(8.dp))
                            Text("Extra/productos ${money(sale.extra)} · Descuento ${money(sale.discount)} · Propina ${money(sale.tip)}", color = Gris, fontSize = 11.sp)
                        }
                    }
                }
            }
        }
    }

    if (showAdd) {
        NewSaleDialog(clients, services.filter { it.active }, barbers.filter { it.active }, { showAdd = false }) { onAddSale(it); showAdd = false }
    }
    if (showClose) {
        CloseCashDialog(total, cash, transfer, card, { showClose = false }) { note ->
            onCloseCash(CashClose(System.currentTimeMillis(), today(), nowTime(), total, cash, transfer, card, note)); showClose = false
        }
    }
}

@Composable
fun MiniMoneyCard(label: String, value: Int, modifier: Modifier) {
    Surface(modifier = modifier, color = Tarjeta, shape = RoundedCornerShape(14.dp)) { Column(Modifier.padding(10.dp)) { Text(money(value), color = Dorado, fontWeight = FontWeight.Bold, fontSize = 13.sp, maxLines = 1); Text(label, color = Gris, fontSize = 10.sp) } }
}

@Composable
fun NewSaleDialog(clients: List<ClientItem>, services: List<ServiceItem>, barbers: List<BarberItem>, onDismiss: () -> Unit, onSave: (SaleItem) -> Unit) {
    var client by remember { mutableStateOf(clients.firstOrNull()?.name ?: "Venta directa") }
    var serviceName by remember { mutableStateOf(services.firstOrNull()?.name ?: "Servicio") }
    var barberName by remember { mutableStateOf(barbers.firstOrNull()?.name ?: "Sin asignar") }
    var serviceAmount by remember { mutableStateOf((services.firstOrNull()?.price ?: 0).toString()) }
    var extra by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("0") }
    var tip by remember { mutableStateOf("0") }
    var payment by remember { mutableStateOf("Efectivo") }
    val selectedService = services.firstOrNull { it.name == serviceName }
    val selectedBarber = barbers.firstOrNull { it.name == barberName }
    val base = serviceAmount.toIntOrNull() ?: 0
    val total = max(0, base + (extra.toIntOrNull() ?: 0) - (discount.toIntOrNull() ?: 0)) + (tip.toIntOrNull() ?: 0)

    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Nueva venta") },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                OutlinedTextField(client, { client = it }, label = { Text("Cliente") }, singleLine = true, modifier = Modifier.fillMaxWidth())
                SelectField("Servicio", serviceName, services.map { it.name }) { serviceName = it; serviceAmount = (services.firstOrNull { s -> s.name == it }?.price ?: 0).toString() }
                SelectField("Barbero", barberName, barbers.map { it.name }) { barberName = it }
                MoneyField("Valor servicio", serviceAmount) { serviceAmount = it }
                MoneyField("Productos / extras", extra) { extra = it }
                MoneyField("Descuento", discount) { discount = it }
                MoneyField("Propina", tip) { tip = it }
                SelectField("Pago", payment, listOf("Efectivo", "Transferencia", "Débito / Crédito")) { payment = it }
                Text("Total: ${money(total)}", color = Dorado, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        confirmButton = {
            Button(onClick = {
                if (total > 0) {
                    val discountValue = discount.toIntOrNull() ?: 0
                    val commission = commissionAmount(max(0, base - discountValue), selectedService, selectedBarber)
                    onSave(SaleItem(System.currentTimeMillis(), client.trim(), serviceName, barberName, total, payment, today(), nowTime(), base, extra.toIntOrNull() ?: 0, discountValue, tip.toIntOrNull() ?: 0, commission))
                }
            }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Registrar") }
        },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun MoneyField(label: String, value: String, onChange: (String) -> Unit) {
    OutlinedTextField(value, { onChange(it.filter(Char::isDigit)) }, label = { Text(label) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
}

@Composable
fun PaymentDialog(title: String, baseAmount: Int, onDismiss: () -> Unit, onConfirm: (ChargeDraft) -> Unit) {
    var payment by remember { mutableStateOf("Efectivo") }
    var extra by remember { mutableStateOf("0") }
    var discount by remember { mutableStateOf("0") }
    var tip by remember { mutableStateOf("0") }
    val total = max(0, baseAmount + (extra.toIntOrNull() ?: 0) - (discount.toIntOrNull() ?: 0)) + (tip.toIntOrNull() ?: 0)
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text(title) },
        text = {
            Column(Modifier.verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(10.dp)) {
                Text("Servicio: ${money(baseAmount)}", color = Dorado, fontSize = 18.sp, fontWeight = FontWeight.Bold)
                MoneyField("Productos / extras", extra) { extra = it }
                MoneyField("Descuento", discount) { discount = it }
                MoneyField("Propina", tip) { tip = it }
                SelectField("Medio de pago", payment, listOf("Efectivo", "Transferencia", "Débito / Crédito")) { payment = it }
                Text("Total a cobrar: ${money(total)}", color = Dorado, fontWeight = FontWeight.Bold, fontSize = 20.sp)
            }
        },
        confirmButton = { Button(onClick = { onConfirm(ChargeDraft(payment, extra.toIntOrNull() ?: 0, discount.toIntOrNull() ?: 0, tip.toIntOrNull() ?: 0)) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Confirmar cobro") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun CloseCashDialog(total: Int, cash: Int, transfer: Int, card: Int, onDismiss: () -> Unit, onSave: (String) -> Unit) {
    var note by remember { mutableStateOf("") }
    AlertDialog(
        onDismissRequest = onDismiss,
        title = { Text("Cierre de caja") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(8.dp)) {
                Text("Total ${money(total)}", color = Dorado, fontWeight = FontWeight.Bold, fontSize = 20.sp)
                Text("Efectivo ${money(cash)}")
                Text("Transferencia ${money(transfer)}")
                Text("Tarjeta ${money(card)}")
                OutlinedTextField(note, { note = it }, label = { Text("Observación opcional") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
            }
        },
        confirmButton = { Button(onClick = { onSave(note.trim()) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar cierre") } },
        dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } }
    )
}

@Composable
fun ClientsScreen(clients: List<ClientItem>, onAdd: (ClientItem) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    var search by remember { mutableStateOf("") }
    val filtered = clients.filter { search.isBlank() || it.name.contains(search, true) || it.phone.contains(search, true) }
    Scaffold(containerColor = Fondo, topBar = { SectionHeader("Clientes", "Historial y fidelización") }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) { Icon(Icons.Default.PersonAdd, null, tint = Color.Black) } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item { OutlinedTextField(search, { search = it }, label = { Text("Buscar cliente") }, leadingIcon = { Icon(Icons.Default.Search, null) }, singleLine = true, modifier = Modifier.fillMaxWidth()) }
            if (filtered.isEmpty()) item { EmptyCard("No hay clientes para mostrar.") }
            items(filtered, key = { it.id }) { client ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Column(Modifier.padding(15.dp)) {
                        Row(verticalAlignment = Alignment.CenterVertically) {
                            Surface(color = Dorado.copy(alpha = 0.15f), shape = RoundedCornerShape(50)) { Icon(Icons.Default.Person, null, tint = Dorado, modifier = Modifier.padding(10.dp)) }
                            Spacer(Modifier.width(12.dp))
                            Column(Modifier.weight(1f)) { Text(client.name, color = Color.White, fontWeight = FontWeight.Bold); Text(client.phone.ifBlank { "Sin teléfono" }, color = Gris, fontSize = 12.sp) }
                            Text("${client.visits} visitas", color = Dorado, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                        }
                        Spacer(Modifier.height(10.dp)); Text("Gasto acumulado: ${money(client.spent)}", color = Gris, fontSize = 13.sp); if (client.notes.isNotBlank()) Text(client.notes, color = Gris, fontSize = 12.sp)
                    }
                }
            }
        }
    }
    if (showAdd) NewClientDialog({ showAdd = false }) { onAdd(it); showAdd = false }
}

@Composable
fun NewClientDialog(onDismiss: () -> Unit, onSave: (ClientItem) -> Unit) {
    var name by remember { mutableStateOf("") }; var phone by remember { mutableStateOf("") }; var notes by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo cliente") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(phone, { phone = it }, label = { Text("Teléfono") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(notes, { notes = it }, label = { Text("Observaciones") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
    } }, confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(ClientItem(System.currentTimeMillis(), name.trim(), phone.trim(), 0, 0, notes.trim())) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun MoreScreen(onBarbers: () -> Unit, onServices: () -> Unit, onCommissions: () -> Unit, onQr: () -> Unit, onSettings: () -> Unit, onLogout: () -> Unit) {
    LazyColumn(modifier = Modifier.fillMaxSize().background(Fondo), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item { SectionHeader("Más", "Administración de la barbería") }
        item { MoreCard(Icons.Default.ContentCut, "Barberos", "Equipo, estado y comisión", onBarbers) }
        item { MoreCard(Icons.Default.Storefront, "Servicios", "Precios, duración y comisión especial", onServices) }
        item { MoreCard(Icons.Default.Paid, "Comisiones", "Resumen por barbero", onCommissions) }
        item { MoreCard(Icons.Default.QrCode2, "QR Reservas", "QR real, enlace y flujo de cliente", onQr) }
        item { MoreCard(Icons.Default.Settings, "Configuración", "Datos de la barbería", onSettings) }
        item { MoreCard(Icons.Default.Logout, "Cerrar sesión", "Salir de esta cuenta", onLogout) }
    }
}

@Composable
fun MoreCard(icon: ImageVector, title: String, subtitle: String, onClick: () -> Unit) {
    Surface(modifier = Modifier.fillMaxWidth().clickable { onClick() }, color = Tarjeta, shape = RoundedCornerShape(16.dp)) {
        Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) { Icon(icon, null, tint = Dorado, modifier = Modifier.size(28.dp)); Spacer(Modifier.width(14.dp)); Column(Modifier.weight(1f)) { Text(title, color = Color.White, fontWeight = FontWeight.Bold); Text(subtitle, color = Gris, fontSize = 12.sp) }; Icon(Icons.Default.ChevronRight, null, tint = Gris) }
    }
}

@Composable
fun BarbersScreen(barbers: List<BarberItem>, onBack: () -> Unit, onAdd: (BarberItem) -> Unit, onToggle: (BarberItem) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(containerColor = Fondo, topBar = { BackHeader("Barberos", onBack) }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) { Icon(Icons.Default.PersonAdd, null, tint = Color.Black) } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (barbers.isEmpty()) item { EmptyCard("Agrega el primer barbero.") }
            items(barbers, key = { it.id }) { barber ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.ContentCut, null, tint = if (barber.active) Dorado else Gris); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(barber.name, color = Color.White, fontWeight = FontWeight.Bold); Text("Comisión general ${barber.commission}%", color = Gris, fontSize = 12.sp) }; Switch(checked = barber.active, onCheckedChange = { onToggle(barber) }) }
                }
            }
        }
    }
    if (showAdd) NewBarberDialog({ showAdd = false }) { onAdd(it); showAdd = false }
}

@Composable
fun NewBarberDialog(onDismiss: () -> Unit, onSave: (BarberItem) -> Unit) {
    var name by remember { mutableStateOf("") }; var commission by remember { mutableStateOf("40") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo barbero") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commission, { commission = it.filter(Char::isDigit).take(3) }, label = { Text("Comisión general %") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
    } }, confirmButton = { Button(onClick = { if (name.isNotBlank()) onSave(BarberItem(System.currentTimeMillis(), name.trim(), (commission.toIntOrNull() ?: 0).coerceIn(0, 100), true)) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun ServicesScreen(services: List<ServiceItem>, onBack: () -> Unit, onAdd: (ServiceItem) -> Unit, onToggle: (ServiceItem) -> Unit) {
    var showAdd by remember { mutableStateOf(false) }
    Scaffold(containerColor = Fondo, topBar = { BackHeader("Servicios", onBack) }, floatingActionButton = { FloatingActionButton(onClick = { showAdd = true }, containerColor = Dorado) { Icon(Icons.Default.Add, null, tint = Color.Black) } }) { padding ->
        LazyColumn(modifier = Modifier.fillMaxSize().padding(padding), contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            if (services.isEmpty()) item { EmptyCard("Agrega el primer servicio.") }
            items(services, key = { it.id }) { service ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) { Icon(Icons.Default.Storefront, null, tint = if (service.active) Dorado else Gris); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(service.name, color = Color.White, fontWeight = FontWeight.Bold); Text("${money(service.price)} · ${service.duration} min", color = Gris, fontSize = 12.sp); Text(if (service.commissionOverride >= 0) "Comisión especial ${service.commissionOverride}%" else "Usa comisión del barbero", color = Gris, fontSize = 11.sp) }; Switch(checked = service.active, onCheckedChange = { onToggle(service) }) }
                }
            }
        }
    }
    if (showAdd) NewServiceDialog({ showAdd = false }) { onAdd(it); showAdd = false }
}

@Composable
fun NewServiceDialog(onDismiss: () -> Unit, onSave: (ServiceItem) -> Unit) {
    var name by remember { mutableStateOf("") }; var price by remember { mutableStateOf("") }; var duration by remember { mutableStateOf("30") }; var commission by remember { mutableStateOf("") }
    AlertDialog(onDismissRequest = onDismiss, title = { Text("Nuevo servicio") }, text = { Column(verticalArrangement = Arrangement.spacedBy(10.dp)) {
        OutlinedTextField(name, { name = it }, label = { Text("Nombre") }, singleLine = true, modifier = Modifier.fillMaxWidth())
        MoneyField("Precio", price) { price = it }
        OutlinedTextField(duration, { duration = it.filter(Char::isDigit) }, label = { Text("Duración en minutos") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        OutlinedTextField(commission, { commission = it.filter(Char::isDigit).take(3) }, label = { Text("Comisión especial % (opcional)") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number), singleLine = true, modifier = Modifier.fillMaxWidth())
        Text("Si la dejas vacía se usa el % configurado en el barbero.", color = Gris, fontSize = 11.sp)
    } }, confirmButton = { Button(onClick = { if (name.isNotBlank() && (price.toIntOrNull() ?: 0) > 0) onSave(ServiceItem(System.currentTimeMillis(), name.trim(), price.toIntOrNull() ?: 0, duration.toIntOrNull() ?: 30, true, commission.toIntOrNull()?.coerceIn(0,100) ?: -1)) }, colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar") } }, dismissButton = { TextButton(onClick = onDismiss) { Text("Cancelar") } })
}

@Composable
fun CommissionsScreen(sales: List<SaleItem>, barbers: List<BarberItem>, services: List<ServiceItem>, onBack: () -> Unit) {
    var filter by remember { mutableStateOf("Hoy") }
    val filtered = sales.filter { when (filter) { "Hoy" -> it.date == today(); "7 días" -> isWithinDays(it.date, 7); "Mes" -> isInCurrentMonth(it.date); else -> true } }
    val rows = filtered.groupBy { it.barber }.map { (barberName, list) ->
        val amount = list.sumOf { sale -> if (sale.commissionAmount > 0) sale.commissionAmount else commissionAmount(max(0, (if (sale.serviceBase > 0) sale.serviceBase else sale.amount) - sale.discount), services.firstOrNull { it.name == sale.service }, barbers.firstOrNull { it.name == sale.barber }) }
        Triple(barberName, list.sumOf { it.amount }, amount)
    }.sortedByDescending { it.third }
    val totalCommission = rows.sumOf { it.third }

    Column(Modifier.fillMaxSize().background(Fondo)) {
        BackHeader("Comisiones", onBack)
        LazyColumn(contentPadding = PaddingValues(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
            item {
                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) { listOf("Hoy", "7 días", "Mes", "Todas").forEach { item -> FilterChip(selected = filter == item, onClick = { filter = item }, label = { Text(item) }) } }
                Spacer(Modifier.height(12.dp)); Text("A pagar: ${money(totalCommission)}", color = Dorado, fontSize = 28.sp, fontWeight = FontWeight.Black)
                Text("Se calcula sobre el valor del servicio menos descuento. Extras/productos y propina no generan comisión.", color = Gris, fontSize = 11.sp)
            }
            if (rows.isEmpty()) item { EmptyCard("No hay ventas con comisión en este período.") }
            items(rows) { row ->
                Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp), modifier = Modifier.fillMaxWidth()) {
                    Row(Modifier.padding(15.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.ContentCut, null, tint = Dorado); Spacer(Modifier.width(12.dp)); Column(Modifier.weight(1f)) { Text(row.first, color = Color.White, fontWeight = FontWeight.Bold); Text("Ventas: ${money(row.second)}", color = Gris, fontSize = 12.sp) }; Text(money(row.third), color = Dorado, fontWeight = FontWeight.Bold)
                    }
                }
            }
        }
    }
}

@Composable
fun QrScreen(
    shopName: String,
    bookingLink: String,
    services: List<ServiceItem>,
    barbers: List<BarberItem>,
    appointments: List<Appointment>,
    blocks: List<ScheduleBlock>,
    onBack: () -> Unit,
    onSaveLink: (String) -> Unit,
    onOnlineBooking: (Appointment) -> Unit
) {
    val context = LocalContext.current
    var link by remember(bookingLink) { mutableStateOf(bookingLink) }
    var showBooking by remember { mutableStateOf(false) }
    val qr = remember(link) { generateQrBitmap(link) }
    Column(Modifier.fillMaxSize().background(Fondo)) {
        BackHeader("QR Reservas", onBack)
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), horizontalAlignment = Alignment.CenterHorizontally) {
            Surface(color = Color.White, shape = RoundedCornerShape(24.dp)) {
                if (qr != null) Image(bitmap = qr.asImageBitmap(), contentDescription = "QR de reservas", modifier = Modifier.padding(18.dp).size(210.dp))
                else Icon(Icons.Default.QrCode2, null, tint = Color.Black, modifier = Modifier.padding(34.dp).size(150.dp))
            }
            Spacer(Modifier.height(16.dp))
            Text(shopName, color = Color.White, fontSize = 22.sp, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(12.dp))
            OutlinedTextField(link, { link = it }, label = { Text("Enlace público de reservas") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
            Spacer(Modifier.height(10.dp))
            Button(onClick = { onSaveLink(link.trim()) }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar enlace y actualizar QR") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { shareText(context, "Reserva tu hora en $shopName: $link") }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.Share, null); Spacer(Modifier.width(8.dp)); Text("Compartir por WhatsApp / redes") }
            Spacer(Modifier.height(8.dp))
            OutlinedButton(onClick = { showBooking = true }, modifier = Modifier.fillMaxWidth()) { Icon(Icons.Default.EventAvailable, null); Spacer(Modifier.width(8.dp)); Text("Probar flujo de reserva del cliente") }
            Spacer(Modifier.height(18.dp))
            Surface(color = Dorado.copy(alpha = 0.10f), shape = RoundedCornerShape(16.dp)) {
                Text("El QR ya es real y apunta al enlace configurado. Para que una reserva hecha desde otro teléfono aparezca automáticamente en esta agenda, el enlace debe estar conectado al backend/web de la barbería. El flujo de cliente queda preparado en esta versión.", color = Gris, fontSize = 12.sp, modifier = Modifier.padding(14.dp))
            }
        }
    }
    if (showBooking) {
        NewAppointmentDialog(emptyList(), services, barbers, appointments, blocks, null, "Online", { showBooking = false }) { onOnlineBooking(it); showBooking = false }
    }
}

@Composable
fun SettingsScreen(shopName: String, shopPhone: String, onBack: () -> Unit, onSave: (String, String) -> Unit) {
    var name by remember(shopName) { mutableStateOf(shopName) }; var phone by remember(shopPhone) { mutableStateOf(shopPhone) }; var saved by remember { mutableStateOf(false) }
    Column(Modifier.fillMaxSize().background(Fondo)) {
        BackHeader("Configuración", onBack)
        Column(Modifier.padding(20.dp).verticalScroll(rememberScrollState()), verticalArrangement = Arrangement.spacedBy(12.dp)) {
            OutlinedTextField(name, { name = it }, label = { Text("Nombre de la barbería") }, singleLine = true, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(phone, { phone = it }, label = { Text("WhatsApp") }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone), singleLine = true, modifier = Modifier.fillMaxWidth())
            Button(onClick = { if (name.isNotBlank()) { onSave(name.trim(), phone.trim()); saved = true } }, modifier = Modifier.fillMaxWidth(), colors = ButtonDefaults.buttonColors(containerColor = Dorado, contentColor = Color.Black)) { Text("Guardar cambios") }
            if (saved) Text("Cambios guardados ✓", color = Verde, fontSize = 13.sp)
            Surface(color = Tarjeta, shape = RoundedCornerShape(16.dp)) { Column(Modifier.padding(16.dp)) { Text("Versión 3", color = Dorado, fontWeight = FontWeight.Bold); Text("Agenda profesional, QR/enlace, recordatorios locales, caja avanzada y comisiones.", color = Gris, fontSize = 12.sp) } }
        }
    }
}

@Composable
fun SelectField(label: String, value: String, options: List<String>, onSelect: (String) -> Unit) {
    var expanded by remember { mutableStateOf(false) }
    Column {
        Text(label, color = Gris, fontSize = 12.sp); Spacer(Modifier.height(4.dp))
        Box {
            OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(value.ifBlank { "Seleccionar" }, modifier = Modifier.weight(1f), color = Color.White); Icon(Icons.Default.ArrowDropDown, null) }
            DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                options.forEach { option -> DropdownMenuItem(text = { Text(option) }, onClick = { onSelect(option); expanded = false }) }
                if (options.isEmpty()) DropdownMenuItem(text = { Text("Sin opciones") }, onClick = { expanded = false })
            }
        }
    }
}

@Composable
fun SectionHeader(title: String, subtitle: String) {
    Column(Modifier.fillMaxWidth().background(Fondo).padding(horizontal = 18.dp, vertical = 14.dp)) { Text(title, color = Color.White, fontSize = 27.sp, fontWeight = FontWeight.Bold); Text(subtitle, color = Gris, fontSize = 12.sp) }
}

@Composable
fun BackHeader(title: String, onBack: () -> Unit) {
    Row(modifier = Modifier.fillMaxWidth().background(Fondo).padding(horizontal = 8.dp, vertical = 8.dp), verticalAlignment = Alignment.CenterVertically) { IconButton(onClick = onBack) { Icon(Icons.Default.ArrowBack, null, tint = Dorado) }; Text(title, color = Color.White, fontSize = 23.sp, fontWeight = FontWeight.Bold) }
}

private fun today(): String = SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).format(Date())
private fun nowTime(): String = SimpleDateFormat(TIME_PATTERN, Locale.getDefault()).format(Date())
private fun parseDate(value: String): Date? = runCatching { SimpleDateFormat(DATE_PATTERN, Locale.getDefault()).apply { isLenient = false }.parse(value) }.getOrNull()
private fun parseDateTime(date: String, time: String): Date? = runCatching { SimpleDateFormat("$DATE_PATTERN $TIME_PATTERN", Locale.getDefault()).apply { isLenient = false }.parse("$date $time") }.getOrNull()

private fun isWithinDays(date: String, days: Int): Boolean {
    val d = parseDate(date)?.time ?: return false
    val start = parseDate(today())?.time ?: return false
    return d in start..(start + days * 24L * 60L * 60L * 1000L)
}

private fun isInCurrentMonth(date: String): Boolean {
    val d = parseDate(date) ?: return false
    val a = Calendar.getInstance().apply { time = d }
    val b = Calendar.getInstance()
    return a.get(Calendar.YEAR) == b.get(Calendar.YEAR) && a.get(Calendar.MONTH) == b.get(Calendar.MONTH)
}

private fun toMinutes(value: String): Int? {
    val parts = value.split(":")
    if (parts.size != 2) return null
    val h = parts[0].toIntOrNull() ?: return null
    val m = parts[1].toIntOrNull() ?: return null
    if (h !in 0..23 || m !in 0..59) return null
    return h * 60 + m
}

private fun appointmentHasConflict(candidate: Appointment, appointments: List<Appointment>, blocks: List<ScheduleBlock>, ignoreId: Long?): Boolean {
    val start = toMinutes(candidate.time) ?: return true
    val end = start + candidate.duration
    val apptConflict = appointments.any {
        it.id != ignoreId && it.barber == candidate.barber && it.date == candidate.date && it.status !in listOf("Cancelada") && run {
            val otherStart = toMinutes(it.time) ?: return@run false
            val otherEnd = otherStart + it.duration
            start < otherEnd && end > otherStart
        }
    }
    if (apptConflict) return true
    return blocks.any {
        it.barber == candidate.barber && it.date == candidate.date && run {
            val blockStart = toMinutes(it.start) ?: return@run false
            val blockEnd = toMinutes(it.end) ?: return@run false
            start < blockEnd && end > blockStart
        }
    }
}

private fun commissionAmount(base: Int, service: ServiceItem?, barber: BarberItem?): Int {
    val percent = when {
        service != null && service.commissionOverride >= 0 -> service.commissionOverride
        barber != null -> barber.commission
        else -> 0
    }
    return base * percent / 100
}

private fun money(value: Int): String {
    val nf = NumberFormat.getIntegerInstance(Locale("es", "CL"))
    return "$${nf.format(value)}"
}

private fun shareText(context: Context, text: String) {
    runCatching {
        context.startActivity(Intent.createChooser(Intent(Intent.ACTION_SEND).apply { type = "text/plain"; putExtra(Intent.EXTRA_TEXT, text) }, "Compartir"))
    }
}

private fun openWhatsAppReminder(context: Context, appointment: Appointment, shopName: String) {
    val digits = appointment.phone.filter(Char::isDigit)
    if (digits.isBlank()) return
    val phone = if (digits.startsWith("56")) digits else if (digits.startsWith("9")) "56$digits" else digits
    val message = Uri.encode("Hola ${appointment.client}, te recordamos tu hora en $shopName el ${appointment.date} a las ${appointment.time} para ${appointment.service}. ¡Te esperamos!")
    runCatching { context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://wa.me/$phone?text=$message"))) }
}

private fun generateQrBitmap(text: String, size: Int = 700): Bitmap? {
    if (text.isBlank()) return null
    return runCatching {
        val matrix = MultiFormatWriter().encode(text, BarcodeFormat.QR_CODE, size, size)
        val bitmap = Bitmap.createBitmap(size, size, Bitmap.Config.RGB_565)
        for (x in 0 until size) for (y in 0 until size) bitmap.setPixel(x, y, if (matrix[x, y]) android.graphics.Color.BLACK else android.graphics.Color.WHITE)
        bitmap
    }.getOrNull()
}

object LocalStore {
    private const val PREF = "barberia_v2"
    private const val APPOINTMENTS = "appointments"
    private const val CLIENTS = "clients"
    private const val BARBERS = "barbers"
    private const val SERVICES = "services"
    private const val SALES = "sales"
    private const val BLOCKS = "blocks"
    private const val CASH_CLOSES = "cash_closes"
    private const val SHOP_NAME = "shop_name"
    private const val SHOP_PHONE = "shop_phone"
    private const val BOOKING_LINK = "booking_link"

    private fun prefs(context: Context) = context.getSharedPreferences(PREF, Context.MODE_PRIVATE)

    fun getShopName(context: Context): String = prefs(context).getString(SHOP_NAME, "Barbería Central") ?: "Barbería Central"
    fun setShopName(context: Context, value: String) = prefs(context).edit().putString(SHOP_NAME, value).apply()
    fun getShopPhone(context: Context): String = prefs(context).getString(SHOP_PHONE, "") ?: ""
    fun setShopPhone(context: Context, value: String) = prefs(context).edit().putString(SHOP_PHONE, value).apply()
    fun getBookingLink(context: Context): String = prefs(context).getString(BOOKING_LINK, "https://barberia.negociospyme.cl/reservar") ?: "https://barberia.negociospyme.cl/reservar"
    fun setBookingLink(context: Context, value: String) = prefs(context).edit().putString(BOOKING_LINK, value).apply()

    fun loadAppointments(context: Context): List<Appointment> {
        val raw = prefs(context).getString(APPOINTMENTS, null)
        if (raw.isNullOrBlank()) return listOf(
            Appointment(1, "Carlos González", "+56911111111", "Corte + barba", "Diego", today(), "13:30", "Confirmada", 18000, 50, "Manual"),
            Appointment(2, "Martín Pérez", "+56922222222", "Corte clásico", "Sebastián", today(), "15:00", "Pendiente", 12000, 30, "Online")
        )
        return runCatching {
            val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(Appointment(o.optLong("id"), o.optString("client"), o.optString("phone"), o.optString("service"), o.optString("barber"), o.optString("date"), o.optString("time"), o.optString("status", "Pendiente"), o.optInt("price"), o.optInt("duration", 30), o.optString("source", "Manual"))) } }
        }.getOrDefault(emptyList())
    }

    fun saveAppointments(context: Context, list: List<Appointment>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("client", it.client); put("phone", it.phone); put("service", it.service); put("barber", it.barber); put("date", it.date); put("time", it.time); put("status", it.status); put("price", it.price); put("duration", it.duration); put("source", it.source) }) }; prefs(context).edit().putString(APPOINTMENTS, arr.toString()).apply()
    }

    fun loadClients(context: Context): List<ClientItem> {
        val raw = prefs(context).getString(CLIENTS, null)
        if (raw.isNullOrBlank()) return listOf(ClientItem(1, "Carlos González", "+56911111111", 8, 112000, "Cliente frecuente"), ClientItem(2, "Martín Pérez", "+56922222222", 5, 68000, ""))
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ClientItem(o.optLong("id"), o.optString("name"), o.optString("phone"), o.optInt("visits"), o.optInt("spent"), o.optString("notes"))) } } }.getOrDefault(emptyList())
    }

    fun saveClients(context: Context, list: List<ClientItem>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("phone", it.phone); put("visits", it.visits); put("spent", it.spent); put("notes", it.notes) }) }; prefs(context).edit().putString(CLIENTS, arr.toString()).apply()
    }

    fun loadBarbers(context: Context): List<BarberItem> {
        val raw = prefs(context).getString(BARBERS, null)
        if (raw.isNullOrBlank()) return listOf(BarberItem(1, "Diego", 40, true), BarberItem(2, "Sebastián", 40, true), BarberItem(3, "Andrés", 45, true))
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(BarberItem(o.optLong("id"), o.optString("name"), o.optInt("commission"), o.optBoolean("active", true))) } } }.getOrDefault(emptyList())
    }

    fun saveBarbers(context: Context, list: List<BarberItem>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("commission", it.commission); put("active", it.active) }) }; prefs(context).edit().putString(BARBERS, arr.toString()).apply()
    }

    fun loadServices(context: Context): List<ServiceItem> {
        val raw = prefs(context).getString(SERVICES, null)
        if (raw.isNullOrBlank()) return listOf(
            ServiceItem(1, "Corte clásico", 12000, 30, true), ServiceItem(2, "Degradado", 14000, 45, true), ServiceItem(3, "Barba", 8000, 20, true), ServiceItem(4, "Corte + barba", 18000, 50, true, 45)
        )
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ServiceItem(o.optLong("id"), o.optString("name"), o.optInt("price"), o.optInt("duration"), o.optBoolean("active", true), if (o.has("commissionOverride")) o.optInt("commissionOverride", -1) else -1)) } } }.getOrDefault(emptyList())
    }

    fun saveServices(context: Context, list: List<ServiceItem>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("name", it.name); put("price", it.price); put("duration", it.duration); put("active", it.active); put("commissionOverride", it.commissionOverride) }) }; prefs(context).edit().putString(SERVICES, arr.toString()).apply()
    }

    fun loadSales(context: Context): List<SaleItem> {
        val raw = prefs(context).getString(SALES, null) ?: return emptyList()
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(SaleItem(o.optLong("id"), o.optString("client"), o.optString("service"), o.optString("barber"), o.optInt("amount"), o.optString("payment"), o.optString("date"), o.optString("time", ""), o.optInt("serviceBase", o.optInt("amount")), o.optInt("extra"), o.optInt("discount"), o.optInt("tip"), o.optInt("commissionAmount"))) } } }.getOrDefault(emptyList())
    }

    fun saveSales(context: Context, list: List<SaleItem>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("client", it.client); put("service", it.service); put("barber", it.barber); put("amount", it.amount); put("payment", it.payment); put("date", it.date); put("time", it.time); put("serviceBase", it.serviceBase); put("extra", it.extra); put("discount", it.discount); put("tip", it.tip); put("commissionAmount", it.commissionAmount) }) }; prefs(context).edit().putString(SALES, arr.toString()).apply()
    }

    fun loadBlocks(context: Context): List<ScheduleBlock> {
        val raw = prefs(context).getString(BLOCKS, null) ?: return emptyList()
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(ScheduleBlock(o.optLong("id"), o.optString("barber"), o.optString("date"), o.optString("start"), o.optString("end"), o.optString("reason"))) } } }.getOrDefault(emptyList())
    }

    fun saveBlocks(context: Context, list: List<ScheduleBlock>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("barber", it.barber); put("date", it.date); put("start", it.start); put("end", it.end); put("reason", it.reason) }) }; prefs(context).edit().putString(BLOCKS, arr.toString()).apply()
    }

    fun loadCashCloses(context: Context): List<CashClose> {
        val raw = prefs(context).getString(CASH_CLOSES, null) ?: return emptyList()
        return runCatching { val arr = JSONArray(raw); buildList { for (i in 0 until arr.length()) { val o = arr.getJSONObject(i); add(CashClose(o.optLong("id"), o.optString("date"), o.optString("time"), o.optInt("total"), o.optInt("cash"), o.optInt("transfer"), o.optInt("card"), o.optString("note"))) } } }.getOrDefault(emptyList())
    }

    fun saveCashCloses(context: Context, list: List<CashClose>) {
        val arr = JSONArray(); list.forEach { arr.put(JSONObject().apply { put("id", it.id); put("date", it.date); put("time", it.time); put("total", it.total); put("cash", it.cash); put("transfer", it.transfer); put("card", it.card); put("note", it.note) }) }; prefs(context).edit().putString(CASH_CLOSES, arr.toString()).apply()
    }
}
