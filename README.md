# Taxi Log – daily taxi expense tracker (Android)

Enter each taxi leg in a few taps; whenever you need it, get a nicely designed
**Excel sheet (.xlsx)** or **PDF report** – same layout as the "taxi seb" Google Sheet.

## Features
- **Add trip** – date (defaults to today), Site, From → To, Amount (OMR), Personnel, Paid By.
  - *From* is filled automatically with the last stop of the day (or home: Ghubrah).
  - Leave Amount empty when a leg is covered by the previous fare → shown as **Included**.
  - Suggestions from your history for sites and places, quick-amount chips.
- **Trips** – all legs grouped by month and day with totals; search; tap to edit or delete.
- **Report** – this month / last month / last 7 days / all / custom range:
  - **Excel**: *Trips* tab (summary cards, day totals, grand total) + *Summary* tab
    (who paid, by site, by day, legs per person).
  - **PDF**: A4 printable report.
  - Open, Share (WhatsApp, Drive, email…) or Save to the phone.
- **Settings** – people, home place, report title, JSON backup/restore.
- Your 50 existing records (24 Oct – 12 Nov 2026) are pre-loaded.

## Install
Every push builds the APK with GitHub Actions. On your phone open the repository's
**Releases → latest** and download `TaxiLog.apk`, then install it (allow "install unknown apps").
New versions install over the old one and keep your data.

## Build locally
Needs Android SDK + JDK 17: `./gradlew assembleRelease`
