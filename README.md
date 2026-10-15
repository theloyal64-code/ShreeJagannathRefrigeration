# Shree Jagannath Refrigeration

Android app for a home-appliance repair and service business.

## Current milestone
- Single Android app with Customer and Admin/Mechanic entry points
- AC, Refrigerator, Washing Machine, Microwave Oven, and Geyser service categories
- Customer request form and booking history
- Demo admin login and request status controls
- **Bookings and status changes persist locally on the same device**, including after closing and reopening the app
- GitHub Actions workflow to build and upload a debug APK

## Important limitations
- Local persistence is not cloud sync: bookings created on a customer's phone will not automatically appear on a mechanic's phone yet.
- Admin login still uses demo credentials (`admin` / `demo1234`) and is not secure for real customer data.
- Cloud database/API, secure role-based authentication, cross-device synchronization, notifications, photo uploads, and payment processing are not implemented yet.
- Do not use this prototype to store real customer data until secure authentication and server-side access controls are implemented.

## Next milestone
Add a hosted backend and database so customer requests are securely stored centrally and visible to authorized admin/mechanic accounts across devices. Deployment will require selecting/configuring a hosting/database project and its credentials.

## Automated build
Pushes and pull requests to `main` trigger the Android APK Build workflow. Download the `ShreeJagannathRefrigeration-debug-apk` artifact from a successful workflow run.

Package: `com.shreejagannath.refrigeration`
