# Cloud backend rollout plan

The Android app currently stores bookings on the device. Do not use it for real customer data until the cloud migration and access rules are complete.

## Target architecture (low-maintenance, free-tier-first)

- Firebase Authentication: separate customer and admin/mechanic identities.
- Cloud Firestore: shared booking records and status updates.
- Firebase Cloud Messaging: booking/status notifications.
- Firebase Crashlytics: crash monitoring.
- PostgreSQL API authorization: customers can read only their own bookings; authorized staff can read and update bookings for this business.
- Automated billing alerts and a tested data-export/backup procedure before production.

## Database schema

The PostgreSQL schema is versioned in `backend/src/schema.sql` and is applied by the API at startup. It defines users, bookings, booking status history, ownership relationships, role/status constraints, coordinate validation, and indexes. The `CREATE TABLE IF NOT EXISTS` statements support a fresh database and repeat startup; they do not retrofit new constraints onto tables created by older schema versions. For any already-used database, apply reviewed `ALTER TABLE` migrations separately before deploying schema changes.

## Required setup before Android integration

1. Create a Firebase project owned by the app developer.
2. Register Android package `com.shreejagannath.refrigeration`.
3. Download `google-services.json` for that Android app and add it to `app/google-services.json` (do not commit service-account private keys).
4. Enable the chosen sign-in method in Firebase Authentication.
5. Provision the PostgreSQL database used by the API and store its connection string only in the hosting provider's secret environment settings.
6. Deploy the API and verify `/health` confirms database connectivity.
7. Create the first admin with the one-time setup secret, then rotate/remove that setup secret according to the deployment procedure.
8. Configure budget alerts and review the current free quotas and billing terms.

## Booking data shape (initial proposal)

Each booking should have a server-generated document ID and fields such as:
- `customerUid`
- `customerName`
- `phone`
- `address`
- `serviceType`
- `issue`
- `preferredVisitAt`
- `status`
- `createdAt` (server timestamp)
- `updatedAt` (server timestamp)

Do not trust a role, customer ID, price, or status supplied by a client without validating it through authentication and security rules/server logic. Never ship admin passwords or service-account credentials in the APK.

## Production gates

- Remove demo credentials (`admin / demo1234`).
- Implement sign-in and role assignment securely.
- Enforce customer ownership and staff authorization in Firestore rules.
- Test two separate devices: customer creates booking, staff sees it, staff changes status, customer sees the update.
- Test offline/retry behavior, duplicate submissions, notifications, backup/restore, and denied unauthorized access.
- Only then consider the cloud-connected version ready for real bookings.
