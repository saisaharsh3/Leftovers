<div align="center">

![Leftovers app icon](https://raw.githubusercontent.com/saisaharsh3/Leftovers/main/docs/icon-96.png)

# Leftovers

**Know what's left.**
A fast, private expense tracker for Android. Log a spend in two taps, see what's safe to spend
today, plan for the things you want, and ask an AI about your money if you'd like to.

[![Build](https://github.com/saisaharsh3/Leftovers/actions/workflows/build.yml/badge.svg)](https://github.com/saisaharsh3/Leftovers/actions/workflows/build.yml)
[![Download](https://img.shields.io/badge/Download-latest%20APK-CDF46F?logo=android&logoColor=black)](https://github.com/saisaharsh3/Leftovers/releases/latest)
![Android 8.0+](https://img.shields.io/badge/Android-8.0%2B-3DDC84?logo=android&logoColor=white)
![Kotlin](https://img.shields.io/badge/Kotlin-Jetpack%20Compose-7F52FF?logo=kotlin&logoColor=white)
[![License: MIT](https://img.shields.io/badge/License-MIT-blue)](https://github.com/saisaharsh3/Leftovers/blob/main/LICENSE)

![Home, adding an expense, Insights and Plan](https://raw.githubusercontent.com/saisaharsh3/Leftovers/main/docs/screenshots/row1.png)

![Activity calendar, filters, Money owed and the AI assistant](https://raw.githubusercontent.com/saisaharsh3/Leftovers/main/docs/screenshots/row2.png)

<a href="https://github.com/saisaharsh3/Leftovers/releases/latest"><img src="https://raw.githubusercontent.com/saisaharsh3/Leftovers/main/docs/badge-download.png" height="40" alt="Download the APK"></a>&nbsp;
<a href="https://apps.obtainium.imranr.dev/redirect?r=obtainium://add/https://github.com/saisaharsh3/Leftovers"><img src="https://raw.githubusercontent.com/saisaharsh3/Leftovers/main/docs/badge-obtainium.png" height="40" alt="Get it on Obtainium"></a>

If Leftovers is useful to you, a ⭐ helps other people find it.

</div>

## Why Leftovers?

Most budget apps want an account, show ads, or send your spending to a server. Leftovers doesn't.

- **It answers the question you actually have:** *how much can I spend today?* What's left this month,
  minus bills still due, spread over the days remaining.
- **Private by design:** no sign-up, no ads, no analytics. Your data never leaves the phone unless you
  choose to connect an AI or an email account.
- **Fast to use:** the keypad opens first, so a spend is logged in two taps. Bank SMS and email alerts can
  suggest entries for you, in any currency.
- **Free and open source** (MIT), with signed releases built in the open by GitHub Actions.

## Features

**Everyday tracking**
- Keypad-first entry; tap a digit to place a cursor and fix just that digit
- Expenses and income with categories, notes, accounts and receipt photos
- Add forgotten spending for any past day from a date strip or calendar
- Split one bill across several categories
- Search every entry by note, category or amount; filter by account, category or #tag
- Add #tags to notes (*"Dinner #goa"*) to group entries across categories and months, with a total
- Tap an entry to edit or delete it. Deleted entries can be restored later from Settings

**Budgets that make sense day to day**
- Monthly budget, or a yearly one split evenly, by smart rollover, or custom per month
- *Safe to spend today*: what's left, minus bills still due, spread over the remaining days
- Gifts, bonuses and other extra income add to that month's budget (switch per income category)
- When a month ends, choose whether what's left carries into the next month, or set it to always or never
- Per-category limits with alerts at 80% and 100%

**Planning**
- Subscriptions and recurring income every month, every 3 or 6 months, or yearly, logged automatically on their
  day, with a reminder the morning before a bill; longer cycles count as their monthly share in totals
- Skip a single payment without stopping the subscription
- Spots repeating expenses that look like a subscription and offers to add them
- Savings goals that say how much to put aside each month and whether you're on track
- Accounts (cash, bank, UPI, cards) with live balances, transfers, a breakdown of how each balance adds up,
  and a one-step move of every entry to another account (with undo)
- Total balance across accounts on Home, with where it's heading by the end of the month; switch it to one
  account (like a wallet) to see just that account's balance, spending and income
- Money owed: what you lent or borrowed and from whom, settled with a tap and kept out of your budget

**Insights**
- Daily bar chart you can touch to read any day
- Category breakdown that opens each category's entries
- Spending calendar that opens any day's entries
- Trends: category changes vs last month, weekend spending, where the month is heading
- Month-over-month comparison and a story-style monthly recap you can share as an image

**AI assistant** (optional)
- Connect Claude, ChatGPT, Gemini, Mistral, Groq, DeepSeek, Grok, OpenRouter or your own server, with your own API key
- Ask questions like *"How much did I spend on food this month?"* or *"Am I on track with my budget?"*
- Tell it what to do: *"Add 250 for coffee today"*, *"Move yesterday's lunch to Cash"*, *"Set a 5,000 limit on Shopping"*
- Snap a bill or receipt and it suggests the entries, split by category
- Every change it proposes waits for you to tap **Apply**. See [AI assistant](#ai-assistant) below.

**Extras**
- Home-screen widgets: *Safe to spend today* (with one-tap add) and *Left this month*
- Evening reminder, app lock (fingerprint, face or PIN)
- Backup and restore to a file or Google Drive (photos included if you like), daily, weekly or monthly automatic
  backups to a folder you choose, CSV export
- Optional backup password: backups are encrypted (AES-256) and need it to restore
- Optional payment detection from bank SMS and from bank email alerts, suggesting entries for you to confirm,
  including money coming in as income.
  Add your own keywords for banks it misses, and test any message to see what it would suggest. One payment never shows up twice, even when it arrives by both SMS and email
- Swipe sideways to move between Home, Activity, Insights and Plan
- Frosted glass design in dark and light mode (or solid surfaces with *Glass effects* off), spring animations
  and high-refresh-rate support

## Privacy

Everything stays on your device. There are no accounts, ads or analytics, and the app makes no
network calls unless you connect the optional AI assistant or an email account.

- The app is excluded from Android's automatic cloud backup, so backups go only where you save them.
- With app lock on, screenshots are blocked and the app is hidden in the recent-apps preview.
- SMS detection is off by default. When on, messages are read on the device and nothing is added without your tap.
- Email detection is off by default and signs in with an *app password* (Gmail, Yahoo, iCloud, Zoho or any
  IMAP server with SSL). It connects only to your mail server, over TLS with the certificate checked. The inbox
  is opened read-only, so nothing is changed or marked as read. Only alerts from banks, cards and payment apps
  are opened (shops, social networks, newsletters and promotions are skipped unopened), and they're read in memory. Only the amount, merchant and date are kept, never the email. The address and app
  password are encrypted with an Android Keystore key, never backed up, and deleted when you disconnect.
- With a backup password, backup files are encrypted. The password is kept encrypted with an Android Keystore key
  and is never written into a backup; if it's forgotten, those backups can't be opened.
- Network access is HTTPS-only and trusts only the system's certificate authorities.

## AI assistant

Tap the chat button on Home (or **Settings → AI assistant**), pick a provider, paste an API key and tap **Connect**.
Tap **Load models** to see every model your key can use.

| Provider | Get a key | Default model |
| --- | --- | --- |
| Claude | [console.anthropic.com](https://console.anthropic.com/settings/keys) | `claude-opus-5-5` (also `claude-sonnet-5-5`, `claude-haiku-4-5`) |
| ChatGPT | [platform.openai.com](https://platform.openai.com/api-keys) | `gpt-5-mini` |
| Gemini | [aistudio.google.com](https://aistudio.google.com/api-keys) | `gemini-flash-lite-latest` (fastest; also `gemini-flash-latest`, `gemini-pro-latest`) |
| Mistral | [console.mistral.ai](https://console.mistral.ai/api-keys) | `mistral-small-latest` |
| Groq | [console.groq.com](https://console.groq.com/keys) | `llama-3.3-70b-versatile` |
| DeepSeek | [platform.deepseek.com](https://platform.deepseek.com/api_keys) | `deepseek-chat` |
| Grok (xAI) | [console.x.ai](https://console.x.ai) | `grok-4` |
| OpenRouter | [openrouter.ai](https://openrouter.ai/keys) | `openrouter/auto` (one key for many models) |
| Custom | your server | any OpenAI-compatible HTTPS server, e.g. one you host yourself |

Usage is billed by the provider to your own account. **Answer style** switches between *Quick*
(the default: a few seconds per answer) and *Thorough* (the model thinks longer).

**What it can do**

- Answer questions: totals for any period, biggest expenses, budget progress, subscriptions, goals.
- Log, edit and delete entries, including by date (*"Move the parking from October 3 to October 5"*).
  If several entries match, it asks which one instead of guessing.
- Set the monthly budget and category limits, add or stop subscriptions, move money between accounts,
  and add to savings goals.
- **Read bills:** tap the camera in the chat to take a photo or pick a screenshot of a bill or receipt.
  It proposes one entry per category, dated from the bill, with any discount or tax spread so the
  entries add up to what you paid.

**Guardrails**

- **Nothing changes without you.** Each change appears as a card with **Confirm** and **Not now**
  (*Confirm all* when there are several). Turn off *Let it propose changes* for read-only use.
- **It sees only what a question needs.** It already knows your category and account names; amounts come
  through specific commands (totals first, at most 50 entries at a time), and each reply shows what it looked at.
- **Personal details stay hidden.** Notes stay on the phone unless you turn on *Share entry notes*. Card and
  account numbers, UPI IDs, phone numbers and emails are blanked out first. Saved receipt photos and SMS are
  never sent; a photo goes only when you attach it, resized and re-encoded so location and camera data are removed.
- **Your key is protected.** It's encrypted with an Android Keystore key, never included in backups, and
  deleted when you disconnect. Custom servers must use HTTPS.
- **Nothing is kept.** Chats are held in memory only (so an answer can finish while you look elsewhere) and are gone when the app closes. The AI is told to treat your data as data, not
  instructions, and each message is limited in how many steps and changes it can make.

What you send is handled under the chosen provider's privacy policy (DeepSeek stores data in China;
OpenRouter passes requests on to the model's own provider).

## Install

1. Open the [latest release](https://github.com/saisaharsh3/Leftovers/releases/latest) on your phone and download `leftovers-<version>.apk`.
2. Open the file. If Android asks, allow your browser or file manager to **install unknown apps**.
3. Tap **Install**. Updates install over the top and keep your data.

**Automatic updates:** add the repo to [Obtainium](https://github.com/ImranR98/Obtainium) with the badge at the
top, and new releases install straight from GitHub.

Requires Android 8.0 (API 26) or newer. What's new in each version is in [CHANGELOG.md](https://github.com/saisaharsh3/Leftovers/blob/main/CHANGELOG.md).

## Development

### Build and test

You need **JDK 17 or newer** and the **Android SDK**; [Android Studio](https://developer.android.com/studio) provides both.

```bash
git clone https://github.com/saisaharsh3/Leftovers.git
cd Leftovers

./gradlew assembleDebug        # app/build/outputs/apk/debug/app-debug.apk
./gradlew installDebug         # install on a connected phone (USB debugging on)
./gradlew testDebugUnitTest    # unit tests: budget maths, forecasts, backups, database upgrades, privacy, every AI provider's format
```

Debug builds install as a separate app, **Leftovers Dev** (amber icon), so they never touch the real app's data.
On Windows use `gradlew.bat`. If Gradle can't find the SDK, create `local.properties` with
`sdk.dir=/path/to/Android/Sdk` (Android Studio does this for you).

### Signed release build

1. Create a keystore once and keep it safe. Every future update must be signed with the same key.
   ```bash
   keytool -genkeypair -v -keystore release.jks -keyalg RSA -keysize 2048 -validity 10000 -alias leftovers
   ```
2. Copy `keystore.properties.example` to `keystore.properties` and fill in the passwords.
   Both files are git-ignored.
3. Run `./gradlew assembleRelease`. The APK is in `app/build/outputs/apk/release/`.

### Publishing a GitHub release

Pushing a tag such as `v1.2.0` runs the [release workflow](https://github.com/saisaharsh3/Leftovers/blob/main/.github/workflows/release.yml), which runs
the tests, builds a signed APK and attaches it to a GitHub release. It needs these repository secrets
(**Settings → Secrets and variables → Actions**):

| Secret | Value |
| --- | --- |
| `SIGNING_KEYSTORE_BASE64` | `base64 -w0 release.jks` (PowerShell: `[Convert]::ToBase64String([IO.File]::ReadAllBytes("release.jks"))`) |
| `SIGNING_STORE_PASSWORD` | keystore password |
| `SIGNING_KEY_ALIAS` | `leftovers` |
| `SIGNING_KEY_PASSWORD` | key password |

Bump `versionCode` and `versionName` in `app/build.gradle.kts`, rename `## Unreleased` in `CHANGELOG.md` to the
version (e.g. `## 1.2.0`; the release page shows that section), and add a short
`fastlane/metadata/android/en-US/changelogs/<versionCode>.txt` for F-Droid. Then:

```bash
git tag v1.2.0
git push origin v1.2.0
```

### Tech stack

- Kotlin, Jetpack Compose, Material 3
- Room (SQLite) with versioned migrations, DataStore for settings
- WorkManager (reminders, bill alerts, automatic backups), Glance (widgets), Biometric (app lock)
- AI providers called over plain HTTPS with `org.json`, so no extra SDKs ship in the app
- [Haze](https://github.com/chrisbanes/haze) for frosted-glass blur
- [Lucide](https://lucide.dev) icons and the [Manrope](https://github.com/davelab6/manrope) typeface

```
app/src/main/java/com/leftovers/app/
├── ai/          AI assistant: provider clients, commands, privacy filter, encrypted key storage
├── data/        Room entities, DAOs, repositories, budget and trend maths
├── ui/          Compose screens, components, theme, icons
├── util/        Formatting, alerts, reminders, backups, SMS parsing
└── widget/      Home-screen widgets
```

## Notes

- **Bank SMS detection** uses the `RECEIVE_SMS` permission, which Google Play restricts to default SMS
  apps and a few exceptions. It works for direct APK installs; remove it before publishing to Play.
- The `INTERNET` permission is used only by the AI assistant and email detection, after you connect them.

## Get involved

- **Try the newest changes:** every push to `dev` is published as a
  [Dev build](https://github.com/saisaharsh3/Leftovers/releases) that installs next to the real app.
- **Report a bug or suggest an idea** with the [issue forms](https://github.com/saisaharsh3/Leftovers/issues/new/choose),
  or ask a question in [Discussions](https://github.com/saisaharsh3/Leftovers/discussions).
- **Your bank's messages not detected?** Paste one (with personal details removed) in an issue; adding it to
  the tests is a great first contribution.
- **Write code:** start with a
  [good first issue](https://github.com/saisaharsh3/Leftovers/issues?q=is%3Aopen+label%3A%22good+first+issue%22) or one marked
  [help wanted](https://github.com/saisaharsh3/Leftovers/issues?q=is%3Aopen+label%3A%22help+wanted%22). Read
  [CONTRIBUTING.md](https://github.com/saisaharsh3/Leftovers/blob/main/CONTRIBUTING.md) and open your pull request against the **`dev`** branch.

### Contributors

[![Contributors](https://contrib.rocks/image?repo=saisaharsh3/Leftovers)](https://github.com/saisaharsh3/Leftovers/graphs/contributors)

## License

[MIT](https://github.com/saisaharsh3/Leftovers/blob/main/LICENSE). Third-party assets are listed in [THIRD_PARTY_NOTICES.md](https://github.com/saisaharsh3/Leftovers/blob/main/THIRD_PARTY_NOTICES.md).

Made by C. Sai Saharsh · [github.com/saisaharsh3](https://github.com/saisaharsh3)
