# Privacy policy

Last updated: 28 November 2026

Leftovers is an expense tracker that keeps your money on your phone. There is no Leftovers account, no Leftovers server, no ads and no analytics. The developer never receives your data.

## What stays on your phone

Your entries, accounts, budgets, subscriptions, goals and settings are stored only in the app's private storage on your phone. Android's own cloud backup is turned off for the app. Backups and exports are files that you choose to make and decide where to keep.

## Bank SMS (only if you turn it on)

If you turn on **Settings → Payment detection → Detect bank SMS**, Leftovers asks for Android's permission to receive SMS (`RECEIVE_SMS`). It is used only to spot payments from your bank and suggest them as entries.

- Messages are read on your phone as they arrive. The app does not read your older messages.
- From a bank payment, the app keeps only the amount, the shop or payee, the date, the sender, the last digits of the account and the balance the message shows. The message text itself is never saved; a short fingerprint is kept only to avoid adding the same payment twice.
- Other texts, such as personal messages and one-time codes, are ignored and leave no record.
- SMS are never sent off your phone, shared with anyone, or used for anything other than suggesting entries.
- If your phone has a built-in on-device AI, it may help read a payment the app's own rules couldn't. That also happens entirely on your phone.

You can turn this off at any time in Settings, or remove the permission in Android's settings.

## Payment app notifications (only if you turn it on)

If you turn on **Detect payment apps** and give notification access, Leftovers reads notifications from payment apps on your phone to spot payments, in the same way and with the same limits as bank SMS. Nothing is sent off your phone.

## Features that use the internet

Leftovers only connects to the internet for these features, and only when you set them up or use them:

- **AI assistant and receipt reading:** if you connect an AI service (such as Anthropic, OpenAI, Google Gemini, Mistral, Groq, DeepSeek, xAI, OpenRouter or your own server) with your own API key, what you ask and the details it needs from your entries are sent to that service, as are any receipt photos you choose to read. That service's own privacy policy applies. Your API key stays on your phone. The on-device assistant, where your phone has one, sends nothing.
- **Email detection:** if you connect a mailbox, the app signs in to your mail service to look for bank payment emails. Your address and app password are encrypted on your phone and never backed up. Like SMS, only the payment details are kept.
- **Exchange rates:** if you ask for today's rates, the app fetches them from the free currency-api. The request names only a currency, nothing about you or your money.
- **Price check for a savings goal:** if you tap Check price, the app opens that shop's product page to read its price.
- **Sync between devices:** if you turn it on, a file is kept in a folder you choose, or on your own WebDAV server. No one else receives it.
- **Update check:** if you turn it on, the app asks GitHub once a week whether a newer version exists.

## Crash reports

If the app crashes, a short report (app version, Android version, phone model and the error) is saved on your phone. On the next launch you can choose to send it as a GitHub issue or an email, which you read before sending, or let it go. Nothing from your entries is in it, and nothing is sent automatically.

## Permissions

- **Receive SMS:** bank SMS detection, only if you turn it on.
- **Notifications:** reminders, budget alerts and suggestions.
- **Internet:** only for the features listed above.

## Children

Leftovers is not aimed at children and does not knowingly collect anything from anyone.

## Deleting your data

Everything is on your phone, so uninstalling the app, or clearing its storage in Android's settings, deletes it all. Disconnecting email or an AI service in the app removes its saved sign-in or key.

## Changes

Any change to this policy will be posted on this page with a new date.

## Contact

Questions or concerns: open an issue at https://github.com/saisaharsh3/Leftovers/issues
