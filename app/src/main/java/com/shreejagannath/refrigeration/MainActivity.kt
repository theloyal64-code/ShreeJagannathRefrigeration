package com.shreejagannath.refrigeration

import android.content.Context
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.compose.foundation.background
import androidx.compose.foundation.clickable
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.ArrowBack
import androidx.compose.material.icons.filled.HomeRepairService
import androidx.compose.material.icons.filled.Person
import androidx.compose.material.icons.filled.Settings
import androidx.compose.material.icons.filled.Search
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import org.json.JSONArray
import org.json.JSONObject

private val Navy = Color(0xFF123B67)
private val Orange = Color(0xFFF28C28)
private val Page = Color(0xFFF5F7FA)
private const val BOOKING_PREFS = "shree_jagannath_bookings"
private const val BOOKINGS_KEY = "bookings"

data class Appliance(val name: String, val detail: String, val emoji: String)
data class ServiceBooking(val id: Int, val customer: String, val phone: String, val appliance: String, val issue: String, val address: String, val preferredSlot: String = "", val pincode: String = "", val status: String = "Pending")

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
                status = item.optString("status", "Pending")
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
    var chosen by remember { mutableStateOf(appliances[0]) }
    var bookings by remember { mutableStateOf(loadBookings(context)) }
    var nextId by remember { mutableIntStateOf((bookings.maxOfOrNull { it.id } ?: 1000) + 1) }

    LaunchedEffect(bookings) {
        saveBookings(context, bookings)
    }

    MaterialTheme(colorScheme = lightColorScheme(primary = Navy, secondary = Orange, background = Page)) {
        Scaffold(topBar = {
            if (screen != "welcome") TopAppBar(
                title = { Text(when (screen) {
                    "customer" -> "Book a Service"
                    "form" -> "Service Request"
                    "history" -> "My Bookings"
                    "adminLogin" -> "Admin Login"
                    "admin" -> "Mechanic Dashboard"
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
                    "welcome" -> Welcome(
                        onCustomer = { screen = "customer" },
                        onAdmin = { screen = "adminLogin" }
                    )
                    "customer" -> CustomerHome(appliances, bookings,
                        onChoose = { chosen = it; screen = "form" },
                        onHistory = { screen = "history" })
                    "form" -> RequestForm(chosen, onSubmit = { name, phone, issue, address, preferredSlot, pincode ->
                        bookings = bookings + ServiceBooking(nextId++, name, phone, chosen.name, issue, address, preferredSlot, pincode)
                        screen = "history"
                    })
                    "history" -> BookingHistory(bookings)
                    "adminLogin" -> AdminLogin(onSuccess = { screen = "admin" })
                    "admin" -> AdminHome(bookings, onStatus = { id, status ->
                        bookings = bookings.map { if (it.id == id) it.copy(status = status) else it }
                    })
                }
            }
        }
    }
}

@Composable
private fun Welcome(onCustomer: () -> Unit, onAdmin: () -> Unit) {
    Column(Modifier.fillMaxSize().padding(24.dp), horizontalAlignment = Alignment.CenterHorizontally, verticalArrangement = Arrangement.Center) {
        Box(Modifier.size(96.dp).background(Navy, RoundedCornerShape(26.dp)), contentAlignment = Alignment.Center) {
            Icon(Icons.Default.HomeRepairService, null, tint = Color.White, modifier = Modifier.size(52.dp))
        }
        Spacer(Modifier.height(20.dp))
        Text("SHREE JAGANNATH", fontSize = 25.sp, fontWeight = FontWeight.ExtraBold, color = Navy)
        Text("REFRIGERATION", fontSize = 22.sp, fontWeight = FontWeight.Bold, color = Orange)
        Spacer(Modifier.height(8.dp))
        Text("Trusted Home Appliance Service", color = Color.DarkGray)
        Spacer(Modifier.height(32.dp))
        Button(onCustomer, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Person, null); Spacer(Modifier.width(8.dp)); Text("Customer — Book a Service")
        }
        Spacer(Modifier.height(12.dp))
        OutlinedButton(onAdmin, Modifier.fillMaxWidth().height(54.dp), shape = RoundedCornerShape(14.dp)) {
            Icon(Icons.Default.Settings, null); Spacer(Modifier.width(8.dp)); Text("Admin / Mechanic Login")
        }
        Spacer(Modifier.height(18.dp))
        Text("AC • Fridge • Washing Machine • Microwave • Geyser", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun CustomerHome(appliances: List<Appliance>, bookings: List<ServiceBooking>, onChoose: (Appliance) -> Unit, onHistory: () -> Unit) {
    LazyColumn(Modifier.fillMaxSize().padding(horizontal = 18.dp)) {
        item {
            Spacer(Modifier.height(16.dp))
            Text("Namaskar! 🙏", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
            Text("What appliance needs service today?", color = Color.DarkGray)
            Spacer(Modifier.height(18.dp))
            Card(colors = CardDefaults.cardColors(containerColor = Navy), shape = RoundedCornerShape(18.dp)) {
                Column(Modifier.padding(18.dp)) {
                    Text("Reliable service at your doorstep", color = Color.White, fontWeight = FontWeight.Bold, fontSize = 18.sp)
                    Spacer(Modifier.height(6.dp))
                    Text("Choose an appliance and send a service request.", color = Color.White)
                }
            }
            Spacer(Modifier.height(20.dp))
            Text("Our Services", fontSize = 20.sp, fontWeight = FontWeight.Bold, color = Navy)
            Spacer(Modifier.height(10.dp))
        }
        items(appliances) { appliance ->
            Card(Modifier.fillMaxWidth().padding(bottom = 10.dp).clickable { onChoose(appliance) }, shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Row(Modifier.padding(16.dp), verticalAlignment = Alignment.CenterVertically) {
                    Text(appliance.emoji, fontSize = 30.sp)
                    Spacer(Modifier.width(12.dp))
                    Column(Modifier.weight(1f)) {
                        Text(appliance.name, fontWeight = FontWeight.Bold, fontSize = 17.sp)
                        Text(appliance.detail, fontSize = 12.sp, color = Color.Gray)
                    }
                    Button(onClick = { onChoose(appliance) }) { Text("Book") }
                }
            }
        }
        item {
            OutlinedButton(onHistory, Modifier.fillMaxWidth()) { Text("My Bookings (${bookings.size})") }
            Spacer(Modifier.height(20.dp))
        }
    }
}

@Composable
private fun RequestForm(appliance: Appliance, onSubmit: (String, String, String, String, String, String) -> Unit) {
    var name by remember { mutableStateOf("") }
    var phone by remember { mutableStateOf("") }
    var issue by remember { mutableStateOf("") }
    var address by remember { mutableStateOf("") }
    var preferredSlot by remember { mutableStateOf("") }
    var pincode by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().verticalScroll(rememberScrollState()).padding(20.dp)) {
        Text("${appliance.emoji} ${appliance.name}", fontSize = 23.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Tell us a few details so the mechanic can review your request.", color = Color.Gray)
        Spacer(Modifier.height(16.dp))
        OutlinedTextField(name, { name = it }, label = { Text("Customer name *") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(phone, { phone = it.filter(Char::isDigit).take(10) }, label = { Text("Mobile number *") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Phone))
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(issue, { issue = it }, label = { Text("Describe the problem *") }, modifier = Modifier.fillMaxWidth(), minLines = 3)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(address, { address = it }, label = { Text("Full address / area *") }, modifier = Modifier.fillMaxWidth(), minLines = 2)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(preferredSlot, { preferredSlot = it }, label = { Text("Preferred visit date & time (optional)") }, placeholder = { Text("e.g. Tomorrow, 10 AM–12 PM") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(pincode, { pincode = it.filter(Char::isDigit).take(6) }, label = { Text("Area PIN code (optional)") }, modifier = Modifier.fillMaxWidth(), singleLine = true, keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Number))
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            error = when {
                name.isBlank() -> "Please enter your name."
                phone.length != 10 -> "Enter a valid 10-digit mobile number."
                issue.isBlank() -> "Please describe the problem."
                address.isBlank() -> "Please enter your address."
                else -> ""
            }
            if (error.isBlank()) onSubmit(name.trim(), phone, issue.trim(), address.trim(), preferredSlot.trim(), pincode)
        }, modifier = Modifier.fillMaxWidth().height(52.dp), shape = RoundedCornerShape(14.dp)) { Text("Submit Service Request") }
        Spacer(Modifier.height(10.dp))
        Text("No payment is collected in-app. The team will confirm your visit time and charges.", fontSize = 12.sp, color = Color.Gray)
    }
}

@Composable
private fun BookingHistory(bookings: List<ServiceBooking>) {
    if (bookings.isEmpty()) Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No bookings yet.") }
    else LazyColumn(Modifier.fillMaxSize().padding(16.dp)) {
        items(bookings.reversed()) { b ->
            Card(Modifier.fillMaxWidth().padding(bottom = 12.dp), shape = RoundedCornerShape(16.dp), colors = CardDefaults.cardColors(containerColor = Color.White)) {
                Column(Modifier.padding(16.dp)) {
                    Row(verticalAlignment = Alignment.CenterVertically) {
                        Column(Modifier.weight(1f)) {
                            Text("#${b.id} • ${b.appliance}", fontWeight = FontWeight.Bold, color = Navy)
                            Text(b.issue)
                        }
                        Status(b.status)
                    }
                    Spacer(Modifier.height(8.dp))
                    Text("Address: ${b.address}", fontSize = 13.sp)
                    if (b.preferredSlot.isNotBlank()) Text("Preferred visit: ${b.preferredSlot}", fontSize = 13.sp, color = Navy)
                    if (b.pincode.isNotBlank()) Text("PIN code: ${b.pincode}", fontSize = 13.sp)
                }
            }
        }
    }
}

@Composable
private fun AdminLogin(onSuccess: () -> Unit) {
    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var error by remember { mutableStateOf("") }
    Column(Modifier.fillMaxSize().padding(24.dp), verticalArrangement = Arrangement.Center) {
        Text("Admin Access", fontSize = 26.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Demo-only login. Secure backend authentication is required before release.")
        Spacer(Modifier.height(18.dp))
        OutlinedTextField(username, { username = it }, label = { Text("Admin username") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        Spacer(Modifier.height(10.dp))
        OutlinedTextField(password, { password = it }, label = { Text("Password") }, modifier = Modifier.fillMaxWidth(), singleLine = true)
        if (error.isNotBlank()) { Spacer(Modifier.height(8.dp)); Text(error, color = MaterialTheme.colorScheme.error) }
        Spacer(Modifier.height(16.dp))
        Button(onClick = {
            if (username == "admin" && password == "demo1234") onSuccess()
            else error = "Demo login: admin / demo1234"
        }, modifier = Modifier.fillMaxWidth().height(52.dp)) { Text("Login") }
    }
}

@Composable
private fun AdminHome(bookings: List<ServiceBooking>, onStatus: (Int, String) -> Unit) {
    var search by remember { mutableStateOf("") }
    var selectedFilter by remember { mutableStateOf("All") }
    var filterExpanded by remember { mutableStateOf(false) }
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
                it.address.contains(query, ignoreCase = true) ||
                it.pincode.contains(query, ignoreCase = true)
        }
        .sortedByDescending { it.id }

    Column(Modifier.fillMaxSize().padding(horizontal = 16.dp)) {
        Spacer(Modifier.height(14.dp))
        Text("Namaskar, Mechanic 👋", fontSize = 24.sp, fontWeight = FontWeight.Bold, color = Navy)
        Text("Review requests, find customers and update service status.", color = Color.Gray)
        Spacer(Modifier.height(14.dp))
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            Stat("New", bookings.count { it.status == "Pending" }.toString(), Modifier.weight(1f))
            Stat("Active", bookings.count { it.status in listOf("Accepted", "On the Way", "In Progress") }.toString(), Modifier.weight(1f))
            Stat("Done", bookings.count { it.status == "Completed" }.toString(), Modifier.weight(1f))
        }
        Spacer(Modifier.height(14.dp))
        OutlinedTextField(
            value = search,
            onValueChange = { search = it },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true,
            label = { Text("Search ID, customer, phone, appliance, area or PIN") },
            leadingIcon = { Icon(Icons.Default.Search, contentDescription = "Search bookings") }
        )
        Spacer(Modifier.height(8.dp))
        Box {
            OutlinedButton(onClick = { filterExpanded = true }, modifier = Modifier.fillMaxWidth()) {
                Text("Status filter: $selectedFilter")
            }
            DropdownMenu(expanded = filterExpanded, onDismissRequest = { filterExpanded = false }) {
                filters.forEach { filter ->
                    DropdownMenuItem(
                        text = { Text(filter) },
                        onClick = { selectedFilter = filter; filterExpanded = false }
                    )
                }
            }
        }
        Spacer(Modifier.height(8.dp))
        Text("Showing ${visibleBookings.size} of ${bookings.size} requests", fontSize = 12.sp, color = Color.Gray)
        Spacer(Modifier.height(8.dp))
        if (bookings.isEmpty()) {
            Box(Modifier.fillMaxSize(), contentAlignment = Alignment.Center) { Text("No service requests yet.") }
        } else if (visibleBookings.isEmpty()) {
            Box(Modifier.weight(1f).fillMaxWidth(), contentAlignment = Alignment.Center) {
                Text("No matching requests. Try another search or status filter.", color = Color.Gray)
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
                                    Text("Booking #${b.id} • ${b.appliance}", fontWeight = FontWeight.Bold, color = Navy)
                                    Text(b.customer)
                                }
                                Status(b.status)
                            }
                            Spacer(Modifier.height(8.dp))
                            Text(b.issue)
                            Text("Phone: ${b.phone}", fontSize = 13.sp, color = Color.Gray)
                            Text("Address: ${b.address}", fontSize = 13.sp, color = Color.Gray)
                            if (b.preferredSlot.isNotBlank()) Text("Preferred visit: ${b.preferredSlot}", fontSize = 13.sp, color = Navy)
                            if (b.pincode.isNotBlank()) Text("PIN code: ${b.pincode}", fontSize = 13.sp, color = Navy)
                            Spacer(Modifier.height(10.dp))
                            if (b.status == "Pending") {
                                Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                                    Button(onClick = { onStatus(b.id, "Accepted") }, modifier = Modifier.weight(1f)) { Text("Accept") }
                                    OutlinedButton(onClick = { onStatus(b.id, "Rejected") }, modifier = Modifier.weight(1f)) { Text("Reject") }
                                }
                            } else if (b.status !in listOf("Completed", "Rejected", "Cancelled")) {
                                var expanded by remember(b.id) { mutableStateOf(false) }
                                Box {
                                    OutlinedButton(onClick = { expanded = true }, modifier = Modifier.fillMaxWidth()) { Text("Update Status") }
                                    DropdownMenu(expanded = expanded, onDismissRequest = { expanded = false }) {
                                        listOf("On the Way", "In Progress", "Completed", "Cancelled").forEach { status ->
                                            DropdownMenuItem(
                                                text = { Text(status) },
                                                onClick = { onStatus(b.id, status); expanded = false }
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

@Composable
private fun Status(status: String) {
    val color = when (status) {
        "Completed" -> Color(0xFF16804A)
        "Rejected", "Cancelled" -> Color(0xFFB3261E)
        "Pending" -> Orange
        else -> Navy
    }
    Surface(color = color.copy(alpha = 0.12f), shape = RoundedCornerShape(50)) {
        Text(status, color = color, modifier = Modifier.padding(horizontal = 9.dp, vertical = 6.dp), fontSize = 11.sp, fontWeight = FontWeight.SemiBold)
    }
}
