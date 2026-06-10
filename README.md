# Project Bluelight

A lightweight Android home-screen widget that lays a "visibility window" over your
existing calendar: each event only starts showing once it's within the number of days
you chose for it. (Codename — public name TBD.)

## How it works

- The **widget** shows the soonest upcoming event that has entered its visibility
  window, as a countdown ("25 days / until Christmas"). It refreshes daily, and
  whenever you change settings. Tap it to open the app.
- The **app** is the settings screen: it lists every event on your calendar for the
  next year, each with a slider for how far ahead it should appear on the widget
  (1 day to 365, or hidden entirely). Untouched events default to 30 days out.
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
    ├── build.gradle.kts        ← dependencies (Jetpack Glance, RecyclerView)
    └── src/main/
        ├── AndroidManifest.xml ← registers the widget + READ_CALENDAR
        ├── java/com/projectbluelight/
        │   ├── BluelightWidget.kt          ← the tile (Glance UI + which event to show)
        │   ├── BluelightWidgetReceiver.kt  ← hooks the widget into Android
        │   ├── CalendarSource.kt           ← reads the next year of events
        │   ├── EventWindows.kt             ← per-event visibility windows (SharedPreferences)
        │   └── MainActivity.kt             ← permission prompt + settings screen
        └── res/
            ├── layout/                     ← settings screen + event row
            ├── xml/bluelight_widget_info.xml ← widget size + refresh
            └── values/strings.xml
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
