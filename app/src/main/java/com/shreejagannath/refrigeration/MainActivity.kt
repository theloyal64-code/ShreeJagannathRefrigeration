package com.shreejagannath.refrigeration

import android.Manifest
import android.app.DatePickerDialog
import android.app.TimePickerDialog
import android.content.Context
import android.content.Intent
import android.content.pm.PackageManager
import android.location.Location
import android.location.Geocoder
import com.google.android.gms.location.LocationServices
import com.google.android.gms.location.Priority
import com.google.android.gms.location.CurrentLocationRequest
import com.google.android.gms.tasks.CancellationTokenSource
import android.location.LocationListener
import android.location.LocationManager
import android.os.Looper
import android.os.Handler
import android.widget.Toast
import androidx.credentials.CredentialManager
import androidx.credentials.GetCredentialRequest
import androidx.credentials.CustomCredential
import com.google.android.libraries.identity.googleid.GetGoogleIdOption
import com.google.android.libraries.identity.googleid.GoogleIdTokenCredential
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.auth.GoogleAuthProvider
import kotlinx.coroutines.launch
import kotlinx.coroutines.tasks.await
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
import androidx.compose.material.icons.filled.CheckCircle
import androidx.compose.material.icons.filled.Build
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
data class ServiceBooking(val id: Int, val customer: String, val phone: String, val appliance: String, val issue: String, val address: String, val preferredSlot: String = "", val pincode: String = "", val status: String = "Pending", val createdAt: Long = System.currentTimeMillis(), val latitude: Double? = null, val longitude: Double? = null, val statusUpdatedAt: Long = createdAt, val assignedMechanic: String = "", val rating: Int = 0, val review: String = "", val reviewedAt: Long = 0L, val cloudId: String = "", val customerUid: String = "", val assignedMechanicUid: String = "")

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
                longitude = if (item.isNull("longitude")) null else item.optDouble("longitude"),
                statusUpdatedAt = item.optLong("statusUpdatedAt", item.optLong("createdAt", System.currentTimeMillis())).takeIf { it > 0L } ?: System.currentTimeMillis(),
                assignedMechanic = item.optString("assignedMechanic", ""),
                rating = item.optInt("rating", 0),
                review = item.optString("review", ""),
                reviewedAt = item.optLong("reviewedAt", 0L),
                cloudId = item.optString("cloudId", ""),
                customerUid = item.optString("customerUid", ""),
                assignedMechanicUid = item.optString("assignedMechanicUid", "")
            )
        }
    } catch (_: Exception) {
        emptyList()
    }
}

internal fun canTransitionBookingStatus(current: String, next: String): Boolean = when (current) {
    "Pending" -> next in listOf("Accepted", "Rejected", "Cancelled")
    "Accepted" -> next in listOf("On the Way", "Cancelled")
    "On the Way" -> next in listOf("In Progress", "Cancelled")
    "In Progress" -> next in listOf("Completed", "Cancelled")
    else -> false
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
            put("statusUpdatedAt", booking.statusUpdatedAt)
            put("assignedMechanic", booking.assignedMechanic)
            put("rating", booking.rating)
            put("review", booking.review)
            put("reviewedAt", booking.reviewedAt)
            put("latitude", booking.latitude ?: JSONObject.NULL)
            put("longitude", booking.longitude ?: JSONObject.NULL)
            put("cloudId", booking.cloudId)
            put("customerUid", booking.customerUid)
            put("assignedMechanicUid", booking.assignedMechanicUid)
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
    var loggedInMechanic by remember { mutableStateOf("") }
    var lastCreatedBookingId by remember { mutableIntStateOf(0) }
    var bookings by remember { mutableStateOf(loadBookings(context)) }
    var nextId by remember { mutableIntStateOf((bookings.maxOfOrNull { it.id } ?: 1000) + 1) }
    val cloudStore = remember { BookingCloudStore() }
    var currentUid by remember { mutableStateOf(FirebaseAuth.getInstance().currentUser?.uid.orEmpty()) }
    var accountRole by remember { mutableStateOf("") }
    var cloudError by remember { mutableStateOf("") }
    var mechanicProfiles by remember { mutableStateOf<List<Map<String, String>>>(emptyList()) }
    var bookingListener by remember { mutableStateOf<ListenerRegistration?>(null) }
    val scope = rememberCoroutineScope()

    LaunchedEffect(currentUid) {
        bookingListener?.remove()
        bookingListener = null
        if (currentUid.isBlank()) {
            accountRole = ""
            bookings = emptyList()
            return@LaunchedEffect
        }
        try {
            val user = FirebaseAuth.getInstance().currentUser ?: throw IllegalStateException("Please sign in with Google again.")
            cloudStore.ensureCustomerProfile(user)
            val profile = FirebaseFirestore.getInstance().collection("users").document(currentUid).get().await()
            accountRole = profile.getString("role") ?: "customer"
            bookingListener = cloudStore.observeBookings(currentUid, accountRole,
                onBookings = { rows -> bookings = rows.map { firestoreBookingToModel(it) }; cloudError = "" },
                onError = { error -> cloudError = error.message ?: "Unable to load cloud bookings." }
            )
        } catch (error: Exception) {
            cloudError = error.message ?: "Unable to connect to the booking database."
        }
    }

    LaunchedEffect(currentUid, accountRole) {
        mechanicProfiles = if (accountRole == "admin") {
            try { cloudStore.listMechanics() } catch (_: Exception) { emptyList() }
        } else emptyList()
    }

    DisposableEffect(Unit) { onDispose { bookingListener?.remove() } }

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
                colors = TopAppBarDefaults.topAppBarColors(containerColor = Navy, titleContentColor = Color.White, navigationIconContentColor = Color.White, actionIconContentColor = Color.White),
                title = { Text(when (screen) {
                    "customer" -> tr(language, "Book a Service", "ସେବା ବୁକ୍ କରନ୍ତୁ")
                    "form" -> tr(language, "Service Request", "ସେବା ଅନୁରୋଧ")
                    "history" -> tr(language, "My Bookings", "ମୋ ବୁକିଂ")
                    "review" -> tr(language, "Rate Your Service", "ସେବାକୁ ରେଟ୍ କରନ୍ତୁ")
                    "bookingConfirmation" -> tr(language, "Booking Confirmed", "ବୁକିଂ ନିଶ୍ଚିତ")
                    "adminLogin" -> tr(language, "Team Login", "ଟିମ୍ ଲଗଇନ୍")
                    "admin" -> tr(language, "Admin Dashboard", "ଆଡମିନ୍ ଡ୍ୟାସବୋର୍ଡ")
                    "mechanic" -> tr(language, "Mechanic Dashboard", "ମେକାନିକ୍ ଡ୍ୟାସବୋର୍ଡ")
                    else -> "Shree Jagannath"
                }, fontWeight = FontWeight.Bold) },
                navigationIcon = {
                    IconButton(onClick = { screen = when (screen) {
                        "form" -> "customer"
                        "history" -> "customer"
                        "review" -> "history"
                        "bookingConfirmation" -> "customer"
                        "admin", "mechanic" -> "welcome"
                        else -> "welcome"
                    } }) { Icon(Icons.Default.ArrowBack, contentDescription = "Back") }
                }
            )
        }) { insets ->
            Box(Modifier.fillMaxSize().background(Page).padding(insets)) {
                when (screen) {
                    "welcome" -> Welcome(language, onLanguage = { language = it }, onCustomer = {
                        val user = FirebaseAuth.getInstance().currentUser
                        if (user == null) {
                            Toast.makeText(context, tr(language, "Please tap the Google sign-in button first.", "ପ୍ରଥମେ Google ଲଗଇନ୍ ବଟନ୍ ଦବାନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                        } else {
                            currentUid = user.uid
                            screen = "customer"
                        }
                    }, onAdmin = { screen = "adminLogin" })
                    "customer" -> CustomerHome(appliances, bookings, language,
                        onChoose = { chosen = it; screen = "form" }, onHistory = { screen = "history" })
                    "form" -> RequestForm(chosen, language, onSubmit = { name, phone, issue, address, preferredSlot, pincode, latitude, longitude ->
                        if (currentUid.isBlank() || accountRole != "customer") {
                            Toast.makeText(context, tr(language, "Please sign in with a customer Google account before booking.", "ବୁକିଂ ପୂର୍ବରୁ ଗ୍ରାହକ Google ଆକାଉଣ୍ଟରେ ଲଗଇନ୍ କରନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                        } else {
                            scope.launch {
                                try {
                                    val cloudId = cloudStore.createBooking(currentUid, mapOf(
                                        "customerName" to name, "phone" to phone, "appliance" to chosen.name,
                                        "issue" to issue, "address" to address, "preferredSlot" to preferredSlot,
                                        "pincode" to pincode, "latitude" to latitude, "longitude" to longitude
                                    ))
                                    val newBooking = ServiceBooking(
                                        id = cloudId.hashCode() and 0x7fffffff, customer = name, phone = phone,
                                        appliance = chosen.name, issue = issue, address = address,
                                        preferredSlot = preferredSlot, pincode = pincode, latitude = latitude,
                                        longitude = longitude, cloudId = cloudId, customerUid = currentUid
                                    )
                                    lastCreatedBookingId = newBooking.id
                                    bookings = bookings.filterNot { it.cloudId == cloudId } + newBooking
                                    screen = "bookingConfirmation"
                                } catch (error: Exception) {
                                    Toast.makeText(context, error.message ?: "Booking could not be saved. Please retry.", Toast.LENGTH_LONG).show()
                                }
                            }
                        }
                    })
                    "bookingConfirmation" -> BookingConfirmation(
                        booking = bookings.firstOrNull { it.id == lastCreatedBookingId },
                        language = language,
                        onViewBookings = { screen = "history" },
                        onHome = { screen = "customer" }
                    )
                    "history" -> BookingHistory(bookings, language, onCancel = { id ->
                        val booking = bookings.firstOrNull { it.id == id }
                        if (booking != null && booking.cloudId.isNotBlank()) scope.launch {
                            try {
                                cloudStore.cancelPendingBooking(booking.cloudId, currentUid)
                            } catch (error: Exception) {
                                Toast.makeText(context, error.message ?: "Could not cancel booking.", Toast.LENGTH_LONG).show()
                            }
                        }
                    }, onReview = { id -> lastCreatedBookingId = id; screen = "review" })
                    "review" -> {
                        val reviewBooking = bookings.firstOrNull { it.id == lastCreatedBookingId }
                        if (reviewBooking != null) ServiceReviewScreen(
                            booking = reviewBooking,
                            language = language,
                            onSubmit = { rating, review ->
                                scope.launch {
                                    try {
                                        cloudStore.submitReview(reviewBooking.cloudId, currentUid, rating, review.trim())
                                        screen = "history"
                                    } catch (error: Exception) {
                                        Toast.makeText(context, error.message ?: "Could not submit review.", Toast.LENGTH_LONG).show()
                                    }
                                }
                            },
                            onPlayStore = {
                                val marketIntent = Intent(Intent.ACTION_VIEW, Uri.parse("market://details?id=${context.packageName}"))
                                runCatching { context.startActivity(marketIntent) }.onFailure {
                                    context.startActivity(Intent(Intent.ACTION_VIEW, Uri.parse("https://play.google.com/store/apps/details?id=${context.packageName}")))
                                }
                            }
                        ) else screen = "history"
                    }
                    "adminLogin" -> TeamLogin(language, onAdminSuccess = { currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty(); screen = "admin" }, onMechanicSuccess = { mechanic ->
                        currentUid = FirebaseAuth.getInstance().currentUser?.uid.orEmpty()
                        loggedInMechanic = mechanic
                        screen = "mechanic"
                    })
                    "mechanic" -> MechanicHome(
                        bookings = bookings.filter { it.assignedMechanicUid == currentUid },
                        mechanicName = loggedInMechanic,
                        language = language,
                        onLogout = { FirebaseAuth.getInstance().signOut(); currentUid = ""; accountRole = ""; loggedInMechanic = ""; screen = "welcome" },
                        onStatus = { id, status ->
                            val booking = bookings.firstOrNull { it.id == id && it.assignedMechanicUid == currentUid }
                            if (booking != null && booking.cloudId.isNotBlank()) scope.launch {
                                try { cloudStore.updateStatus(booking.cloudId, currentUid, "mechanic", status) }
                                catch (error: Exception) { Toast.makeText(context, error.message ?: "Could not update booking status.", Toast.LENGTH_LONG).show() }
                            }
                        }
                    )
                    "admin" -> AdminHome(bookings, language, mechanicProfiles, onStatus = { id, status ->
                        val booking = bookings.firstOrNull { it.id == id }
                        if (booking != null && booking.cloudId.isNotBlank()) scope.launch {
                            try { cloudStore.updateStatus(booking.cloudId, currentUid, "admin", status) }
                            catch (error: Exception) { Toast.makeText(context, error.message ?: "Could not update booking status.", Toast.LENGTH_LONG).show() }
                        }
                    }, onAssign = { id, mechanic ->
                        val booking = bookings.firstOrNull { it.id == id }
                        val target = mechanicProfiles.firstOrNull { it["displayName"] == mechanic }
                        if (booking != null && booking.cloudId.isNotBlank()) scope.launch {
                            try {
                                if (mechanic.isBlank()) cloudStore.assignMechanic(booking.cloudId, "", "")
                                else if (target != null) cloudStore.assignMechanic(booking.cloudId, target["uid"].orEmpty(), mechanic)
                                else throw IllegalStateException("Create a mechanic account and profile before assigning this job.")
                            } catch (error: Exception) { Toast.makeText(context, error.message ?: "Could not assign mechanic.", Toast.LENGTH_LONG).show() }
                        }
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
    Column(
        Modifier.fillMaxSize().background(Color(0xFFF4F8FD)).verticalScroll(rememberScrollState()).padding(bottom = 2.dp),
        verticalArrangement = Arrangement.spacedBy(0.dp)
    ) {
        Row(Modifier.fillMaxWidth().background(Color(0xFF0B3765)).padding(horizontal = 16.dp, vertical = 9.dp), verticalAlignment = Alignment.CenterVertically) {
            Icon(Icons.Default.HomeRepairService, contentDescription = null, tint = Color.White, modifier = Modifier.size(37.dp))
            Spacer(Modifier.width(9.dp))
            Column(Modifier.weight(1f)) {
                Text("SHREE JAGANNATH", color = Color.White, fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, lineHeight = 17.sp, maxLines = 1)
                Text("REFRIGERATION", color = Color(0xFFFF9B3D), fontSize = 13.sp, fontWeight = FontWeight.ExtraBold)
            }
            Column(horizontalAlignment = Alignment.End) {
                Text(tr(language, "Home Appliances", "ଘରୋଇ ଉପକରଣ"), color = Color(0xFFE6EFF8), fontSize = 10.sp)
                Text(tr(language, "Care Experts", "ସେବା ବିଶେଷଜ୍ଞ"), color = Color(0xFFE6EFF8), fontSize = 10.sp)
                Box(Modifier.padding(top = 3.dp).width(45.dp).height(3.dp).background(Orange, RoundedCornerShape(50)))
                Row(
                    Modifier.padding(top = 5.dp),
                    horizontalArrangement = Arrangement.spacedBy(4.dp),
                    verticalAlignment = Alignment.CenterVertically
                ) {
                    GoogleSignInButton(language = language, onSignedIn = onCustomer, compact = true)
                    listOf(
                        AppLanguage.ENGLISH to "EN",
                        AppLanguage.ODIA to "ଓଡ଼ିଆ"
                    ).forEach { (option, label) ->
                        Box(
                            Modifier
                                .background(
                                    if (language == option) Orange else Color(0xFF28517A),
                                    RoundedCornerShape(50)
                                )
                                .clickable { onLanguage(option) }
                                .padding(horizontal = 8.dp, vertical = 4.dp)
                        ) {
                            Text(
                                label,
                                color = Color.White,
                                fontSize = 9.sp,
                                fontWeight = FontWeight.Bold,
                                maxLines = 1
                            )
                        }
                    }
                }
            }
        }
        Row(
            Modifier.fillMaxWidth().height(270.dp).padding(horizontal = 16.dp, vertical = 3.dp),
            verticalAlignment = Alignment.CenterVertically
        ) {
            Column(Modifier.weight(1.05f), verticalArrangement = Arrangement.Center) {
                Text(tr(language, "Your Appliances", "ଆପଣଙ୍କ ଉପକରଣ"), fontSize = 27.sp, lineHeight = 29.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF0B3765))
                Text(tr(language, "Our Care", "ଆମର ଯତ୍ନ"), fontSize = 30.sp, lineHeight = 33.sp, fontWeight = FontWeight.ExtraBold, color = Orange)
                Spacer(Modifier.height(5.dp))
                Text(tr(language, "Fast • Reliable • Affordable", "ଦ୍ରୁତ • ଭରସାଯୋଗ୍ୟ • ସୁଲଭ"), fontSize = 11.sp, lineHeight = 15.sp, color = Color(0xFF4F6378), fontWeight = FontWeight.SemiBold)
                Box(Modifier.padding(top = 7.dp, bottom = 7.dp).width(35.dp).height(3.dp).background(Orange, RoundedCornerShape(50)))
                listOf(
                    "✓" to tr(language, "Expert Technicians", "ଦକ୍ଷ ଟେକ୍ନିସିଆନ୍"),
                    "◷" to tr(language, "On-Time Service", "ଠିକ୍ ସମୟରେ ସେବା"),
                    "₹" to tr(language, "Transparent Pricing", "ସ୍ପଷ୍ଟ ମୂଲ୍ୟ"),
                    "★" to tr(language, "Trusted by Homes", "ଘରେ ଘରେ ଭରସା")
                ).forEach { (symbol, label) ->
                    Row(Modifier.padding(vertical = 3.dp), verticalAlignment = Alignment.CenterVertically) {
                        Surface(color = Navy, shape = RoundedCornerShape(50)) {
                            Box(Modifier.size(23.dp), contentAlignment = Alignment.Center) { Text(symbol, color = Color.White, fontSize = 12.sp, fontWeight = FontWeight.Bold) }
                        }
                        Spacer(Modifier.width(6.dp))
                        Text(label, color = Color(0xFF40576F), fontSize = 10.sp, lineHeight = 13.sp, fontWeight = FontWeight.Medium, maxLines = 2)
                    }
                }
            }
            Spacer(Modifier.width(4.dp))
            Card(
                Modifier.weight(0.95f).fillMaxHeight(0.82f),
                shape = RoundedCornerShape(22.dp),
                colors = CardDefaults.cardColors(containerColor = Color(0xFFE3EFFB))
            ) {
                Column(Modifier.fillMaxSize().background(Brush.linearGradient(listOf(Color(0xFFEAF3FC), Color(0xFFD3E5F7)))).padding(horizontal = 4.dp, vertical = 6.dp), verticalArrangement = Arrangement.SpaceEvenly, horizontalAlignment = Alignment.CenterHorizontally) {
                    Appliance3DPreview("Refrigerator", 0f, Modifier.fillMaxWidth(0.74f).weight(1f))
                    Appliance3DPreview("Washing Machine", 0f, Modifier.fillMaxWidth(0.84f).weight(1f))
                    Appliance3DPreview("AC Service", 0f, Modifier.fillMaxWidth(0.94f).weight(0.78f))
                    Text(tr(language, "Home Appliance Care", "ଘରୋଇ ଉପକରଣ ସେବା"), color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                }
            }
        }
        // Clean transition between hero content and actions; removed mismatched ribbon.
        Spacer(Modifier.height(4.dp))
        Column(Modifier.fillMaxWidth().padding(horizontal = 16.dp)) {
            Button(
                onClick = onCustomer,
                modifier = Modifier.fillMaxWidth().height(66.dp),
                shape = RoundedCornerShape(18.dp),
                colors = ButtonDefaults.buttonColors(containerColor = Color(0xFF0B4B86)),
                contentPadding = PaddingValues(horizontal = 16.dp)
            ) {
                Icon(Icons.Default.Person, contentDescription = null, modifier = Modifier.size(30.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(tr(language, "Customer", "ଗ୍ରାହକ"), fontSize = 17.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Book a Service", "ସେବା ବୁକ୍ କରନ୍ତୁ"), fontSize = 12.sp, color = Color(0xFFE2EDF8))
                }
                Text("❯", fontSize = 23.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(8.dp))
            Spacer(Modifier.height(5.dp))
            OutlinedButton(
                onClick = onAdmin,
                modifier = Modifier.fillMaxWidth().height(62.dp),
                shape = RoundedCornerShape(18.dp),
                contentPadding = PaddingValues(horizontal = 16.dp),
                colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)
            ) {
                Icon(Icons.Default.Settings, contentDescription = null, modifier = Modifier.size(28.dp))
                Spacer(Modifier.width(12.dp))
                Column(Modifier.weight(1f), horizontalAlignment = Alignment.Start) {
                    Text(tr(language, "Admin / Mechanic", "ଆଡମିନ୍ / ମେକାନିକ୍"), fontSize = 15.sp, fontWeight = FontWeight.Bold)
                    Text(tr(language, "Login to Continue", "ଲଗଇନ୍ କରି ଆଗକୁ ବଢ଼ନ୍ତୁ"), fontSize = 11.sp, color = Color(0xFF61768B))
                }
                Text("❯", fontSize = 22.sp, fontWeight = FontWeight.Bold)
            }
            Spacer(Modifier.height(7.dp))
            Text(tr(language, "We Service All Major Appliances", "ସମସ୍ତ ପ୍ରମୁଖ ଉପକରଣର ସେବା"), fontSize = 15.sp, fontWeight = FontWeight.ExtraBold, color = Navy, maxLines = 1)
            Box(Modifier.padding(top = 4.dp, bottom = 6.dp).width(34.dp).height(3.dp).background(Orange, RoundedCornerShape(50)))
            Row(Modifier.fillMaxWidth(), horizontalArrangement = Arrangement.spacedBy(7.dp)) {
                listOf(
                    "AC Service" to "AC",
                    "Refrigerator" to tr(language, "Fridge", "ଫ୍ରିଜ୍"),
                    "Washing Machine" to tr(language, "Washer", "ୱାଶର୍"),
                    "Microwave Oven" to tr(language, "Microwave", "ମାଇକ୍ରୋୱେଭ୍")
                ).forEach { (applianceType, label) ->
                    Column(Modifier.weight(1f), horizontalAlignment = Alignment.CenterHorizontally) {
                        Card(
                            modifier = Modifier.fillMaxWidth().height(58.dp),
                            shape = RoundedCornerShape(15.dp),
                            colors = CardDefaults.cardColors(containerColor = Color.White),
                            elevation = CardDefaults.cardElevation(defaultElevation = 4.dp)
                        ) {
                            Box(
                                Modifier.fillMaxSize().background(
                                    Brush.linearGradient(listOf(Color.White, Color(0xFFE4EFFB)))
                                ).padding(4.dp),
                                contentAlignment = Alignment.Center
                            ) {
                                Appliance3DPreview(applianceType, 0f, Modifier.fillMaxSize())
                            }
                        }
                        Spacer(Modifier.height(5.dp))
                        Text(label, color = Navy, fontSize = 9.sp, fontWeight = FontWeight.Bold, maxLines = 1)
                    }
                }
            }
            Row(
                Modifier.fillMaxWidth().padding(top = 7.dp, bottom = 2.dp),
                horizontalArrangement = Arrangement.spacedBy(6.dp)
            ) {
                listOf(
                    "✓" to tr(language, "Quality Service", "ଗୁଣାତ୍ମକ ସେବା"),
                    "₹" to tr(language, "Fair Pricing", "ଉଚିତ ମୂଲ୍ୟ"),
                    "★" to tr(language, "Customer Care", "ଗ୍ରାହକ ସେବା")
                ).forEach { (symbol, label) ->
                    Card(
                        modifier = Modifier.weight(1f),
                        shape = RoundedCornerShape(12.dp),
                        colors = CardDefaults.cardColors(containerColor = Color(0xFFE7F0FA)),
                        elevation = CardDefaults.cardElevation(defaultElevation = 1.dp)
                    ) {
                        Column(
                            Modifier.fillMaxWidth().padding(vertical = 7.dp, horizontal = 2.dp),
                            horizontalAlignment = Alignment.CenterHorizontally
                        ) {
                            Text(symbol, color = Color(0xFF0B4B86), fontSize = 17.sp, fontWeight = FontWeight.ExtraBold)
                            Text(label, color = Color(0xFF536A80), fontSize = 8.sp, fontWeight = FontWeight.SemiBold, maxLines = 1)
                        }
                    }
                }
            }
        }
    }
}


@Composable
private fun GoogleSignInButton(language: AppLanguage, onSignedIn: () -> Unit, compact: Boolean = false) {
    val context = LocalContext.current
    val scope = rememberCoroutineScope()
    var signingIn by remember { mutableStateOf(false) }
    OutlinedButton(
        onClick = {
            if (!signingIn) {
                signingIn = true
                scope.launch {
                    try {
                        val credentialManager = CredentialManager.create(context)
                        val googleIdOption = GetGoogleIdOption.Builder()
                            .setFilterByAuthorizedAccounts(false)
                            .setServerClientId(context.getString(R.string.default_web_client_id))
                            .setAutoSelectEnabled(false)
                            .build()
                        val request = GetCredentialRequest.Builder()
                            .addCredentialOption(googleIdOption)
                            .build()
                        val result = credentialManager.getCredential(context, request)
                        val credential = result.credential
                        if (credential is CustomCredential &&
                            credential.type == GoogleIdTokenCredential.TYPE_GOOGLE_ID_TOKEN_CREDENTIAL
                        ) {
                            val googleCredential = GoogleIdTokenCredential.createFrom(credential.data)
                            val firebaseCredential = GoogleAuthProvider.getCredential(googleCredential.idToken, null)
                            FirebaseAuth.getInstance().signInWithCredential(firebaseCredential).await()
                            Toast.makeText(
                                context,
                                tr(language, "Signed in with Google", "Google ଦ୍ୱାରା ଲଗଇନ୍ ସଫଳ"),
                                Toast.LENGTH_SHORT
                            ).show()
                            onSignedIn()
                        } else {
                            Toast.makeText(context, tr(language, "Google sign-in was not completed.", "Google ଲଗଇନ୍ ସମ୍ପୂର୍ଣ୍ଣ ହୋଇନାହିଁ।"), Toast.LENGTH_LONG).show()
                        }
                    } catch (e: Exception) {
                        Toast.makeText(
                            context,
                            tr(language, "Google sign-in failed. Please try again.", "Google ଲଗଇନ୍ ବିଫଳ। ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।"),
                            Toast.LENGTH_LONG
                        ).show()
                    } finally {
                        signingIn = false
                    }
                }
            }
        },
        enabled = !signingIn,
        modifier = if (compact) Modifier.size(34.dp) else Modifier.fillMaxWidth().height(54.dp),
        shape = RoundedCornerShape(if (compact) 50.dp else 16.dp),
        contentPadding = if (compact) PaddingValues(0.dp) else PaddingValues(horizontal = 16.dp)
    ) {
        if (compact) {
            Text("G", fontSize = 20.sp, fontWeight = FontWeight.ExtraBold, color = Color(0xFF4285F4))
        } else {
            Text(if (signingIn) tr(language, "Signing in...", "ଲଗଇନ୍ ହେଉଛି...") else tr(language, "Continue with Google", "Google ସହିତ ଆଗକୁ ବଢ଼ନ୍ତୁ"), fontWeight = FontWeight.Bold)
        }
    }
}

@Composable
private fun CustomerHome(appliances: List<Appliance>, bookings: List<ServiceBooking>, language: AppLanguage, onChoose: (Appliance) -> Unit, onHistory: () -> Unit) {
    LazyColumn(
        modifier = Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEAF2FB), Color(0xFFF7F9FC)))).padding(horizontal = 16.dp),
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
                elevation = CardDefaults.cardElevation(defaultElevation = 4.dp),
                border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFFD9E5F2))
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
    var locationAccuracy by remember { mutableStateOf<Float?>(null) }
    var locating by remember { mutableStateOf(false) }
    var error by remember { mutableStateOf("") }
    var issueExpanded by remember { mutableStateOf(false) }
    var showSubmitConfirmation by remember { mutableStateOf(false) }
    var isSubmitting by remember { mutableStateOf(false) }
    val issues = issueOptions[appliance.name] ?: listOf("Not working", "Making noise", "Leakage", "Other")
    val context = LocalContext.current
    fun captureCurrentLocation() {
        val mainHandler = Handler(Looper.getMainLooper())
        val fusedClient = LocationServices.getFusedLocationProviderClient(context)
        val cancellation = CancellationTokenSource()
        var completed = false
        lateinit var timeout: Runnable

        fun finishWithLocation(location: Location) {
            if (completed) return
            completed = true
            mainHandler.removeCallbacks(timeout)
            latitude = location.latitude
            longitude = location.longitude
            locationAccuracy = if (location.hasAccuracy()) location.accuracy else null
            locating = false

            Thread {
                var formattedAddress = ""
                var postalCode = ""
                try {
                    val geocoder = Geocoder(context, Locale.getDefault())
                    @Suppress("DEPRECATION")
                    val result = geocoder.getFromLocation(location.latitude, location.longitude, 1)?.firstOrNull()
                    formattedAddress = result?.getAddressLine(0).orEmpty()
                    postalCode = result?.postalCode.orEmpty()
                } catch (_: Exception) { }

                mainHandler.post {
                    if (formattedAddress.isNotBlank()) {
                        address = formattedAddress
                    } else {
                        val coordinates = "GPS pin: %.6f, %.6f".format(Locale.US, location.latitude, location.longitude)
                        if (address.isBlank() || address.startsWith("GPS pin:")) address = coordinates
                    }
                    if (pincode.isBlank() && postalCode.length == 6) pincode = postalCode
                    val accuracyText = locationAccuracy?.let { " ±${it.toInt()} m" }.orEmpty()
                    val message = if (formattedAddress.isNotBlank()) {
                        tr(language, "Location captured$accuracyText. Address filled—please verify it.", "ଲୋକେସନ୍ ମିଳିଲା$accuracyText। ଠିକଣା ଭରାଗଲା—ଦୟାକରି ଯାଞ୍ଚ କରନ୍ତୁ।")
                    } else {
                        tr(language, "GPS pin captured$accuracyText, but no readable street address was returned. Coordinates were added; please add a landmark.", "GPS ପିନ୍ ମିଳିଲା$accuracyText, କିନ୍ତୁ ପଢ଼ିହେବା ଠିକଣା ମିଳିଲା ନାହିଁ। କୋଅର୍ଡିନେଟ୍ ଯୋଡାଗଲା; ଲ୍ୟାଣ୍ଡମାର୍କ ଲେଖନ୍ତୁ।")
                    }
                    Toast.makeText(context, message, Toast.LENGTH_LONG).show()
                }
            }.start()
        }

        timeout = Runnable {
            if (!completed) {
                cancellation.cancel()
                fusedClient.lastLocation
                    .addOnSuccessListener { cached ->
                        if (cached != null) finishWithLocation(cached)
                        else if (!completed) {
                            completed = true
                            locating = false
                            Toast.makeText(context, tr(language, "GPS could not get a fix. Turn on Precise location, move near a window or outdoors, or type the address manually.", "GPS ମିଳିଲା ନାହିଁ। Precise location ଚାଲୁ କରନ୍ତୁ, ଖୋଲା ସ୍ଥାନରେ ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                        }
                    }
                    .addOnFailureListener {
                        if (!completed) {
                            completed = true
                            locating = false
                            Toast.makeText(context, tr(language, "GPS timed out. Retry or enter the address manually.", "GPS ସମୟ ଶେଷ ହେଲା। ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                        }
                    }
            }
        }

        try {
            val manager = context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
            if (!manager.isProviderEnabled(LocationManager.GPS_PROVIDER) &&
                !manager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)) {
                locating = false
                Toast.makeText(context, tr(language, "Turn on Location in phone settings, then retry.", "ଫୋନ୍ ସେଟିଂରେ Location ଚାଲୁ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                return
            }

            mainHandler.postDelayed(timeout, 25000L)
            Toast.makeText(context, tr(language, "Finding your location… Keep Location on. Manual address entry is still available.", "ଲୋକେସନ୍ ଖୋଜାଯାଉଛି… Location ଚାଲୁ ରଖନ୍ତୁ। ଠିକଣା ନିଜେ ମଧ୍ୟ ଲେଖିପାରିବେ।"), Toast.LENGTH_LONG).show()

            val request = CurrentLocationRequest.Builder()
                .setPriority(Priority.PRIORITY_HIGH_ACCURACY)
                .setMaxUpdateAgeMillis(15000L)
                .setDurationMillis(20000L)
                .build()

            fusedClient.getCurrentLocation(request, cancellation.token)
                .addOnSuccessListener { location ->
                    if (location != null) {
                        finishWithLocation(location)
                    } else {
                        fusedClient.lastLocation
                            .addOnSuccessListener { cached ->
                                if (cached != null) finishWithLocation(cached)
                                else if (!completed) {
                                    mainHandler.removeCallbacks(timeout)
                                    completed = true
                                    locating = false
                                    Toast.makeText(context, tr(language, "No location fix yet. Turn on Precise location and try outdoors, or type the address manually.", "ଲୋକେସନ୍ ମିଳିଲା ନାହିଁ। Precise location ଚାଲୁ କରି ଖୋଲା ସ୍ଥାନରେ ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                                }
                            }
                            .addOnFailureListener {
                                if (!completed) {
                                    mainHandler.removeCallbacks(timeout)
                                    completed = true
                                    locating = false
                                    Toast.makeText(context, tr(language, "Location unavailable. Retry or enter the address manually.", "ଲୋକେସନ୍ ମିଳୁନାହିଁ। ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
                .addOnFailureListener {
                    if (!completed) {
                        fusedClient.lastLocation
                            .addOnSuccessListener { cached ->
                                if (cached != null) finishWithLocation(cached)
                                else if (!completed) {
                                    mainHandler.removeCallbacks(timeout)
                                    completed = true
                                    locating = false
                                    Toast.makeText(context, tr(language, "GPS failed. Retry or enter the address manually.", "GPS ବିଫଳ ହେଲା। ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ କିମ୍ବା ଠିକଣା ନିଜେ ଲେଖନ୍ତୁ।"), Toast.LENGTH_LONG).show()
                                }
                            }
                    }
                }
        } catch (_: SecurityException) {
            mainHandler.removeCallbacks(timeout)
            locating = false
            Toast.makeText(context, tr(language, "Allow location permission in app settings, then retry.", "ଆପ୍ ସେଟିଂରେ Location ଅନୁମତି ଦେଇ ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।"), Toast.LENGTH_LONG).show()
        } catch (_: Exception) {
            mainHandler.removeCallbacks(timeout)
            locating = false
            Toast.makeText(context, tr(language, "Could not start GPS. Turn on phone Location and retry.", "GPS ଆରମ୍ଭ ହୋଇପାରିଲା ନାହିଁ। ଫୋନ୍ Location ଚାଲୁ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।"), Toast.LENGTH_LONG).show()
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
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFDCEBFA), Color(0xFFF1EAFE), Color(0xFFE1F4F1)))).verticalScroll(rememberScrollState()).padding(horizontal = 16.dp, vertical = 14.dp)) {
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy), elevation = CardDefaults.cardElevation(3.dp)) {
            Row(Modifier.fillMaxWidth().padding(18.dp), verticalAlignment = Alignment.CenterVertically) {
                Surface(shape = RoundedCornerShape(16.dp), color = Color.White.copy(alpha = 0.14f)) {
                    Text(appliance.emoji, modifier = Modifier.padding(13.dp), fontSize = 30.sp)
                }
                Spacer(Modifier.width(14.dp))
                Column(Modifier.weight(1f)) {
                    Text(tr(language, "BOOK A SERVICE VISIT", "ସେବା ବୁକ୍ କରନ୍ତୁ"), color = Color(0xFFDCEAF8), fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text(applianceLabel(language, appliance.name), fontSize = 22.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                    Text(tr(language, "Repair • Installation • Servicing", "ମରାମତି • ଇନଷ୍ଟଲେସନ୍ • ସର୍ଭିସିଂ"), color = Color(0xFFE0EBF7), fontSize = 12.sp)
                }
            }
        }
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(20.dp), colors = CardDefaults.cardColors(containerColor = Color(0xFFF4F8FF)), elevation = CardDefaults.cardElevation(4.dp), border = androidx.compose.foundation.BorderStroke(1.dp, Color(0xFF8BB9E8))) {
            Column(Modifier.fillMaxWidth().padding(16.dp)) {
                Text(tr(language, "Customer details", "ଗ୍ରାହକ ବିବରଣୀ"), fontSize = 18.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(tr(language, "Tell us how to contact you.", "ଆପଣଙ୍କ ସହ ଯୋଗାଯୋଗ ପାଇଁ ବିବରଣୀ ଦିଅନ୍ତୁ।"), fontSize = 12.sp, color = Color(0xFF687789))
                Spacer(Modifier.height(12.dp))
        OutlinedTextField(name, { name = it }, label = { Text(tr(language, "Customer name *", "ଗ୍ରାହକଙ୍କ ନାମ *")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color(0xFF2676B9), unfocusedBorderColor = Color(0xFFB6CBE0)), leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Navy) })
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text(tr(language, "Mobile number *", "ମୋବାଇଲ୍ ନମ୍ବର *")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color(0xFF2676B9), unfocusedBorderColor = Color(0xFFB6CBE0)), leadingIcon = { Icon(Icons.Default.Person, contentDescription = null, tint = Navy) }, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Spacer(Modifier.height(10.dp))
        Text(tr(language, "Select the problem *", "ସମସ୍ୟା ବାଛନ୍ତୁ *"), fontWeight = FontWeight.SemiBold, color = Navy)
        Box {
            Button(onClick = { issueExpanded = true }, modifier = Modifier.fillMaxWidth()) { Text(if (issue.isBlank()) tr(language, "Choose a problem", "ସମସ୍ୟା ବାଛନ୍ତୁ") else issueLabel(language, issue)) }
            DropdownMenu(expanded = issueExpanded, onDismissRequest = { issueExpanded = false }, modifier = Modifier.heightIn(max = 340.dp)) {
                issues.forEach { option -> DropdownMenuItem(text = { Text(issueLabel(language, option)) }, leadingIcon = { Text(problemEmoji(option), fontSize = 22.sp) }, onClick = { issue = option; issueExpanded = false }) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(address, { address = it }, label = { Text(tr(language, "Full address / area *", "ପୂରା ଠିକଣା / ଅଞ୍ଚଳ *")) }, modifier = Modifier.fillMaxWidth(), minLines = 2, shape = RoundedCornerShape(14.dp), colors = OutlinedTextFieldDefaults.colors(focusedContainerColor = Color.White, unfocusedContainerColor = Color.White, focusedBorderColor = Color(0xFF2676B9), unfocusedBorderColor = Color(0xFFB6CBE0)))
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
        }, modifier = Modifier.fillMaxWidth().heightIn(min = 52.dp), enabled = !locating, shape = RoundedCornerShape(15.dp), colors = ButtonDefaults.outlinedButtonColors(contentColor = Navy)) {
            Text(when {
                locating -> tr(language, "Finding location…", "ଲୋକେସନ୍ ଖୋଜାଯାଉଛି…")
                latitude != null && longitude != null -> tr(language, "GPS location saved ✓ (tap to refresh)", "GPS ଲୋକେସନ୍ ସେଭ୍ ହେଲା ✓")
                else -> tr(language, "📍 Get address from current location (optional)", "📍 GPS ଦ୍ୱାରା ଠିକଣା ଭରନ୍ତୁ (ଇଚ୍ଛାଧୀନ)")
            })
        }
        if (latitude != null && longitude != null) {
            Text(tr(language, "Precise GPS captured • ±${locationAccuracy?.toInt() ?: 0} m. This pin will guide the technician.", "ସଠିକ୍ GPS ମିଳିଲା • ±${locationAccuracy?.toInt() ?: 0} ମିଟର୍। ଏହି ପିନ୍ ଟେକ୍ନିସିଆନ୍‌ଙ୍କୁ ରାସ୍ତା ଦେଖାଇବ।"), fontSize = 12.sp, color = Color(0xFF16804A), fontWeight = FontWeight.SemiBold)
        } else {
            Text(tr(language, "Address is required. Type it manually, or use GPS to fill it automatically. If GPS cannot find a street address, the coordinates will be saved and you can add a landmark.", "ଠିକଣା ଦେବା ଆବଶ୍ୟକ। ନିଜେ ଲେଖନ୍ତୁ କିମ୍ବା GPS ଦ୍ୱାରା ଭରନ୍ତୁ। GPS ଠିକଣା ନ ପାଇଲେ କୋଅର୍ଡିନେଟ୍ ସେଭ୍ ହେବ ଏବଂ ଲ୍ୟାଣ୍ଡମାର୍କ ଯୋଡିପାରିବେ।"), fontSize = 12.sp, color = Color(0xFF245783), fontWeight = FontWeight.SemiBold)
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
        OutlinedTextField(pincode, { pincode = it.filter(Char::isDigit).take(6) }, label = { Text(tr(language, "Area PIN code (optional)", "ପିନ୍ କୋଡ୍ (ଇଚ୍ଛାଧୀନ)")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, shape = RoundedCornerShape(14.dp), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
            }
        }
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
                TextButton(
                    enabled = !isSubmitting,
                    onClick = {
                        if (!isSubmitting) {
                            isSubmitting = true
                            showSubmitConfirmation = false
                            onSubmit(name.trim(), phone, issue.trim(), address.trim(), preferredSlot.trim(), pincode, latitude, longitude)
                        }
                    }
                ) {
                    Text(tr(language, "Confirm booking", "ବୁକିଂ ନିଶ୍ଚିତ କରନ୍ତୁ"))
                }
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
        // Appliance-specific factory colours: white AC, stainless/silver fridge and washer,
        // dark glass microwave, and white enamel geyser with hot/cold indicators.
        val appliancePalette = when (appliance) {
            "AC Service" -> listOf(Color(0xFFE3E8ED), Color(0xFFFAFCFD), Color(0xFFD0D8DF), Color(0xFFB6C1CB), Color(0xFFDCE3E9), Color(0xFFF4F7F9))
            "Refrigerator" -> listOf(Color(0xFF9EAAB5), Color(0xFFE5E9ED), Color(0xFF7E8B97), Color(0xFF687681), Color(0xFFB9C3CB), Color(0xFFF4F6F8))
            "Washing Machine" -> listOf(Color(0xFFB9C5CF), Color(0xFFF1F4F6), Color(0xFF9AA8B3), Color(0xFF6F7D88), Color(0xFFC9D2D9), Color(0xFFE8EDF1))
            "Microwave Oven" -> listOf(Color(0xFF5C6873), Color(0xFFADB7C0), Color(0xFF454F58), Color(0xFF303941), Color(0xFF727F89), Color(0xFFD3D9DE))
            else -> listOf(Color(0xFFB9C7D2), Color(0xFFF4F7F9), Color(0xFF9CAEBB), Color(0xFF748997), Color(0xFFC8D4DC), Color(0xFFE8EEF2))
        }
        val faces = listOf(
            Face(listOf(0, 1, 2, 3), appliancePalette[0]),
            Face(listOf(4, 7, 6, 5), appliancePalette[1]),
            Face(listOf(0, 4, 5, 1), appliancePalette[2]),
            Face(listOf(1, 5, 6, 2), appliancePalette[3]),
            Face(listOf(2, 6, 7, 3), appliancePalette[4]),
            Face(listOf(3, 7, 4, 0), appliancePalette[5])
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
                    // White split AC body, dark outlet, directional louvers and status LED.
                    panelRect(0.035f, 0.12f, 0.93f, 0.30f, Color(0xFFF9FBFC))
                    panelLine(0.08f, 0.18f, 0.92f, 0.18f, Color(0xFFD1D9DF), 1.0f)
                    panelRect(0.07f, 0.55f, 0.86f, 0.29f, Color(0xFFBBC6CE))
                    for (i in 0..5) panelLine(0.13f, 0.60f + i * 0.038f, 0.87f, 0.60f + i * 0.038f, Color(0xFF53616B), 1.15f)
                    drawCircle(Color(0xFF20A36A), w * 0.012f, facePoint(0.84f, 0.28f))
                }
                "Refrigerator" -> {
                    // Stainless steel finish, separate freezer/fridge doors and metal handles.
                    panelLine(0.03f, 0.43f, 0.97f, 0.43f, Color(0xFF687783), 1.7f)
                    panelRect(0.79f, 0.15f, 0.035f, 0.19f, Color(0xFF53616C))
                    panelRect(0.79f, 0.53f, 0.035f, 0.23f, Color(0xFF53616C))
                    panelLine(0.06f, 0.04f, 0.94f, 0.04f, Color(0xFFFFFFFF), 1.1f)
                    panelLine(0.10f, 0.09f, 0.10f, 0.36f, Color(0xFFF8FAFC), 1.0f)
                }
                "Washing Machine" -> {
                    // White/silver cabinet, dark control strip, realistic glass drum.
                    panelRect(0.08f, 0.08f, 0.84f, 0.16f, Color(0xFF53616C))
                    panelRect(0.12f, 0.11f, 0.30f, 0.08f, Color(0xFFDEE5EA))
                    drawCircle(Color(0xFF20A36A), w * 0.008f, facePoint(0.83f, 0.16f))
                    val center = facePoint(0.5f, 0.62f)
                    val radius = w * 0.072f
                    drawCircle(Color(0xFF303A42), radius * 1.16f, center)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFEAF5F8), Color(0xFF7D9EAC), Color(0xFF263E4A)), radius = radius, center = center), radius, center)
                    drawCircle(Color(0xFFDCE5E9), radius * 0.72f, center, style = Stroke(width = 1.7f))
                    drawArc(Color(0xFFB9D4DF), 210f, 115f, false, Offset(center.x - radius * 0.62f, center.y - radius * 0.62f), Size(radius * 1.24f, radius * 1.24f), style = Stroke(width = 1.3f))
                }
                "Microwave Oven" -> {
                    // Dark tinted glass door, metallic border and tactile control buttons.
                    panelRect(0.07f, 0.17f, 0.64f, 0.64f, Color(0xFF242C33))
                    panelRect(0.105f, 0.205f, 0.57f, 0.57f, Color(0xFF42535F))
                    panelLine(0.14f, 0.24f, 0.59f, 0.24f, Color(0xFF9FB5C2), 1.1f)
                    panelRect(0.76f, 0.24f, 0.12f, 0.035f, Color(0xFFD8DEE3))
                    for (i in 0..2) drawCircle(Color(0xFF252E35), w * 0.009f, facePoint(0.83f, 0.38f + i * 0.16f))
                }
                else -> {
                    // White enamel geyser, thermostat dial and red/blue hot-cold indicators.
                    panelRect(0.15f, 0.25f, 0.70f, 0.045f, Color(0xFF73818A))
                    val control = facePoint(0.5f, 0.18f)
                    drawCircle(Brush.radialGradient(listOf(Color(0xFFFFFFFF), Color(0xFF9CAEB8), Color(0xFF42535E)), radius = w * 0.027f, center = control), w * 0.027f, control)
                    panelRect(0.18f, 0.36f, 0.64f, 0.47f, Color(0xFFE3EBF0))
                    panelLine(0.23f, 0.42f, 0.77f, 0.42f, Color(0xFFBAC8D0), 1.3f)
                    drawCircle(Color(0xFFE34B4B), w * 0.009f, facePoint(0.34f, 0.49f))
                    drawCircle(Color(0xFF3186D8), w * 0.009f, facePoint(0.66f, 0.49f))
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
private fun BookingConfirmation(
    booking: ServiceBooking?,
    language: AppLanguage,
    onViewBookings: () -> Unit,
    onHome: () -> Unit
) {
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp),
        horizontalAlignment = Alignment.CenterHorizontally,
        verticalArrangement = Arrangement.Center
    ) {
        Surface(color = Color(0xFFE3F5E9), shape = RoundedCornerShape(50)) {
            Box(Modifier.size(82.dp), contentAlignment = Alignment.Center) {
                Icon(Icons.Default.CheckCircle, contentDescription = null, tint = Color(0xFF21834A), modifier = Modifier.size(54.dp))
            }
        }
        Spacer(Modifier.height(18.dp))
        Text(tr(language, "Service Booking Received!", "ସେବା ବୁକିଂ ଗ୍ରହଣ କରାଗଲା!"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Spacer(Modifier.height(8.dp))
        Text(tr(language, "Keep your booking ID for future reference.", "ପରେ ଦରକାର ପାଇଁ ବୁକିଂ ଆଇଡି ରଖନ୍ତୁ।"), color = Color.Gray)
        Spacer(Modifier.height(22.dp))
        if (booking != null) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 3.dp)) {
                Column(Modifier.fillMaxWidth().padding(20.dp)) {
                    Text(tr(language, "BOOKING ID", "ବୁକିଂ ଆଇଡି"), color = Color.Gray, fontSize = 12.sp, fontWeight = FontWeight.Bold)
                    Text("#${booking.id}", fontSize = 32.sp, fontWeight = FontWeight.ExtraBold, color = Orange)
                    HorizontalDivider(Modifier.padding(vertical = 12.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Current status", "ବର୍ତ୍ତମାନ ସ୍ଥିତି"), Modifier.weight(1f), color = Color.Gray)
                        Text(statusLabel(language, booking.status), fontWeight = FontWeight.Bold, color = Navy)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Appliance", "ଉପକରଣ"), Modifier.weight(1f), color = Color.Gray)
                        Text(booking.appliance, fontWeight = FontWeight.SemiBold, color = Navy)
                    }
                    Spacer(Modifier.height(10.dp))
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Text(tr(language, "Requested on", "ଅନୁରୋଧ ତାରିଖ"), Modifier.weight(1f), color = Color.Gray)
                        Text(SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(booking.createdAt)), fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Navy)
                    }
                    Spacer(Modifier.height(10.dp))
                    Text(tr(language, "Your request is saved on this device. Our team will update the status when it is processed.", "ଆପଣଙ୍କ ଅନୁରୋଧ ଏହି ଡିଭାଇସରେ ସେଭ୍ ହୋଇଛି। ଟିମ୍ ପ୍ରକ୍ରିୟା କଲେ ସ୍ଥିତି ଅପଡେଟ୍ ହେବ।"), fontSize = 12.sp, color = Color.Gray)
                }
            }
        } else {
            Text(tr(language, "Booking details are unavailable. Open My Bookings to check your requests.", "ବୁକିଂ ବିବରଣୀ ମିଳୁନାହିଁ। ଆପଣଙ୍କ ଅନୁରୋଧ ଦେଖିବାକୁ ମୋ ବୁକିଂ ଖୋଲନ୍ତୁ।"), color = Color.Gray)
        }
        Spacer(Modifier.height(20.dp))
        Button(onClick = onViewBookings, modifier = Modifier.fillMaxWidth().height(52.dp)) {
            Text(tr(language, "View My Bookings", "ମୋ ବୁକିଂ ଦେଖନ୍ତୁ"))
        }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onHome, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(tr(language, "Book Another Service", "ଅନ୍ୟ ସେବା ବୁକ୍ କରନ୍ତୁ"))
        }
    }
}

@Composable
private fun BookingHistory(bookings: List<ServiceBooking>, language: AppLanguage, onCancel: (Int) -> Unit, onReview: (Int) -> Unit) {
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
                                    Text("${tr(language, "Customer: ", "ଗ୍ରାହକ: ")}${b.customer}", fontSize = 12.sp, fontWeight = FontWeight.SemiBold, color = Color(0xFF40576F))
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
                            if (b.status == "Completed") {
                                Spacer(Modifier.height(8.dp))
                                if (b.rating > 0) {
                                    Text("★".repeat(b.rating) + "☆".repeat(5 - b.rating) + "  ${tr(language, "Your rating", "ଆପଣଙ୍କ ରେଟିଂ")}", color = Orange, fontWeight = FontWeight.Bold)
                                    if (b.review.isNotBlank()) Text(b.review, color = Color(0xFF40576F), fontSize = 13.sp)
                                    OutlinedButton(onClick = { onReview(b.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr(language, "Edit Review", "ରିଭ୍ୟୁ ବଦଳାନ୍ତୁ")) }
                                } else {
                                    Button(onClick = { onReview(b.id) }, modifier = Modifier.fillMaxWidth()) { Text(tr(language, "★ Rate & Review Service", "★ ସେବାକୁ ରେଟ୍ ଓ ରିଭ୍ୟୁ କରନ୍ତୁ")) }
                                }
                            }
                            Text(
                                tr(language, "Status last updated: ", "ସ୍ଥିତି ଶେଷଥର ବଦଳିଥିଲା: ") +
                                    SimpleDateFormat("dd MMM yyyy, hh:mm a", Locale.getDefault()).format(Date(b.statusUpdatedAt)),
                                fontSize = 11.sp,
                                color = Color.Gray
                            )
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
                TextButton(
                    onClick = {
                        val booking = bookings.firstOrNull { it.id == bookingId }
                        if (booking?.status == "Pending") onCancel(bookingId)
                        pendingCancelId = null
                    }
                ) {
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
private fun ServiceReviewScreen(
    booking: ServiceBooking,
    language: AppLanguage,
    onSubmit: (Int, String) -> Unit,
    onPlayStore: () -> Unit
) {
    var rating by remember(booking.id) { mutableIntStateOf(booking.rating.coerceIn(0, 5)) }
    var review by remember(booking.id) { mutableStateOf(booking.review) }
    Column(
        Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp),
        horizontalAlignment = Alignment.CenterHorizontally
    ) {
        Spacer(Modifier.height(18.dp))
        Text("★", fontSize = 54.sp, color = Orange)
        Text(tr(language, "How was your service?", "ଆପଣଙ୍କ ସେବା ଅନୁଭୂତି କେମିତି ଥିଲା?"), fontSize = 23.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Spacer(Modifier.height(6.dp))
        Text("#${booking.id} • ${applianceLabel(language, booking.appliance)}", color = Color.Gray)
        Spacer(Modifier.height(18.dp))
        Row(horizontalArrangement = Arrangement.Center, verticalAlignment = Alignment.CenterVertically) {
            (1..5).forEach { star ->
                TextButton(onClick = { rating = star }, contentPadding = PaddingValues(4.dp)) {
                    Text(if (star <= rating) "★" else "☆", fontSize = 38.sp, color = if (star <= rating) Orange else Color.LightGray)
                }
            }
        }
        Text(
            when (rating) {
                1 -> tr(language, "Poor", "ଖରାପ")
                2 -> tr(language, "Could be better", "ଆହୁରି ଭଲ ହୋଇପାରିଥାନ୍ତା")
                3 -> tr(language, "Good", "ଭଲ")
                4 -> tr(language, "Very good", "ବହୁତ ଭଲ")
                5 -> tr(language, "Excellent!", "ଅତ୍ୟନ୍ତ ଭଲ!")
                else -> tr(language, "Tap a star to rate", "ରେଟିଂ ପାଇଁ ଷ୍ଟାର୍ ଚୟନ କରନ୍ତୁ")
            },
            color = Navy,
            fontWeight = FontWeight.SemiBold
        )
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(
            value = review,
            onValueChange = { if (it.length <= 500) review = it },
            modifier = Modifier.fillMaxWidth().heightIn(min = 120.dp),
            label = { Text(tr(language, "Write your feedback (optional)", "ଆପଣଙ୍କ ମତାମତ ଲେଖନ୍ତୁ (ଇଚ୍ଛାଧୀନ)")) },
            placeholder = { Text(tr(language, "Tell us what went well or what we can improve…", "କେଉଁଟି ଭଲ ହେଲା କିମ୍ବା କଣ ସୁଧାରିବା ଉଚିତ ଲେଖନ୍ତୁ…")) }
        )
        Text("${review.length}/500", modifier = Modifier.align(Alignment.End), fontSize = 11.sp, color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        Button(
            onClick = { if (rating in 1..5) onSubmit(rating, review) },
            enabled = rating in 1..5,
            modifier = Modifier.fillMaxWidth().height(52.dp)
        ) { Text(tr(language, "Submit Feedback", "ମତାମତ ଦାଖଲ କରନ୍ତୁ")) }
        Spacer(Modifier.height(8.dp))
        OutlinedButton(onClick = onPlayStore, modifier = Modifier.fillMaxWidth().height(50.dp)) {
            Text(tr(language, "Rate us on Google Play", "Google Play ରେ ରେଟ୍ କରନ୍ତୁ"))
        }
        Spacer(Modifier.height(8.dp))
        Text(
            tr(language, "Your in-app feedback is saved on this device. Google Play rating opens the app's Play Store listing when it is published.", "ଆପ୍‌ରେ ଦିଆଯାଇଥିବା ମତାମତ ଏହି ଡିଭାଇସ୍‌ରେ ସେଭ୍ ହୁଏ। ଆପ୍ ପ୍ରକାଶିତ ହେଲେ Google Play ରେଟିଂ ତାହାର ଲିଷ୍ଟିଂ ଖୋଲିବ।"),
            fontSize = 12.sp, color = Color.Gray
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
private fun TeamLogin(language: AppLanguage, onAdminSuccess: () -> Unit, onMechanicSuccess: (String) -> Unit) {
    var role by remember { mutableStateOf("Admin") }
    var roleMenu by remember { mutableStateOf(false) }
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    var signingIn by remember { mutableStateOf(false) }
    val scope = rememberCoroutineScope()
    val context = LocalContext.current
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text(tr(language, "Secure Team Access", "ସୁରକ୍ଷିତ ଟିମ୍ ପ୍ରବେଶ"), fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text(tr(language, "Use the team email and password provisioned by the app administrator.", "ଆପ୍ ଆଡମିନ୍ ଦେଇଥିବା ଟିମ୍ ଇମେଲ୍ ଓ ପାସୱାର୍ଡ ବ୍ୟବହାର କରନ୍ତୁ।"), color = Color.Gray)
        Spacer(Modifier.height(18.dp))
        Box {
            OutlinedButton(onClick = { roleMenu = true }, modifier = Modifier.fillMaxWidth()) { Text(tr(language, "Login role: ", "ଲଗଇନ୍ ଭୂମିକା: ") + role) }
            DropdownMenu(expanded = roleMenu, onDismissRequest = { roleMenu = false }) {
                listOf("Admin", "Mechanic").forEach { item -> DropdownMenuItem(text = { Text(item) }, onClick = { role = item; error = ""; roleMenu = false }) }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(username, { username = it; error = "" }, label = { Text(tr(language, "Team email", "ଟିମ୍ ଇମେଲ୍")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Email))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it; error = "" }, label = { Text(tr(language, "Password", "ପାସୱାର୍ଡ")) }, modifier = Modifier.fillMaxWidth(), singleLine = true, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Password))
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(enabled = !signingIn && username.isNotBlank() && password.isNotBlank(), onClick = {
            signingIn = true
            error = ""
            scope.launch {
                try {
                    val result = FirebaseAuth.getInstance().signInWithEmailAndPassword(username.trim(), password).await()
                    val user = result.user ?: throw IllegalStateException("Team account was not returned.")
                    val profile = FirebaseFirestore.getInstance().collection("users").document(user.uid).get().await()
                    val expectedRole = if (role == "Admin") "admin" else "mechanic"
                    if (!profile.exists() || profile.getString("role") != expectedRole) {
                        FirebaseAuth.getInstance().signOut()
                        throw IllegalStateException(tr(language, "This account is not provisioned for the selected team role.", "ଏହି ଆକାଉଣ୍ଟ ଚୟନିତ ଟିମ୍ ଭୂମିକା ପାଇଁ ସେଟ୍ ହୋଇନାହିଁ।"))
                    }
                    if (expectedRole == "admin") onAdminSuccess()
                    else onMechanicSuccess(profile.getString("displayName")?.takeIf { it.isNotBlank() } ?: user.email?.substringBefore("@") ?: user.uid)
                } catch (e: Exception) {
                    error = e.message ?: tr(language, "Sign-in failed. Check your details and try again.", "ଲଗଇନ୍ ବିଫଳ। ବିବରଣୀ ଯାଞ୍ଚ କରି ପୁଣି ଚେଷ୍ଟା କରନ୍ତୁ।")
                } finally { signingIn = false }
            }
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) {
            Text(if (signingIn) tr(language, "Signing in...", "ଲଗଇନ୍ ହେଉଛି...") else tr(language, "Sign in securely", "ସୁରକ୍ଷିତ ଲଗଇନ୍"))
        }
    }
}
@Composable
private fun MechanicHome(
    bookings: List<ServiceBooking>,
    mechanicName: String,
    language: AppLanguage,
    onLogout: () -> Unit,
    onStatus: (Int, String) -> Unit
) {
    val context = LocalContext.current
    val activeCount = bookings.count { it.status in listOf("Accepted", "On the Way", "In Progress") }
    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEAF2FB), Color(0xFFF7F9FC)))).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(12.dp))
        Row(verticalAlignment = Alignment.CenterVertically) {
            Column(Modifier.weight(1f)) {
                Text(tr(language, "Namaskar,", "ନମସ୍କାର,") + " $mechanicName", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
                Text(tr(language, "Your assigned service jobs", "ଆପଣଙ୍କୁ ଦିଆଯାଇଥିବା ସେବା କାମ"), color = Color.Gray)
            }
            TextButton(onClick = onLogout) { Text(tr(language, "Logout", "ଲଗଆଉଟ୍")) }
        }
        Spacer(Modifier.height(12.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(tr(language, "Assigned", "ଦିଆଯାଇଛି"), bookings.size.toString(), Modifier.weight(1f))
            Stat(tr(language, "Active", "ଚାଲୁ"), activeCount.toString(), Modifier.weight(1f))
            Stat(tr(language, "Completed", "ସମ୍ପୂର୍ଣ୍ଣ"), bookings.count { it.status == "Completed" }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(12.dp))
        if (bookings.isEmpty()) {
            Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.fillMaxWidth().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally) {
                    Icon(Icons.Default.Build, contentDescription = null, tint = Navy, modifier = Modifier.size(38.dp))
                    Spacer(Modifier.height(8.dp))
                    Text(tr(language, "No bookings assigned yet", "ଏପର୍ଯ୍ୟନ୍ତ କୌଣସି ବୁକିଂ ଦିଆଯାଇନାହିଁ"), fontWeight = FontWeight.Bold, color = Navy)
                }
            }
        } else {
            LazyColumn(verticalArrangement = Arrangement.spacedBy(10.dp), contentPadding = PaddingValues(bottom = 20.dp)) {
                items(bookings.sortedByDescending { it.createdAt }, key = { it.id }) { booking ->
                    Card(Modifier.fillMaxWidth(), colors = CardDefaults.cardColors(containerColor = Color.White), elevation = CardDefaults.cardElevation(defaultElevation = 2.dp)) {
                        Column(Modifier.fillMaxWidth().padding(14.dp)) {
                            Row(verticalAlignment = Alignment.CenterVertically) {
                                Text("#${booking.id} • ${booking.appliance}", Modifier.weight(1f), fontWeight = FontWeight.Bold, color = Navy)
                                Text(statusLabel(language, booking.status), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Orange)
                            }
                            Spacer(Modifier.height(6.dp))
                            Text(booking.customer, fontWeight = FontWeight.SemiBold)
                            Text(booking.issue, color = Color.Gray)
                            Text(booking.phone)
                            Text(booking.address, color = Color.Gray)
                            if (booking.preferredSlot.isNotBlank()) Text(tr(language, "Preferred visit: ", "ପସନ୍ଦର ସମୟ: ") + booking.preferredSlot, color = Color.Gray)
                            if (booking.pincode.isNotBlank()) Text(tr(language, "PIN: ", "ପିନ୍: ") + booking.pincode, color = Color.Gray)
                            if (booking.latitude != null && booking.longitude != null) {
                                TextButton(onClick = {
                                    val uri = android.net.Uri.parse("google.navigation:q=${booking.latitude},${booking.longitude}")
                                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_VIEW, uri).apply { setPackage("com.google.android.apps.maps") }) }
                                        .onFailure { Toast.makeText(context, tr(language, "Maps app unavailable.", "ମ୍ୟାପ୍ସ ଆପ୍ ଉପଲବ୍ଧ ନାହିଁ।"), Toast.LENGTH_SHORT).show() }
                                }) { Text(tr(language, "Navigate to customer", "ଗ୍ରାହକଙ୍କ ପାଖକୁ ଯାଆନ୍ତୁ")) }
                            }
                            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                OutlinedButton(onClick = {
                                    runCatching { context.startActivity(android.content.Intent(android.content.Intent.ACTION_DIAL, android.net.Uri.parse("tel:${booking.phone}"))) }
                                }, modifier = Modifier.weight(1f)) { Text(tr(language, "Call customer", "ଗ୍ରାହକଙ୍କୁ କଲ୍")) }
                            }
                            val nextStatuses = when (booking.status) {
                                "Pending" -> listOf("Accepted", "Rejected")
                                "Accepted" -> listOf("On the Way")
                                "On the Way" -> listOf("In Progress")
                                "In Progress" -> listOf("Completed")
                                else -> emptyList()
                            }
                            if (nextStatuses.isNotEmpty()) {
                                Spacer(Modifier.height(8.dp))
                                nextStatuses.forEach { next ->
                                    Button(onClick = { onStatus(booking.id, next) }, modifier = Modifier.fillMaxWidth()) {
                                        Text(tr(language, "Mark as ", "ସ୍ଥିତି କରନ୍ତୁ: ") + statusLabel(language, next))
                                    }
                                }
                            }
                            Text(tr(language, "Status last updated: ", "ସ୍ଥିତି ଶେଷ ଅପଡେଟ୍: ") + SimpleDateFormat("dd MMM, hh:mm a", Locale.getDefault()).format(Date(booking.statusUpdatedAt)), fontSize = 11.sp, color = Color.Gray)
                        }
                    }
                }
            }
        }
    }
}

@Composable
private fun AdminHome(bookings: List<ServiceBooking>, language: AppLanguage, onStatus: (Int, String) -> Unit, onAssign: (Int, String) -> Unit = { _, _ -> }) {
    val context = LocalContext.current
    var pendingStatusChange by remember { mutableStateOf<Pair<Int, String>?>(null) }
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var selectedMechanicFilter by remember { mutableStateOf("All mechanics") }
    var mechanicFilterExpanded by remember { mutableStateOf(false) }
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
    val mechanicFilters = listOf("All mechanics", "Unassigned", "Mechanic 1", "Mechanic 2", "Senior Technician")
    val visibleBookings = bookings
        .filter { selectedMechanicFilter == "All mechanics" || (if (selectedMechanicFilter == "Unassigned") it.assignedMechanic.isBlank() else it.assignedMechanic == selectedMechanicFilter) }
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

    Column(Modifier.fillMaxSize().background(Brush.verticalGradient(listOf(Color(0xFFEAF2FB), Color(0xFFF7F9FC)))).padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Card(Modifier.fillMaxWidth(), shape = RoundedCornerShape(22.dp), colors = CardDefaults.cardColors(containerColor = Navy), elevation = CardDefaults.cardElevation(3.dp)) {
            Column(Modifier.fillMaxWidth().background(Brush.linearGradient(listOf(Color(0xFF123B67), Color(0xFF245783)))).padding(18.dp)) {
                Text(tr(language, "OWNER DASHBOARD", "ମାଲିକ ଡ୍ୟାସବୋର୍ଡ"), fontSize = 11.sp, fontWeight = FontWeight.Bold, color = Color(0xFFFFB86B))
                Spacer(Modifier.height(4.dp))
                Text(tr(language, "Namaskar, Owner 👋", "ନମସ୍କାର, ମାଲିକ 👋"), fontSize = 24.sp, fontWeight = FontWeight.ExtraBold, color = Color.White)
                Text(tr(language, "Manage service requests, assign technicians and track every job.", "ସେବା ଅନୁରୋଧ ପରିଚାଳନା, ଟେକ୍ନିସିଆନ୍ ନିଯୁକ୍ତି ଏବଂ କାମର ସ୍ଥିତି ଦେଖନ୍ତୁ।"), color = Color(0xFFE0EBF7), fontSize = 12.sp, lineHeight = 18.sp)
            }
        }
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat(tr(language, "New", "ନୂଆ"), bookings.count { it.status == "Pending" }.toString(), Modifier.weight(1f))
            Stat(tr(language, "Active", "ଚାଲୁ"), bookings.count { it.status in listOf("Accepted", "On the Way", "In Progress") }.toString(), Modifier.weight(1f))
            Stat(tr(language, "Done", "ସମ୍ପୂର୍ଣ୍ଣ"), bookings.count { it.status == "Completed" }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(10.dp))
        Box {
            OutlinedButton(onClick = { mechanicFilterExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                val mechanicFilterLabel = when (selectedMechanicFilter) {
                    "All mechanics" -> tr(language, "All mechanics", "ସମସ୍ତ ମେକାନିକ୍")
                    "Unassigned" -> tr(language, "Unassigned", "ନିଯୁକ୍ତ ହୋଇନାହାନ୍ତି")
                    else -> selectedMechanicFilter
                }
                Text(tr(language, "Mechanic view: ", "ମେକାନିକ୍ ଭ୍ୟୁ: ") + mechanicFilterLabel)
            }
            DropdownMenu(expanded = mechanicFilterExpanded, onDismissRequest = { mechanicFilterExpanded = false }) {
                mechanicFilters.forEach { mechanic ->
                    DropdownMenuItem(
                        text = { Text(when (mechanic) {
                            "All mechanics" -> tr(language, "All mechanics", "ସମସ୍ତ ମେକାନିକ୍")
                            "Unassigned" -> tr(language, "Unassigned", "ନିଯୁକ୍ତ ହୋଇନାହାନ୍ତି")
                            else -> mechanic
                        }) },
                        onClick = { selectedMechanicFilter = mechanic; mechanicFilterExpanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(10.dp))
        OutlinedButton(
            onClick = {
                fun csvCell(value: String): String = "\"" + value.replace("\"", "\"\"").replace("\n", " ").replace("\r", " ") + "\""
                val rows = mutableListOf(
                    listOf("Booking ID", "Created At", "Customer", "Phone", "Appliance", "Issue", "Status", "Assigned Mechanic", "Status Updated At", "Address", "Preferred Visit", "PIN Code", "GPS Latitude", "GPS Longitude").joinToString(",")
                )
                visibleBookings.sortedByDescending { it.createdAt }.forEach { booking ->
                    rows += listOf(
                        booking.id.toString(),
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(booking.createdAt)),
                        booking.customer, booking.phone, booking.appliance, booking.issue, booking.status,
                        booking.assignedMechanic.ifBlank { "Unassigned" },
                        SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault()).format(Date(booking.statusUpdatedAt)),
                        booking.address, booking.preferredSlot, booking.pincode,
                        booking.latitude?.toString().orEmpty(), booking.longitude?.toString().orEmpty()
                    ).joinToString(",") { csvCell(it) }
                }
                reportText = rows.joinToString("\n")
                exportLauncher.launch("ShreeJagannath-Bookings.csv")
            },
            modifier = Modifier.fillMaxWidth()
        ) { Text(tr(language, "Export Filtered Bookings as CSV", "ଫିଲ୍ଟର୍ ହୋଇଥିବା ବୁକିଂ CSV କରନ୍ତୁ")) }
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
                            var assignmentExpanded by remember(b.id) { mutableStateOf(false) }
                            val mechanicOptions = listOf("Unassigned", "Mechanic 1", "Mechanic 2", "Senior Technician")
                            Box(Modifier.fillMaxWidth()) {
                                OutlinedButton(onClick = { assignmentExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                                    Text(tr(language, "Assigned mechanic: ", "ନିଯୁକ୍ତ ମେକାନିକ୍: ") + if (b.assignedMechanic.isBlank()) tr(language, "Unassigned", "ନିଯୁକ୍ତ ହୋଇନାହାନ୍ତି") else b.assignedMechanic)
                                }
                                DropdownMenu(expanded = assignmentExpanded, onDismissRequest = { assignmentExpanded = false }) {
                                    mechanicOptions.forEach { mechanic ->
                                        DropdownMenuItem(
                                            text = { Text(if (mechanic == "Unassigned") tr(language, "Unassigned", "ନିଯୁକ୍ତ ହୋଇନାହାନ୍ତି") else mechanic) },
                                            onClick = {
                                                onAssign(b.id, if (mechanic == "Unassigned") "" else mechanic)
                                                assignmentExpanded = false
                                            }
                                        )
                                    }
                                }
                            }
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
