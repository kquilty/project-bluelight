package com.projectbluelight

import android.Manifest
import android.content.pm.PackageManager
import android.os.Bundle
import android.widget.TextView
import androidx.activity.ComponentActivity
import androidx.activity.result.contract.ActivityResultContracts
import androidx.core.content.ContextCompat
import androidx.glance.appwidget.updateAll
import androidx.lifecycle.lifecycleScope
import kotlinx.coroutines.launch

// The whole app screen, for now. It asks for permission to read your calendar,
// then nudges the widget to refresh. Down the road this becomes the settings
// screen where you set each event's visibility window with a slider.
class MainActivity : ComponentActivity() {

    private val requestPermission =
        registerForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
            showStatus(granted)
            if (granted) refreshWidget()
        }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        if (hasCalendarPermission()) {
            showStatus(true)
            refreshWidget()
        } else {
            requestPermission.launch(Manifest.permission.READ_CALENDAR)
        }
    }

    private fun hasCalendarPermission(): Boolean =
        ContextCompat.checkSelfPermission(this, Manifest.permission.READ_CALENDAR) ==
            PackageManager.PERMISSION_GRANTED

    private fun showStatus(granted: Boolean) {
        findViewById<TextView>(R.id.status).text =
            if (granted) {
                "Calendar access granted.\nAdd the Project Bluelight widget to your home screen."
            } else {
                "Calendar access is needed to show upcoming events.\nTap back in here, or enable it in Settings, to try again."
            }
    }

    private fun refreshWidget() {
        lifecycleScope.launch {
            BluelightWidget().updateAll(applicationContext)
        }
    }
}
