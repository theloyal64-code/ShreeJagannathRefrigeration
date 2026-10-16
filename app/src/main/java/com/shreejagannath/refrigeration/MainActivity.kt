package com.shreejagannath.refrigeration

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.widget.Toast
import android.net.Uri
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.background
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.LazyRow
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.gestures.detectDragGestures
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.graphics.graphicsLayer
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.Check
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Calendar
import java.util.Locale

private val Navy = Color(0xFF123B67)
private val Orange = Color(0xFFF28C28)
private val Page = Color(0xFFF5F7FA)
private const val BOOKING_PREFS = "shree_jagannath_bookings"
private const val BOOKINGS_KEY = "bookings"
private const val LANGUAGE_KEY = "app_language"

data class Appliance(val name: String, val detail: String, val emoji: String)
private enum class AppLanguage { ENGLISH, ODIA }
private fun tr(language: AppLanguage, english: String, odia: String): String = if (language == AppLanguage.ODIA) odia else english
private val issueOptions = mapOf(
    "AC Service" to listOf("Not cooling", "Weak airflow", "Water leakage", "Making noise", "Not turning on", "Remote not working", "Bad smell", "Ice on indoor unit", "High electricity use", "Installation / servicing"),
    "Refrigerator" to listOf("Not cooling", "Overcooling", "Water leakage", "Making noise", "Not turning on", "Ice build-up", "Door / gasket problem", "Light not working", "Compressor runs continuously", "Bad smell", "Freezer not freezing"),
    "Washing Machine" to listOf("Not starting", "Not draining water", "Not spinning", "Water leakage", "Making noise", "Door / lid problem", "Not taking water", "Too much vibration", "Clothes still wet", "Detergent not dispensing", "Error code on display"),
    "Microwave Oven" to listOf("Not heating", "Not turning on", "Sparking inside", "Turntable not moving", "Making noise", "Door problem", "Display not working", "Uneven heating", "Burning smell", "Buttons not responding"),
    "Geyser" to listOf("No hot water", "Water not heating enough", "Water leakage", "Not turning on", "Trips electricity", "Making noise", "Takes too long to heat", "Hot water runs out quickly", "Rusty water", "Installation / servicing")
)
data class ServiceBooking(val id: Int, val customer: String, val phone: String, val appliance: String, val issue: String, val address: String, val preferredSlot: String = "", val pincode: String = "", val status: String = "Pending", val createdAt: Long = System.currentTimeMillis(), val latitude: Double? = null, val longitude: Double? = null)

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContent { MainApp() }
    }
}

private fun loadBookings(context: Context): List<ServiceBooking> {
    val raw = context.getSharedPreferences(BOOKING_PREFS, Context.MODE_PRIVATE)
        .getString(BOOKINGS_KEY, null) ?: return emptyList()
    return try {
        val json = JSONArray(raw)
        (0 until json.length()).mapNotNull { index ->
            val item = json.optJSONObject(index) ?: return@mapNotNull null
            ServiceBooking(
                id = item.optInt("id"),
                customer = item.optString("customer"),
                phone = item.optString("phone"),
                appliance = item.optString("appliance"),
                issue = item.optString("issue"),
                address = item.optString("address"),
                preferredSlot = item.optString("preferredSlot"),
                pincode = item.optString("pincode"),
                status = item.optString("status", "Pending"),
                createdAt = item.optLong("createdAt", 0L).takeIf { it > 0L } ?: System.currentTimeMillis(),
                latitude = if (item.isNull("latitude")) null else item.optDouble("latitude"),
                longitude = if (item.isNull("longitude")) null else item.optDouble("longitude")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

private fun saveBookings(context: Context, bookings: List<ServiceBooking>) {
    val json = JSONArray()
    bookings.forEach { booking ->
        json.put(JSONObject().apply {
            put("id", booking.id)
            put("customer", booking.customer)
            put("phone", booking.phone)
            put("appliance", booking.appliance)
            put("issue", booking.issue)
            put("address", booking.address)
            put("preferredSlot", booking.preferredSlot)
            put("pincode", booking.pincode)
            put("status", booking.status)
            put("createdAt", booking.createdAt)
            put("latitude", booking.latitude ?: JSONObject.NULL)
            put("longitude", booking.longitude ?: JSONObject.NULL)
        })
    }
    context.getSharedPreferences(BOOKING_PREFS, Context.MODE_PRIVATE)
        .edit()
        .putString(BOOKINGS_KEY, json.toString())
        .apply()
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun MainApp() {
    val context = LocalContext.current
    val appliances = listOf(
        Appliance("AC Service", "Repair • Installation • Servicing", "❄️"),
        Appliance("Refrigerator", "Cooling • Repair • Leakage", "🧊"),
        Appliance("Washing Machine", "Repair • Installation", "🧺"),
        Appliance("Microwave Oven", "Heating • Repair", "🍲"),
        Appliance("Geyser", "Installation • Repair", "🚿")
    )
    var screen by remember { mutableStateOf("welcome") }
    var language by remember {
        mutableStateOf(if (context.getSharedPreferences(BOOKING_PREFS, Context.MODE_PRIVATE).getString(LANGUAGE_KEY, "ENGLISH") == "ODIA") AppLanguage.ODIA else AppLanguage.ENGLISH)
    }
    var chosen by remember { mutableStateOf(appliances[0]) }
    var bookings by remember { mutableStateOf(loadBookings(context)) }
    var nextId by remember { mutableIntStateOf((bookings.maxOfOrNull { it.id } ?: 1000) + 1) }

    LaunchedEffect(bookings) {
        saveBookings(context, bookings)
    }
    LaunchedEffect(language) {
        context.getSharedPreferences(BOOKING_PREFS, Context.MODE_PRIVATE).edit()
            .putString(LANGUAGE_KEY, language.name).apply()
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Orange, background = Page)) {
        Scaffold(topBar = {
            if (screen != "welcome") TopAppBar(
                title = { Text(when (screen) {
                    "customer" -> tr(language, "Book a Service", "ସେବା ବୁକ୍ କରନ୍ତୁ")
                    "form" -> tr(language, "Service Request", "ସେବା ଅନୁରୋଧ")
                    "history" -> tr(language, "My Bookings", "ମୋ ବୁକିଂ")
                    "adminLogin" -> tr(language, "Admin Login", "ଆଡମିନ୍ ଲଗଇନ୍")
                    "admin" -> tr(language, "Mechanic Dashboard", "ମେକାନିକ୍ ଡ୍ୟାସବୋର୍ଡ")
                    else -> "Shree Jagannath"
                }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { screen = when (screen) {
                        "form" -> "customer"
                        "history" -> "customer"
                        "admin" -> "welcome"
                        else -> "welcome"
                    } }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }) { insets ->
            Box(Modifier.fillMaxSize().background(Page).padding(insets)) {
                when (screen) {
                    "welcome" -> Welcome(language, onLanguage = { language = it }, onCustomer = { screen = "customer" }, onAdmin = { screen = "adminLogin" })
                    "customer" -> CustomerHome(appliances, bookings, language,
                        onChoose = { chosen = it; screen = "form" }, onHistory = { screen = "history" })
                    "form" -> RequestForm(chosen, language, onSubmit = { name, phone, issue, address, preferredSlot, pincode, latitude, longitude ->
                        bookings = bookings + ServiceBooking(nextId++, name, phone, chosen.name, issue, address, preferredSlot, pincode, latitude = latitude, longitude = longitude)
                        screen = "history"
                    })
                    "history" -> BookingHistory(bookings, language, onCancel = { id -> bookings = bookings.map { if (it.id == id && it.status == "Pending") it.copy(status = "Cancelled") else it } })
                    "adminLogin" -> AdminLogin(language, onSuccess = { screen = "admin" })
                    "admin" -> AdminHome(bookings, language, onStatus = { id, status ->
                        bookings = bookings.map { if (it.id == id) it.copy(status = status) else it }
                    })
                }
            }
        }
    }
}

private fun fetchCommonsPhoto(searchTerm: String): android.graphics.Bitmap? {
    return runCatching {
        val encoded = java.net.URLEncoder.encode(searchTerm, "UTF-8")
        val api = java.net.URL("https://commons.wikimedia.org/w/api.php?action=query&generator=search&gsrsearch=$encoded&gsrnamespace=6&gsrlimit=8&prop=imageinfo&iiprop=url&iiurlwidth=420&format=json")
        val connection = api.openConnection() as java.net.HttpURLConnection
        connection.connectTimeout = 10000
        connection.readTimeout = 15000
        connection.setRequestProperty("User-Agent", "ShreeJagannathRefrigeration/1.0 (Android app)")
        val json = connection.inputStream.bufferedReader().use { JSONObject(it.readText()) }
        val pages = json.optJSONObject("query")?.optJSONObject("pages") ?: return null
        val candidates = (0 until pages.length()).mapNotNull { index ->
            pages.optJSONObject(pages.names()?.optString(index))?.optJSONArray("imageinfo")?.optJSONObject(0)?.optString("thumburl")
        }
        candidates.firstNotNullOfOrNull { imageUrl ->
            runCatching {
                val imageConnection = java.net.URL(imageUrl).openConnection() as java.net.HttpURLConnection
                imageConnection.connectTimeout = 10000
                imageConnection.readTimeout = 15000
                imageConnection.inputStream.use { android.graphics.BitmapFactory.decodeStream(it) }
            }.getOrNull()
        }
    }.getOrNull()
}

@Composable
private fun Welcome(language: AppLanguage, onLanguage: (AppLanguage) -> Unit, onCustomer: () -> Unit, onAdmin: () -> Unit) {
    Column(Modifier.fillMaxSize().background(Color(0xFFF4F8FD)).verticalScroll(rememberScrollState())) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0B3765)).padding(horizontal = 20.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HomeRepairService, contentDescription = null, tint = Color.White, modifier = Modifier.size(42.dp))
            Spacer(Modifier.width(10.dp))
            Column(Modifier.weight(1f)) {
                Text("SHREE JAGANNATH", color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 18.sp)
                Text("REFRIGERATION", color = Color(0xFFFF9B3D), fontSize = 14.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(tr(language, "Home Appliances", "ଘରୋଇ ଉପକରଣ"), color = Color(0xFFE6EFF8), fontSize = 11.sp)
                Text(tr(language, "Care Experts", "ସେବା ବିଶେଷଜ୍ଞ"), color = Color(0xFFE6EFF8), fontSize = 11.sp)
                Box(Modifier.padding(top = 4.dp).width(52.dp).height(3.dp).background(Orange, RoundedCornerShape(50)))
            }
        }
        Column(Modifier.fillMaxWidth().padding(horizontal = 22.dp)) {
            Spacer(Modifier.height(22.dp))
            Text(tr(language, "Your Appliances", "ଆପଣଙ୍କ ଉପକରଣ"), fontSize = 33.sp, lineHeight = 36.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B3765))
            Text(tr(language, "Our Care", "ଆମର ଯତ୍ନ"), fontSize = 35.sp, lineHeight = 39.sp, fontWeight = FontWeight.ExtraBold, color = Orange)
            Spacer(Modifier.height(7.dp))
            Text(tr(language, "Fast  •  Reliable  •  Affordable", "ଦ୍ରୁତ  •  ଭରସାଯୋଗ୍ୟ  •  ସୁଲଭ"), fontSize = 15.sp, color = Color(0xFF4F6378), fontWeight = FontWeight.Medium)
            Box(Modifier.padding(top = 10.dp).width(42.dp).height(4.dp).background(Orange, RoundedCornerShape(50)))
            Spacer(Modifier.height(12.dp))
            Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(24.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F0FA))) {
                Row(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFFEAF3FC), Color(0xFFD8E8F8)))).padding(horizontal = 8.dp, vertical = 8.dp), horizontalArrangement = Arrangement.SpaceEvenly, verticalAlignment = Alignment.CenterVertically) {
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Appliance3DPreview("Refrigerator", 0f, Modifier.width(78.dp).height(116.dp))
                        Text(tr(language, "Fridge", "ଫ୍ରିଜ୍"), fontSize = 10.sp, color = Navy, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Appliance3DPreview("Washing Machine", 0f, Modifier.width(88.dp).height(116.dp))
                        Text(tr(language, "Washer", "ୱାଶର୍"), fontSize = 10.sp, color = Navy, fontWeight = FontWeight.SemiBold)
                    }
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Appliance3DPreview("AC Service", 0f, Modifier.width(88.dp).height(116.dp))
                        Text("AC", fontSize = 10.sp, color = Navy, fontWeight = FontWeight.SemiBold)
                    }
                }
            }
            Spacer(Modifier.height(13.dp))
            val benefits = listOf(
                "✓" to tr(language, "Expert Technicians", "ଦକ୍ଷ ଟେକ୍ନିସିଆନ୍"),
                "◷" to tr(language, "On-Time Service", "ଠିକ୍ ସମୟରେ ସେବା"),
                "₹" to tr(language, "Transparent Pricing", "ସ୍ପଷ୍ଟ ମୂଲ୍ୟ"),
                "★" to tr(language, "Trusted by Homes", "ଘରେ ଘରେ ଭରସା")
            )
            benefits.forEach { (symbol, label) ->
                Row(Modifier.fillMaxWidth().padding(vertical = 4.dp), verticalAlignment = Alignment.CenterVertically) {
                    Surface(color = Navy, shape = RoundedCornerShape(50)) {
                        Box(Modifier.size(28.dp), contentAlignment = Alignment.Center) { Text(symbol, color = Color.White, fontSize = 16.sp, fontWeight = FontWeight.Bold) }
                    }
                    Spacer(Modifier.width(10.dp))
                    Text(label, color = Color(0xFF40576F), fontSize = 13.sp, fontWeight = FontWeight.Medium)
                }
            }
            Spacer(Modifier.height(15.dp))
            Button(onClick = onCustomer, modifier = Modifier.fillMaxWidth().height(78.dp), shape = RoundedCornerShape(20.dp), colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4B86)), contentPadding = PaddingValues(horizontal = 18.dp)) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(34.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(tr(language, "Customer", "ଗ୍ରାହକ"), fontSize = 18.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Book a Service", "ସେବା ବୁକ୍ କରନ୍ତୁ"), fontSize = 13.sp, color = Color(0xFFE2EDF8))
                }
                Text("❯", fontSize = 24.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(10.dp))
            OutlinedButton(onClick = onAdmin, modifier = Modifier.fillMaxWidth().height(74.dp), shape = RoundedCornerShape(20.dp), contentPadding = PaddingValues(horizontal = 18.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(32.dp))
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(tr(language, "Admin / Mechanic", "ଆଡମିନ୍ / ମେକାନିକ୍"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Login to Continue", "ଲଗଇନ୍ କରି ଆଗକୁ ବଢ଼ନ୍ତୁ"), fontSize = 12.sp, color = Color(0xFF61768B))
                }
                Text("❯", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(18.dp))
            Text(tr(language, "We Service All Major Appliances", "ସମସ୍ତ ପ୍ରମୁଖ ଉପକରଣର ସେବା"), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
            Box(Modifier.padding(top = 6.dp, bottom = 10.dp).width(38.dp).height(3.dp).background(Orange, RoundedCornerShape(50)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                listOf("❄" to "AC", "▯" to tr(language, "Fridge", "ଫ୍ରିଜ୍"), "◉" to tr(language, "Washer", "ୱାଶର୍"), "▣" to tr(language, "Microwave", "ମାଇକ୍ରୋୱେଭ୍")).forEach { (icon, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Surface(color = Color.White, shape = RoundedCornerShape(15.dp), shadowElevation = 1.dp) {
                            Box(Modifier.fillMaxWidth().height(58.dp), contentAlignment = Alignment.Center) { Text(icon, color = Navy, fontSize = 29.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(label, color = Navy, fontSize = 10.sp, fontWeight = FontWeight.Bold, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.height(18.dp))
            Divider(color = Color(0xFFD5E1ED))
            Row(Modifier.fillMaxWidth().padding(vertical = 14.dp), horizontalArrangement = Arrangement.SpaceBetween, verticalAlignment = Alignment.CenterVertically) {
                listOf("✓" to tr(language, "Quality Service", "ଗୁଣାତ୍ମକ ସେବା"), "₹" to tr(language, "Fair Pricing", "ଉଚିତ ମୂଲ୍ୟ"), "★" to tr(language, "Customer Care", "ଗ୍ରାହକ ସେବା")).forEach { (icon, label) ->
                    Column(horizontalAlignment = Alignment.CenterHorizontally, modifier = Modifier.weight(1f)) {
                        Text(icon, color = Navy, fontSize = 22.sp, fontWeight = FontWeight.Bold)
                        Text(label, color = Color(0xFF536A80), fontSize = 10.sp)
                    }
                }
            }
            Spacer(Modifier.height(12.dp))
        }
    }
}

@Composable
private fun CustomerHome(appliances: List<Appliance>, bookings: List<ServiceBooking>, language: AppLanguage, onChoose: (Appliance) -> Unit, onHistory: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(horizontal = 16.dp),
        contentPadding = PaddingValues(top = 12.dp, bottom = 22.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Row(verticalAlignment = Alignment.CenterVertically, modifier = Modifier.fillMaxWidth()) {
                Column(Modifier.weight(1f)) {
                    Text(tr(language, "Namaskar! 🙏", "ନମସ୍କାର! 🙏"), fontSize = 25.sp, fontWeight = FontWeight.Bold, color = Navy)
                    Text(tr(language, "What can we repair for you today?", "ଆଜି କେଉଁ ଉପକରଣ ମରାମତି କରିବା?"), fontSize = 14.sp, color = Color(0xFF687789))
                }
                Surface(color = Color.White, shape = RoundedCornerShape(14.dp), shadowElevation = 1.dp) {
                    Row(Modifier.clickable(onClick = onHistory).padding(horizontal = 12.dp, vertical = 10.dp), verticalAlignment = Alignment.CenterVertically) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Navy, modifier = Modifier.size(18.dp))
                        Spacer(Modifier.width(6.dp))
                        Text("${bookings.size}", color = Navy, fontWeight = FontWeight.Bold, fontSize = 14.sp)
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Card(modifier = Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy)) {
                Row(
                    modifier = Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF173F68), Color(0xFF245783)))).padding(20.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    Column(Modifier.weight(1f)) {
                        Text(tr(language, "Reliable service. Right at home.", "ଭରସାଯୋଗ୍ୟ ସେବା, ଆପଣଙ୍କ ଘରେ।"), color = Color.White, fontSize = 19.sp, fontWeight = FontWeight.Bold, lineHeight = 25.sp)
                        Spacer(Modifier.height(7.dp))
                        Text(tr(language, "Book a visit for repair, installation or regular servicing.", "ମରାମତି, ଇନଷ୍ଟଲେସନ୍ କିମ୍ବା ସର୍ଭିସିଂ ପାଇଁ ବୁକ୍ କରନ୍ତୁ।"), color = Color(0xFFE4EDF7), fontSize = 13.sp, lineHeight = 19.sp)
                    }
                    Spacer(Modifier.width(10.dp))
                    Surface(color = Color.White.copy(alpha = 0.12f), shape = RoundedCornerShape(18.dp)) {
                        Icon(Icons.Default.HomeRepairService, contentDescription = null, tint = Color.White, modifier = Modifier.padding(12.dp).size(32.dp))
                    }
                }
            }
            Spacer(Modifier.height(4.dp))
            Text(tr(language, "Our Services", "ଆମ ସେବା"), fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(tr(language, "Select an appliance to get started", "ଆରମ୍ଭ କରିବାକୁ ଉପକରଣ ବାଛନ୍ତୁ"), fontSize = 13.sp, color = Color(0xFF758295))
        }
        items(appliances, key = { it.name }) { appliance ->
            var rotation by remember(appliance.name) { mutableFloatStateOf(0f) }
            Card(
                modifier = Modifier.fillMaxWidth(),
                shape = RoundedCornerShape(20.dp),
                colors = CardDefaults.cardColors(containerColor = Color.White),
                elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
            ) {
                Column(Modifier.padding(14.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Box(
                            Modifier.size(width = 126.dp, height = 132.dp)
                                .background(Brush.linearGradient(listOf(Color(0xFFF0F5FB), Color(0xFFE4EDF7))), RoundedCornerShape(16.dp))
                                .pointerInput(appliance.name) {
                                    detectDragGestures { change, drag ->
                                        change.consume()
                                        rotation = (rotation + drag.x * 1.5f) % 360f
                                    }
                                }
                                .clickable { rotation = (rotation + 30f) % 360f },
                            contentAlignment = Alignment.Center
                        ) {
                            Appliance3DPreview(appliance.name, rotation, Modifier.fillMaxSize().padding(5.dp))
                        }
                        Spacer(Modifier.width(14.dp))
                        Column(Modifier.weight(1f), verticalArrangement = Arrangement.Center) {
                            Text(applianceLabel(language, appliance.name), fontWeight = FontWeight.Bold, fontSize = 17.sp, color = Color(0xFF172433), lineHeight = 22.sp)
                            Spacer(Modifier.height(6.dp))
                            Text(appliance.detail, fontSize = 12.sp, color = Color(0xFF6C7887), lineHeight = 17.sp)
                            Spacer(Modifier.height(9.dp))
                            Surface(color = Color(0xFFEAF2FA), shape = RoundedCornerShape(50)) {
                                Text(tr(language, "360° view", "360° ଦୃଶ୍ୟ"), modifier = Modifier.padding(horizontal = 9.dp, vertical = 5.dp), fontSize = 10.sp, color = Navy, fontWeight = FontWeight.SemiBold)
                            }
                        }
                    }
                    Spacer(Modifier.height(13.dp))
                    Button(
                        onClick = { onChoose(appliance) },
                        modifier = Modifier.fillMaxWidth().height(48.dp),
                        shape = RoundedCornerShape(14.dp),
                        colors = ButtonDefaults.buttonColors(containerColor = Navy),
                        contentPadding = PaddingValues(horizontal = 16.dp)
                    ) {
                        Text(tr(language, "Book service", "ସେବା ବୁକ୍ କରନ୍ତୁ"), fontWeight = FontWeight.SemiBold, fontSize = 15.sp)
                        Spacer(Modifier.width(8.dp))
                        Icon(Icons.Default.ArrowBack, contentDescription = null, modifier = Modifier.size(17.dp).graphicsLayer(rotationZ = 180f))
                    }
                }
            }
        }
        item {
            OutlinedButton(onClick = onHistory, modifier = Modifier.fillMaxWidth().height(50.dp), shape = RoundedCornerShape(14.dp)) {
                Text(tr(language, "View my bookings", "ମୋ ବୁକିଂ ଦେଖନ୍ତୁ") + " (${bookings.size})")
            }
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun RequestForm(appliance: Appliance, language: AppLanguage, onSubmit: (String, String, String, String, String, String, Double?, Double?) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var preferredSlot by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var latitude by remember { mutableStateOf<Double?>(null) }
    var longitude by remember { mutableStateOf<Double?>(null) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var issueExpanded by remember { mutableStateOf(false) }
    var showSubmitConfirmation by remember { mutableStateOf(false) }
    val issues = issueOptions[appliance.name] ?: listOf("Not working", "Making noise", "Leakage", "Other")
    val context = LocalContext.current
    fun captureCurrentLocation() {
        val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        try {
            val provider = when {
                manager.isProviderEnabled(LocationManager.GPS_PROVIDER) -> LocationManager.GPS_PROVIDER
                manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER) -> LocationManager.NETWORK_PROVIDER
                else -> null
            }
            if (provider == null) {
                locating = false
                Toast.makeText(context, tr(language, "Turn on device location and try again.", "ଫୋନ୍‌ର ଲୋକେସନ୍ ଚାଲୁ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                return
            }
            val listener = object : LocationListener {
                override fun onLocationChanged(location: Location) {
                    latitude = location.latitude
                    longitude = location.longitude
                    locating = false
                    Toast.makeText(context, tr(language, "GPS location saved for this booking.", "ଏହି ବୁକିଂ ପାଇଁ GPS ଲୋକେସନ୍ ସେଭ୍ ହେଲା।"), Toast.LENGTH_SHORT).show()
                }
            }
            @Suppress("DEPRECATION")
            manager.requestSingleUpdate(provider, listener, Looper.getMainLooper())
            Toast.makeText(context, tr(language, "Getting location…", "ଲୋକେସନ୍ ନିଆଯାଉଛି…"), Toast.LENGTH_SHORT).show()
        } catch (_: Exception) {
            locating = false
            Toast.makeText(context, tr(language, "Could not get location. Please enter address manually.", "ଲୋକେସନ୍ ମିଳିଲା ନାହିଁ। ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
        }
    }
    val locationPermissionLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestMultiplePermissions()) { grants ->
        if (grants[Manifest.permission.ACCESS_FINE_LOCATION] == true || grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true) {
            locating = true
            captureCurrentLocation()
        } else {
            locating = false
            Toast.makeText(context, tr(language, "Location permission denied. You can still enter your address.", "ଲୋକେସନ୍ ଅନୁମତି ମିଳିଲା ନାହିଁ। ଠିକଣା ଲେଖିପାରିବେ।"), Toast.LENGTH_LONG).show()
        }
    }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("${appliance.emoji} ${applianceLabel(language, appliance.name)}", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(tr(language, "Choose the problem below—no long description needed.", "ତଳୁ ସମସ୍ୟା ବାଛନ୍ତୁ—ଲମ୍ବା ବର୍ଣ୍ଣନା ଦରକାର ନାହିଁ।"), color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text(tr(language, "Customer name *", "ଗ୍ରାହକଙ୍କ ନାମ *")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text(tr(language, "Mobile number *", "ମୋବାଇଲ୍ ନମ୍ବର *")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Spacer(Modifier.height(10.dp))
        Text(tr(language, "Select the problem *", "ସମସ୍ୟା ବାଛନ୍ତୁ *"), fontWeight = FontWeight.SemiBold, color = Navy)
        Box {
            OutlinedButton(onClick = { issueExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (issue.isBlank()) tr(language, "Choose a problem", "ସମସ୍ୟା ବାଛନ୍ତୁ") else issueLabel(language, issue)) }
            DropdownMenu(expanded = issueExpanded, onDismissRequest = { issueExpanded = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                issues.forEach { option -> DropdownMenuItem(text = { Text(issueLabel(language, option)) }, leadingIcon = { Text(problemEmoji(option), fontSize = 22.sp) }, onClick = { issue = option; issueExpanded = false }) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(address, { address = it }, label = { Text(tr(language, "Full address / area *", "ପୂରା ଠିକଣା / ଅଞ୍ଚଳ *")) }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = {
            val fine = context.checkSelfPermission(Manifest.permission.ACCESS_FINE_LOCATION) == PackageManager.PERMISSION_GRANTED
            val coarse = context.checkSelfPermission(Manifest.permission.ACCESS_COARSE_LOCATION) == PackageManager.PERMISSION_GRANTED
            if (fine || coarse) {
                locating = true
                captureCurrentLocation()
            } else {
                locationPermissionLauncher.launch(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION))
            }
        }, modifier = Modifier.fillMaxWidth(), enabled = !locating) {
            Text(when {
                locating -> tr(language, "Getting GPS location…", "GPS ଲୋକେସନ୍ ନିଆଯାଉଛି…")
                latitude != null && longitude != null -> tr(language, "GPS location saved ✓ (tap to refresh)", "GPS ଲୋକେସନ୍ ସେଭ୍ ହେଲା ✓")
                else -> tr(language, "Use current GPS location (optional)", "ବର୍ତ୍ତମାନ GPS ଲୋକେସନ୍ ବ୍ୟବହାର କରନ୍ତୁ (ଇଚ୍ଛାଧୀନ)")
            })
        }
        if (latitude != null && longitude != null) {
            Text(tr(language, "Location will be shared with the mechanic for this booking.", "ଏହି ବୁକିଂ ପାଇଁ ଲୋକେସନ୍ ମେକାନିକ୍‌ଙ୍କ ସହ ସେୟାର୍ ହେବ।"), fontSize = 12.sp, color = Color(0xFF16804A))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                val now = Calendar.getInstance()
                DatePickerDialog(
                    context,
                    { _, year, month, day ->
                        val selectedDate = Calendar.getInstance().apply {
                            set(Calendar.YEAR, year)
                            set(Calendar.MONTH, month)
                            set(Calendar.DAY_OF_MONTH, day)
                        }
                        TimePickerDialog(
                            context,
                            { _, hour, minute ->
                                selectedDate.set(Calendar.HOUR_OF_DAY, hour)
                                selectedDate.set(Calendar.MINUTE, minute)
                                selectedDate.set(Calendar.SECOND, 0)
                                if (selectedDate.timeInMillis <= System.currentTimeMillis()) {
                                    error = tr(language, "Choose a future visit time.", "ଦୟାକରି ଭବିଷ୍ୟତର ସମୟ ବାଛନ୍ତୁ।")
                                } else {
                                    preferredSlot = SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(selectedDate.time)
                                    error = ""
                                }
                            },
                            now.get(Calendar.HOUR_OF_DAY),
                            now.get(Calendar.MINUTE),
                            false
                        ).show()
                    },
                    now.get(Calendar.YEAR),
                    now.get(Calendar.MONTH),
                    now.get(Calendar.DAY_OF_MONTH)
                ).apply { datePicker.minDate = System.currentTimeMillis() }.show()
            },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text(
                if (preferredSlot.isBlank())
                    tr(language, "Choose preferred visit date & time (optional)", "ପସନ୍ଦର ତାରିଖ ଓ ସମୟ ବାଛନ୍ତୁ (ଇଚ୍ଛାଧୀନ)")
                else preferredSlot
            )
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pincode, { pincode = it.filter(Char::isDigit).take(6) }, label = { Text(tr(language, "Area PIN code (optional)", "ପିନ୍ କୋଡ୍ (ଇଚ୍ଛାଧୀନ)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = when {
                name.isBlank() -> tr(language, "Please enter your name.", "ଦୟାକରି ନାମ ଲେଖନ୍ତୁ।")
                (phone.length != 10 || phone[0] !in '6'..'9') -> tr(language, "Enter a valid Indian 10-digit mobile number.", "ଠିକ୍ ୧୦ ଅଙ୍କର ଭାରତୀୟ ମୋବାଇଲ୍ ନମ୍ବର ଦିଅନ୍ତୁ।")
                pincode.isNotBlank() && pincode.length != 6 -> tr(language, "Enter a valid 6-digit PIN code or leave it blank.", "ଠିକ୍ ୬ ଅଙ୍କର ପିନ୍ କୋଡ୍ ଦିଅନ୍ତୁ କିମ୍ବା ଖାଲି ରଖନ୍ତୁ।")
                issue.isBlank() -> tr(language, "Please select a problem.", "ଦୟାକରି ସମସ୍ୟା ବାଛନ୍ତୁ।")
                address.isBlank() -> tr(language, "Please enter your address.", "ଦୟାକରି ଠିକଣା ଲେଖନ୍ତୁ।")
                else -> ""
            }
            if (error.isBlank()) showSubmitConfirmation = true
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text(tr(language, "Review Request", "ଅନୁରୋଧ ଯାଞ୍ଚ କରନ୍ତୁ")) }
        Spacer(Modifier.height(10.dp))
        Text(tr(language, "No payment is collected in-app. The team will confirm your visit time and charges.", "ଆପ୍‌ରେ କୌଣସି ପେମେଣ୍ଟ ନିଆଯାଏ ନାହିଁ। ଟିମ୍ ଆପଣଙ୍କ ସମୟ ଓ ଖର୍ଚ୍ଚ ନିଶ୍ଚିତ କରିବ।"), fontSize = 12.sp, color = Color.Gray)
    }
    if (showSubmitConfirmation) {
        AlertDialog(
            onDismissRequest = { showSubmitConfirmation = false },
            title = { Text(tr(language, "Confirm service request", "ସେବା ଅନୁରୋଧ ନିଶ୍ଚିତ କରନ୍ତୁ")) },
            text = {
                Column(verticalArrangement = Arrangement.spacedBy(5.dp)) {
                    Text("${tr(language, "Customer", "ଗ୍ରାହକ")}: ${name.trim()}")
                    Text("${tr(language, "Mobile", "ମୋବାଇଲ୍")}: $phone")
                    Text("${tr(language, "Appliance", "ଉପକରଣ")}: ${applianceLabel(language, appliance.name)}")
                    Text("${tr(language, "Problem", "ସମସ୍ୟା")}: ${issueLabel(language, issue)}")
                    Text("${tr(language, "Address", "ଠିକଣା")}: ${address.trim()}")
                    if (preferredSlot.isNotBlank()) Text("${tr(language, "Preferred visit", "ପସନ୍ଦର ସମୟ")}: $preferredSlot")
                    if (pincode.isNotBlank()) Text("${tr(language, "PIN code", "ପିନ୍ କୋଡ୍")}: $pincode")
                }
            },
            confirmButton = {
                TextButton(onClick = {
                    showSubmitConfirmation = false
                    onSubmit(name.trim(), phone, issue.trim(), address.trim(), preferredSlot.trim(), pincode, latitude, longitude)
                }) { Text(tr(language, "Confirm booking", "ବୁକିଂ ନିଶ୍ଚିତ କରନ୍ତୁ")) }
            },
            dismissButton = {
                TextButton(onClick = { showSubmitConfirmation = false }) {
                    Text(tr(language, "Edit details", "ବିବରଣୀ ବଦଳାନ୍ତୁ"))
                }
            }
        )
    }
}


private fun applianceLabel(language: AppLanguage, appliance: String): String {
    if (language == AppLanguage.ENGLISH) return appliance
    return when (appliance) {
        "AC Service" -> "ଏସି ସେବା"
        "Refrigerator" -> "ଫ୍ରିଜ୍"
        "Washing Machine" -> "ୱାଶିଂ ମେସିନ୍"
        "Microwave Oven" -> "ମାଇକ୍ରୋୱେଭ୍ ଓଭନ୍"
        "Geyser" -> "ଗିଜର୍"
        else -> appliance
    }
}

private fun issueLabel(language: AppLanguage, issue: String): String {
    if (language == AppLanguage.ENGLISH) return issue
    return when (issue) {
        "Not cooling" -> "ଥଣ୍ଡା କରୁନାହିଁ"
        "Water leakage" -> "ପାଣି ଲିକ୍ ହେଉଛି"
        "Making noise" -> "ଅଧିକ ଶବ୍ଦ ହେଉଛି"
        "Not turning on" -> "ଚାଲୁ ହେଉନାହିଁ"
        "Bad smell" -> "ଖରାପ ଗନ୍ଧ ଆସୁଛି"
        "Installation / servicing" -> "ଇନଷ୍ଟଲେସନ୍ / ସର୍ଭିସିଂ"
        "Weak airflow" -> "ବାୟୁ ପ୍ରବାହ କମ୍"
        "Remote not working" -> "ରିମୋଟ୍ କାମ କରୁନାହିଁ"
        "Ice on indoor unit" -> "ଭିତର ୟୁନିଟ୍‌ରେ ବରଫ ଜମୁଛି"
        "High electricity use" -> "ବିଦ୍ୟୁତ୍ ଖର୍ଚ୍ଚ ଅଧିକ"
        "Overcooling" -> "ଅତ୍ୟଧିକ ଥଣ୍ଡା ହେଉଛି"
        "Light not working" -> "ଲାଇଟ୍ ଜଳୁନାହିଁ"
        "Compressor runs continuously" -> "କମ୍ପ୍ରେସର୍ ଲଗାତାର ଚାଲୁଛି"
        "Freezer not freezing" -> "ଫ୍ରିଜର୍ ବରଫ କରୁନାହିଁ"
        "Not taking water" -> "ପାଣି ନେଉନାହିଁ"
        "Too much vibration" -> "ଅଧିକ କମ୍ପନ ହେଉଛି"
        "Clothes still wet" -> "କପଡ଼ା ଓଦା ରହୁଛି"
        "Detergent not dispensing" -> "ଡିଟର୍ଜେଣ୍ଟ୍ ବାହାରୁନାହିଁ"
        "Error code on display" -> "ଡିସପ୍ଲେରେ ଏରର୍ ଦେଖାଉଛି"
        "Display not working" -> "ଡିସପ୍ଲେ କାମ କରୁନାହିଁ"
        "Uneven heating" -> "ସମାନ ଭାବେ ଗରମ ହେଉନାହିଁ"
        "Burning smell" -> "ପୋଡ଼ା ଗନ୍ଧ ଆସୁଛି"
        "Buttons not responding" -> "ବଟନ୍ କାମ କରୁନାହିଁ"
        "Water not heating enough" -> "ପାଣି ଯଥେଷ୍ଟ ଗରମ ହେଉନାହିଁ"
        "Takes too long to heat" -> "ଗରମ ହେବାକୁ ଅଧିକ ସମୟ ଲାଗୁଛି"
        "Hot water runs out quickly" -> "ଗରମ ପାଣି ଶୀଘ୍ର ସରିଯାଉଛି"
        "Rusty water" -> "କଳଙ୍କି ରଙ୍ଗର ପାଣି ଆସୁଛି"
        "Ice build-up" -> "ବରଫ ଜମୁଛି"
        "Door / gasket problem" -> "ଦ୍ୱାର / ଗାସକେଟ୍ ସମସ୍ୟା"
        "Not starting" -> "ଚାଲୁ ହେଉନାହିଁ"
        "Not draining water" -> "ପାଣି ବାହାରୁନାହିଁ"
        "Not spinning" -> "ଘୂରୁନାହିଁ"
        "Door / lid problem" -> "ଦ୍ୱାର / ଢାଙ୍କୁଣୀ ସମସ୍ୟା"
        "Not heating" -> "ଗରମ କରୁନାହିଁ"
        "Sparking inside" -> "ଭିତରେ ସ୍ପାର୍କ ହେଉଛି"
        "Turntable not moving" -> "ଭିତର ପ୍ଲେଟ୍ ଘୂରୁନାହିଁ"
        "Door problem" -> "ଦ୍ୱାର ସମସ୍ୟା"
        "No hot water" -> "ଗରମ ପାଣି ଆସୁନାହିଁ"
        "Trips electricity" -> "ବିଦ୍ୟୁତ୍ ଟ୍ରିପ୍ ହେଉଛି"
        "Not working" -> "କାମ କରୁନାହିଁ"
        "Making noise" -> "ଅଧିକ ଶବ୍ଦ ହେଉଛି"
        "Leakage" -> "ଲିକେଜ୍"
        "Other" -> "ଅନ୍ୟ ସମସ୍ୟା"
        else -> issue
    }
}

private fun problemEmoji(issue: String): String = when {
    issue.contains("cool", true) || issue.contains("heating", true) || issue.contains("hot water", true) -> "🌡️"
    issue.contains("leak", true) || issue.contains("drain", true) -> "💧"
    issue.contains("noise", true) -> "🔊"
    issue.contains("turn", true) || issue.contains("start", true) -> "⚡"
    issue.contains("door", true) || issue.contains("lid", true) || issue.contains("gasket", true) -> "🔒"
    issue.contains("ice", true) -> "🧊"
    issue.contains("spin", true) -> "🌀"
    issue.contains("smell", true) -> "🌬️"
    else -> "🛠️"
}


@Composable
private fun ApplianceDrawing(appliance: String, issue: String, showMarker: Boolean, modifier: Modifier = Modifier) {
    val body = Color(0xFFDAE2EB)
    val edge = Color(0xFF64748B)
    val dark = Color(0xFF334155)
    Canvas(modifier) {
        drawRoundRect(Color(0x240F172A), Offset(size.width*0.14f, size.height*0.87f), Size(size.width*0.72f, size.height*0.055f), androidx.compose.ui.geometry.CornerRadius(size.width*0.05f))
        val w = size.width
        val h = size.height
        // Perspective side face behind each appliance's front panel.
        val side = Path().apply {
            when (appliance) {
                "AC Service" -> { moveTo(w*0.84f,h*0.24f); lineTo(w*0.90f,h*0.18f); lineTo(w*0.90f,h*0.62f); lineTo(w*0.84f,h*0.72f); close() }
                "Refrigerator" -> { moveTo(w*0.73f,h*0.08f); lineTo(w*0.80f,h*0.02f); lineTo(w*0.80f,h*0.85f); lineTo(w*0.73f,h*0.92f); close() }
                "Washing Machine" -> { moveTo(w*0.80f,h*0.12f); lineTo(w*0.87f,h*0.06f); lineTo(w*0.87f,h*0.84f); lineTo(w*0.80f,h*0.90f); close() }
                "Microwave Oven" -> { moveTo(w*0.88f,h*0.22f); lineTo(w*0.94f,h*0.16f); lineTo(w*0.94f,h*0.72f); lineTo(w*0.88f,h*0.78f); close() }
                else -> { moveTo(w*0.69f,h*0.12f); lineTo(w*0.76f,h*0.06f); lineTo(w*0.76f,h*0.82f); lineTo(w*0.69f,h*0.88f); close() }
            }
        }
        drawPath(side, color = Color(0xFF64788D))
        when (appliance) {
            "AC Service" -> {
                drawRoundRect(Brush.verticalGradient(listOf(Color(0xFFF8FBFF), Color(0xFFB8C7D8), Color(0xFF7E91A8))), Offset(w*0.16f,h*0.24f), Size(w*0.68f,h*0.48f), androidx.compose.ui.geometry.CornerRadius(14f))
                drawRoundRect(edge, Offset(w*0.16f,h*0.24f), Size(w*0.68f,h*0.48f), androidx.compose.ui.geometry.CornerRadius(14f), style=Stroke(3f))
                for (i in 0..5) drawLine(dark, Offset(w*0.24f,h*(0.52f+i*0.025f)), Offset(w*0.76f,h*(0.52f+i*0.025f)), strokeWidth=2.5f)
                drawCircle(edge, 3.5f, Offset(w*0.73f,h*0.34f))
            }
            "Refrigerator" -> {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF9FCFF), Color(0xFFD7E1EB), Color(0xFF9AAEC2))), Offset(w*0.27f,h*0.08f), Size(w*0.46f,h*0.84f), androidx.compose.ui.geometry.CornerRadius(12f))
                drawRoundRect(edge, Offset(w*0.27f,h*0.08f), Size(w*0.46f,h*0.84f), androidx.compose.ui.geometry.CornerRadius(12f), style=Stroke(3f))
                drawLine(edge, Offset(w*0.28f,h*0.43f), Offset(w*0.72f,h*0.43f), strokeWidth=3f)
                drawLine(edge, Offset(w*0.64f,h*0.20f), Offset(w*0.64f,h*0.36f), strokeWidth=4f)
                drawLine(edge, Offset(w*0.64f,h*0.54f), Offset(w*0.64f,h*0.78f), strokeWidth=4f)
            }
            "Washing Machine" -> {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF9FCFF), Color(0xFFD3DFEA), Color(0xFF91A6BB))), Offset(w*0.20f,h*0.12f), Size(w*0.60f,h*0.78f), androidx.compose.ui.geometry.CornerRadius(12f))
                drawRoundRect(edge, Offset(w*0.20f,h*0.12f), Size(w*0.60f,h*0.78f), androidx.compose.ui.geometry.CornerRadius(12f), style=Stroke(3f))
                drawLine(edge, Offset(w*0.22f,h*0.30f), Offset(w*0.78f,h*0.30f), strokeWidth=3f)
                drawCircle(Color(0xFF8DA8C4), w*0.19f, Offset(w*0.50f,h*0.59f))
                drawCircle(edge, w*0.19f, Offset(w*0.50f,h*0.59f), style=Stroke(4f))
                drawCircle(Color.White, w*0.12f, Offset(w*0.50f,h*0.59f))
                drawCircle(edge, 3f, Offset(w*0.31f,h*0.21f))
            }
            "Microwave Oven" -> {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF9FCFF), Color(0xFFD6E1EB), Color(0xFF8EA2B8))), Offset(w*0.12f,h*0.22f), Size(w*0.76f,h*0.56f), androidx.compose.ui.geometry.CornerRadius(10f))
                drawRoundRect(edge, Offset(w*0.12f,h*0.22f), Size(w*0.76f,h*0.56f), androidx.compose.ui.geometry.CornerRadius(10f), style=Stroke(3f))
                drawRoundRect(Color(0xFF9CB4CA), Offset(w*0.19f,h*0.30f), Size(w*0.48f,h*0.40f), androidx.compose.ui.geometry.CornerRadius(5f))
                drawCircle(Color.White, w*0.12f, Offset(w*0.43f,h*0.50f), style=Stroke(2.5f))
                drawCircle(edge, 3f, Offset(w*0.76f,h*0.37f))
                drawCircle(edge, 3f, Offset(w*0.76f,h*0.48f))
                drawCircle(edge, 3f, Offset(w*0.76f,h*0.59f))
            }
            else -> {
                drawRoundRect(Brush.horizontalGradient(listOf(Color(0xFFF9FCFF), Color(0xFFD6E1EB), Color(0xFF8EA2B8))), Offset(w*0.31f,h*0.12f), Size(w*0.38f,h*0.76f), androidx.compose.ui.geometry.CornerRadius(w*0.16f))
                drawRoundRect(edge, Offset(w*0.31f,h*0.12f), Size(w*0.38f,h*0.76f), androidx.compose.ui.geometry.CornerRadius(w*0.16f), style=Stroke(3f))
                drawLine(edge, Offset(w*0.36f,h*0.29f), Offset(w*0.64f,h*0.29f), strokeWidth=3f)
                drawCircle(Color(0xFF8DA8C4), w*0.06f, Offset(w*0.50f,h*0.20f))
                drawLine(edge, Offset(w*0.50f,h*0.88f), Offset(w*0.50f,h*0.96f), strokeWidth=4f)
            }
        }
        // Gloss highlight along the front edge.
        drawLine(Color(0xBFFFFFFF), Offset(w*0.20f,h*0.27f), Offset(w*0.20f,h*0.64f), strokeWidth = w*0.012f)
        if (showMarker) {
            val mx = w * when {
                issue.contains("door", true) || issue.contains("gasket", true) -> 0.72f
                issue.contains("leak", true) || issue.contains("drain", true) -> 0.50f
                issue.contains("noise", true) || issue.contains("spin", true) -> 0.50f
                issue.contains("smell", true) || issue.contains("turning on", true) || issue.contains("starting", true) -> 0.75f
                issue.contains("installation", true) -> 0.22f
                else -> 0.52f
            }
            val my = h * when {
                issue.contains("leak", true) || issue.contains("drain", true) -> 0.84f
                issue.contains("door", true) || issue.contains("gasket", true) -> 0.50f
                issue.contains("cool", true) -> 0.30f
                issue.contains("heating", true) || issue.contains("hot water", true) -> 0.58f
                issue.contains("noise", true) -> 0.60f
                issue.contains("spin", true) -> 0.59f
                issue.contains("ice", true) -> 0.38f
                issue.contains("sparking", true) -> 0.50f
                issue.contains("smell", true) -> 0.32f
                issue.contains("turning on", true) || issue.contains("starting", true) -> 0.22f
                issue.contains("installation", true) -> 0.18f
                else -> 0.45f
            }
            drawCircle(Color(0x30000000), radius = w*0.095f, center = Offset(mx + w*0.018f, my + w*0.02f))
            drawCircle(Brush.radialGradient(listOf(Color(0xFFFF8A80), Color(0xFFD92D20), Color(0xFF8B1111)), radius = w*0.11f, center = Offset(mx-w*0.025f,my-w*0.028f)), radius = w*0.085f, center = Offset(mx,my))
            drawCircle(Color.White, radius = w*0.026f, center = Offset(mx-w*0.025f,my-w*0.028f))
            drawCircle(Color(0xFF7F1D1D), radius = w*0.085f, center = Offset(mx,my), style=Stroke(width=2f))
            drawLine(Color(0xFFD92D20), Offset(mx,my-w*0.09f), Offset(mx,my-w*0.18f), strokeWidth=3f)
        }
    }
}

@Composable
private fun Appliance3DPreview(appliance: String, rotationDegrees: Float, modifier: Modifier = Modifier) {
    Canvas(modifier) {
        val w = size.width
        val h = size.height
        val angle = Math.toRadians(rotationDegrees.toDouble())
        val c = kotlin.math.cos(angle).toFloat()
        val sn = kotlin.math.sin(angle).toFloat()
        data class V(val x: Float, val y: Float, val z: Float)
        data class P(val x: Float, val y: Float, val depth: Float)
        fun project(v: V): P {
            val rx = v.x * c - v.z * sn
            val rz = v.x * sn + v.z * c
            // Orthographic projection preserves proportions at every rotation angle.
            return P(w * (0.5f + rx * 0.34f), h * (0.47f + v.y * 0.36f), rz)
        }
        val (halfW, halfH, halfD) = when (appliance) {
            "AC Service" -> Triple(0.88f, 0.42f, 0.38f)
            "Refrigerator" -> Triple(0.53f, 0.96f, 0.40f)
            "Washing Machine" -> Triple(0.68f, 0.77f, 0.42f)
            "Microwave Oven" -> Triple(0.86f, 0.49f, 0.43f)
            else -> Triple(0.49f, 0.92f, 0.40f)
        }
        val vertices = listOf(
            V(-halfW, -halfH, -halfD), V(halfW, -halfH, -halfD), V(halfW, halfH, -halfD), V(-halfW, halfH, -halfD),
            V(-halfW, -halfH, halfD), V(halfW, -halfH, halfD), V(halfW, halfH, halfD), V(-halfW, halfH, halfD)
        )
        data class Face(val ids: List<Int>, val color: Color)
        val faces = listOf(
            Face(listOf(0, 1, 2, 3), Color(0xFFB7C8D9)),
            Face(listOf(4, 7, 6, 5), Color(0xFFE6EEF6)),
            Face(listOf(0, 4, 5, 1), Color(0xFF9AAFC3)),
            Face(listOf(1, 5, 6, 2), Color(0xFF71879D)),
            Face(listOf(2, 6, 7, 3), Color(0xFF8FA4B8)),
            Face(listOf(3, 7, 4, 0), Color(0xFFD5E1EC))
        ).map { face -> face to face.ids.map { project(vertices[it]).depth }.average().toFloat() }
            .sortedBy { it.second }

        // Paint all opaque faces back-to-front using the projected depth.
        faces.forEach { (face, _) ->
            val points = face.ids.map { project(vertices[it]) }
            val path = Path().apply {
                moveTo(points[0].x, points[0].y)
                points.drop(1).forEach { lineTo(it.x, it.y) }
                close()
            }
            val shaded = Color(
                red = (face.color.red * 0.92f).coerceIn(0f, 1f),
                green = (face.color.green * 0.94f).coerceIn(0f, 1f),
                blue = (face.color.blue * 0.97f).coerceIn(0f, 1f),
                alpha = 1f
            )
            drawPath(path, Brush.linearGradient(listOf(face.color, shaded), start = Offset(w * 0.18f, h * 0.12f), end = Offset(w * 0.84f, h * 0.86f)))
            drawPath(path, Color(0xFF536B82), style = Stroke(width = 1.25f))
        }

        // Appliance details are drawn in the front-face coordinate system and never float outside the shell.
        if (c > -0.02f) {
            val p0 = project(vertices[4])
            val p1 = project(vertices[5])
            val p2 = project(vertices[6])
            val p3 = project(vertices[7])
            fun facePoint(u: Float, v: Float): Offset {
                val tx = p0.x + (p1.x - p0.x) * u
                val ty = p0.y + (p1.y - p0.y) * u
                val bx = p3.x + (p2.x - p3.x) * u
                val by = p3.y + (p2.y - p3.y) * u
                return Offset(tx + (bx - tx) * v, ty + (by - ty) * v)
            }
            fun panelRect(u: Float, v: Float, rw: Float, rh: Float, color: Color) {
                val pts = listOf(facePoint(u, v), facePoint(u + rw, v), facePoint(u + rw, v + rh), facePoint(u, v + rh))
                val path = Path().apply {
                    moveTo(pts[0].x, pts[0].y)
                    pts.drop(1).forEach { lineTo(it.x, it.y) }
                    close()
                }
                drawPath(path, color)
                drawPath(path, Color(0xFF50667D), style = Stroke(width = 0.9f))
            }
            fun panelLine(u1: Float, v1: Float, u2: Float, v2: Float, color: Color, width: Float = 1.4f) {
                drawLine(color, facePoint(u1, v1), facePoint(u2, v2), strokeWidth = width)
            }
            when (appliance) {
                "AC Service" -> {
                    panelRect(0.07f, 0.55f, 0.86f, 0.29f, Color(0xFFDCE7F0))
                    for (i in 0..5) panelLine(0.13f, 0.60f + i * 0.038f, 0.87f, 0.60f + i * 0.038f, Color(0xFF627A90), 1.15f)
                    drawCircle(Color(0xFF526B82), w * 0.012f, facePoint(0.84f, 0.28f))
                }
                "Refrigerator" -> {
                    panelLine(0.03f, 0.43f, 0.97f, 0.43f, Color(0xFF71879D), 1.7f)
                    panelRect(0.79f, 0.15f, 0.035f, 0.19f, Color(0xFF6D8298))
                    panelRect(0.79f, 0.53f, 0.035f, 0.23f, Color(0xFF6D8298))
                    panelLine(0.06f, 0.04f, 0.94f, 0.04f, Color(0xFFF8FBFF), 1.1f)
                }
                "Washing Machine" -> {
                    panelRect(0.08f, 0.08f, 0.84f, 0.16f, Color(0xFFB5C7D8))
                    val center = facePoint(0.5f, 0.62f)
                    val radius = w * 0.072f
                    drawCircle(Color(0xFF4C647B), radius * 1.16f, center)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFEAF5FF), Color(0xFF6D91AE), Color(0xFF304D68)), radius = radius, center = center), radius, center)
                    drawCircle(Color(0xFFDCE8F2), radius * 0.72f, center, style = Stroke(width = 1.7f))
                }
                "Microwave Oven" -> {
                    panelRect(0.08f, 0.18f, 0.62f, 0.62f, Color(0xFF30485E))
                    panelRect(0.12f, 0.22f, 0.54f, 0.54f, Color(0xFF7699B4))
                    panelLine(0.16f, 0.28f, 0.60f, 0.28f, Color(0xFFBFD9EA), 1.1f)
                    for (i in 0..2) drawCircle(Color(0xFF435A70), w * 0.009f, facePoint(0.83f, 0.32f + i * 0.18f))
                }
                else -> {
                    panelRect(0.15f, 0.25f, 0.70f, 0.045f, Color(0xFF52677D))
                    val control = facePoint(0.5f, 0.18f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFF7FBFF), Color(0xFF8DA7BC), Color(0xFF425A70)), radius = w * 0.027f, center = control), w * 0.027f, control)
                    panelRect(0.18f, 0.36f, 0.64f, 0.47f, Color(0xFF71869A))
                    panelLine(0.23f, 0.42f, 0.77f, 0.42f, Color(0xFFDAE4ED), 1.3f)
                }
            }
        }
        drawOval(Brush.radialGradient(listOf(Color(0x300F172A), Color.Transparent)), Offset(w * 0.18f, h * 0.83f), Size(w * 0.64f, h * 0.10f))
    }
}

@Composable
private fun IssueIllustration(appliance: String, issue: String, language: AppLanguage) {
    Card(colors = CardDefaults.cardColors(containerColor = Color(0xFFF0F5FA)), shape = RoundedCornerShape(14.dp)) {
        Column(Modifier.fillMaxWidth().padding(14.dp), horizontalAlignment = Alignment.CenterHorizontally) {
            Text(tr(language, "Problem area guide", "ସମସ୍ୟା ଚିହ୍ନଟ ଗାଇଡ୍"), fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(8.dp))
            Box(Modifier.fillMaxWidth().height(156.dp).padding(horizontal = 28.dp, vertical = 4.dp), contentAlignment = Alignment.Center) {
                ApplianceDrawing(appliance, issue, showMarker = true, modifier = Modifier.fillMaxSize())
            }
            Text(tr(language, "Marked", "ବଛାଯାଇଛି") + ": ${issueLabel(language, issue)}", fontWeight = FontWeight.SemiBold, color = Navy)
            Text(tr(language, "Illustrative guide — select the closest matching issue.", "ଏହା ଏକ ସାଧାରଣ ଗାଇଡ୍ — ମେଳ ଖାଉଥିବା ସମସ୍ୟା ବାଛନ୍ତୁ।"), fontSize = 11.sp, color = Color.Gray)
        }
    }
}

@Composable
private fun BookingHistory(bookings: List<ServiceBooking>, language: AppLanguage, onCancel: (Int) -> Unit) {
    val context = LocalContext.current
    var pendingCancelId by remember { mutableStateOf<Int?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedStatus by remember { mutableStateOf("All") }
    val historyStatuses = listOf("All", "Pending", "Accepted", "On the Way", "In Progress", "Completed", "Rejected", "Cancelled")
    val visibleBookings = bookings.filter { booking ->
        val query = search.trim()
        (selectedStatus == "All" || booking.status == selectedStatus) &&
            (query.isBlank() || booking.id.toString().contains(query, ignoreCase = true) ||
            booking.customer.contains(query, ignoreCase = true) ||
            booking.phone.contains(query, ignoreCase = true) ||
            booking.appliance.contains(query, ignoreCase = true) ||
            booking.status.contains(query, ignoreCase = true) ||
            booking.issue.contains(query, ignoreCase = true) ||
            booking.address.contains(query, ignoreCase = true) ||
            booking.preferredSlot.contains(query, ignoreCase = true) ||
            booking.pincode.contains(query, ignoreCase = true))
    }.sortedByDescending { it.createdAt }
    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(8.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(tr(language, "Total", "ମୋଟ"), bookings.size.toString(), Modifier.weight(1f))
            Stat(tr(language, "Pending", "ବିଚାରାଧୀନ"), bookings.count { it.status == "Pending" }.toString(), Modifier.weight(1f))
            Stat(tr(language, "Completed", "ସମ୍ପୂର୍ଣ୍ଣ"), bookings.count { it.status == "Completed" }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr(language, "Search ID, customer, phone, appliance, issue or area", "ଆଇଡି, ଗ୍ରାହକ, ଫୋନ୍, ଉପକରଣ, ସମସ୍ୟା କିମ୍ବା ଠିକଣା ଖୋଜନ୍ତୁ")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bookings") }
        )
        Spacer(Modifier.height(8.dp))
        LazyRow(horizontalArrangement = Arrangement.spacedBy(8.dp), contentPadding = PaddingValues(vertical = 2.dp)) {
            items(historyStatuses) { status ->
                FilterChip(
                    selected = selectedStatus == status,
                    onClick = { selectedStatus = status },
                    label = { Text(statusLabel(language, status)) }
                )
            }
        }
        Spacer(Modifier.height(8.dp))
        if (bookings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(tr(language, "No bookings yet.", "ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି ବୁକିଂ ନାହିଁ।")) }
        } else if (visibleBookings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) { Text(tr(language, "No matching bookings. Try another status or search.", "ମେଳ ଖାଉଥିବା ବୁକିଂ ନାହିଁ। ଅନ୍ୟ ସ୍ଥିତି କିମ୍ବା ଖୋଜ ଚେଷ୍ଟା କରନ୍ତୁ।"), color = Color.Gray) }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visibleBookings, key = { it.id }) { b ->
                    Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                        Column(Modifier.padding(16.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("#${b.id} • ${applianceLabel(language, b.appliance)}", fontWeight = FontWeight.Bold, color = Navy)
                                    Text(tr(language, "Requested: ", "ଅନୁରୋଧ ତାରିଖ: ") + SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(b.createdAt)), fontSize = 11.sp, color = Color.Gray)
                                    Text(issueLabel(language, b.issue))
                                }
                                Status(b.status, language)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text("${tr(language, "Mobile", "ମୋବାଇଲ୍")}: ${b.phone}", fontSize = 13.sp, color = Color.Gray)
                            Text("${tr(language, "Address", "ଠିକଣା")}: ${b.address}", fontSize = 13.sp)
                            Spacer(Modifier.height(12.dp))
                            BookingProgress(b.status, language)
                            if (b.preferredSlot.isNotBlank()) Text("${tr(language, "Preferred visit: ", "ପସନ୍ଦର ସମୟ: ")}${b.preferredSlot}", fontSize = 13.sp, color = Navy)
                            if (b.pincode.isNotBlank()) Text("${tr(language, "PIN code: ", "ପିନ୍ କୋଡ୍: ")}${b.pincode}", fontSize = 13.sp)
                            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(
                                    onClick = {
                                        val details = "SHREE JAGANNATH REFRIGERATION\nService Booking #${b.id}\nAppliance: ${b.appliance}\nIssue: ${b.issue}\nStatus: ${b.status}\nRequested: ${SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(b.createdAt))}\nCustomer: ${b.customer}\nPhone: ${b.phone}\nAddress: ${b.address}" +
                                            (if (b.preferredSlot.isNotBlank()) "\nPreferred visit: ${b.preferredSlot}" else "") +
                                            (if (b.pincode.isNotBlank()) "\nPIN code: ${b.pincode}" else "")
                                        val shareIntent = Intent(Intent.ACTION_SEND).apply {
                                            type = "text/plain"
                                            putExtra(Intent.EXTRA_SUBJECT, "Service Booking #${b.id}")
                                            putExtra(Intent.EXTRA_TEXT, details)
                                        }
                                        context.startActivity(Intent.createChooser(shareIntent, tr(language, "Share booking details", "ବୁକିଂ ବିବରଣୀ ସେୟାର୍ କରନ୍ତୁ")))
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(tr(language, "Share Details", "ବିବରଣୀ ସେୟାର୍")) }
                                if (b.status == "Pending") {
                                    OutlinedButton(onClick = { pendingCancelId = b.id }, modifier = Modifier.weight(1f)) {
                                        Text(tr(language, "Cancel Request", "ଅନୁରୋଧ ବାତିଲ୍"))
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingCancelId?.let { bookingId ->
        AlertDialog(
            onDismissRequest = { pendingCancelId = null },
            title = { Text(tr(language, "Cancel this request?", "ଏହି ଅନୁରୋଧ ବାତିଲ୍ କରିବେ କି?")) },
            text = { Text(tr(language, "This cannot be undone from the customer screen.", "ଗ୍ରାହକ ସ୍କ୍ରିନ୍‌ରୁ ଏହାକୁ ପୁନଃ ପରିବର୍ତ୍ତନ କରିହେବ ନାହିଁ।")) },
            confirmButton = {
                TextButton(onClick = { onCancel(bookingId); pendingCancelId = null }) {
                    Text(tr(language, "Cancel request", "ଅନୁରୋଧ ବାତିଲ୍"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingCancelId = null }) {
                    Text(tr(language, "Keep booking", "ବୁକିଂ ରଖନ୍ତୁ"))
                }
            }
        )
    }
}

@Composable
private fun BookingProgress(status: String, language: AppLanguage) {
    val steps = listOf("Pending", "Accepted", "On the Way", "In Progress", "Completed")
    if (status == "Rejected" || status == "Cancelled") {
        Text(tr(language, "Request $status", "ଅନୁରୋଧ: ${statusLabel(language, status)}"), color = MaterialTheme.colorScheme.error, fontWeight = FontWeight.SemiBold, fontSize = 13.sp)
        return
    }
    val current = steps.indexOf(status).coerceAtLeast(0)
    Column {
        Text(tr(language, "Service progress", "ସେବାର ପ୍ରଗତି"), fontWeight = FontWeight.SemiBold, color = Navy, fontSize = 13.sp)
        Spacer(Modifier.height(8.dp))
        steps.forEachIndexed { index, step ->
            Row(verticalAlignment = Alignment.CenterVertically) {
                Box(
                    Modifier.size(22.dp).background(
                        if (index <= current) Navy else Color(0xFFE1E6ED),
                        RoundedCornerShape(50)
                    ),
                    contentAlignment = Alignment.Center
                ) {
                    if (index < current) {
                        Icon(Icons.Default.Check, contentDescription = null, tint = Color.White, modifier = Modifier.size(14.dp))
                    } else {
                        Text("${index + 1}", color = if (index == current) Color.White else Color.DarkGray, fontSize = 10.sp)
                    }
                }
                Spacer(Modifier.width(8.dp))
                Text(statusLabel(language, step), color = if (index == current) Navy else Color.Gray, fontWeight = if (index == current) FontWeight.Bold else FontWeight.Normal, fontSize = 13.sp)
            }
            if (index < steps.lastIndex) {
                Box(Modifier.padding(start = 10.dp).width(2.dp).height(7.dp).background(if (index < current) Navy else Color(0xFFE1E6ED)))
            }
        }
    }
}

@Composable
private fun AdminLogin(language: AppLanguage, onSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(tr(language, "Admin Access", "ଆଡମିନ୍ ପ୍ରବେଶ"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(tr(language, "Demo-only login. Secure backend authentication is required before release.", "ଏହା ଡେମୋ ଲଗଇନ୍। ପ୍ରକାଶ ପୂର୍ବରୁ ସୁରକ୍ଷିତ ଲଗଇନ୍ ଆବଶ୍ୟକ।"))
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(username, { username = it }, label = { Text(tr(language, "Admin username", "ଆଡମିନ୍ ୟୁଜରନେମ୍")) }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text(tr(language, "Password", "ପାସୱାର୍ଡ")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (username == "admin" && password == "demo1234") onSuccess()
            else error = tr(language, "Demo login: admin / demo1234", "ଡେମୋ ଲଗଇନ୍: admin / demo1234")
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text(tr(language, "Login", "ଲଗଇନ୍")) }
    }
}

@Composable
private fun AdminHome(bookings: List<ServiceBooking>, language: AppLanguage, onStatus: (Int, String) -> Unit) {
    val context = LocalContext.current
    var pendingStatusChange by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }
    var reportText by remember { mutableStateOf("") }
    val exportLauncher = rememberLauncherForActivityResult(ActivityResultContracts.CreateDocument("text/csv")) { uri ->
        if (uri != null) {
            runCatching {
                context.contentResolver.openOutputStream(uri)?.bufferedWriter(Charsets.UTF_8)?.use { writer ->
                    writer.write("\uFEFF")
                    writer.write(reportText)
                } ?: error("Unable to open destination")
            }.onSuccess {
                Toast.makeText(context, tr(language, "CSV report saved.", "CSV ରିପୋର୍ଟ ସେଭ୍ ହେଲା।"), Toast.LENGTH_SHORT).show()
            }.onFailure {
                Toast.makeText(context, tr(language, "Could not save report.", "ରିପୋର୍ଟ ସେଭ୍ ହୋଇପାରିଲା ନାହିଁ।"), Toast.LENGTH_LONG).show()
            }
        }
    }
    val filters = listOf("All", "Pending", "Accepted", "On the Way", "In Progress", "Completed", "Rejected", "Cancelled")
    val visibleBookings = bookings
        .filter { selectedFilter == "All" || it.status == selectedFilter }
        .filter {
            val query = search.trim()
            query.isBlank() ||
                it.id.toString().contains(query, ignoreCase = true) ||
                it.customer.contains(query, ignoreCase = true) ||
                it.phone.contains(query, ignoreCase = true) ||
                it.appliance.contains(query, ignoreCase = true) ||
                it.issue.contains(query, ignoreCase = true) ||
                it.address.contains(query, ignoreCase = true) ||
                it.preferredSlot.contains(query, ignoreCase = true) ||
                it.pincode.contains(query, ignoreCase = true)
        }
        .sortedByDescending { it.id }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text(tr(language, "Namaskar, Mechanic 👋", "ନମସ୍କାର, ମେକାନିକ୍ 👋"), fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(tr(language, "Review requests, find customers and update service status.", "ଅନୁରୋଧ ଦେଖନ୍ତୁ, ଗ୍ରାହକ ଖୋଜନ୍ତୁ ଏବଂ ସେବା ସ୍ଥିତି ବଦଳାନ୍ତୁ।"), color = Color.Gray)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(tr(language, "New", "ନୂଆ"), bookings.count { it.status == "Pending" }.toString(), Modifier.weight(1f))
            Stat(tr(language, "Active", "ଚାଲୁ"), bookings.count { it.status in listOf("Accepted", "On the Way", "In Progress") }.toString(), Modifier.weight(1f))
            Stat(tr(language, "Done", "ସମ୍ପୂର୍ଣ୍ଣ"), bookings.count { it.status == "Completed" }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\""
                val rows = mutableListOf(
                    listOf("Booking ID", "Created At", "Customer", "Phone", "Appliance", "Issue", "Status", "Address", "Preferred Visit", "PIN Code", "GPS Latitude", "GPS Longitude").joinToString(",")
                )
                bookings.sortedByDescending { it.createdAt }.forEach { booking ->
                    rows += listOf(
                        booking.id.toString(),
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(booking.createdAt)),
                        booking.customer, booking.phone, booking.appliance, booking.issue, booking.status,
                        booking.address, booking.preferredSlot, booking.pincode,
                        booking.latitude?.toString().orEmpty(), booking.longitude?.toString().orEmpty()
                    ).joinToString(",") { csvCell(it) }
                }
                reportText = rows.joinToString("\n")
                exportLauncher.launch("ShreeJagannath-Bookings.csv")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(tr(language, "Save Booking Report as CSV", "ବୁକିଂ ରିପୋର୍ଟ CSV ଭାବେ ସେଭ୍ କରନ୍ତୁ")) }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text(tr(language, "Search ID, customer, phone, appliance, issue, area or PIN", "ଆଇଡି, ଗ୍ରାହକ, ଫୋନ୍, ଉପକରଣ, ସମସ୍ୟା କିମ୍ବା ପିନ୍ ଖୋଜନ୍ତୁ")) },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bookings") }
        )
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { filterExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text(tr(language, "Status filter", "ସ୍ଥିତି ଫିଲ୍ଟର") + ": ${statusLabel(language, selectedFilter)}")
            }
            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                filters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(statusLabel(language, filter)) },
                        onClick = { selectedFilter = filter; filterExpanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text(tr(language, "Showing ${visibleBookings.size} of ${bookings.size} requests", "${visibleBookings.size} ରୁ ${bookings.size} ଟି ଅନୁରୋଧ ଦେଖାଯାଉଛି"), fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (bookings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text(tr(language, "No service requests yet.", "ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି ସେବା ଅନୁରୋଧ ନାହିଁ।")) }
        } else if (visibleBookings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text(tr(language, "No matching requests. Try another search or status filter.", "କୌଣସି ମେଳ ଅନୁରୋଧ ନାହିଁ। ଅନ୍ୟ ଖୋଜ କିମ୍ବା ଫିଲ୍ଟର୍ ଚେଷ୍ଟା କରନ୍ତୁ।"), color = Color.Gray)
            }
        } else {
            LazyColumn(Modifier.weight(1f)) {
                items(visibleBookings, key = { it.id }) { b ->
                    Card(
                        Modifier.fillMaxWidth().padding(bottom = 12.dp),
                        shape = RoundedCornerShape(16.dp),
                        colors = CardDefaults.cardColors(containerColor = Color.White)
                    ) {
                        Column(Modifier.padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Column(Modifier.weight(1f)) {
                                    Text("${tr(language, "Booking", "ବୁକିଂ")} #${b.id} • ${applianceLabel(language, b.appliance)}", fontWeight = FontWeight.Bold, color = Navy)
                                    Text(b.customer)
                                    Text(
                                        SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(b.createdAt)),
                                        fontSize = 11.sp,
                                        color = Color.Gray
                                    )
                                }
                                Status(b.status, language)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(issueLabel(language, b.issue))
                            Text("${tr(language, "Phone", "ଫୋନ୍")}: ${b.phone}", fontSize = 13.sp, color = Color.Gray)
                            Text("${tr(language, "Address", "ଠିକଣା")}: ${b.address}", fontSize = 13.sp, color = Color.Gray)
                            if (b.pincode.isNotBlank()) {
                                Text("${tr(language, "PIN code", "ପିନ୍ କୋଡ୍")}: ${b.pincode}", fontSize = 13.sp, color = Color.Gray)
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), modifier = Modifier.fillMaxWidth()) {
                                OutlinedButton(
                                    onClick = { context.startActivity(Intent(Intent.ACTION_DIAL, Uri.parse("tel:${b.phone}"))) },
                                    modifier = Modifier.weight(1f)
                                ) { Text(tr(language, "Call customer", "ଗ୍ରାହକଙ୍କୁ କଲ୍ କରନ୍ତୁ")) }
                                OutlinedButton(
                                    onClick = {
                                        val mapsUri = if (b.latitude != null && b.longitude != null) {
                                            Uri.parse("https://www.google.com/maps/dir/?api=1&destination=${b.latitude},${b.longitude}")
                                        } else {
                                            val fullAddress = listOf(b.address, b.pincode, "India")
                                                .filter { it.isNotBlank() }
                                                .joinToString(", ")
                                            Uri.parse("https://www.google.com/maps/search/?api=1&query=${Uri.encode(fullAddress)}")
                                        }
                                        val mapIntent = Intent(Intent.ACTION_VIEW, mapsUri)
                                        runCatching {
                                            context.startActivity(mapIntent)
                                        }.onFailure {
                                            Toast.makeText(
                                                context,
                                                tr(language, "Could not open Google Maps. Please install or enable a maps app.", "ଗୁଗୁଲ୍ ମ୍ୟାପ୍ ଖୋଲିହେଲା ନାହିଁ। ମ୍ୟାପ୍ ଆପ୍ ଇନଷ୍ଟଲ୍ କରନ୍ତୁ।"),
                                                Toast.LENGTH_LONG
                                            ).show()
                                        }
                                    },
                                    modifier = Modifier.weight(1f)
                                ) { Text(tr(language, "Open in Google Maps", "ଗୁଗୁଲ୍ ମ୍ୟାପ୍‌ରେ ଖୋଲନ୍ତୁ")) }
                            }
                            if (b.preferredSlot.isNotBlank()) Text("${tr(language, "Preferred visit: ", "ପସନ୍ଦର ସମୟ: ")}${b.preferredSlot}", fontSize = 13.sp, color = Navy)
                            if (b.pincode.isNotBlank()) Text("${tr(language, "PIN code: ", "ପିନ୍ କୋଡ୍: ")}${b.pincode}", fontSize = 13.sp, color = Navy)
                            Spacer(Modifier.height(10.dp))
                            if (b.status == "Pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onStatus(b.id, "Accepted") }, modifier = Modifier.weight(1f)) { Text(tr(language, "Accept", "ଗ୍ରହଣ କରନ୍ତୁ")) }
                                    OutlinedButton(onClick = { pendingStatusChange = b.id to "Rejected" }, modifier = Modifier.weight(1f)) { Text(tr(language, "Reject", "ଅସ୍ୱୀକାର କରନ୍ତୁ")) }
                                }
                            } else if (b.status !in listOf("Completed", "Rejected", "Cancelled")) {
                                var expanded by remember(b.id) { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(language, "Update Status", "ସ୍ଥିତି ବଦଳାନ୍ତୁ")) }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        val nextStatuses = when (b.status) {
                                            "Accepted" -> listOf("On the Way", "Cancelled")
                                            "On the Way" -> listOf("In Progress", "Cancelled")
                                            "In Progress" -> listOf("Completed", "Cancelled")
                                            else -> emptyList()
                                        }
                                        nextStatuses.forEach { status ->
                                            DropdownMenuItem(
                                                text = { Text(statusLabel(language, status)) },
                                                onClick = { if (status == "Cancelled") pendingStatusChange = b.id to status else onStatus(b.id, status); expanded = false }
                                            )
                                        }
                                    }
                                }
                            }
                        }
                    }
                }
            }
        }
    }
    pendingStatusChange?.let { (bookingId, nextStatus) ->
        AlertDialog(
            onDismissRequest = { pendingStatusChange = null },
            title = {
                Text(
                    if (nextStatus == "Rejected") tr(language, "Reject this booking?", "ଏହି ବୁକିଂ ଅସ୍ୱୀକାର କରିବେ କି?")
                    else tr(language, "Cancel this service?", "ଏହି ସେବା ବାତିଲ୍ କରିବେ କି?")
                )
            },
            text = {
                Text(
                    if (nextStatus == "Rejected") tr(language, "The customer request will be marked as rejected.", "ଗ୍ରାହକଙ୍କ ଅନୁରୋଧ ଅସ୍ୱୀକୃତ ଭାବେ ଚିହ୍ନିତ ହେବ।")
                    else tr(language, "The booking will be marked as cancelled.", "ବୁକିଂଟି ବାତିଲ୍ ଭାବେ ଚିହ୍ନିତ ହେବ।")
                )
            },
            confirmButton = {
                TextButton(onClick = { onStatus(bookingId, nextStatus); pendingStatusChange = null }) {
                    Text(tr(language, "Confirm", "ନିଶ୍ଚିତ କରନ୍ତୁ"))
                }
            },
            dismissButton = {
                TextButton(onClick = { pendingStatusChange = null }) {
                    Text(tr(language, "Go back", "ଫେରିଯାଆନ୍ତୁ"))
                }
            }
        )
    }
}

@Composable
private fun Stat(title: String, value: String, modifier: Modifier = Modifier) {
    Card(modifier, shape = RoundedCornerShape(14.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
        Column(Modifier.padding(12.dp)) {
            Text(value, fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text(title, fontSize = 12.sp, color = Color.Gray)
        }
    }
}


private fun statusLabel(language: AppLanguage, status: String): String {
    if (language == AppLanguage.ENGLISH) return status
    return when (status) {
        "Pending" -> "ବିଚାରାଧୀନ"
        "Accepted" -> "ଗ୍ରହଣ କରାଯାଇଛି"
        "On the Way" -> "ରାସ୍ତାରେ ଅଛନ୍ତି"
        "In Progress" -> "କାମ ଚାଲିଛି"
        "Completed" -> "ସମ୍ପୂର୍ଣ୍ଣ"
        "Rejected" -> "ଅସ୍ୱୀକୃତ"
        "Cancelled" -> "ବାତିଲ୍"
        "All" -> "ସମସ୍ତ"
        else -> status
    }
}

@Composable
private fun Status(status: String, language: AppLanguage) {
    val color = when (status) {
        "Completed" -> Color(0xFF16804A)
        "Rejected", "Cancelled" -> Color(0xFFB3261E)
        "Pending" -> Orange
        else -> Navy
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(statusLabel(language, status), color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
