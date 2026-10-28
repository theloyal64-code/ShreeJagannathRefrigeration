# Firebase / Firestore Setup

The Android app uses Firebase Authentication and Cloud Firestore for shared booking data. The PostgreSQL API in `backend/` is legacy scaffolding and is not part of the app's current database path.

## 1. Enable Firebase services

Firebase project: `shree-jagannath-refrigeration`

In Firebase Console:

1. Open **Build → Authentication → Sign-in method**.
2. Enable **Google** for customer sign-in.
3. Enable **Email/Password** for owner/admin and mechanic accounts.
4. Open **Build → Firestore Database** and create the database in the region appropriate for the service business. Start in production mode; do not use open test rules.

## 2. Deploy database rules and indexes

Install Firebase CLI on a trusted computer, authenticate with a project owner account, then run from the repository root:

```bash
firebase login
firebase use shree-jagannath-refrigeration
firebase deploy --only firestore:rules,firestore:indexes
```

The deployment files are:

- `firestore.rules` — customer ownership, staff roles, allowed updates, status transition validation, and review rules.
- `firestore.indexes.json` — composite indexes for customer booking history and mechanic job lists.
- `firebase.json` — tells Firebase CLI where those files are.

Do not replace the rules with public read/write rules.

## 3. Firestore data model

### `users/{firebaseAuthUid}`

Customer profile is created by the app after Google sign-in. Fields include:

- `uid`: Firebase Authentication UID
- `role`: `customer`, `admin`, or `mechanic`
- `displayName`, `email`, `photoURL`, `phone`
- `createdAt`, `updatedAt`

Customer clients may create only their own `customer` profile. Staff roles must be created by a trusted project owner in Firebase Console.

### `bookings/{autoDocumentId}`

Fields include:

- `displayId`: short display number (not the security identifier)
- `customerUid`: owner UID from Firebase Authentication
- `customerName`, `phone`
- `appliance`: AC Service, Refrigerator, Washing Machine, Microwave Oven, or Geyser
- `issue`, `address`, `preferredSlot`, `pincode`
- `latitude`, `longitude` (optional if GPS was not available)
- `status`: Pending, Accepted, On the Way, In Progress, Completed, Rejected, or Cancelled
- `assignedMechanic`, `assignedMechanicUid`
- `rating`, `review`, `reviewedAt`
- `createdAt`, `statusUpdatedAt`

Each booking has a `statusHistory/{eventId}` subcollection for status events.

## 4. Provision the owner and mechanics

For each staff member:

1. Create an email/password account in Firebase Authentication.
2. Copy that account's exact Authentication UID.
3. In Firestore, create `users/{UID}` with fields:
   - `uid`: exact UID
   - `role`: `admin` for the owner or `mechanic` for a technician
   - `displayName`: staff display name (for mechanics, use the desired assignment label)
   - `email`: account email
   - `phone`: staff phone number
4. Do not place staff passwords in Firestore, source code, or the APK.

The app checks the Firestore role after Firebase email/password sign-in. A user who signs in as Admin must have `role: "admin"`; a Mechanic must have `role: "mechanic"`.

## 5. Acceptance tests before real customer use

- Google sign-in creates a customer profile under the correct UID.
- Customer A cannot read or edit Customer B's bookings.
- Customer booking is visible to the admin on another device.
- Admin can assign a booking to an existing mechanic profile.
- Only the assigned mechanic can view/update that mechanic's job.
- Status transitions outside the permitted workflow are rejected.
- Customer can cancel only a Pending booking.
- Customer can review only a Completed booking, with a rating from 1 to 5 and review text no longer than 500 characters.
- Unauthorized access to staff roles and unrelated bookings is denied.

## 6. Operational note

The GitHub repository contains the Firebase deployment configuration, but creating the Cloud Firestore database and enabling Authentication providers are project-console operations. They must be completed by someone with access to the Firebase project before the app can use the cloud database.
