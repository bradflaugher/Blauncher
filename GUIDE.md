# Blauncher User Guide

Blauncher is a text-only launcher with no icons, no dock, and no visible
buttons. Everything is a gesture or a tap on text. This page is the tour the
app itself deliberately doesn't give you.

## Install and set up

1. Download `Blauncher.apk` from the
   [latest release](https://github.com/bradflaugher/Blauncher/releases/tag/latest)
   (optionally verify it against `Blauncher.apk.sha256` — see `SECURITY.md`).
2. Install it (you may need to allow installs from your browser or file
   manager), then open it and tap **Set as default launcher**.
3. Requires Android 17 (API 37) or newer.

## The home screen

The home screen is the date at the top and, along the bottom, a search bar
beside a round key glyph that opens your password manager. The notification
bar is always visible and the middle of the screen is empty on purpose:
everything else is gestures.

| Gesture | What it does |
| --- | --- |
| **Long-press empty space** | Opens **Settings** — this is the big one to know |
| Swipe up | Opens the app drawer |
| Swipe down | Notification shade (or search — configurable) |
| Swipe left | Opens the camera (configurable) |
| Swipe right | Opens the phone dialer (configurable) |
| Tap the date | Opens your calendar |

- **Search bar** — tap it and type. It is a small composer, not a one-line
  field: enter starts a new line, the box grows to a few lines and then
  scrolls, and there is no length limit, so whole paragraphs are fine. While
  there is text, the key glyph beside the bar becomes a filled **send**
  button; tap it (or Ctrl+Enter / Shift+Enter on a hardware keyboard) and
  the results page opens in your default browser in one step. An **×** at
  the end of the bar clears the text.
  - The results come from the **search engine** picked in Settings → Home
    screen: divid3 (a private search router) by default, or DuckDuckGo,
    Google, Bing, Brave Search, Kagi, Startpage, Ecosia, Perplexity, or
    ChatGPT. The launcher builds the
    results URL itself and opens it, so nothing waits for a second enter.
  - **Browser default** is also there: it hands the raw text to the browser
    as a web search and lets the browser choose the engine. Some browsers
    only put the text in their address bar and wait for enter, which is
    why it is not the default. Failing everything, DuckDuckGo opens.
  - Unsent text is a **draft**: it stays if you tap elsewhere, open the
    drawer or settings, switch apps to copy something, rotate, or the
    launcher restarts. Only a successful send or the **×** button empties
    the bar, and if no app could take the search the text stays put.
- **Password manager** — the key glyph beside the search bar launches your
  password manager. On first run it binds to a known password manager
  already installed (Bitwarden, 1Password, Proton Pass, KeePassDX,
  Keepass2Android, Enpass, Keeper, LastPass, Dashlane, NordPass); until one
  is bound the glyph is drawn faded and tapping it opens the picker.
  **Long-press** the glyph to pick any other app; the same choice lives in
  Settings → Home screen.
- **Long-press** the date to choose which app it opens.
- The date's alignment (left, center, right) is in Settings.

## The app drawer

Swipe up from the home screen. Apps are listed in **groups** (AI Agents,
People, Focus, News, Media, and so on), each marked with a small colored
glyph, alphabetical within the group. An **emphasized** app is bold and
sits at the top of its group. Once a group has one, its other apps fold
into a single faded line under the bold ones — `+5 · Gemini · Perplexity ·
Poe…`, the count in the group's color — so the apps you chose stand out
without a long tail below them.

- **Tap the folded line** to expand the group in place; the row turns into
  **− fewer** and tapping it again folds the group back. Expansion lasts
  for the current visit only: the drawer always opens compact.
- Folded apps are never out of reach: **search matches every app**, folded
  or not, and a freshly installed app stays visible in its group until it is
  an hour old.
- Groups without an emphasized app list every app as before.

- **Search first**: the keyboard opens automatically. Type a few letters —
  if exactly one app matches, it launches by itself. Press enter to launch
  the first match. Start with a space to browse without auto-launch.
- No matches? Enter searches the web. Start the query with `!` to search
  DuckDuckGo directly.
- **Long-press an app** for its menu: **Uninstall · Rename · Group ·
  Info**.
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
- If your device has a **Private Space**, it appears at the bottom of the
  drawer with a tap-to-unlock row.
- Swipe down from the top of the list to close the drawer.

## Smart ordering

The order of the groups is not fixed — it follows the time of day (news
surfaces in the morning, focus apps during work hours, media in the evening)
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

- **Blauncher card** — set/change default launcher, app info.
- **Smart ordering** — see above.
- **Home screen** — **Password manager** (the app the key glyph opens),
  **Search engine** (where the search bar sends its text), **Bold date**,
  and the date's alignment (long-press *Date alignment* to also apply it to
  the app drawer).
- **Appearance** — theme (long-press *Theme* for the System option) and
  text size.
- **Gestures** — the swipe-left and swipe-right apps (long-press either row
  to disable that gesture) and what swipe-down does (notifications or
  search).

## Privacy notes

The app requests no internet access, uses no usage-stats permission, and its
data (including what smart ordering learns) never leaves the device — it is
even excluded from device backups. Details in `SECURITY.md`.
