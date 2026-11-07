# Changelog

All notable changes to Leftovers. Download any version from
[Releases](https://github.com/saisaharsh3/Leftovers/releases).

## Unreleased

- Choose the app's background: the glow in Aurora, Ocean, Sunset, Forest or Rose, plain, or a photo of your own, shown in its own colours, with how soft and how dimmed it is up to you. Over a photo, cards are liquid glass that bends the picture at their edges (Android 13 and newer; frosted glass before that), like Apple's clear Liquid Glass, the same in light and dark. The text over a photo is white or black, picked by how bright the photo is or chosen yourself; only the text changes. Settings → Appearance
- Choose an accent colour for buttons, switches and highlights; on the light theme it's deepened so text in it stays readable. Settings → Appearance
- Festival greetings: on the main festivals where you live (worked out on the phone from your currency), Home says so ("Happy Diwali 🪔") and a notification comes that morning. Settings → Security & reminders
- A note on your week: Sunday evenings, a short note when there's something to say, such as spending down on last week, a category that changed, or no-spend days. Settings → Security & reminders
- Help & features, at the bottom of Settings: every feature in a line or two and where to find it, with a search ("transfer", "PDF", "backup") and a way to open each one
- The AI assistant answers "how do I…" and "where is…" from the same guide, with the path to each feature
- On this phone: right after the app opens, it waits for Android to start its AI ("Starting the phone's AI…") instead of saying it isn't ready, and starts it as soon as the assistant opens
- New versions show on the bell, with its red dot, after pulling down on Home, and in Leftovers Dev whenever the app opens or Notices is opened
- The AI assistant has its own icon, a chat bubble with a robot face, so it reads as AI
- AI assistant settings: the connected AI with Test beside it, one-tap Switch to, and Disconnect as quiet red text
- A long amount at the top of Home shrinks to fit instead of running off the screen
- Accounts can be put in any order: tap Reorder in Accounts and drag them by finger. There are more icons to choose for one, too (#47)
- Envelope budgets: a Dates budget can count only the entries chosen for it, those with a #tag like #trip and/or from one account, so rent and other spending in the same dates leave it be, in Safe to spend, the bars and the alerts too (#42)
- The budget's spent figures leave out accounts kept out of the budget, as Safe to spend already did (#33)
- Budget plan: Weeks, one amount for every week, two weeks, four weeks or any number of days, from a payday you choose, for people paid weekly or every two weeks; each period starts fresh (#45)
- Backups that failed with "can no longer open that folder" after choosing it again: the app now keeps the folder for good, clears out folders it no longer uses so Android doesn't refuse, says so when Android still won't keep one, and turning Sync off no longer takes away a folder backups use too (#41)
- Block screenshots, in Settings → Security, for a blank preview in Recents and no screenshots, without turning on App lock (#44)
- The month heading is a month on its own in every language: "Ноябрь 2026" in Russian, not "ноября 2026" (#44)
- A settled lent or borrowed entry shows what it was, when it was lent and paid back, the account, whether it came from a split bill, and when it was added
- Importing statements from payment apps like PhonePe, Paytm and Google Pay: amounts come from the columns that hold amounts, not the card or account used ("Paid by XX1234" was read as an amount), PDFs that print each payment as a block are read, transfers between your own accounts are kept out, and the note is who was paid
- Two real payments alike in one file, ₹80 at the same shop twice in a day, are both imported; only entries already in the app are skipped as duplicates
- An import can be undone right after it, and Settings → Import entries lists recent imports, each with a Remove that moves its entries to Deleted entries, so an import that came in wrong can be taken back
- A subscription already logged by hand or from its SMS isn't logged again, and its SMS isn't suggested once the app has logged it
- Pulling down on Home waits 4 seconds at most for email, sync and updates; the rest finishes in the background
- Dragging an account lifts a solid row, without a light outline
- Activity search: typing fast no longer loses letters, several words find entries with all of them in any order ("rent 18000", "sam lunch"), account names are searched too, and a #tag filter stays on while searching
- The PDF statement has a Money owed section: what was lent, borrowed and paid back in the month or year, as lending isn't spending and wasn't in it before; names only when notes are included
- Reading bank SMS and emails: the amount paid, not the balance or card limit printed before it ("Avl Bal Rs.12,000 after debit of Rs.500" is ₹500); "transaction of", "payment of", "charged", "transferred" and "UPI txn" are understood; offers aren't suggested; autopay names who it's for; and receipts take the Total paid, not the Subtotal or Item total
- bKash messages from Bangladesh are read: Send Money, Cash Out, Payment and money received, in Tk (#51)
- Dev builds: the AI Android already keeps on 68 phones and tablets (Gemini Nano: Pixel 9, 10 and 11, Galaxy S26 and the latest Folds and Flips, OnePlus 13 and 15, recent Xiaomi, POCO, OPPO, vivo, iQOO, realme, Honor, Motorola, Sony, Sharp and Lenovo models; the full list is in the AI assistant's settings), with no key and nothing leaving the phone. Pick "On this phone" in the AI assistant to ask questions and add or edit entries (each still waits for Apply); it also sums up a month in Insights, picks a category for a payment to somewhere new, and reads bank SMS and emails the rules can't. Release and F-Droid builds don't include it, as it needs Google's closed library
- On this phone reads photos of bills too (the camera button and photos in the chat), on phones whose built-in AI can see images
- AI assistant: each AI keeps its own key, so switching to On this phone and back to Gemini or Claude needs no key typed again, with one-tap Switch buttons in its settings
- AI assistant: it can make a PDF statement of a month or year, export entries to CSV, and create savings goals, from the chat
- The assistant no longer keeps showing its dots after an instant answer

## 1.9.0

- Home screen widgets come in Glass, Dark or Light (Settings → Appearance), and fit any size they're made: one row high, each keeps what matters
- Pop-ups are clearer liquid glass: the screen behind shows through
- The Left this month widget in 50/30/20 shows Needs, Wants and Savings side by side, so they fit its size
- Money owed: lent or borrowed can be saved without an amount, as a reminder (a laptop a friend is sending, say): it shows as amount pending and counts nowhere until it's filled in (#36)
- 50/30/20: an option to pick Needs, Wants or Savings first when adding an expense, then one of its categories; off,
  all categories stay in one row (#39)
- 50/30/20: the Left this month widget shows Needs, Wants and Savings, each with how much of its part is used (#39)
- 50/30/20 with no income set or logged splits the budget instead of reading "of ₹0" (#39)
- Pulling down on Home also looks for a new version, when Tell me about new versions is on
- Money owed: an entry can be paid back on its own, with Received (money lent) or Paid back (borrowed) in its editor, and the money goes back through its account
- After an update, what's new pops up once. The bell on Home opens Notices: what's new, and a newer version when there is one
- The AI assistant adds a split bill as one entry, with people's shares in Money owed and parts in other categories, and a bill photo across several categories becomes one split bill
- Money owed: Remind is a bell button, so the amount owed keeps to one line
- Bottom sheets are liquid glass like the tab bar and pop-ups (Android 13 and newer), and open on the app's own screen: they slide up over a lightly blurred screen, close with a tap outside, Back or a drag down, and move up above the keyboard
- Split: the keyboard's done key adds the part, then the keyboard closes and the list shows it
- Budget plan: Dates, one amount for days you choose, like two weeks or a trip, as well as monthly and yearly (#34)
- 50/30/20, an optional budget mode: needs, wants and savings as shares of your income, with each spending
  category in one (tap to move it in Categories) and three bars in Budget (#39)
- A split bill shows as one entry with its parts under it, and its total (#36). With splits shown separately, each entry still says it's split, and the bill's whole amount
- A split bill shows where its money went: a thin bar of the whole amount, white for your share, a clear colour for each part and a green for each person who owes you (an outline and a ✓ once they've paid back), with a pill for each piece to tap
- When the budget is used up, Home counts the bills still due apart from what's spent
- Check for a new version in Leftovers Dev looks for a newer Dev build, not a release
- Every Dev build has its own number (1.8.2-dev.57) and is kept as its own download for a while, so you can go
  back to an earlier one: any Dev build installs over any other, keeping your Dev data
- Automatic backups no longer stop without a word: one that's due is also made when the app opens (some phones,
  like HyperOS and MIUI, never run an app's background work), Settings says why the last one failed, there's Back
  up now, and "keep 1 file" removes older ones even when a cloud folder renamed a copy (#35)
- Editing an entry can split it, or turn it into money lent or borrowed, like adding one (#36)
- An account can be left out of Safe to spend and the budget, or out of the All accounts balance (#33)
- The budget month can start on any day from the 1st to the 28th, payday to payday (#34)
- Recurring income can be interest: a yearly rate on an account's balance, worked out on the balance each time
  it's logged (#32)
- An account in its own currency needs its rate before it's saved. Get today's rate fills it in, and Keep rates
  up to date fetches it daily (off unless you turn it on) (#30)
- Payment apps: the apps being read are on top, and others can be hidden from the list, with Show hidden apps to
  bring them back (#17)
- Savings goals can link up to four shops, Amazon and Flipkart for example; Check prices looks at each and marks
  the cheapest

## 1.8.2

- Pop-ups open on the app's own screen instead of in a window of their own. On some phones the app under such a
  window stopped redrawing until the next touch, so a tapped card stayed behind its pop-up, vanished after it
  closed, or showed an account's numbers only after a touch (#27)
- "Safe to spend ₹0 · ₹0 of ₹0" once the month's budget is used up now says "Over this month's budget" and by how
  much, on Home and in the widget
- Sheets and dialogs are nearly solid, so their text is readable on phones that don't blur behind them, and the
  date picker's year list no longer shows the days through it (#27)
- Extra income adds to the budget is now a switch in the budget plan, off unless you turn it on, so side income
  can go to savings (#26). Turn it on to keep the old behaviour; the per-category switches still choose which income
- Every currency can be chosen, the Ghana cedi among them (#28)
- Activity: By month or All time, to see an account's whole history at once (#29)
- An account can have its own currency: its entries are entered and shown in it, and totals, budgets and charts
  count them converted at the rate you set for the account (#30)
- Tags have their own row in the note box, with suggestions, and show beside the note instead of inside it (#31)
- Settings → Tell me about new versions (off unless turned on) and Check for a new version
- The glass of pop-ups is frostier inside, so what's behind doesn't show through their text

## 1.8.1

- Pull down on Home to check for new payment emails, log subscriptions that have come due and sync with your
  other phones right away; it says what it found
- Pop-up cards (an entry, the balance, Money owed, subscriptions) are liquid glass like the tab bar on Android 13
  and newer, bending the screen behind them at their edges
- Home leads with Safe to spend today, large and on the background instead of in a card, and the total balance
  card holds the month's spending and income under it
- Sync between your devices, with no account: Settings → Sync. Use a folder that Syncthing or Nextcloud's app keeps
  the same on each phone, or your own WebDAV server (Nextcloud, ownCloud and others). Each phone merges in the
  others' entries when the app opens, every few hours and on Sync now; entries deleted or changed on one phone are
  removed on the others (into Deleted entries, so they can be brought back). Files are encrypted with your backup
  password
- Quick add widget: log ₹50, ₹100, ₹200 or ₹500 in one tap from the home screen, with Undo. It shows what you've
  spent today, switches category with a tap, and fits its size: a taller widget gets bigger buttons and an
  Other amount button
- Money owed: Remind sends someone who owes you a friendly message through WhatsApp, SMS or any app
- A detected payment to a place you've logged before opens under the category you used last time
- Haptic feedback: switches click as they flip, choosing a segment or chip ticks, and saving, deleting, paying back
  and Undo confirm
- If the app closes unexpectedly, it offers to report it on GitHub or by email on the next launch. You see the
  report first, and nothing from your entries is in it
- Settings showed an old version number (1.6.0) at the bottom; it shows the real one now
- Each release is rebuilt from scratch and compared with the published APK, the way F-Droid checks it

## 1.8.0

- The tab bar is liquid glass on Android 13 and newer: what's behind it bends at its rounded edges, with a bright
  rim. With Glass effects off, or on older phones, it's the frosted bar as before
- Importing another app's backup keeps each entry's title and description (joined into its note) and its tags (as
  #tags), and makes any category it had that Leftovers doesn't, with a fitting icon, instead of putting the entries
  under Other. The import screen says which categories are new and shows the spending and income totals before
  anything is added (#21)
- Ivy Wallet backups import their transactions even when most of them are planned payments (#21)
- The AI assistant says why a request was refused, in the service's own words, and tells a wrong key apart from a
  key that isn't allowed to do something. Settings → AI assistant has a Test connection button that sends one
  short message and says whether it worked (#22)
- Settings rows always show their title in full; a long value beside it shortens itself instead
- Import entries also reads other apps' JSON backups and exports, and backups that are a .zip of JSON. Entries keep
  their category and account names, whichever app made the file; picking one under Restore offers its entries (#21)
- Restoring a backup asks whether to keep what's in the app and add the backup's entries, or replace everything.
  Keeping adds only what isn't already here, into the categories, accounts and goals with the same names, and
  leaves your settings as they are
- PDF bank statements can be imported too, password-protected ones included (the password is only used to open
  the file on the phone). Each row's date and amounts are read, and the running balance says which way the money went
- Savings goals can have a product link (Amazon, Flipkart or any shop). Check price reads the price from the shop's
  page when you tap it, to compare with your target or use as the target; nothing is checked in the background
- A lone Upcoming or Saving for card on Home lays out across the row instead of leaving the width empty
- Pay back part of what's owed: Pay back on Money owed (or on someone's card on Home) starts at everything owed;
  change the amount for part of it, and pick the account it went into. What's left stays open, and paying the
  rest settles it (#20)
- Import entries reads Excel files (.xlsx and the older .xls), the HTML ".xls" many banks download, and CSV or
  tab-separated text in UTF-16 as well as UTF-8, from a bank or any other app. Moves between your own accounts
  are left out (#21)
- Picking a statement or another app's export under Restore no longer shows a technical error: it offers to import
  its entries instead (#21)
- On Home, one subscription or one savings goal fills the whole width, and two share it (#18)
- Lent and borrowed money now moves your account balance while it's open: lending takes it out of the account
  it was paid from, borrowing puts it into the account it came into, and marking it settled puts the balance back.
  It still never counts as spending or touches the budget. Entries from before this update have no account, so
  balances don't change until you pick one for them (#19)
- Tapping an entry (on Home, in Activity or Stats) opens a card with its amount, category, account, date and
  photos, to edit it or delete it with Undo
- Tapping the balance on Home opens a card with every account, to switch which one Home shows. Tapping an account
  in Accounts shows its balance, money in and out, and its latest entries
- Pop-up cards grow out of what was tapped and shrink back into it, in the same glass as the other cards. Money
  owed cards show each entry's photos
- Bank messages from Kotak, Bank of Baroda, PNB, Canara, Union Bank, IDFC First, Yes Bank, IndusInd, Federal and
  AU are recognised, plus card spends, ATM withdrawals, EMIs, salary, refunds and interest (#7)
- Tapping a subscription (on Home or in Subscriptions) opens a card with its next charge, yearly cost and start, with
  Edit and Pause or Resume
- Payment apps: choose which apps' notifications are read (Settings → Payment apps), including your bank's own app
  once it has sent you a notification, and pick the account each one's payments go to (#17)
- Money owed on Home is one card with what you're owed and owe, and the people with the biggest balances and what
  their latest entry was for. Tapping someone opens a card with every entry, to edit or mark settled (#18)
- Photos: tapping a photo opens it full size, with pinch to zoom and Remove there; the small thumbnail no longer has
  a cross to hit by mistake. Entries with a photo show a camera icon on Home and in Activity (#18)
- Reading a bill photo also picks the account it was paid from: UPI or bank goes to a bank account, card to a
  card, cash to cash
- If reading a bill photo fails, a short message says why, and tapping it tries again
- Bottom button bars (Add, Save) fade in at the top instead of starting with a hard edge (#11)
- New screenshots in the README and the store listing

## 1.7.1

- App lock locks as soon as you leave the app (or after 30 seconds or 5 minutes, in Settings → Lock options).
  Picking a photo or sharing a file doesn't lock it
- App lock can use its own PIN or password instead of the phone's screen lock, with fingerprint or face as a
  shortcut. Only a salted hash is kept, and after five wrong tries it makes you wait, longer each time

## 1.7.0

- Pure black (AMOLED) in Settings → Appearance: a black background with no glow, for OLED screens (#1)
- AI: on the add screen, attach a bill photo and tap Fill in from this photo; the amount, date, category and note
  are filled in for you to check. The photo is only sent when you tap
- AI: Ask AI about this month on Insights, for what changed against last month and your budget, and what to do
- AI: the assistant now knows Money owed (who owes you, lending, borrowing, settling up) and #tags, and splits a
  shared bill into your share and what others owe
- AI: waits longer before giving up when the AI service is busy
- Activity shows the account chosen on Home however you open it, including from the tab bar (#13)
- Detect payments from payment apps' notifications: FamApp, Google Pay, PhonePe, Paytm, BHIM and CRED (Settings →
  Detect payment apps, then allow notification access). Only those apps are read, offers and reminders are skipped,
  and money sent to you comes in as income. A payment also seen in a bank SMS shows up once
- Statement as a PDF: pick any month or year with the arrows
- Statement as a PDF (Insights → download button): one page for the month shown or the whole year, with income,
  spending, budget, categories, biggest expenses and subscriptions; a year adds each month's totals. Notes are
  left out unless you turn them on, and every entry can be listed on extra pages. Made on the phone, then shared
- Lent or borrowed, right from the add screen: tap the hand button next to Split, pick I lent or I borrowed and
  who it's with: one person or several, each the whole amount, split evenly, or each their own amount. It goes
  to Money owed and stays out of your budget. Back returns to expense or income
- Money owed on Home: a card for each person. Tap one to edit it or mark it settled
- Entries on Money owed end in a chevron, like other rows you can open
- Swipe across the amount to move between Expense and Income
- Safe to spend, Total balance and Money owed cards on Home grow into the page they open, like Saving for does
- Subscriptions: swipe the calendar to see what's charged in the coming months
- Email detection reads FamPay (FamApp) payments
- The add screen works in landscape: the keypad sits on the right and the rest scrolls
- Time format in Settings → Appearance: like the phone, 12-hour or 24-hour
- Notes: the cursor starts at the end of the note, tag suggestions work anywhere in it, and picking one puts
  the cursor after it
- Tags need a letter and must start a word, so "Order #4521" no longer makes a tag
- Tapping an Upcoming card on Home opens that subscription, on its day in the calendar
- With no budget set, Home shows what you spent today instead of "over budget"
- CSV import saves everything in one go: a thousand entries take a couple of seconds instead of about seven
- Rounded amounts round the same way whether spent or a balance, so 12.50 shows 13 in both
- Split with people: when adding an expense, tap Split, pick who's sharing it, and split it equally or type
  each person's amount. Your share is your expense; each person's share goes to Money owed. Splitting across
  categories is in the same sheet, so it's one button
- Import from CSV (Settings → Data): a bank statement or another app's export. Columns are found by name,
  categories are matched by name (others go to Other), and entries already in Leftovers are skipped
- Quick Settings tile: add "Add expense" to your notification panel to open the keypad in one tap
- Year in review: the year as a story, like the monthly recap (Insights → Your 2026 so far)
- Travel mode: tap the currency symbol when adding to spend in another currency at your own rate. It's saved
  in your currency, and the original amount (e.g. AED 45) shows next to it. Rates are remembered, nothing goes online
- Home shows today's date next to the greeting ("Good evening · Sat, 10 Oct")
- Tab switching is back to the slide and fade from before, without the blank moment: a tab opened for the
  first time now shows its content straight away instead of an empty screen while it loads

## 1.6.0

- Detected payments go to the account the message names, by its last digits or by name: "in your HBL A/C",
  "HDFC Bank card". An account named "HBL Bank" also matches "HBL". Test a message shows which account it
  would go to, or what to name one so it matches (#6)
- Fixed an empty Home screen after tapping a back arrow twice quickly, or pressing Esc on a keyboard
- Smoother tab switching: the old tab fades out before the new one fades in, so two screens never show at once (#6)
- Faster start: housekeeping waits until the first screen is up
- The Dev build is now optimized like a release, so testers see the real app's speed (it was a debug build,
  about three times slower to start)

## 1.5.0

- *Glass effects* switch (Settings → Appearance), in dark and light: off gives solid cards without the
  see-through look and lit edges; bars, dialogs and sheets keep their frosted blur
- Light mode cards are now real glass too: a more colourful background shows through, and cards catch
  light at the top with a bright rim
- Money coming in is detected too: bank SMS and emails that say "credited" or "received" show on Home as
  income (in green, with who it's from) and open as an Income entry (#6)
- Email detection no longer brings back payments you already added or dismissed, checks every 15 minutes
  and also whenever you open the app, so you don't need to tap *Check now*
- Activity has no swipe actions any more, so entries can't be edited or deleted by accident; tap an entry
  to edit or delete it (#6)
- Dialogs are real frosted glass: the screen behind blurs and the panel is see-through (Android 12+);
  on phones without window blur they stay solid so text is easy to read. The blur fades in with the dialog (#6)
- The calendar and bottom sheets (keywords, accounts and others) get the same frosted glass
- Screen headers (Activity, Insights, Plan and others) no longer end in a dark band: the frost fades out
  softly, so content blurs gradually as it scrolls under the title (#6)

## 1.4.0

- New entries start on the account Home is showing (or the default account when Home shows all) (#6)
- #tag suggestions while typing a note: type #g and pick #goa (#6)
- Money owed entries can have up to six photos or screenshots, picked several at a time (#6)
- Narrow phones: the tab bar, balance card, Spent card and photo buttons no longer get cut off
- *Include photos in backups* (Settings → Data): receipt and Money owed photos go in the backup (up to 40 MB) (#6)
- SMS and email detection understand other currencies: $, €, £, AED, RM, Rp, kr and more, before or after the number (#6)
- Subscriptions list the next charge first, show five, and keep the rest behind *Show all*
- Tapping a later day in the calendar explains that later days are for repeating entries
- The tab bar and headers look lighter and more like glass
- Fixed SMS and email detection missing payments: bank footers mentioning OTPs or "requested" no longer
  hide a message, SBI amounts without Rs ("debited by 500.0") and card alerts ("Thank you for using…") are
  recognised, merchant names stop before "Refno" or "(UPI Ref…)", and email always looks back two days
- Detected payments name the real payee from UPI details ("UPI/P2A/…/NAME") instead of footer text like
  "To block UPI", and email senders without a name show the bank ("Axis Bank", not "alerts")
- Recent detections (Settings → Detection keywords) shows what happened to each bank SMS or email, and
  warns when SMS permission is off
- Accounts can have the last digits of their account or card; detected payments naming them go to that account (#6)
- *Delete all entries* also deletes Money owed (#6)
- *Reset app* erases everything and starts fresh, after typing RESET (#6)
- Automatic backup: choose daily, weekly or monthly, how many files to keep (1–5), and see when the last
  backup ran (#6)
- Confirmation dialogs use the frosted glass style (#6)
- Currency names no longer repeat ("Continue with AED", not "AED AED"), and letter symbols get a space ("AED 250") (#6)

## 1.3.0

- Home: choose one account on the balance card, like switching wallets; balance, spending, income and
  recent entries follow it, and Spent / Income open Activity filtered to it (#4)
- Detection keywords: add your own words for bank messages the built-in rules miss, and test any message
  to see what would be suggested (Settings, under SMS and email detection) (#4)
- Repeat every month: the calendar unlocks later days, so a salary or bill can start on its real date
- Deleting an entry logged by a subscription or recurring income can stop that subscription too, including
  repeats added without a note
- Plan counts recurring income too (e.g. "8 active · +₹75,000 coming in")
- New recurring income shows income templates (Salary, Freelance, Rent received, Interest…), not Netflix and Rent
- Subscriptions and recurring income can be paid from or into a chosen account (e.g. salary into the bank)
- Settings: *Report a problem or suggest an idea* opens GitHub issues
- Budget: the header button is gone; a small pencil on the budget card shows it opens the editor

## 1.2.2

- Home: *Spent this month* and *Income* open Activity on the matching tab, and an income card under
  *Upcoming* (like Salary) opens Subscriptions on Income
- Activity: the Income tab's card shows income; tapping an entry logged by a subscription or recurring income
  opens Subscriptions (swipe right still edits that one entry); swiping right now says *Edit*
- Budget: tapping the *Left* card opens the budget editor
- Plan: Accounts removed from *More* (it's on Home)
- Add screen: only today is labelled; other days show their weekday
- AI chat keeps answering if you leave the screen, the conversation is still there when you return, and a
  *Stop* button cancels an answer
- Subscription totals no longer show paise

## 1.2.1 (withdrawn)

Replaced by 1.2.2. Everything below is included in it.


- **Deleted entries** (Settings → Data): everything you delete is kept so you can restore it later. Filter by
  when it was deleted (today, 7 days, 30 days or any dates), restore or delete for good one by one or all at
  once, and choose how long to keep them (30 days, 90 days, 1 year or forever). Receipt photos come back too
- Email detection reads only alerts from banks, cards and payment apps. Shop receipts, LinkedIn, newsletters,
  bank offers and loan promotions are skipped without being opened
- Fixed: Undo didn't bring back an entry that was the only one on its day
- Smoother Activity list: deleted rows fade out while the rest glide into place, and Undo slides them back

## 1.2.0 (withdrawn)

Replaced by 1.2.1 because Undo could fail to bring back an entry. Everything below is included in 1.2.1.


- **Payments from email:** connect Gmail, Yahoo, iCloud, Zoho or any IMAP mailbox with an app password
  (Settings → Detect payments in email). Bank alerts and shop receipts show up on Home to confirm, like SMS.
  Read-only over TLS, only payment emails, nothing but the amount, merchant and date is kept, and the
  password is encrypted and deleted on disconnect
- SMS detection no longer suggests a payment twice when the bank sends two alerts, or when you've already
  logged it yourself
- Smoother swipes in Activity: a deleted row slides away and the list closes the gap smoothly, and swiping
  right opens the editor without the row jumping back underneath it. The edit and delete icons slide in
  and out with the row instead of popping
- Undo after deleting now looks like the rest of the app, sits above the tab bar (it was hidden behind it),
  and stays for 8 seconds even if you switch tabs, so a delete made while swiping between tabs can be undone
- Swiping an entry left shows a small red *Delete* once letting go would delete it
- Plan's last section is now called *More*, since it holds Money owed as well as setup
- Test builds (*Leftovers Dev*) have an amber icon so they're never mistaken for the real app

## 1.1.1

- Calmer add screen: the note sits on its own line, the account is one tap-to-change button, and repeat,
  split and receipt are small icons. A long note no longer pushes the receipt button off screen
- Removed voice entry
- Sheets (like *Money owed*) no longer show a stray outline where the keyboard was

## 1.1.0

**New**
- Swipe sideways to move between Home, Activity, Insights and Plan; tabs slide in the direction you go
- Voice entry: tap the mic on the add screen and say *"250 for lunch"*
- Filter Activity by account, category or #tag; tags in notes group entries across months with a total
- Subscriptions every 3 or 6 months, and *Skip the next one* for a single payment
- Spots repeating expenses that look like subscriptions and offers to add them
- Total balance shows where it's heading by the end of the month
- Money owed: track what you lent or borrowed (Plan → Money owed)
- Share the monthly recap as an image
- Optional backup password: backups are encrypted with AES-256

**Under the hood**
- Database upgrades are tested from version 2 to the current one
- Insights changes month with the arrows (a sideways swipe now switches tabs there too)

## 1.0.6

- Yearly subscriptions: pick the month and day; they're logged once a year and count as their monthly
  share in the Subscriptions and Plan totals. The AI assistant can add them too
- The AI assistant has its own chat icon, so it no longer looks like the monthly recap
- Fixed weekly automatic backup sometimes writing a duplicate file on the day it was turned on
- Subscription totals are rounded to whole amounts

## 1.0.5

**AI assistant**
- More providers: Mistral, Groq, DeepSeek, Grok (xAI), OpenRouter and any OpenAI-compatible HTTPS server
- *Load models* lists the models your key can use; *Answer style* switches between Quick and Thorough
- Much faster: answers in seconds instead of minutes (lighter thinking, fewer round trips, Gemini Flash-Lite by default)
- Photograph or pick a screenshot of a bill and it proposes entries by category, adding up to what you paid
- Finds entries by date, and asks which one when several match instead of guessing
- Redesigned chat: message bubbles, typing indicator, clear action cards with Confirm / Not now
- Fixes: replies after a confirmed change now appear; *Confirm all* replies once; private notes are reported as private

**App**
- Back gestures no longer stutter: a back swipe now plays the same smooth animation as the back button
- Unit tests check every AI provider's request and reply format

## 1.0.4

**AI assistant (optional)**
- Connect Claude, ChatGPT or Gemini with your own API key, and disconnect at any time (the key is deleted)
- Ask about your spending, budget, subscriptions and goals, or tell it what to add, edit or delete
- Every proposed change waits for **Apply**; *Apply all* when there are several; read-only mode available
- Privacy guardrails: only what a question needs is shared (and shown), notes off by default, card/account/phone
  numbers, UPI IDs and emails hidden, key encrypted with the Android Keystore, chats not saved

**Improvements**
- Accounts show how each balance adds up, and flag entries dated before the account was added
- Add and edit screens slide up smoothly instead of morphing from the + button
- Recurring income now says "Received on…", and the Subscriptions summary follows the selected tab
- HTTPS-only network security config

**Fixes**
- The "Changes saved" message no longer draws a square behind its rounded pill

## 1.0.3

- Move every entry from one account to another, with undo
- Tap a category in Insights to see its entries; touch the daily chart to read any day; swipe to change month
- Spending trends in Insights
- Tap a digit to place a cursor in the amount and fix just that digit; the amount shrinks smoothly
- Split one entry across several categories
- Activity search also matches amounts
- Weekly automatic backup to a folder you choose
- Reminder the morning before a subscription is charged
- *Left this month* home-screen widget
- Smoother first opening of screens (baseline profile)

## 1.0.2

- Extra income (gifts, freelance…) adds to that month's budget, switchable per income category
- At the end of a month, choose whether what's left carries into the next (ask, always or never)
- Total balance across accounts on Home
- Tap a day in the Insights calendar to see its entries
- Budget gauge is centred and large amounts fit inside it
- Smoother scrolling and lower battery use: the background no longer redraws every frame
- Removed quick add

## 1.0.1

- "Made by" link in Settings
- Uses the display's highest refresh rate (90/120 Hz where available)

## 1.0.0

- First release
