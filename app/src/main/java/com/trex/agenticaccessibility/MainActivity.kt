package com.trex.agenticaccessibility

import android.Manifest
import android.content.Intent
import android.content.pm.PackageManager
import android.os.Bundle
import android.provider.Settings
import android.widget.*
import androidx.activity.ComponentActivity
import androidx.lifecycle.lifecycleScope
import com.trex.agenticaccessibility.agent.AgentController
import com.trex.agenticaccessibility.security.SecureStore
import com.trex.agenticaccessibility.voice.AndroidSpeechToText
import com.trex.agenticaccessibility.voice.AndroidTextToSpeech
import kotlinx.coroutines.launch

class MainActivity : ComponentActivity() {
    private lateinit var controller: AgentController
    private lateinit var task: EditText
    private lateinit var speech: AndroidSpeechToText

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val store = SecureStore(this)
        controller = AgentController(this, store, AndroidTextToSpeech(this))
        speech = AndroidSpeechToText(this)

        val root = LinearLayout(this).apply { orientation = LinearLayout.VERTICAL; setPadding(28, 28, 28, 28) }
        val status = TextView(this).apply { text = "● Ready"; textSize = 20f }
        task = EditText(this).apply { hint = "Tell the agent what to do…"; minLines = 3; gravity = 48 }
        val privacy = TextView(this).apply { text = "Privacy: relevant screen/UI state is sent to your configured AI endpoint. Do not use this app for passwords or other secrets." }
        val endpoint = EditText(this).apply { hint = "OpenAI-compatible endpoint"; setText(store.get("endpoint") ?: "https://api.openai.com/v1/chat/completions") }
        val model = EditText(this).apply { hint = "Model"; setText(store.get("model") ?: "gpt-4o-mini") }
        val key = EditText(this).apply { hint = "API key"; inputType = 0x81 }
        val start = Button(this).apply { text = "Start Agent" }
        val stop = Button(this).apply { text = "Stop" }
        val access = Button(this).apply { text = "Accessibility Settings" }
        val clear = Button(this).apply { text = "Clear task history" }
        val mic = Button(this).apply { text = "🎙 Push to talk" }
        val log = TextView(this).apply { text = "Activity log\n" }
        listOf(status, task, privacy, endpoint, model, key, start, stop, access, clear, mic, log).forEach { root.addView(it) }
        setContentView(root)

        access.setOnClickListener { startActivity(Intent(Settings.ACTION_ACCESSIBILITY_SETTINGS)) }
        start.setOnClickListener {
            speech.cancel()
            store.put("endpoint", endpoint.text.toString().trim())
            store.put("model", model.text.toString().trim())
            if (key.text.isNotBlank()) store.put("api_key", key.text.toString())
            controller.start(task.text.toString())
        }
        stop.setOnClickListener { speech.cancel(); controller.stop(); status.text = "■ Stopped" }
        clear.setOnClickListener { controller.clearHistory() }
        mic.setOnClickListener {
            if (checkSelfPermission(Manifest.permission.RECORD_AUDIO) != PackageManager.PERMISSION_GRANTED) {
                requestPermissions(arrayOf(Manifest.permission.RECORD_AUDIO), 42)
            } else listen()
        }
        lifecycleScope.launch {
            controller.events.collect {
                status.text = it.status
                log.text = "Activity log\n" + it.log.joinToString("\n")
            }
        }
    }

    private fun listen() {
        speech.listen(onResult = { spoken ->
            runOnUiThread {
                val command = spoken.lowercase(Locale.getDefault()).trim().replace(Regex("\\s+"), " ")
                when {
                    command in setOf("stop", "stop agent", "cancel", "cancel task", "don't post it", "do not post it") -> {
                        controller.stop(); task.setText("")
                    }
                    command in setOf("go back", "back") -> controller.back()
                    command in setOf("wait", "pause") -> { }
                    else -> { task.setText(spoken); controller.start(spoken) }
                }
            }
        })
    }

    override fun onRequestPermissionsResult(requestCode: Int, permissions: Array<out String>, grantResults: IntArray) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == 42 && grantResults.firstOrNull() == PackageManager.PERMISSION_GRANTED) listen()
    }

    override fun onDestroy() {
        speech.cancel()
        controller.stop()
        super.onDestroy()
    }
}
