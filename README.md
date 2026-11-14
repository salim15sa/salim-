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

---

# My Wallet – personal income, expense & budget manager (Android)

Second app in this repo (`expense/` module), installed as **MyWallet.apk** from the same
**Releases → latest** page. Amounts default to OMR (change currency in Settings).

## Features
- **Home** – balance for the month, income vs expense, % saved, today's spend vs today's limit,
  budget alerts, colourful category donut, recent entries. Switch month with the arrows at the top.
- **Add income / expense** – big amount field, colourful category grid (Food, Grocery, Travel, Fuel,
  Home & Rent, Bills, Mobile & Net, Tools, Hospital, Shopping, Family, Education, Entertainment…;
  Salary, Bonus, Overtime, Side business, Refund…), date, paid with (Cash/Card/Bank/Wallet), note.
  While typing it shows what will be left in that category's budget.
- **Budgets** – a monthly limit per category and/or for the whole month. For each one you see
  used / left, days left, **how much you can spend per day** for the rest of the month, and whether
  you are ahead of or under plan.
  - After saving an expense a message pops up, e.g. *Food: 30 OMR left for this month –
    16 days left → about 1.875 OMR per day*.
  - Phone notification when a budget reaches 80 % and when it is exceeded.
- **Transactions** – grouped by day, search, filter by expense/income and category, tap to edit,
  delete with undo.
- **Reports** – month or whole year: income, expense, savings, avg per day, 6-month trend chart,
  daily spending chart with daily-budget line, category breakdown with budgets, income sources,
  payment methods. Download as **PDF** or **Excel** (open, share or save).
- **Settings** – your own app name, currency & decimals, add/edit/delete categories (emoji + colour),
  payment methods, alert switches, JSON backup / restore.
