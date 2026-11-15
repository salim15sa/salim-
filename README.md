# Apps in this repository

| App | Folder | APK |
|---|---|---|
| **ATS Projects** – ELV / KNX device checklist from drawings | `ats/` | `ATS.apk` |
| **Taxi Log** – daily taxi expense tracker | `app/` | `TaxiLog.apk` |

Every push builds both APKs with GitHub Actions. On your phone open the repository's
**Releases → latest**, download the APK and install it (allow "install unknown apps").
New versions install over the old one and keep your data.

---

# ATS Projects (Android)

Site progress checklist for ATS – Al Arkan Technology Services, in the same layout as the
"Project Progress Checklist" sheet: **Floor | System | Device | Location | First Fix | Conduit |
Cable Pulled | Installed | Tested | Note**.

## Features
- **Projects** – one card per project with status, progress and next step (the same fields as the
  *Project Summary* Google Sheet: scope, status, latest update, next step, contact, phone, location).
- **Read drawing** – pick the drawing PDF (or a photo). The app sends it to Claude, which reads the
  legend and lists every device with its floor and room (cameras, intercom, Wi-Fi access points,
  touch screens, sensors, AC gateways, switches, glass-break sensors, keypads, data/TV points,
  access control…). You check the list, fix anything, and add it to the checklist in one tap.
  Needs an Anthropic API key (Settings). It is stored only on the phone.
- **Add device** by hand – floor, system, device type and room are chosen from lists; the last
  choice is remembered, names are numbered automatically (CAM12, CAM13…), quantity adds many at once.
- **Checklist** – tap a stage cell to cycle blank → ✓ → X. Mark a whole group at once
  (e.g. all CCTV on the ground floor = Conduit ✓). Filter by floor or system.
- **Excel & PDF with the ATS logo**
  - Project: *Checklist* sheet (merged floor/system/location cells, ✓ green, X red) +
    *Summary* sheet (stage progress, by floor & system, device quantities).
  - All projects: the *Project Summary* sheet.
  - Open, share (WhatsApp, Drive, email…) or save.
- **Backup / restore** all projects as a JSON file.

The device lists (systems, device types, floors, rooms) are in
`ats/src/main/java/com/ats/projects/Catalog.kt`.

---

# Taxi Log (Android)

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

## Build locally
Needs Android SDK + JDK 17: `./gradlew :ats:assembleRelease :app:assembleRelease`
