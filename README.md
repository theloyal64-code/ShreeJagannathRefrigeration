# Shree Jagannath Refrigeration

Android service-booking app for AC, refrigerator, washing machine, microwave oven and geyser repair. Customer-facing language options are English and Odia.

## Current application architecture

- Android UI: Kotlin + Jetpack Compose.
- Customer authentication: Firebase Authentication with Google sign-in.
- Shared booking database: Cloud Firestore (the Android app does not use the legacy PostgreSQL API).
- Firestore collections: `users`, `bookings`, and each booking's `statusHistory` subcollection.
- Security: `firestore.rules` enforces customer ownership, role-based staff access, allowed status transitions, and review limits.
- Query indexes: `firestore.indexes.json`.
- GPS: precise location capture with readable-address geocoding where available and coordinate fallback.
- Build: GitHub Actions builds a debug APK on pushes and pull requests.

## Booking database behaviour

- New bookings are written to Firestore, not only to device storage.
- Customer history is queried by the signed-in customer's Firebase UID.
- Admin accounts can see all bookings; mechanics can see only bookings assigned to their Firebase UID.
- Status updates and assignments are written to Firestore so other authorized devices can see the changes.
- Customers can cancel only pending bookings and review only completed bookings.
- The app keeps a local cache for a smoother UI, but Firestore is the source of truth for authenticated bookings.

## Required one-time Firebase setup

The Firebase project ID configured for this Android app is `shree-jagannath-refrigeration`. Before real use:

1. In Firebase Console, create/enable the **Cloud Firestore database** in the appropriate region.
2. In Authentication > Sign-in method, enable **Google** and **Email/Password**. Google is for customers; Email/Password is for team accounts.
3. Deploy `firestore.rules` and `firestore.indexes.json` from the repository using Firebase CLI. See [BACKEND_SETUP.md](BACKEND_SETUP.md).
4. Provision the owner/admin and each mechanic as Firebase Authentication email/password users. In Firestore, create `users/{AUTH_UID}` documents with `uid`, `role` (`admin` or `mechanic`), `displayName`, `email`, and `phone`. Never let the client choose its own staff role.
5. For assignment to appear in the Admin Dashboard, set each mechanic's `displayName` to the desired display label.
6. Test with two separate devices/accounts: customer creates booking, admin sees it, admin assigns a mechanic, mechanic sees it and changes status, and customer sees the status update.

**Important:** Firestore security rules and the Firebase Console database cannot be activated from a GitHub code change alone. The one-time Firebase Console setup and rules deployment are required. Do not use real customer data until these steps and cross-account tests pass.

## Legacy PostgreSQL folder

The `backend/` Node.js/PostgreSQL API and `render.yaml` are legacy scaffolding and are **not used by the current Android booking flow**. Do not deploy them for this Firestore-first setup.

## Build

Push to `main` or open a pull request to run the Android debug build workflow. Download the `ShreeJagannathRefrigeration-debug-apk` artifact from a successful GitHub Actions run.

Package: `com.shreejagannath.refrigeration`
