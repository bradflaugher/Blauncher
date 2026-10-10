# Blauncher User Guide

Blauncher is a minimal launcher with no app icons, no dock, and no visible
buttons. Everything is a gesture or a tap on text. The app teaches what you
need on first run (see [First-run tips](#first-run-tips)); this page is the
full tour, and [Questions](#questions) answers the usual ones.

## Install and set up

1. Download `Blauncher.apk` from the
   [latest release](https://github.com/bradflaugher/Blauncher/releases/tag/latest)
   (optionally verify it against `Blauncher.apk.sha256` — see `SECURITY.md`).
2. Install it (you may need to allow installs from your browser or file
   manager), then open it and tap **Set as default launcher**.
3. Requires Android 15 (API 35) or newer.

## First-run tips

A fresh install shows a small card above the search bar that teaches one
thing at a time:

1. **Tap the search bar for your apps.** Every app is listed under it, and
   typing narrows the list. Tap the bar, or just tap the card.
2. **Long-press for settings.** Touch and hold any empty spot, or tap the
   card.
3. **Tap the date or the shortcut.** The date opens your calendar and the
   button beside the search bar your shortcut app (a password manager to
   start); hold either to pick the app. Use either one, or
   tap the card to say got it.

Each tip goes away for good once you have done what it says; nothing times
out, so a tip is never gone before it has done its job. The first time the
apps come up, one line under the search bar adds: type to find an app,
long-press an app for its menu. It goes once you open any app's menu,
or tap its **×**.

- **×** on the home card skips all the tips at once.
- **Settings → Help and FAQ** lists every gesture on one page (with the apps
  your swipes are actually set to), answers the usual questions, and has
  **Show tips again** to bring the tips back.
- Updating from a build without tips: if you had already opened settings,
  the tips are skipped for you. If you had already finished the two-tip
  tour, the newer date-and-shortcut tip is skipped too.
- With TalkBack, the home screen offers **Open apps** and **Open settings**
  as actions, and the tip card reads as a button that does what it teaches.

## The home screen

The home screen is the date at the top and, along the bottom, a search bar
beside a round shortcut button (a key, for your password manager, until you
choose otherwise). The notification bar is always visible and the middle of
the screen is empty on purpose: everything else is gestures.

Home and the app drawer are one surface. The search bar sits on top of a
sheet holding every app: tap the bar and the sheet lifts, the bar riding up
to the top of the screen with the keyboard up and your apps filling in below
it while the date fades away. Swipe up, left and right are yours for three
apps of your choice.

| Gesture | What it does |
| --- | --- |
| **Long-press empty space** | Opens **Settings** — this is the big one to know |
| Tap the search bar | Your apps, listed under the bar, with the keyboard up |
| Swipe up | Opens an app you choose |
| Swipe down | Notification shade (or search — configurable) |
| Swipe left | Opens an app you choose |
| Swipe right | Opens an app you choose |

Until a swipe has an app, swiping that way opens the app picker so you can
choose one; after that it opens your app. Change or turn off any of them in
Settings → Gestures.
| Tap the date | Opens your calendar |

- **Search bar** — the pill reading *Search apps and the web*: one search
  for both, and the only one. Tap it and the apps come up with the keyboard.
  - As you type, the apps below the bar narrow to the ones your text
    matches, best match first (a name that starts with your text beats one
    that only contains it). Tap any of them to open it; nothing opens by
    itself.
  - **Enter** (the keyboard's Go key) or the filled **send** button that
    replaces the shortcut button while there is text always searches the
    web, even when an app matches: an app opens only when you tap it. So you can search
    for `weather` with a Weather app installed.
  - The box grows to a few lines and then scrolls, and there is no length
    limit, so whole paragraphs are fine. Shift+Enter on a hardware keyboard
    starts a new line. An **×** at the end of the bar clears the text.
  - The results come from the **search engine** picked in Settings → Home
    screen: divid3 (a private search router) by default, or DuckDuckGo,
    Google, Bing, Brave Search, Kagi, Startpage, Ecosia, Perplexity, or
    ChatGPT. The launcher builds the
    results URL itself and opens it, so nothing waits for a second enter.
  - **Browser default** is also there: it hands the raw text to the browser
    as a web search and lets the browser choose the engine. Some browsers
    only put the text in their address bar and wait for enter, which is
    why it is not the default. Failing everything, DuckDuckGo opens.
  - Unsent text is a **draft**: it stays if you close the apps, scroll the
    list, open settings, switch apps to copy something, rotate, or the
    launcher restarts. Only a successful web search, opening an app while
    there is text, or the **×** button empties the bar, and if no app could
    take the search the text stays put.
- **Shortcut button** — the round button beside the search bar opens one
  app of your choice. On first run it binds to a known password manager
  already installed (Bitwarden, 1Password, Proton Pass, KeePassDX,
  Keepass2Android, Enpass, Keeper, LastPass, Dashlane, NordPass) and wears a
  key; until an app is bound the glyph is drawn faded and tapping it opens
  the picker. **Long-press** it to pick any other app (the same choice is
  Settings → Home screen → *Shortcut app*), and pick its look from about
  thirty line-art glyphs (lock, shield, card, camera, phone, chat, mail,
  music, pin, globe, book, pencil, clock, sparkle, terminal, bolt, star,
  heart and the drawer's group glyphs) in Settings → Home screen →
  *Shortcut icon*. An app in a paused work profile or a locked Private Space
  stays bound until it is actually uninstalled.
- **Long-press** the date to choose which app it opens.
- The date's alignment (left, center, right) is in Settings.
- **Keyboard and mouse** (Chromebooks, desktop windows): scroll the mouse
  wheel, or press the up arrow or Enter, to bring up your apps; type a letter
  to start a search with it; right-click empty space for Settings.
- **TalkBack**: every home gesture — all apps, Settings, the swipe-left and
  swipe-right apps (when enabled) and the swipe-down action — is also in the
  home screen's custom actions menu.
- Back puts your apps away; with them down it does nothing on the home
  screen, as in any launcher. Everywhere else it is predictive back: start the back swipe and the screen behind
  peeks through before you let go. Phones stay in portrait; tablets,
  unfolded foldables and desktop windows rotate freely, and rotating,
  folding or resizing keeps you on the screen you were on.

## Your apps

Tap the search bar on the home screen. Apps are listed in **groups** (AI Agents,
People, Focus, News, Media, and so on), each marked with a small colored
glyph (a deeper shade of the same color in the light theme, so it stays
legible), alphabetical within the group. An **emphasized** app is bold and
sits at the top of its group. Once a group has one, its other apps fold
into a single faded line under the bold ones — `+5 · Gemini · Perplexity ·
Poe…`, the count in the group's color — so the apps you chose stand out
without a long tail below them.

- **Tap the folded line** to expand the group in place; the row turns into
  **− fewer** and tapping it again folds the group back. Expansion lasts
  for the current visit only: your apps always come up compact.
- Folded apps are never out of reach: **search matches every app**, folded
  or not, and a freshly installed app (marked ✦) stays visible in its group
  until it is an hour old — listed just above the folded line, never counted
  in it.
- Groups without an emphasized app list every app as before.

- **Search** is the bar above the list. Type and the list narrows to the
  matches, best first; an app opens only when you tap it. Enter always
  searches the web.
- Scrolling the list puts the keyboard away so you can browse; the text
  stays.
- **Long-press an app** for its menu: **Uninstall · Rename · Group ·
  Info**. Saving an empty name in **Rename** brings back the original one.
  - **Group** opens a sheet named after the app, with two parts:
    - **Emphasize** (the switch at the top) makes the app bold and first
      in its group; the other apps in that group fold into one line. It
      does not change which group the app is in.
    - **Groups** lists every group with its glyph and shows where the app
      is now — the automatic pick is already ticked. Tick several to have
      the app appear under each, then **Save**. **Automatic** clears your
      picks and lets the launcher decide again.
  - **Long-press the colored glyph** next to any app to toggle emphasis
    without opening the menu; a short toast confirms it. Tapping the glyph
    launches the app like the rest of the row.
- With **TalkBack**, each app reads its name with its group and whether it
  is new, in a work profile, or in Private Space. The menu items and the
  emphasis toggle are offered as actions on the app, no long-press needed.
- If your device has a **Private Space**, it appears at the bottom of the
  drawer with a tap-to-unlock row.
- Swipe down from the search bar or the top of the list, or go back, to put
  your apps away. Pressing the home button or opening an app does too.

## Smart ordering

The order of the groups is not fixed — it follows the time of day (news
surfaces in the morning, focus apps during work hours, media in the evening,
sleep and meditation apps such as Headspace or Calm from about 8:30 pm)
and quietly learns from what you actually open. Learning happens entirely on
this device, is never sent anywhere, and fades after a couple of weeks. Apps
stay alphabetical inside each group, with emphasized apps first.

In **Settings → Smart ordering**:

- **Pinned groups** — pin any number of groups to always stay on top, in the
  order you tap them (numbered as you pick). AI Agents is pinned by default.
- **Right now** — a read-only peek at which groups the launcher would
  surface first at this moment.
- **App groups → Refresh** — clears all manual group choices and
  re-categorizes every app automatically (asks before doing it).
- **Usage learning → Reset** — forgets everything learned from your
  launches (also asks first).

## Settings reference

Long-press anywhere on the home screen to get here.

- **Blauncher card** — set/change default launcher, app info, and:
  - **Help and FAQ**: every gesture on one page, the questions below, and
    **Show tips again**.
  - **Send feedback**: opens a new issue on the project's GitHub page in
    your browser, for bugs and ideas.
  - **Share Blauncher**: the system share sheet with a line and the Google
    Play link. Nothing is ever shared without you choosing it.
  - **Rate Blauncher**: opens Blauncher's Google Play page (in the Play
    Store app, or the browser if there is none) to rate or review it.
    Blauncher never asks you to rate it; this is only here if you want it.
- **Smart ordering** — see above.
- **Home screen** — **Shortcut app** (the app the button beside the search
  bar opens), **Shortcut icon** (the glyph it wears), **Search engine**
  (where the search bar sends web searches), **Bold date**, and the date's
  alignment (long-press *Date alignment* to also apply it to your apps).
- **Appearance** — theme (long-press *Theme* for the System option) and
  text size, which scales on top of the system font size rather than
  replacing it.
- **Gestures** — the swipe-up, swipe-left and swipe-right apps (long-press
  any of these rows to disable that gesture) and what swipe-down does
  (notifications or search).

## Questions

The same answers are in **Settings → Help and FAQ**.

- **How do I go back to my old launcher?** Settings → Change default
  launcher, then pick it. Android's own Settings → Apps → Default apps →
  Home app works too.
- **Where are the icons and widgets?** There are none, on purpose. Tap the
  search bar and type a few letters instead.
- **An app is in the wrong group.** Long-press it, choose **Group**, and
  tick the groups you want.
- **Why did the order of the groups change?** Smart ordering follows the
  time of day and the apps you open. Pin groups or reset the learning in
  Settings → Smart ordering.
- **Can I see fewer apps?** Emphasize the ones you use (long-press, Group,
  Emphasize). The rest of that group folds into one line, and search still
  finds them.
- **Does Blauncher go online?** No. It has no internet permission. A search
  opens in your browser, which does the searching.
- **Found a bug, or have an idea?** Settings → **Send feedback** opens a new
  issue on GitHub.

## Privacy notes

The app requests no internet access, uses no usage-stats permission, and its
data (including what smart ordering learns) never leaves the device — it is
even excluded from device backups. Details in `SECURITY.md`.
