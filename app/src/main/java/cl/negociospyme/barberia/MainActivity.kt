package cl.negociospyme.barberia

import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.Image
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyListScope
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.shape.RoundedCornerShape
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
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import com.google.zxing.BarcodeFormat
import com.google.zxing.MultiFormatWriter
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL
import java.text.NumberFormat
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

private val Fondo = Color(0xFF101010)
private val Tarjeta = Color(0xFF1A1A1A)
private val Dorado = Color(0xFFD9B735)
private val Gris = Color(0xFFAAAAAA)
private val Verde = Color(0xFF55B85A)
private val Rojo = Color(0xFFE66767)
private const val BASE_URL = "https://appbarberia.negociospyme.cl"

data class Session(val token:String,val shopId:Long,val shopName:String,val slug:String,val userName:String,val email:String)
data class Barber(val id:Long,val name:String,val phone:String,val commission:Double,val active:Boolean)
data class Service(val id:Long,val name:String,val price:Double,val duration:Int,val commission:Double?,val active:Boolean)
data class Client(val id:Long,val name:String,val phone:String,val visits:Int,val spent:Double,val points:Int)
data class Product(val id:Long,val name:String,val price:Double,val stock:Int,val minStock:Int,val active:Boolean)
data class Appointment(val id:Long,val client:String,val phone:String,val start:String,val status:String,val barberId:Long,val barber:String,val serviceId:Long,val service:String,val price:Double)
data class Sale(val id:Long,val total:Double,val commission:Double,val payment:String,val created:String,val detail:String,val client:String,val barber:String)
data class Dashboard(val appointments:Int,val sales:Double,val barbers:Int,val lowStock:Int,val plan:String,val status:String,val next:List<String>)

class MainActivity: ComponentActivity(){
    override fun onCreate(savedInstanceState: Bundle?){
        super.onCreate(savedInstanceState)
        setContent{
            MaterialTheme(colorScheme=darkColorScheme(primary=Dorado,background=Fondo,surface=Tarjeta,onPrimary=Color.Black)){
                BarberiaCloudApp()
            }
        }
    }
}

object Api {
    private fun connection(path:String,method:String,token:String?=null):HttpURLConnection{
        return (URL(BASE_URL+path).openConnection() as HttpURLConnection).apply{
            requestMethod=method
            connectTimeout=12000
            readTimeout=12000
            setRequestProperty("Accept","application/json")
            if(token!=null) setRequestProperty("Authorization","Bearer $token")
        }
    }
    private fun read(c:HttpURLConnection):JSONObject{
        val stream=if(c.responseCode in 200..299)c.inputStream else c.errorStream
        val body=stream?.bufferedReader(Charsets.UTF_8)?.use{it.readText()}?:"{}"
        val j=JSONObject(body)
        if(!j.optBoolean("ok",false)) throw IllegalStateException(j.optString("error","Error del servidor"))
        return j
    }
    suspend fun get(path:String,token:String)=withContext(Dispatchers.IO){ read(connection(path,"GET",token)) }
    suspend fun post(path:String,token:String?,data:JSONObject)=withContext(Dispatchers.IO){
        val c=connection(path,"POST",token).apply{
            doOutput=true
            setRequestProperty("Content-Type","application/json; charset=utf-8")
        }
        c.outputStream.use{it.write(data.toString().toByteArray(Charsets.UTF_8))}
        read(c)
    }
    suspend fun login(email:String,password:String):Session{
        val j=post("/api/login.php",null,JSONObject().put("email",email).put("password",password))
        val u=j.getJSONObject("usuario"); val b=j.getJSONObject("barberia")
        return Session(j.getString("token"),b.getLong("id"),b.getString("nombre"),b.optString("slug"),u.getString("nombre"),u.getString("email"))
    }
}

fun money(v:Double)=NumberFormat.getCurrencyInstance(Locale("es","CL")).format(v)
fun today():String=SimpleDateFormat("yyyy-MM-dd",Locale.getDefault()).format(Date())
fun monthStart():String=SimpleDateFormat("yyyy-MM-01",Locale.getDefault()).format(Date())
fun JSONObject.str(k:String)=optString(k,"")
fun JSONObject.long(k:String)=optLong(k,0L)
fun JSONObject.dbl(k:String)=optDouble(k,0.0)
fun JSONObject.int(k:String)=optInt(k,0)
fun JSONObject.bool(k:String)=optInt(k,1)==1 || optBoolean(k,false)

@Composable
fun BarberiaCloudApp(){
    val ctx=LocalContext.current
    val prefs=remember{ctx.getSharedPreferences("barberia_cloud",Context.MODE_PRIVATE)}
    var session by remember{
        mutableStateOf(
            prefs.getString("token",null)?.let{
                Session(it,prefs.getLong("shopId",0),prefs.getString("shopName","Barbería")!!,prefs.getString("slug","")!!,prefs.getString("userName","Usuario")!!,prefs.getString("email","")!!)
            }
        )
    }
    if(session==null){
        LoginScreen{ s->
            prefs.edit().putString("token",s.token).putLong("shopId",s.shopId).putString("shopName",s.shopName).putString("slug",s.slug)
                .putString("userName",s.userName).putString("email",s.email).apply()
            session=s
        }
    }else{
        CloudShell(session!!){
            prefs.edit().clear().apply(); session=null
        }
    }
}

@Composable
fun LoginScreen(onLogin:(Session)->Unit){
    var email by remember{mutableStateOf("")}; var pass by remember{mutableStateOf("")}; var loading by remember{mutableStateOf(false)}
    var error by remember{mutableStateOf("")}; val scope=rememberCoroutineScope()
    Column(Modifier.fillMaxSize().background(Fondo).padding(28.dp),verticalArrangement=Arrangement.Center){
        Icon(Icons.Default.ContentCut,null,tint=Dorado,modifier=Modifier.size(70.dp))
        Spacer(Modifier.height(18.dp)); Text("BARBERÍA",fontSize=38.sp,fontWeight=FontWeight.Black,color=Dorado)
        Text("NegociosPyme Cloud",color=Gris,fontSize=18.sp); Spacer(Modifier.height(28.dp))
        OutlinedTextField(email,{email=it},label={Text("Correo")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pass,{pass=it},label={Text("Contraseña")},modifier=Modifier.fillMaxWidth(),singleLine=true,visualTransformation=PasswordVisualTransformation())
        if(error.isNotBlank()){Spacer(Modifier.height(10.dp));Text(error,color=Rojo)}
        Spacer(Modifier.height(18.dp))
        Button(onClick={
            loading=true;error=""
            scope.launch{
                runCatching{Api.login(email.trim(),pass)}.onSuccess{onLogin(it)}.onFailure{error=it.message?:"Error";loading=false}
            }
        },modifier=Modifier.fillMaxWidth().height(54.dp),enabled=!loading){
            if(loading) CircularProgressIndicator(Modifier.size(22.dp),strokeWidth=2.dp) else Text("INGRESAR",fontWeight=FontWeight.Bold)
        }
    }
}

data class NavItem(val key:String,val title:String,val icon:ImageVector)
private val navItems=listOf(
    NavItem("inicio","Inicio",Icons.Default.Home),
    NavItem("agenda","Agenda",Icons.Default.CalendarMonth),
    NavItem("caja","Caja",Icons.Default.PointOfSale),
    NavItem("clientes","Clientes",Icons.Default.People),
    NavItem("mas","Más",Icons.Default.Menu)
)

@Composable
fun CloudShell(s:Session,onLogout:()->Unit){
    var screen by remember{mutableStateOf("inicio")}
    Scaffold(
        bottomBar={
            NavigationBar(containerColor=Color(0xFF161616)){
                navItems.forEach{n->
                    NavigationBarItem(selected=screen==n.key,onClick={screen=n.key},icon={Icon(n.icon,null)},label={Text(n.title)})
                }
            }
        },
        containerColor=Fondo
    ){pad->
        Box(Modifier.padding(pad)){
            when(screen){
                "inicio"->HomeScreen(s,{screen=it},onLogout)
                "agenda"->AgendaScreen(s)
                "caja"->CashScreen(s)
                "clientes"->ClientsScreen(s)
                else->MoreScreen(s,{screen=it},onLogout)
            }
            when(screen){
                "barberos"->BarbersScreen(s){screen="mas"}
                "servicios"->ServicesScreen(s){screen="mas"}
                "inventario"->ProductsScreen(s){screen="mas"}
                "reportes"->ReportsScreen(s){screen="mas"}
                "qr"->QrScreen(s){screen="mas"}
            }
        }
    }
}

@Composable
fun HomeScreen(s:Session,navigate:(String)->Unit,onLogout:()->Unit){
    var data by remember{mutableStateOf<Dashboard?>(null)}; var error by remember{mutableStateOf("")}; val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{
        val j=Api.get("/api/dashboard.php",s.token); val x=j.getJSONObject("summary"); val b=j.getJSONObject("barberia"); val a=j.getJSONArray("proximas")
        Dashboard(x.int("citas_hoy"),x.dbl("ventas_hoy"),x.int("barberos_activos"),x.int("stock_bajo"),b.str("plan"),b.str("estado"),
            List(a.length()){i->val o=a.getJSONObject(i);"${o.str("inicio").takeLast(8).take(5)} · ${o.str("cliente_nombre")} · ${o.str("servicio")}"})
    }.onSuccess{data=it;error=""}.onFailure{error=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    LazyColumn(Modifier.fillMaxSize().background(Fondo),contentPadding=PaddingValues(18.dp),verticalArrangement=Arrangement.spacedBy(14.dp)){
        item{
            Row(verticalAlignment=Alignment.CenterVertically){
                Column(Modifier.weight(1f)){Text(s.shopName,fontSize=26.sp,fontWeight=FontWeight.Bold);Text("Conectado a NegociosPyme Cloud",color=Verde)}
                IconButton(onClick={load()}){Icon(Icons.Default.Refresh,null,tint=Dorado)}
            }
        }
        if(error.isNotBlank()) item{ErrorBox(error)}
        data?.let{d->
            item{
                Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){
                    Stat("${d.appointments}","Citas hoy",Modifier.weight(1f));Stat(money(d.sales),"Ventas",Modifier.weight(1f));Stat("${d.lowStock}","Stock bajo",Modifier.weight(1f))
                }
            }
            item{Text("Plan ${d.plan} · ${d.status}",color=Gris)}
            item{SectionTitle("Próximas citas")}
            if(d.next.isEmpty()) item{Text("Sin próximas citas",color=Gris)} else items(d.next){CardLine(it)}
        }
        item{SectionTitle("Gestión rápida")}
        item{MenuCard("Barberos","Equipo y comisiones",Icons.Default.ContentCut){navigate("barberos")}}
        item{MenuCard("Servicios","Precios, duración y comisión",Icons.Default.Build){navigate("servicios")}}
        item{MenuCard("Inventario","Productos y stock",Icons.Default.Inventory2){navigate("inventario")}}
        item{MenuCard("Reportes","Ventas y comisiones",Icons.Default.BarChart){navigate("reportes")}}
        item{MenuCard("QR reservas","Link público para clientes",Icons.Default.QrCode2){navigate("qr")}}
        item{OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){Text("Cerrar sesión")}}
    }
}

@Composable fun Stat(v:String,l:String,m:Modifier){Surface(m,shape=RoundedCornerShape(14.dp),color=Tarjeta){Column(Modifier.padding(12.dp)){Text(v,color=Dorado,fontWeight=FontWeight.Bold,fontSize=18.sp);Text(l,color=Gris,fontSize=12.sp)}}}
@Composable fun SectionTitle(t:String){Text(t,fontWeight=FontWeight.Bold,fontSize=19.sp)}
@Composable fun ErrorBox(t:String){Surface(color=Color(0xFF4B2020),shape=RoundedCornerShape(12.dp)){Text(t,color=Color(0xFFFFB2B2),modifier=Modifier.padding(12.dp))}}
@Composable fun CardLine(t:String){Surface(color=Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Text(t,modifier=Modifier.padding(14.dp))}}
@Composable fun MenuCard(t:String,sub:String,ic:ImageVector,onClick:()->Unit){Surface(color=Tarjeta,shape=RoundedCornerShape(14.dp),modifier=Modifier.fillMaxWidth().clickable{onClick()}){Row(Modifier.padding(16.dp),verticalAlignment=Alignment.CenterVertically){Icon(ic,null,tint=Dorado);Spacer(Modifier.width(14.dp));Column(Modifier.weight(1f)){Text(t,fontWeight=FontWeight.Bold);Text(sub,color=Gris,fontSize=13.sp)};Icon(Icons.Default.ChevronRight,null,tint=Gris)}}}

@Composable
fun AgendaScreen(s:Session){
    var date by remember{mutableStateOf(today())}; var list by remember{mutableStateOf<List<Appointment>>(emptyList())}
    var barbers by remember{mutableStateOf<List<Barber>>(emptyList())}; var services by remember{mutableStateOf<List<Service>>(emptyList())}
    var show by remember{mutableStateOf(false)}; var error by remember{mutableStateOf("")}; val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{
        val a=Api.get("/api/appointments.php?from=$date&to=$date",s.token).getJSONArray("items")
        val bs=Api.get("/api/barbers.php",s.token).getJSONArray("items")
        val ss=Api.get("/api/services.php",s.token).getJSONArray("items")
        Triple(List(a.length()){i->val o=a.getJSONObject(i);Appointment(o.long("id"),o.str("cliente_nombre"),o.str("cliente_telefono"),o.str("inicio"),o.str("estado"),o.long("barbero_id"),o.str("barbero"),o.long("servicio_id"),o.str("servicio"),o.dbl("precio"))},
            List(bs.length()){i->val o=bs.getJSONObject(i);Barber(o.long("id"),o.str("nombre"),o.str("telefono"),o.dbl("comision_pct"),o.bool("activo"))},
            List(ss.length()){i->val o=ss.getJSONObject(i);Service(o.long("id"),o.str("nombre"),o.dbl("precio"),o.int("duracion_min"),if(o.isNull("comision_pct"))null else o.dbl("comision_pct"),o.bool("activo"))})
    }.onSuccess{list=it.first;barbers=it.second.filter{x->x.active};services=it.third.filter{x->x.active};error=""}.onFailure{error=it.message?:"Error"}}}
    LaunchedEffect(date){load()}
    Column(Modifier.fillMaxSize().background(Fondo).padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){Text("Agenda",fontSize=26.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick={load()}){Icon(Icons.Default.Refresh,null)}}
        OutlinedTextField(date,{date=it},label={Text("Fecha yyyy-MM-dd")},modifier=Modifier.fillMaxWidth(),singleLine=true)
        if(error.isNotBlank()) ErrorBox(error)
        Spacer(Modifier.height(8.dp))
        Button(onClick={show=true},modifier=Modifier.fillMaxWidth()){Icon(Icons.Default.Add,null);Spacer(Modifier.width(6.dp));Text("Nueva cita")}
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp)){
            items(list){a->
                Surface(color=Tarjeta,shape=RoundedCornerShape(13.dp),modifier=Modifier.fillMaxWidth()){
                    Column(Modifier.padding(14.dp)){
                        Text("${a.start.takeLast(8).take(5)} · ${a.client}",fontWeight=FontWeight.Bold)
                        Text("${a.service} · ${a.barber} · ${money(a.price)}",color=Gris)
                        Text(a.status,color=if(a.status=="cancelada")Rojo else Verde)
                        Row(horizontalArrangement=Arrangement.spacedBy(6.dp)){
                            if(a.status!="confirmada"&&a.status!="atendida"&&a.status!="cancelada") SmallAction("Confirmar"){scope.launch{runCatching{Api.post("/api/appointments.php",s.token,JSONObject().put("action","status").put("id",a.id).put("estado","confirmada"))}.onSuccess{load()}}}
                            if(a.status!="atendida"&&a.status!="cancelada") SmallAction("Atendida"){scope.launch{runCatching{Api.post("/api/appointments.php",s.token,JSONObject().put("action","status").put("id",a.id).put("estado","atendida"))}.onSuccess{load()}}}
                            if(a.status!="cancelada"&&a.status!="atendida") SmallAction("Cancelar"){scope.launch{runCatching{Api.post("/api/appointments.php",s.token,JSONObject().put("action","status").put("id",a.id).put("estado","cancelada"))}.onSuccess{load()}}}
                        }
                    }
                }
            }
        }
    }
    if(show) AppointmentDialog(date,barbers,services,onDismiss={show=false}){name,phone,bid,sid,d,t->
        scope.launch{runCatching{Api.post("/api/appointments.php",s.token,JSONObject().put("action","save").put("cliente_nombre",name).put("cliente_telefono",phone).put("barbero_id",bid).put("servicio_id",sid).put("inicio","$d $t:00"))}
            .onSuccess{show=false;date=d;load()}.onFailure{error=it.message?:"Error"}}
    }
}

@Composable fun SmallAction(t:String,onClick:()->Unit){TextButton(onClick=onClick,contentPadding=PaddingValues(horizontal=7.dp)){Text(t,fontSize=12.sp)}}

@Composable
fun AppointmentDialog(defaultDate:String,barbers:List<Barber>,services:List<Service>,onDismiss:()->Unit,onSave:(String,String,Long,Long,String,String)->Unit){
    var name by remember{mutableStateOf("")};var phone by remember{mutableStateOf("")};var date by remember{mutableStateOf(defaultDate)};var time by remember{mutableStateOf("10:00")}
    var barber by remember{mutableStateOf(barbers.firstOrNull())};var service by remember{mutableStateOf(services.firstOrNull())}
    AlertDialog(onDismissRequest=onDismiss,title={Text("Nueva cita")},text={
        Column(Modifier.verticalScroll(rememberScrollState())){
            OutlinedTextField(name,{name=it},label={Text("Cliente")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(phone,{phone=it},label={Text("Teléfono")},modifier=Modifier.fillMaxWidth())
            Picker("Barbero",barber?.name?:"Seleccionar",barbers.map{it.name}){n->barber=barbers.firstOrNull{it.name==n}}
            Picker("Servicio",service?.name?:"Seleccionar",services.map{"${it.name} · ${money(it.price)}"}){n->service=services.firstOrNull{n.startsWith(it.name)}}
            OutlinedTextField(date,{date=it},label={Text("Fecha yyyy-MM-dd")},modifier=Modifier.fillMaxWidth())
            OutlinedTextField(time,{time=it},label={Text("Hora HH:mm")},modifier=Modifier.fillMaxWidth())
        }
    },confirmButton={Button(onClick={if(name.isNotBlank()&&barber!=null&&service!=null)onSave(name,phone,barber!!.id,service!!.id,date,time)}){Text("Guardar")}},dismissButton={TextButton(onClick=onDismiss){Text("Cancelar")}})
}

@Composable
fun Picker(label:String,current:String,options:List<String>,onPick:(String)->Unit){
    var open by remember{mutableStateOf(false)}
    Column{Text(label,color=Gris,fontSize=12.sp);Box{OutlinedButton(onClick={open=true},modifier=Modifier.fillMaxWidth()){Text(current,modifier=Modifier.weight(1f));Icon(Icons.Default.ArrowDropDown,null)}
        DropdownMenu(open,{open=false}){options.forEach{o->DropdownMenuItem(text={Text(o)},onClick={onPick(o);open=false})}}}}
}

@Composable
fun BarbersScreen(s:Session,onBack:()->Unit){
    var list by remember{mutableStateOf<List<Barber>>(emptyList())};var edit by remember{mutableStateOf<Barber?>(null)};var create by remember{mutableStateOf(false)};var err by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{val a=Api.get("/api/barbers.php",s.token).getJSONArray("items");List(a.length()){i->val o=a.getJSONObject(i);Barber(o.long("id"),o.str("nombre"),o.str("telefono"),o.dbl("comision_pct"),o.bool("activo"))}}.onSuccess{list=it}.onFailure{err=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    ManagedList("Barberos",onBack,{create=true},err){list.forEach{b->item{Surface(color=Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable{edit=b}){Column(Modifier.padding(14.dp)){Text(b.name,fontWeight=FontWeight.Bold);Text("${b.commission}% comisión · ${if(b.active)"Activo" else "Inactivo"}",color=Gris)}}}}}
    if(create||edit!=null) BarberDialog(edit,{create=false;edit=null}){name,phone,comm,active->
        scope.launch{runCatching{Api.post("/api/barbers.php",s.token,JSONObject().put("action","save").put("id",edit?.id?:0).put("nombre",name).put("telefono",phone).put("comision_pct",comm).put("activo",if(active)1 else 0))}
            .onSuccess{create=false;edit=null;load()}.onFailure{err=it.message?:"Error"}}
    }
}

@Composable fun BarberDialog(b:Barber?,dismiss:()->Unit,save:(String,String,Double,Boolean)->Unit){
    var name by remember{mutableStateOf(b?.name?:"")};var phone by remember{mutableStateOf(b?.phone?:"")};var comm by remember{mutableStateOf((b?.commission?:40.0).toString())};var active by remember{mutableStateOf(b?.active?:true)}
    AlertDialog(onDismissRequest=dismiss,title={Text(if(b==null)"Nuevo barbero" else "Editar barbero")},text={Column{OutlinedTextField(name,{name=it},label={Text("Nombre")});OutlinedTextField(phone,{phone=it},label={Text("Teléfono")});OutlinedTextField(comm,{comm=it},label={Text("Comisión %")});Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});Text(" Activo")}}},confirmButton={Button(onClick={save(name,phone,comm.toDoubleOrNull()?:0.0,active)}){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun ServicesScreen(s:Session,onBack:()->Unit){
    var list by remember{mutableStateOf<List<Service>>(emptyList())};var edit by remember{mutableStateOf<Service?>(null)};var create by remember{mutableStateOf(false)};var err by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{val a=Api.get("/api/services.php",s.token).getJSONArray("items");List(a.length()){i->val o=a.getJSONObject(i);Service(o.long("id"),o.str("nombre"),o.dbl("precio"),o.int("duracion_min"),if(o.isNull("comision_pct"))null else o.dbl("comision_pct"),o.bool("activo"))}}.onSuccess{list=it}.onFailure{err=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    ManagedList("Servicios",onBack,{create=true},err){list.forEach{x->item{Surface(color=Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable{edit=x}){Column(Modifier.padding(14.dp)){Text(x.name,fontWeight=FontWeight.Bold);Text("${money(x.price)} · ${x.duration} min · ${if(x.active)"Activo" else "Inactivo"}",color=Gris)}}}}}
    if(create||edit!=null) ServiceDialog(edit,{create=false;edit=null}){name,price,dur,comm,active->
        scope.launch{runCatching{val j=JSONObject().put("action","save").put("id",edit?.id?:0).put("nombre",name).put("precio",price).put("duracion_min",dur).put("activo",if(active)1 else 0);if(comm!=null)j.put("comision_pct",comm);Api.post("/api/services.php",s.token,j)}
            .onSuccess{create=false;edit=null;load()}.onFailure{err=it.message?:"Error"}}
    }
}
@Composable fun ServiceDialog(x:Service?,dismiss:()->Unit,save:(String,Double,Int,Double?,Boolean)->Unit){
    var name by remember{mutableStateOf(x?.name?:"")};var price by remember{mutableStateOf((x?.price?:12000.0).toInt().toString())};var dur by remember{mutableStateOf((x?.duration?:30).toString())};var comm by remember{mutableStateOf(x?.commission?.toString()?:"")};var active by remember{mutableStateOf(x?.active?:true)}
    AlertDialog(onDismissRequest=dismiss,title={Text("Servicio")},text={Column{OutlinedTextField(name,{name=it},label={Text("Nombre")});OutlinedTextField(price,{price=it},label={Text("Precio")});OutlinedTextField(dur,{dur=it},label={Text("Duración min")});OutlinedTextField(comm,{comm=it},label={Text("Comisión % opcional")});Row(verticalAlignment=Alignment.CenterVertically){Switch(active,{active=it});Text(" Activo")}}},confirmButton={Button(onClick={save(name,price.toDoubleOrNull()?:0.0,dur.toIntOrNull()?:30,comm.toDoubleOrNull(),active)}){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun ClientsScreen(s:Session){
    var list by remember{mutableStateOf<List<Client>>(emptyList())};var create by remember{mutableStateOf(false)};var edit by remember{mutableStateOf<Client?>(null)};var err by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{val a=Api.get("/api/clients.php",s.token).getJSONArray("items");List(a.length()){i->val o=a.getJSONObject(i);Client(o.long("id"),o.str("nombre"),o.str("telefono"),o.int("visitas"),o.dbl("gasto_total"),o.int("puntos"))}}.onSuccess{list=it}.onFailure{err=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    ManagedList("Clientes",{}, {create=true},err,showBack=false){list.forEach{x->item{Surface(color=Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth().clickable{edit=x}){Column(Modifier.padding(14.dp)){Text(x.name,fontWeight=FontWeight.Bold);Text("${x.phone} · ${x.visits} visitas · ${money(x.spent)} · ${x.points} puntos",color=Gris)}}}}}
    if(create||edit!=null) ClientDialog(edit,{create=false;edit=null}){n,p->
        scope.launch{runCatching{Api.post("/api/clients.php",s.token,JSONObject().put("id",edit?.id?:0).put("nombre",n).put("telefono",p))}.onSuccess{create=false;edit=null;load()}.onFailure{err=it.message?:"Error"}}
    }
}
@Composable fun ClientDialog(x:Client?,dismiss:()->Unit,save:(String,String)->Unit){
    var name by remember{mutableStateOf(x?.name?:"")};var phone by remember{mutableStateOf(x?.phone?:"")}
    AlertDialog(onDismissRequest=dismiss,title={Text("Cliente")},text={Column{OutlinedTextField(name,{name=it},label={Text("Nombre")});OutlinedTextField(phone,{phone=it},label={Text("Teléfono")})}},confirmButton={Button(onClick={save(name,phone)}){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun ProductsScreen(s:Session,onBack:()->Unit){
    var list by remember{mutableStateOf<List<Product>>(emptyList())};var create by remember{mutableStateOf(false)};var edit by remember{mutableStateOf<Product?>(null)};var err by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{val a=Api.get("/api/products.php",s.token).getJSONArray("items");List(a.length()){i->val o=a.getJSONObject(i);Product(o.long("id"),o.str("nombre"),o.dbl("precio"),o.int("stock"),o.int("stock_minimo"),o.bool("activo"))}}.onSuccess{list=it}.onFailure{err=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    ManagedList("Inventario",onBack,{create=true},err){list.forEach{x->item{Surface(color=if(x.stock<=x.minStock)Color(0xFF412323) else Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(14.dp)){Row{Column(Modifier.weight(1f).clickable{edit=x}){Text(x.name,fontWeight=FontWeight.Bold);Text("${money(x.price)} · Stock ${x.stock} · mínimo ${x.minStock}",color=Gris)};IconButton(onClick={scope.launch{runCatching{Api.post("/api/products.php",s.token,JSONObject().put("action","adjust_stock").put("id",x.id).put("cantidad",-1))}.onSuccess{load()}.onFailure{err=it.message?:"Error"}}}){Icon(Icons.Default.Remove,null)};IconButton(onClick={scope.launch{runCatching{Api.post("/api/products.php",s.token,JSONObject().put("action","adjust_stock").put("id",x.id).put("cantidad",1))}.onSuccess{load()}}}){Icon(Icons.Default.Add,null)}}}}}}}
    if(create||edit!=null) ProductDialog(edit,{create=false;edit=null}){n,p,st,min,act->
        scope.launch{runCatching{Api.post("/api/products.php",s.token,JSONObject().put("action","save").put("id",edit?.id?:0).put("nombre",n).put("precio",p).put("stock",st).put("stock_minimo",min).put("activo",if(act)1 else 0))}.onSuccess{create=false;edit=null;load()}.onFailure{err=it.message?:"Error"}}
    }
}
@Composable fun ProductDialog(x:Product?,dismiss:()->Unit,save:(String,Double,Int,Int,Boolean)->Unit){
    var n by remember{mutableStateOf(x?.name?:"")};var p by remember{mutableStateOf((x?.price?:0.0).toInt().toString())};var st by remember{mutableStateOf((x?.stock?:0).toString())};var min by remember{mutableStateOf((x?.minStock?:2).toString())};var act by remember{mutableStateOf(x?.active?:true)}
    AlertDialog(onDismissRequest=dismiss,title={Text("Producto")},text={Column{OutlinedTextField(n,{n=it},label={Text("Nombre")});OutlinedTextField(p,{p=it},label={Text("Precio")});OutlinedTextField(st,{st=it},label={Text("Stock")});OutlinedTextField(min,{min=it},label={Text("Stock mínimo")});Row(verticalAlignment=Alignment.CenterVertically){Switch(act,{act=it});Text(" Activo")}}},confirmButton={Button(onClick={save(n,p.toDoubleOrNull()?:0.0,st.toIntOrNull()?:0,min.toIntOrNull()?:0,act)}){Text("Guardar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun CashScreen(s:Session){
    var sales by remember{mutableStateOf<List<Sale>>(emptyList())};var services by remember{mutableStateOf<List<Service>>(emptyList())};var barbers by remember{mutableStateOf<List<Barber>>(emptyList())};var products by remember{mutableStateOf<List<Product>>(emptyList())};var clients by remember{mutableStateOf<List<Client>>(emptyList())}
    var serviceSale by remember{mutableStateOf(false)};var productSale by remember{mutableStateOf(false)};var err by remember{mutableStateOf("")};val scope=rememberCoroutineScope()
    fun load(){scope.launch{runCatching{
        val a=Api.get("/api/sales.php?from=${monthStart()}&to=${today()}",s.token).getJSONArray("items")
        val sv=Api.get("/api/services.php",s.token).getJSONArray("items");val br=Api.get("/api/barbers.php",s.token).getJSONArray("items");val pr=Api.get("/api/products.php",s.token).getJSONArray("items");val cl=Api.get("/api/clients.php",s.token).getJSONArray("items")
        listOf(
            List(a.length()){i->val o=a.getJSONObject(i);Sale(o.long("id"),o.dbl("total"),o.dbl("comision_total"),o.str("medio_pago"),o.str("creado_en"),o.str("detalle"),o.str("cliente"),o.str("barbero"))},
            List(sv.length()){i->val o=sv.getJSONObject(i);Service(o.long("id"),o.str("nombre"),o.dbl("precio"),o.int("duracion_min"),if(o.isNull("comision_pct"))null else o.dbl("comision_pct"),o.bool("activo"))},
            List(br.length()){i->val o=br.getJSONObject(i);Barber(o.long("id"),o.str("nombre"),o.str("telefono"),o.dbl("comision_pct"),o.bool("activo"))},
            List(pr.length()){i->val o=pr.getJSONObject(i);Product(o.long("id"),o.str("nombre"),o.dbl("precio"),o.int("stock"),o.int("stock_minimo"),o.bool("activo"))},
            List(cl.length()){i->val o=cl.getJSONObject(i);Client(o.long("id"),o.str("nombre"),o.str("telefono"),o.int("visitas"),o.dbl("gasto_total"),o.int("puntos"))}
        )
    }.onSuccess{
        @Suppress("UNCHECKED_CAST")
        sales=it[0] as List<Sale>; services=it[1] as List<Service>;barbers=it[2] as List<Barber>;products=it[3] as List<Product>;clients=it[4] as List<Client>
    }.onFailure{err=it.message?:"Error"}}}
    LaunchedEffect(Unit){load()}
    Column(Modifier.fillMaxSize().background(Fondo).padding(16.dp)){
        Text("Caja",fontSize=26.sp,fontWeight=FontWeight.Bold)
        val total=sales.sumOf{it.total};Text("Este mes: ${money(total)}",color=Dorado,fontSize=20.sp)
        Row(horizontalArrangement=Arrangement.spacedBy(8.dp)){Button(onClick={serviceSale=true},modifier=Modifier.weight(1f)){Text("Cobrar servicio")};Button(onClick={productSale=true},modifier=Modifier.weight(1f)){Text("Vender producto")}}
        if(err.isNotBlank()) ErrorBox(err)
        Spacer(Modifier.height(10.dp))
        LazyColumn(verticalArrangement=Arrangement.spacedBy(7.dp)){items(sales){x->Surface(color=Tarjeta,shape=RoundedCornerShape(12.dp),modifier=Modifier.fillMaxWidth()){Column(Modifier.padding(13.dp)){Text("${money(x.total)} · ${x.detail}",fontWeight=FontWeight.Bold);Text("${x.payment} · ${x.created} · ${x.barber}",color=Gris)}}}}
    }
    if(serviceSale) ServiceSaleDialog(services.filter{it.active},barbers.filter{it.active},clients,{serviceSale=false}){svc,bar,cli,pay,disc,tip->
        scope.launch{runCatching{Api.post("/api/sales.php",s.token,JSONObject().put("tipo","servicio").put("servicio_id",svc).put("barbero_id",bar).put("cliente_id",cli).put("medio_pago",pay).put("descuento",disc).put("propina",tip))}.onSuccess{serviceSale=false;load()}.onFailure{err=it.message?:"Error"}}
    }
    if(productSale) ProductSaleDialog(products.filter{it.active&&it.stock>0},clients,{productSale=false}){pid,qty,cli,pay->
        scope.launch{runCatching{Api.post("/api/sales.php",s.token,JSONObject().put("tipo","producto").put("producto_id",pid).put("cantidad",qty).put("cliente_id",cli).put("medio_pago",pay))}.onSuccess{productSale=false;load()}.onFailure{err=it.message?:"Error"}}
    }
}

@Composable
fun ServiceSaleDialog(services:List<Service>,barbers:List<Barber>,clients:List<Client>,dismiss:()->Unit,save:(Long,Long,Long,String,Double,Double)->Unit){
    var svc by remember{mutableStateOf(services.firstOrNull())};var bar by remember{mutableStateOf(barbers.firstOrNull())};var cli by remember{mutableStateOf<Client?>(null)};var pay by remember{mutableStateOf("efectivo")};var disc by remember{mutableStateOf("0")};var tip by remember{mutableStateOf("0")}
    AlertDialog(onDismissRequest=dismiss,title={Text("Cobrar servicio")},text={Column(Modifier.verticalScroll(rememberScrollState())){Picker("Servicio",svc?.name?:"",services.map{it.name}){n->svc=services.firstOrNull{it.name==n}};Picker("Barbero",bar?.name?:"",barbers.map{it.name}){n->bar=barbers.firstOrNull{it.name==n}};Picker("Cliente",cli?.name?:"Sin cliente",listOf("Sin cliente")+clients.map{it.name}){n->cli=clients.firstOrNull{it.name==n}};Picker("Pago",pay,listOf("efectivo","transferencia","debito","credito","otro")){pay=it};OutlinedTextField(disc,{disc=it},label={Text("Descuento")});OutlinedTextField(tip,{tip=it},label={Text("Propina")})}},confirmButton={Button(onClick={if(svc!=null&&bar!=null)save(svc!!.id,bar!!.id,cli?.id?:0,pay,disc.toDoubleOrNull()?:0.0,tip.toDoubleOrNull()?:0.0)}){Text("Cobrar")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun ProductSaleDialog(products:List<Product>,clients:List<Client>,dismiss:()->Unit,save:(Long,Int,Long,String)->Unit){
    var p by remember{mutableStateOf(products.firstOrNull())};var cli by remember{mutableStateOf<Client?>(null)};var qty by remember{mutableStateOf("1")};var pay by remember{mutableStateOf("efectivo")}
    AlertDialog(onDismissRequest=dismiss,title={Text("Vender producto")},text={Column{Picker("Producto",p?.name?:"",products.map{"${it.name} · Stock ${it.stock}"}){n->p=products.firstOrNull{n.startsWith(it.name)}};OutlinedTextField(qty,{qty=it},label={Text("Cantidad")});Picker("Cliente",cli?.name?:"Sin cliente",listOf("Sin cliente")+clients.map{it.name}){n->cli=clients.firstOrNull{it.name==n}};Picker("Pago",pay,listOf("efectivo","transferencia","debito","credito","otro")){pay=it}}},confirmButton={Button(onClick={if(p!=null)save(p!!.id,qty.toIntOrNull()?:1,cli?.id?:0,pay)}){Text("Vender")}},dismissButton={TextButton(onClick=dismiss){Text("Cancelar")}})
}

@Composable
fun ReportsScreen(s:Session,onBack:()->Unit){
    var text by remember{mutableStateOf("Cargando...")};var rows by remember{mutableStateOf<List<String>>(emptyList())};val scope=rememberCoroutineScope()
    LaunchedEffect(Unit){scope.launch{runCatching{val j=Api.get("/api/reports.php?from=${monthStart()}&to=${today()}",s.token);val x=j.getJSONObject("summary");val a=j.getJSONArray("por_barbero");Pair("Ventas ${money(x.dbl("ventas"))} · Comisiones ${money(x.dbl("comisiones"))} · ${x.int("operaciones")} operaciones",List(a.length()){i->val o=a.getJSONObject(i);"${o.str("nombre")}: ${money(o.dbl("ventas"))} · comisión ${money(o.dbl("comisiones"))}"})}.onSuccess{text=it.first;rows=it.second}.onFailure{text=it.message?:"Error"}}}
    Column(Modifier.fillMaxSize().background(Fondo).padding(16.dp)){BackTitle("Reportes",onBack);Text(text,color=Dorado);Spacer(Modifier.height(12.dp));rows.forEach{CardLine(it);Spacer(Modifier.height(7.dp))}}
}

@Composable
fun QrScreen(s:Session,onBack:()->Unit){
    val ctx=LocalContext.current
    val link="$BASE_URL/reservar.php?b=${s.slug}"
    val bmp=remember(link){qrBitmap(link,650)}
    Column(Modifier.fillMaxSize().background(Fondo).padding(20.dp).verticalScroll(rememberScrollState()),horizontalAlignment=Alignment.CenterHorizontally){
        BackTitle("QR reservas",onBack);Spacer(Modifier.height(18.dp));bmp?.let{Image(it.asImageBitmap(),null,modifier=Modifier.size(280.dp))}
        Spacer(Modifier.height(14.dp));Text(link,color=Gris)
        Button(onClick={val i=Intent(Intent.ACTION_SEND).apply{type="text/plain";putExtra(Intent.EXTRA_TEXT,"Reserva tu hora aquí: $link")};ctx.startActivity(Intent.createChooser(i,"Compartir"))}){Icon(Icons.Default.Share,null);Spacer(Modifier.width(6.dp));Text("Compartir")}
        OutlinedButton(onClick={ctx.startActivity(Intent(Intent.ACTION_VIEW,Uri.parse(link)))}){Text("Abrir página de reservas")}
    }
}
fun qrBitmap(text:String,size:Int):Bitmap?=runCatching{val m=MultiFormatWriter().encode(text,BarcodeFormat.QR_CODE,size,size);Bitmap.createBitmap(size,size,Bitmap.Config.RGB_565).apply{for(x in 0 until size)for(y in 0 until size)setPixel(x,y,if(m[x,y])android.graphics.Color.BLACK else android.graphics.Color.WHITE)}}.getOrNull()

@Composable
fun MoreScreen(s:Session,navigate:(String)->Unit,onLogout:()->Unit){
    LazyColumn(Modifier.fillMaxSize().background(Fondo),contentPadding=PaddingValues(16.dp),verticalArrangement=Arrangement.spacedBy(9.dp)){
        item{Text("Más",fontSize=26.sp,fontWeight=FontWeight.Bold)}
        item{MenuCard("Barberos","Equipo y comisiones",Icons.Default.ContentCut){navigate("barberos")}}
        item{MenuCard("Servicios","Precios y duración",Icons.Default.Build){navigate("servicios")}}
        item{MenuCard("Inventario","Stock y productos",Icons.Default.Inventory2){navigate("inventario")}}
        item{MenuCard("Reportes","Ventas y comisiones",Icons.Default.BarChart){navigate("reportes")}}
        item{MenuCard("QR reservas","Página pública",Icons.Default.QrCode2){navigate("qr")}}
        item{Text("${s.userName} · ${s.email}",color=Gris)}
        item{OutlinedButton(onClick=onLogout,modifier=Modifier.fillMaxWidth()){Text("Cerrar sesión")}}
    }
}

@Composable
fun ManagedList(title:String,onBack:()->Unit,onAdd:()->Unit,error:String,showBack:Boolean=true,content:LazyListScope.()->Unit){
    Column(Modifier.fillMaxSize().background(Fondo).padding(16.dp)){
        Row(verticalAlignment=Alignment.CenterVertically){if(showBack)IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text(title,fontSize=25.sp,fontWeight=FontWeight.Bold,modifier=Modifier.weight(1f));IconButton(onClick=onAdd){Icon(Icons.Default.Add,null,tint=Dorado)}}
        if(error.isNotBlank())ErrorBox(error)
        LazyColumn(verticalArrangement=Arrangement.spacedBy(8.dp),content=content)
    }
}
@Composable fun BackTitle(t:String,onBack:()->Unit){Row(Modifier.fillMaxWidth(),verticalAlignment=Alignment.CenterVertically){IconButton(onClick=onBack){Icon(Icons.Default.ArrowBack,null)};Text(t,fontSize=25.sp,fontWeight=FontWeight.Bold)}}
