package com.trex.agenticaccessibility

import android.content.Intent
import android.os.Bundle
import android.provider.Settings
import android.widget.Toast
import androidx.activity.ComponentActivity
import com.trex.agenticaccessibility.security.SecureStore

class SettingsActivity : ComponentActivity() {
    override fun onCreate(state: Bundle?) { super.onCreate(state); val store=SecureStore(this); val open=android.widget.Button(this).apply{ text="Open Accessibility Settings"; setOnClickListener{startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS))} }; setContentView(open) }
}
