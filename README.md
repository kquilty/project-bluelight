# Project Bluelight

A lightweight Android home-screen widget that lays a "visibility window" over your
existing calendar: each event only starts showing once it's within the number of days
you chose for it. (Codename — public name TBD.)

This is the **widget shell**: it draws a single hardcoded tile ("25 days until Christmas")
so we can confirm the widget pipeline works on a real phone before wiring in the calendar.

## What's here

```
project-bluelight/
├── settings.gradle.kts
├── build.gradle.kts            ← plugin versions
├── gradle.properties
├── gradle/wrapper/             ← Gradle version pointer (Android Studio finishes setup)
└── app/
    ├── build.gradle.kts        ← dependencies (Jetpack Glance)
    └── src/main/
        ├── AndroidManifest.xml ← registers the widget
        ├── java/com/projectbluelight/
        │   ├── BluelightWidget.kt          ← the tile (all the UI is here)
        │   └── BluelightWidgetReceiver.kt  ← hooks the widget into Android
        └── res/
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
5. Long-press your home screen → **Widgets** → find **Project Bluelight** → drag the
   tile onto the screen.

## Publish to GitHub (optional)

From inside this folder:

```
git init
git add .
git commit -m "Widget shell"
git branch -M main
git remote add origin https://github.com/<your-username>/project-bluelight.git
git push -u origin main
```

(Create the empty `project-bluelight` repo on github.com first, without a README.)
