import { readFileSync } from "node:fs";
import { after, before, beforeEach, test } from "node:test";
import assert from "node:assert/strict";
import {
  initializeTestEnvironment,
  assertFails,
  assertSucceeds
} from "@firebase/rules-unit-testing";
import {
  collection,
  doc,
  deleteDoc,
  getDoc,
  serverTimestamp,
  setDoc,
  updateDoc,
  writeBatch,
  Timestamp
} from "firebase/firestore";

const projectId = "demo-shree-jagannath-refrigeration";
let testEnv;

before(async () => {
  testEnv = await initializeTestEnvironment({
    projectId,
    firestore: { rules: readFileSync("firestore.rules", "utf8") }
  });
});

after(async () => {
  await testEnv?.cleanup();
});

beforeEach(async () => {
  await testEnv.clearFirestore();
});

function profile(uid, role = "customer") {
  return {
    uid,
    role,
    displayName: uid,
    email: `${uid}@example.test`,
    photoURL: "",
    phone: "",
    createdAt: Timestamp.now(),
    updatedAt: Timestamp.now()
  };
}

function booking(customerUid, overrides = {}) {
  const now = Timestamp.now();
  return {
    customerUid,
    displayId: 123456,
    customerName: "Test Customer",
    phone: "9876543210",
    appliance: "AC Service",
    issue: "Not cooling",
    address: "Puri, Odisha",
    preferredSlot: "Any time",
    pincode: "752001",
    latitude: null,
    longitude: null,
    status: "Pending",
    assignedMechanic: "",
    assignedMechanicUid: "",
    rating: 0,
    review: "",
    createdAt: now,
    statusUpdatedAt: now,
    ...overrides
  };
}

async function seed(docs) {
  await testEnv.withSecurityRulesDisabled(async (context) => {
    const db = context.firestore();
    for (const [path, data] of docs) {
      await setDoc(doc(db, path), data);
    }
  });
}

test("customer cannot create a profile with an elevated staff role", async () => {
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(setDoc(doc(db, "users/customer-1"), {
    uid: "customer-1",
    role: "admin",
    displayName: "Escalated",
    email: "customer-1@example.test",
    photoURL: "",
    phone: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});

test("customer can atomically create a booking and its initial history event", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1"),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
  assert.equal((await getDoc(bookingRef)).data().status, "Pending");
  assert.equal((await getDoc(historyRef)).data().newStatus, "Pending");
});

test("customer cannot read another customer's booking", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["users/customer-2", profile("customer-2")],
    ["bookings/booking-2", booking("customer-2")]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(getDoc(doc(db, "bookings/booking-2")));
});

test("customer cannot forge an initial Pending history event after booking creation", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(setDoc(doc(db, "bookings/booking-1/statusHistory/fake"), {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  }));
});

test("admin can assign a real mechanic profile but not an arbitrary UID", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  const ref = doc(db, "bookings/booking-1");
  await assertSucceeds(updateDoc(ref, {
    assignedMechanicUid: "mechanic-1",
    assignedMechanic: "mechanic-1"
  }));
  await assertFails(updateDoc(ref, {
    assignedMechanicUid: "not-a-mechanic",
    assignedMechanic: "Unknown"
  }));
});

test("admin status change and matching history event must be atomic", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  const bookingRef = doc(db, "bookings/booking-1");
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.update(bookingRef, {
    status: "Accepted",
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "admin-1",
    oldStatus: "Pending",
    newStatus: "Accepted",
    createdAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
});

test("staff cannot append a fake history event without a matching state transition", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["bookings/booking-1", booking("customer-1", { status: "Accepted" })]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  await assertFails(setDoc(doc(db, "bookings/booking-1/statusHistory/fake"), {
    changedBy: "admin-1",
    oldStatus: "Pending",
    newStatus: "Accepted",
    createdAt: serverTimestamp()
  }));
});

test("only the assigned mechanic can read a mechanic booking", async () => {
  await seed([
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["users/mechanic-2", profile("mechanic-2", "mechanic")],
    ["bookings/booking-1", booking("customer-1", {
      assignedMechanic: "mechanic-1",
      assignedMechanicUid: "mechanic-1"
    })]
  ]);
  const assignedDb = testEnv.authenticatedContext("mechanic-1").firestore();
  const otherDb = testEnv.authenticatedContext("mechanic-2").firestore();
  await assertSucceeds(getDoc(doc(assignedDb, "bookings/booking-1")));
  await assertFails(getDoc(doc(otherDb, "bookings/booking-1")));
});

test("customer cancellation updates booking and audit event atomically", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(db, "bookings/booking-1");
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.update(bookingRef, {
    status: "Cancelled",
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "Pending",
    newStatus: "Cancelled",
    createdAt: serverTimestamp()
  });
  await assertSucceeds(batch.commit());
});

test("customer can edit their review on a completed booking", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["bookings/booking-1", booking("customer-1", { status: "Completed" })]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const ref = doc(db, "bookings/booking-1");
  await assertSucceeds(updateDoc(ref, {
    rating: 5,
    review: "Excellent service",
    reviewedAt: serverTimestamp()
  }));
  await assertSucceeds(updateDoc(ref, {
    rating: 4,
    review: "Updated after the service",
    reviewedAt: serverTimestamp()
  }));
});

test("admin cannot delete a booking and erase its service record", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  await assertFails(deleteDoc(doc(db, "bookings/booking-1")));
});

test("customer cannot create a booking with out-of-range GPS coordinates", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1", { latitude: 91, longitude: 181 }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("customer cannot inject unknown fields into a booking", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1"),
    unexpectedPrivilegedField: true,
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("customer cannot change booking status or assign a mechanic directly", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const ref = doc(db, "bookings/booking-1");

  await assertFails(updateDoc(ref, {
    status: "Accepted",
    statusUpdatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ref, {
    assignedMechanic: "mechanic-1",
    assignedMechanicUid: "mechanic-1"
  }));
});

test("customer cannot create a booking with only one GPS coordinate", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1", { latitude: 20.0, longitude: null }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("customer cannot create a booking with a malformed PIN code", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1", { pincode: "123" }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("customer cannot create a booking with letters in the PIN code", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const bookingRef = doc(collection(db, "bookings"));
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.set(bookingRef, {
    ...booking("customer-1", { pincode: "ABC123" }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "customer-1",
    oldStatus: "",
    newStatus: "Pending",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("admin cannot skip required booking status transitions", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["bookings/booking-1", booking("customer-1", { status: "Pending" })]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  const bookingRef = doc(db, "bookings/booking-1");
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.update(bookingRef, {
    status: "Completed",
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "admin-1",
    oldStatus: "Pending",
    newStatus: "Completed",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("unassigned mechanic cannot change a booking status", async () => {
  await seed([
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["bookings/booking-1", booking("customer-1", {
      status: "Accepted",
      assignedMechanicUid: "mechanic-2",
      assignedMechanic: "mechanic-2"
    })]
  ]);
  const db = testEnv.authenticatedContext("mechanic-1").firestore();
  const bookingRef = doc(db, "bookings/booking-1");
  const historyRef = doc(collection(bookingRef, "statusHistory"));
  const batch = writeBatch(db);
  batch.update(bookingRef, {
    status: "On the Way",
    statusUpdatedAt: serverTimestamp()
  });
  batch.set(historyRef, {
    changedBy: "mechanic-1",
    oldStatus: "Accepted",
    newStatus: "On the Way",
    createdAt: serverTimestamp()
  });
  await assertFails(batch.commit());
});

test("customer cannot read another customer's booking history", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["users/customer-2", profile("customer-2")],
    ["bookings/booking-2", booking("customer-2")],
    ["bookings/booking-2/statusHistory/event-1", {
      changedBy: "customer-2",
      oldStatus: "",
      newStatus: "Pending",
      createdAt: Timestamp.now()
    }]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(getDoc(doc(db, "bookings/booking-2/statusHistory/event-1")));
});

test("mechanic cannot assign themselves to a booking", async () => {
  await seed([
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("mechanic-1").firestore();
  await assertFails(updateDoc(doc(db, "bookings/booking-1"), {
    assignedMechanicUid: "mechanic-1",
    assignedMechanic: "mechanic-1"
  }));
});

test("admin cannot silently edit customer booking details while changing status", async () => {
  await seed([
    ["users/admin-1", profile("admin-1", "admin")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("admin-1").firestore();
  await assertFails(updateDoc(doc(db, "bookings/booking-1"), {
    status: "Accepted",
    statusUpdatedAt: serverTimestamp(),
    phone: "9123456789"
  }));
});

test("customer cannot promote their existing profile to admin", async () => {
  await seed([["users/customer-1", profile("customer-1", "customer")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(updateDoc(doc(db, "users/customer-1"), {
    role: "admin",
    updatedAt: serverTimestamp()
  }));
});

test("assigned mechanic cannot edit customer booking details while changing status", async () => {
  await seed([
    ["users/mechanic-1", profile("mechanic-1", "mechanic")],
    ["bookings/booking-1", booking("customer-1", {
      status: "Accepted",
      assignedMechanicUid: "mechanic-1",
      assignedMechanic: "mechanic-1"
    })]
  ]);
  const db = testEnv.authenticatedContext("mechanic-1").firestore();
  await assertFails(updateDoc(doc(db, "bookings/booking-1"), {
    status: "On the Way",
    statusUpdatedAt: serverTimestamp(),
    address: "Different address"
  }));
});

test("customer cannot change booking details while cancelling a pending booking", async () => {
  await seed([
    ["users/customer-1", profile("customer-1")],
    ["bookings/booking-1", booking("customer-1")]
  ]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  await assertFails(updateDoc(doc(db, "bookings/booking-1"), {
    status: "Cancelled",
    statusUpdatedAt: serverTimestamp(),
    phone: "9123456789"
  }));
});

test("customer profile creation requires all expected typed fields and server timestamps", async () => {
  const db = testEnv.authenticatedContext("customer-new").firestore();
  await assertFails(setDoc(doc(db, "users/customer-new"), {
    uid: "customer-new",
    role: "customer"
  }));
  await assertSucceeds(setDoc(doc(db, "users/customer-new"), {
    uid: "customer-new",
    role: "customer",
    displayName: "New Customer",
    email: "customer-new@example.test",
    photoURL: "",
    phone: "",
    createdAt: serverTimestamp(),
    updatedAt: serverTimestamp()
  }));
});


test("customer profile metadata updates require typed values and a server timestamp", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const ref = doc(db, "users/customer-1");
  await assertFails(updateDoc(ref, {
    displayName: { unexpected: "object" },
    updatedAt: serverTimestamp()
  }));
  await assertFails(updateDoc(ref, {
    displayName: "Changed Name",
    updatedAt: Timestamp.fromDate(new Date(Date.now() - 60_000))
  }));
  await assertSucceeds(updateDoc(ref, {
    displayName: "Changed Name",
    updatedAt: serverTimestamp()
  }));
});


test("customer cannot create a booking with a non-Indian or malformed phone number", async () => {
  await seed([["users/customer-1", profile("customer-1")]]);
  const db = testEnv.authenticatedContext("customer-1").firestore();
  const ref = doc(collection(db, "bookings"));
  await assertFails(setDoc(ref, {
    ...booking("customer-1", { phone: "call-me-123" }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  }));
  await assertFails(setDoc(doc(collection(db, "bookings")), {
    ...booking("customer-1", { phone: "1234567890" }),
    createdAt: serverTimestamp(),
    statusUpdatedAt: serverTimestamp()
  }));
});
