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

The home screen shows a clock, the date, and up to eight apps of your
choosing. Everything else is gestures:

| Gesture | What it does |
| --- | --- |
| **Long-press empty space** | Opens **Settings** — this is the big one to know |
| Swipe up | Opens the app drawer |
| Swipe down | Notification shade (or search — configurable) |
| Swipe left | Opens the camera (configurable) |
| Swipe right | Opens the phone dialer (configurable) |
| Tap the clock | Opens your clock app |
| Tap the date | Opens your calendar |

- **Tap** a home-screen app to launch it. **Long-press** it to put a
  different app in that slot; while choosing, type a name and tap **Rename**
  to relabel the slot.
- **Long-press** the clock or date to choose which app they open.
- The number of home apps (0–8), their alignment, and the status bar are all
  in Settings.

## The app drawer

Swipe up from the home screen. Apps are listed in **groups** (AI Agents,
People, Focus, News, Media, and so on), each marked with a small colored
glyph, alphabetical within the group.

- **Search first**: the keyboard opens automatically. Type a few letters —
  if exactly one app matches, it launches by itself. Press enter to launch
  the first match. Start with a space to browse without auto-launch.
- No matches? Enter searches the web. Start the query with `!` to search
  DuckDuckGo directly.
- **Long-press an app** for its menu: **Uninstall · Rename · Group · Hide ·
  Info**.
  - **Group** lets you pick which group(s) the app appears under — pick
    several to have it show up in each, or **Automatic** to let the
    launcher decide again.
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
stay alphabetical inside each group, so nothing jumps around within a group.

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
- **Home screen** — number of home apps, alignment (long-press *Alignment*
  to also apply it to the app drawer), bottom alignment, status bar, date &
  time visibility.
- **Appearance** — theme (long-press *Theme* for the System option) and
  text size.
- **Gestures** — the swipe-left and swipe-right apps (long-press either row
  to disable that gesture) and what swipe-down does (notifications or
  search).

## Privacy notes

The app requests no internet access, uses no usage-stats permission, and its
data (including what smart ordering learns) never leaves the device — it is
even excluded from device backups. Details in `SECURITY.md`.
