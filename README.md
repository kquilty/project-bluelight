# Project Bluelight

A lightweight Android home-screen widget that lists what you should be thinking
about: each calendar event surfaces once it's within the lead time you gave it —
your niece's birthday two weeks out (gifts take shopping time), the dentist one day
out. (Codename — public name TBD.)

## How it works

- The **widget** is a scrollable list of every event that has entered its visibility
  window, soonest first, each with a countdown ("12 days · Maya's birthday"). Events
  appear as their lead time begins and fall off once they pass. It refreshes daily,
  and whenever you change settings. Tap it to open the app.
- The **app** (full Jetpack Compose, Material 3, permanently dark) sorts your next
  year into three sections: *On your widget*, *Waiting their turn*, and *Everything
  else*. Tap any event and a bottom sheet asks the only question the product has:
  **when does this start mattering?** — chips from 1 day to 1 year, with a live
  preview line ("Will surface Dec 1 — 24 days ahead"). Everything starts hidden;
  you promote what deserves headspace, so recurring noise (standups, gym) never
  shows up uninvited.
- Recurring events (birthdays, holidays) count down to their **next** occurrence,
  and one slider covers every occurrence.
- Windows are stored on-device per event ID. Calendar access is read-only.

## What's here

```
project-bluelight/
├── settings.gradle.kts
├── build.gradle.kts            ← plugin versions
├── gradle.properties
├── gradle/wrapper/             ← Gradle version pointer (Android Studio finishes setup)
└── app/
    ├── build.gradle.kts        ← dependencies (Jetpack Glance, Compose + Material 3)
    └── src/main/
        ├── AndroidManifest.xml ← registers the widget + READ_CALENDAR
        ├── java/com/projectbluelight/
        │   ├── BluelightWidget.kt          ← the tile (Glance UI + window filtering)
        │   ├── BluelightWidgetReceiver.kt  ← hooks the widget into Android
        │   ├── CalendarSource.kt           ← reads the next year of events
        │   ├── EventWindows.kt             ← per-event lead times (SharedPreferences)
        │   ├── MainActivity.kt             ← the whole app: onboarding + lead-time list
        │   └── ui/Theme.kt                 ← Bluelight palette + Material 3 theme
        └── res/
            ├── drawable/, mipmap-anydpi-v26/ ← adaptive launcher icon
            ├── xml/bluelight_widget_info.xml ← widget size + refresh
            └── values/                     ← strings, colors, window theme
```

## Build & install (Android Studio)

1. Install **Android Studio** (it bundles the JDK and Android SDK).
2. `File > Open` this folder. Let Gradle sync finish — if it offers to update
   Gradle / the Android Gradle Plugin, accept.
3. On your phone: `Settings > About phone`, tap **Build number** 7 times to unlock
   Developer options, then enable **USB debugging**. Plug the phone in and allow the
   prompt.
4. Pick your phone in the device dropdown and press **Run** (▶). It installs the app.
5. Open the app once and grant calendar access, then set each event's window.
6. Long-press your home screen → **Widgets** → find **Project Bluelight** → drag the
   tile onto the screen.

## Publish to GitHub (optional)

From inside this folder:

```
git branch -M main
git remote add origin https://github.com/<your-username>/project-bluelight.git
git push -u origin main
```

(Create the empty `project-bluelight` repo on github.com first, without a README.)
