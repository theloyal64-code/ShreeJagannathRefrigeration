package com.shreejagannath.refrigeration

import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.FirebaseUser
import com.google.firebase.Timestamp
import com.google.firebase.firestore.FieldValue
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.ListenerRegistration
import com.google.firebase.firestore.Query
import kotlinx.coroutines.tasks.await

/**
 * Firestore persistence layer. The UI must never treat SharedPreferences as the source
 * of truth for cross-device bookings; Firestore rules enforce ownership and staff roles.
 */
internal class BookingCloudStore(
    private val db: FirebaseFirestore = FirebaseFirestore.getInstance()
) {
    suspend fun ensureCustomerProfile(user: FirebaseUser) {
        val ref = db.collection("users").document(user.uid)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            if (!snapshot.exists()) {
                transaction.set(ref, mapOf(
                    "uid" to user.uid,
                    "role" to "customer",
                    "displayName" to (user.displayName ?: ""),
                    "email" to (user.email ?: ""),
                    "photoURL" to (user.photoUrl?.toString() ?: ""),
                    "phone" to (user.phoneNumber ?: ""),
                    "createdAt" to FieldValue.serverTimestamp(),
                    "updatedAt" to FieldValue.serverTimestamp()
                ))
            } else {
                val existingRole = snapshot.getString("role")
                require(existingRole in listOf("customer", "admin", "mechanic")) {
                    "Your account profile is invalid. Contact the app administrator."
                }
                if (existingRole == "customer") {
                    transaction.update(ref, mapOf(
                        "displayName" to (user.displayName ?: ""),
                        "email" to (user.email ?: ""),
                        "photoURL" to (user.photoUrl?.toString() ?: ""),
                        "updatedAt" to FieldValue.serverTimestamp()
                    ))
                }
            }
        }.await()
    }

    fun observeBookings(
        uid: String,
        role: String,
        onBookings: (List<Map<String, Any?>>) -> Unit,
        onError: (Exception) -> Unit
    ): ListenerRegistration {
        val query: Query = when (role) {
            "admin" -> db.collection("bookings").orderBy("createdAt", Query.Direction.DESCENDING)
            "mechanic" -> db.collection("bookings")
                .whereEqualTo("assignedMechanicUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
            else -> db.collection("bookings")
                .whereEqualTo("customerUid", uid)
                .orderBy("createdAt", Query.Direction.DESCENDING)
        }
        return query.addSnapshotListener { snapshot, error ->
            if (error != null) {
                onError(error)
                return@addSnapshotListener
            }
            if (snapshot == null) return@addSnapshotListener
            onBookings(snapshot.documents.map { document ->
                document.data.orEmpty() + ("cloudId" to document.id)
            })
        }
    }

    suspend fun createBooking(uid: String, values: Map<String, Any?>): String {
        require(uid.isNotBlank() && FirebaseAuth.getInstance().currentUser?.uid == uid) {
            "Your sign-in session changed. Please sign in again before booking."
        }
        val customerName = (values["customerName"] as? String)?.trim().orEmpty()
        val phone = (values["phone"] as? String)?.trim().orEmpty()
        val issue = (values["issue"] as? String)?.trim().orEmpty()
        val address = (values["address"] as? String)?.trim().orEmpty()
        val preferredSlot = (values["preferredSlot"] as? String)?.trim()
        val pincode = (values["pincode"] as? String)?.trim()
        val latitude = values["latitude"]
        val longitude = values["longitude"]

        require(values["appliance"] is String && (values["appliance"] as String) in listOf(
            "AC Service", "Refrigerator", "Washing Machine", "Microwave Oven", "Geyser"
        )) { "Select a valid appliance." }
        require(customerName.isNotEmpty() && customerName.length <= 100) {
            "Enter a customer name with at most 100 characters."
        }
        require(phone.length in 7..20) { "Enter a valid contact number." }
        require(issue.isNotEmpty() && issue.length <= 200) { "Select a valid problem." }
        require(address.isNotEmpty() && address.length <= 1000) { "Enter a service address with at most 1000 characters." }
        require(preferredSlot != null && preferredSlot.length <= 100 && pincode != null) {
            "Booking details are incomplete. Please review the form."
        }
        require(pincode.isEmpty() || (pincode.length == 6 && pincode.all(Char::isDigit))) {
            "Enter a valid six-digit PIN code or leave it blank."
        }
        require(latitude == null || (latitude is Number && latitude.toDouble().isFinite() && latitude.toDouble() in -90.0..90.0)) {
            "The GPS latitude is invalid. Refresh the location or enter the address manually."
        }
        require(longitude == null || (longitude is Number && longitude.toDouble().isFinite() && longitude.toDouble() in -180.0..180.0)) {
            "The GPS longitude is invalid. Refresh the location or enter the address manually."
        }
        val ref = db.collection("bookings").document()
        val data = values.toMutableMap().apply {
            put("customerName", customerName)
            put("phone", phone)
            put("issue", issue)
            put("address", address)
            put("preferredSlot", preferredSlot)
            put("pincode", pincode)
            put("displayId", (ref.id.hashCode() and 0x7fffffff))
            put("customerUid", uid)
            put("status", "Pending")
            put("assignedMechanic", "")
            put("assignedMechanicUid", "")
            put("rating", 0)
            put("review", "")
            put("createdAt", FieldValue.serverTimestamp())
            put("statusUpdatedAt", FieldValue.serverTimestamp())
        }
        val historyRef = ref.collection("statusHistory").document()
        val batch = db.batch()
        batch.set(ref, data)
        batch.set(historyRef, mapOf(
            "changedBy" to uid,
            "oldStatus" to "",
            "newStatus" to "Pending",
            "createdAt" to FieldValue.serverTimestamp()
        ))
        batch.commit().await()
        return ref.id
    }

    private fun requireActiveUser(uid: String) {
        require(uid.isNotBlank() && FirebaseAuth.getInstance().currentUser?.uid == uid) {
            "Your sign-in session changed. Please sign in again and retry."
        }
    }

    suspend fun cancelPendingBooking(cloudId: String, uid: String) {
        requireActiveUser(uid)
        val ref = db.collection("bookings").document(cloudId)
        val historyRef = ref.collection("statusHistory").document()
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "Booking not found." }
            require(snapshot.getString("customerUid") == uid) { "You cannot cancel another customer's booking." }
            val oldStatus = snapshot.getString("status") ?: "Pending"
            require(oldStatus == "Pending") { "Only pending bookings can be cancelled." }
            transaction.update(ref, mapOf(
                "status" to "Cancelled",
                "statusUpdatedAt" to FieldValue.serverTimestamp()
            ))
            transaction.set(historyRef, mapOf(
                "changedBy" to uid,
                "oldStatus" to oldStatus,
                "newStatus" to "Cancelled",
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun updateStatus(cloudId: String, uid: String, role: String, nextStatus: String) {
        requireActiveUser(uid)
        require(role in listOf("admin", "mechanic")) { "Your account does not have staff access." }
        val ref = db.collection("bookings").document(cloudId)
        val historyRef = ref.collection("statusHistory").document()
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "Booking not found." }
            val oldStatus = snapshot.getString("status") ?: "Pending"
            require(role == "admin" || (role == "mechanic" && snapshot.getString("assignedMechanicUid") == uid)) {
                "This booking is not assigned to your account."
            }
            require(canTransitionBookingStatus(oldStatus, nextStatus)) {
                "This status change is not allowed."
            }
            transaction.update(ref, mapOf(
                "status" to nextStatus,
                "statusUpdatedAt" to FieldValue.serverTimestamp()
            ))
            transaction.set(historyRef, mapOf(
                "changedBy" to uid,
                "oldStatus" to oldStatus,
                "newStatus" to nextStatus,
                "createdAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun submitReview(cloudId: String, uid: String, rating: Int, review: String) {
        requireActiveUser(uid)
        val normalizedReview = review.trim()
        require(rating in 1..5) { "Rating must be between 1 and 5." }
        require(normalizedReview.length <= 500) { "Review must be 500 characters or fewer." }
        val ref = db.collection("bookings").document(cloudId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "Booking not found." }
            require(snapshot.getString("customerUid") == uid) { "You cannot review another customer's booking." }
            require(snapshot.getString("status") == "Completed") { "Only completed bookings can be reviewed." }
            transaction.update(ref, mapOf(
                "rating" to rating,
                "review" to normalizedReview,
                "reviewedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
    }

    suspend fun listMechanics(): List<Map<String, String>> {
        return db.collection("users").whereEqualTo("role", "mechanic").get().await().documents.map { doc ->
            mapOf(
                "uid" to doc.id,
                "displayName" to (doc.getString("displayName") ?: doc.getString("email")?.substringBefore("@") ?: doc.id)
            )
        }
    }
    suspend fun assignMechanic(cloudId: String, mechanicUid: String) {
        val bookingRef = db.collection("bookings").document(cloudId)
        db.runTransaction { transaction ->
            val booking = transaction.get(bookingRef)
            require(booking.exists()) { "Booking not found." }
            require(booking.getString("status") in listOf("Pending", "Accepted")) {
                "A mechanic can only be assigned while a booking is Pending or Accepted."
            }

            val mechanicName = if (mechanicUid.isBlank()) {
                ""
            } else {
                val profileRef = db.collection("users").document(mechanicUid)
                val profile = transaction.get(profileRef)
                require(profile.exists() && profile.getString("role") == "mechanic") {
                    "The selected account is not an active mechanic profile."
                }
                profile.getString("displayName")?.takeIf { it.isNotBlank() }
                    ?: profile.getString("email")?.substringBefore("@")
                    ?: mechanicUid
            }

            transaction.update(bookingRef, mapOf(
                "assignedMechanicUid" to mechanicUid,
                "assignedMechanic" to mechanicName
            ))
        }.await()
    }
}


internal fun firestoreBookingToModel(data: Map<String, Any?>): ServiceBooking {
    fun string(key: String) = data[key] as? String ?: ""
    fun long(key: String, fallback: Long = 0L): Long = when (val value = data[key]) {
        is Timestamp -> value.toDate().time
        is Number -> value.toLong()
        else -> fallback
    }
    val cloudId = string("cloudId")
    val created = long("createdAt", System.currentTimeMillis()).takeIf { it > 0L } ?: System.currentTimeMillis()
    return ServiceBooking(
        id = (data["displayId"] as? Number)?.toInt() ?: (cloudId.hashCode() and 0x7fffffff),
        customer = string("customerName"), phone = string("phone"), appliance = string("appliance"),
        issue = string("issue"), address = string("address"), preferredSlot = string("preferredSlot"),
        pincode = string("pincode"), status = string("status").ifBlank { "Pending" }, createdAt = created,
        latitude = (data["latitude"] as? Number)?.toDouble(), longitude = (data["longitude"] as? Number)?.toDouble(),
        statusUpdatedAt = long("statusUpdatedAt", created).takeIf { it > 0L } ?: created,
        assignedMechanic = string("assignedMechanic"), rating = (data["rating"] as? Number)?.toInt() ?: 0,
        review = string("review"), reviewedAt = long("reviewedAt"), cloudId = cloudId,
        customerUid = string("customerUid"), assignedMechanicUid = string("assignedMechanicUid")
    )
}
