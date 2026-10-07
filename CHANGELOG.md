# Changelog

All notable changes to Leftovers. Download any version from
[Releases](https://github.com/saisaharsh3/Leftovers/releases).

## Unreleased

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
