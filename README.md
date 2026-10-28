# Shree Jagannath Refrigeration

Android app for home-appliance repair and service.

## Implemented in the Android prototype
- Customer service booking flow for AC, refrigerator, washing machine, microwave oven and geyser.
- English/Odia language selection.
- Appliance-specific issue selection, booking history, progress timeline, cancellation of pending bookings, service reviews, and shareable booking summary.
- Customer Google sign-in UI using Firebase Authentication.
- Admin/mechanic dashboards and a protected-looking team login screen; demo credentials have been removed.
- Professional navy/blue visual styling on customer, admin and mechanic screens.
- GitHub Actions creates a debug APK and publishes the latest successful build.

## Important: not yet production-connected
- Customer bookings and status changes in the Android UI are still stored locally on that device. They do **not** sync to other phones yet.
- The Node.js/PostgreSQL API in `backend/` is a deployment-ready starting point, but it is not deployed or connected to the Android app yet.
- Team login is intentionally disabled until secure server authentication is deployed and wired into the app. Do not use this build to manage real customer data.
- Google sign-in authenticates a customer with Firebase, but the current API does not yet verify Firebase ID tokens. This must be implemented before connecting Google accounts to server-side bookings.
- No push notifications, online payments, or Play Store release configuration has been completed.
- Appliance illustrations are custom-drawn previews, not real product photography or engineering-accurate fault diagrams.

## Backend deployment preparation
- `backend/Dockerfile` builds the API container.
- `render.yaml` is a Render deployment blueprint. Before deployment, provision a PostgreSQL database and provide its connection string as `DATABASE_URL`; the API also requires `JWT_SECRET` and `ADMIN_SETUP_TOKEN`.
- After deployment, call `/health` to verify API and database connectivity. Create the first admin using `POST /api/admin/initial-admin` with the `x-admin-setup-token` header. Use a long unique password and keep the setup token secret.
- The backend must then be integrated into Android (customer Google token verification, booking create/list/cancel/review, team login, assignment and status updates) before cross-device sync works.

## Automated Android build
Pushes to `main` trigger the Android APK Build workflow. Download the `ShreeJagannathRefrigeration-debug-apk` artifact from a successful workflow run, or use the latest release channel.

Package: `com.shreejagannath.refrigeration`
