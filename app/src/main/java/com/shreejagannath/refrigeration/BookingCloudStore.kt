package com.shreejagannath.refrigeration

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
        require(values["appliance"] in listOf(
            "AC Service", "Refrigerator", "Washing Machine", "Microwave Oven", "Geyser"
        )) { "Select a valid appliance." }
        require((values["issue"] as? String)?.isNotBlank() == true) { "Select an issue." }
        require((values["address"] as? String)?.isNotBlank() == true) { "Enter your service address." }
        val ref = db.collection("bookings").document()
        val data = values.toMutableMap().apply {
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
        ref.set(data).await()
        ref.collection("statusHistory").add(mapOf(
                "changedBy" to uid,
                "oldStatus" to "",
                "newStatus" to "Pending",
                "createdAt" to FieldValue.serverTimestamp()
            )).await()
        return ref.id
    }

    suspend fun cancelPendingBooking(cloudId: String, uid: String) {
        val ref = db.collection("bookings").document(cloudId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "Booking not found." }
            require(snapshot.getString("customerUid") == uid) { "You cannot cancel another customer's booking." }
            require(snapshot.getString("status") == "Pending") { "Only pending bookings can be cancelled." }
            transaction.update(ref, mapOf(
                "status" to "Cancelled",
                "statusUpdatedAt" to FieldValue.serverTimestamp()
            ))
        }.await()
        ref.collection("statusHistory").add(mapOf(
            "changedBy" to uid,
            "oldStatus" to "Pending",
            "newStatus" to "Cancelled",
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun updateStatus(cloudId: String, uid: String, role: String, nextStatus: String) {
        val ref = db.collection("bookings").document(cloudId)
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
        }.await()
        ref.collection("statusHistory").add(mapOf(
            "changedBy" to uid,
            "newStatus" to nextStatus,
            "createdAt" to FieldValue.serverTimestamp()
        )).await()
    }

    suspend fun submitReview(cloudId: String, uid: String, rating: Int, review: String) {
        require(rating in 1..5) { "Rating must be between 1 and 5." }
        require(review.length <= 500) { "Review must be 500 characters or fewer." }
        val ref = db.collection("bookings").document(cloudId)
        db.runTransaction { transaction ->
            val snapshot = transaction.get(ref)
            require(snapshot.exists()) { "Booking not found." }
            require(snapshot.getString("customerUid") == uid) { "You cannot review another customer's booking." }
            require(snapshot.getString("status") == "Completed") { "Only completed bookings can be reviewed." }
            transaction.update(ref, mapOf(
                "rating" to rating,
                "review" to review.trim(),
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
    suspend fun assignMechanic(cloudId: String, mechanicUid: String, mechanicName: String) {
        db.collection("bookings").document(cloudId).update(mapOf(
            "assignedMechanicUid" to mechanicUid,
            "assignedMechanic" to mechanicName
        )).await()
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
