# Shree Jagannath Refrigeration

Android starter for a home-appliance repair and service business.

## First milestone
- Single Android app with Customer and Admin/Mechanic entry points
- AC, Refrigerator, Washing Machine, Microwave Oven, and Geyser service categories
- Customer request form and booking history
- Demo admin login and request status controls
- GitHub Actions workflow to build and upload a debug APK

## Prototype limitations
Booking data is currently stored only in app memory. It is not shared between devices and does not persist after the app process closes. Backend/database, production authentication, notifications, photos, and payment processing are not implemented yet. The demo admin credentials are not secure and must not be used for production or real customer data.

## Automated build
Pushes and pull requests to main trigger the Android APK Build workflow. It can also be run manually from the Actions tab. Download the ShreeJagannathRefrigeration-debug-apk artifact from a successful workflow run.

Package: com.shreejagannath.refrigeration
