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
  scrolls, and there is no length limit, so whole paragraphs are fine. Send
  with the **arrow** at the end of the bar (or Ctrl+Enter / Shift+Enter on a
  hardware keyboard). The text goes to your **default browser**, which
  searches with the engine you chose inside that browser (Chrome, Firefox,
  Brave, Edge, and most others accept searches this way). If the browser
  cannot take a search, the system web-search handler gets it instead, and
  failing that DuckDuckGo opens in the browser.
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
sits at the top of its group; once a group has one, its other apps fade
back so the ones you chose stand out.

- **Search first**: the keyboard opens automatically. Type a few letters —
  if exactly one app matches, it launches by itself. Press enter to launch
  the first match. Start with a space to browse without auto-launch.
- No matches? Enter searches the web. Start the query with `!` to search
  DuckDuckGo directly.
- **Long-press an app** for its menu: **Uninstall · Rename · Group · Hide ·
  Info**.
  - **Group** opens a sheet named after the app, with two parts:
    - **Emphasize** (the switch at the top) makes the app bold and first
      in its group; the other apps in that group fade back. It does not
      change which group the app is in.
    - **Groups** lists every group with its glyph and shows where the app
      is now — the automatic pick is already ticked. Tick several to have
      the app appear under each, then **Save**. **Automatic** clears your
      picks and lets the launcher decide again.
  - **Long-press the colored glyph** next to any app to toggle emphasis
    without opening the menu; a short toast confirms it. Tapping the glyph
    launches the app like the rest of the row.
  - **Hide** removes it from the drawer; find it later under Settings →
    **Hidden apps**, where the same menu shows **Show** to bring it back.
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

- **Blauncher card** — hidden apps, set/change default launcher, app info.
- **Smart ordering** — see above.
- **Home screen** — **Password manager** (the app the key glyph opens),
  **Bold date**, and the date's alignment (long-press *Date alignment* to also
  apply it to the app drawer).
- **Appearance** — theme (long-press *Theme* for the System option) and
  text size.
- **Gestures** — the swipe-left and swipe-right apps (long-press either row
  to disable that gesture) and what swipe-down does (notifications or
  search).

## Privacy notes

The app requests no internet access, uses no usage-stats permission, and its
data (including what smart ordering learns) never leaves the device — it is
even excluded from device backups. Details in `SECURITY.md`.
