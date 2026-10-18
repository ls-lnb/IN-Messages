# Messages — a Fossify Messages fork

<img alt="Logo" src="graphics/icon.webp" width="120" />

This is a fork of [Fossify Messages](https://github.com/FossifyOrg/Messages), the privacy-focused, open-source SMS/MMS app: no ads, no internet permission for SMS, material design with dark theme, message blocking, SMS/MMS backup and restore, and efficient search. For everything about the base app, see the [Fossify Messages README](https://github.com/FossifyOrg/Messages#readme).

## What this fork adds

All of the base app's features, plus **sender grouping**, **one time password handling** and **colour customisation**, aimed at short-code clutter (banks, delivery services, promotions — especially Indian short codes).

It installs as **IN Messages** (`com.blinkingblue.inmessages`), so it can sit alongside the original app.

### Sender grouping

- **Grouped conversations** — threads from the senders of a group merge into one conversation in the list, so codes from the same bank stop flooding it.
- **Two-tab sender picker** — *Search senders* and *Grouped senders*. Senders move between the tabs when you save, the picker stays open so you can see the move, and its header shows the group's name. Similar senders (same bank core code) float to the top of the search tab, suggested but not preselected.
- **Quick "Add to group"** — from an ungrouped short code, pick a group from a name-only dialog and the sender is filed immediately.
- **Group list ordering** — the add-to-group dialog lists groups with the most recent activity first, mirroring the conversation list, with an A–Z toggle in the header; your choice is remembered.
- **Safer group edits** — moving a sender out of a group no longer wipes that group's remaining members, members missing from the SMS list are no longer dropped, and ungrouping a conversation asks for confirmation first.
- **Group icons and sync progress** — grouped conversations show a group icon, with progress shown while the grouping syncs.
- **Sender codes in grouped threads** — received messages show the sender's code, so you can still tell senders apart inside a group.
- **Search inside grouped conversations** — full-text search across every thread in a group, with a match count and navigation between matches.
- **Settings export/import** — back up and restore all settings, sender groups included, as a portable JSON file.

### Search

- **Prioritised results** — sender groups rank first, then individual senders, then message text. Group matching ignores separators, so "hdfc bank" finds "HDFC-BANK".
- **Yellow highlighter** — matched words are highlighted with a yellow marker background, both in the in-conversation search (the one with ↑/↓ navigation) and in the global results list.
- **Instant jump to the newest message** — the scroll-to-bottom button jumps straight to the bottom instead of animating through the whole history.
- **Tidier conversation list** — the list title was oversized (1.2× the base text size, larger than messages themselves); it now tracks the message text at 1.05×.

### Grouped-sender notifications

- **One notification per group, headed with the group name** — messages from every member accumulate in it, and each message still shows which short code wrote it. Tapping opens the grouped conversation.
- **Reading anywhere clears it** — opening the thread, marking read from the conversation list, archiving or deleting clears the shared notification. Splitting or editing a group also clears the notifications it leaves behind, so nothing stale lingers.
- **Known trade-off** — a collapsed notification only shows the newest message, so several new messages in one group look like one notification until expanded. Bundled or counted variants are still being evaluated.

### One time passwords

- **Codes in the notification, no expansion needed** — an `OTP: 123456` line appears in the notification header, the code itself is bold and highlighted inside the message line, and a **Copy code** action puts it on the clipboard.
- **Codes in the conversation** — the code is underlined in the thread; long-press it for **Copy code**.
- **Broad format support** — `otp`, `one time password` / `pin` / `passcode` / `code`, `verification`, `verify`, `security`, `authentication`, `confirmation`, `login` and `identification` code, `temporary password`, `passcode`, `tpin`, `use code`, `your code`, matched case-insensitively with spaces or dashes interchangeable.
- **Numeric and mixed codes** — plain 4–8 digit codes, plus mixed alphanumeric ones such as Steam's `2MB8G` (4–10 characters holding at least two digits and two letters, any case). When a message holds both a code and a mixed reference, the digits win.
- **Amounts, dates and masked or plain words are never mistaken for codes** — `Rs:5000`, `Rs5000`, `1234.56`, `11-09-2026`, `XX1001`, and words such as `AMAZON` or `VALID` are ignored, so balance alerts, card suffixes, dates and bank names do not turn into codes.
- **Codes may sit either side of a phrase**, up to 80 characters away, and the nearest candidate wins.
- **Your own formats** — "Detect one time passwords" switches the feature on or off, and the OTP keywords screen adds your own phrases with a live sample-message preview while listing the built-in formats read-only. Codes stay hidden when lock-screen visibility is set to show nothing.

### Colours

- **Pick the app's colours** — Settings → *Customize colours* sets the primary, text and background colours, each with a live swatch, plus the launcher icon colour in its own section. **Reset colours** restores the app's own defaults and leaves the icon alone.
- **Follow system colours** — on Android 12+ the app can take its colours from your wallpaper instead. While that is on, the colour rows and reset are disabled, because the system theme would override anything picked.
- **Picked colours stick** — a colour applies immediately, does not change with day or night, and if a pick would leave the text unreadable the other colour is matched to it, with a note saying so. The launcher icon offers 19 built-in colours; some launchers cache the icon until it is tapped.

### Development

- **Unit tests, including a message corpus** — `OtpDetectorTest` covers formats and the amount/date/masked-number rules; `OtpCorpusTest` replays 391 real-shaped messages (30 with a code, 361 without). Run them with `./gradlew testFossDebugUnitTest`.
- **Corpus sources**, with thanks and their licences: [skyaara/indian-payment-sms-dataset](https://github.com/skyaara/indian-payment-sms-dataset) (CC BY 4.0), [abhirajsinha/omoi-sms-parser](https://github.com/abhirajsinha/omoi-sms-parser) (MIT) and [akhilnarang/bank-sms-parser](https://github.com/akhilnarang/bank-sms-parser) (MIT), plus messages received on the device. Provenance is recorded in `app/src/test/resources/otp-corpus.tsv`.
- **CI** — the tests run on pull requests to `main` and gate the release build; a universal APK is released on every push to `main`. Work lands on `develop` first.
