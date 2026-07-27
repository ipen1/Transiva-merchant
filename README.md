# Transiva Merchant Android

Repository khusus Merchant Transiva.

- Application ID: `com.transiva.merchant`
- Java namespace tetap: `com.transiva.app`
- Login/session hanya menerima role `merchant`
- Source Java Admin, Driver, dan Customer dihapus dari source aktif
- Driver foreground location/navigation tidak disertakan

## Firebase
Daftarkan Android app `com.transiva.merchant` di Firebase project Transiva lalu replace `app/google-services.json` dengan file resmi sebelum production.

## GitHub Secrets
`KEYSTORE_BASE64`, `KEYSTORE_PASSWORD`, `KEY_ALIAS`, `KEY_PASSWORD`.
