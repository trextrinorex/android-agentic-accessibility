package com.trex.agenticaccessibility.agent

import android.content.Context
import com.trex.agenticaccessibility.accessibility.AccessibilityBridge
import com.trex.agenticaccessibility.ai.ActionType
import com.trex.agenticaccessibility.ai.LlmClient
import com.trex.agenticaccessibility.security.SecureStore
import com.trex.agenticaccessibility.safety.SafetyManager
import com.trex.agenticaccessibility.voice.AndroidTextToSpeech
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow

class AgentController(private val context: Context, private val store: SecureStore, private val tts: AndroidTextToSpeech) {
    data class UiState(val status: String, val log: List<String>)

    private val state = MutableStateFlow(UiState("● Ready", emptyList()))
    val events: StateFlow<UiState> = state.asStateFlow()
    private val scope = CoroutineScope(SupervisorJob() + Dispatchers.Main.immediate)
    private var job: Job? = null
    private val memory = TaskMemory()

    fun start(goal: String) {
        if (goal.isBlank()) return
        stop()
        SafetyManager.beginTask()
        job = scope.launch {
            memory.start(goal)
            emit("● Running", "Goal: $goal")
            val endpoint = store.get("endpoint") ?: return@launch fail("Configure an LLM endpoint.")
            val key = store.get("api_key") ?: return@launch fail("Configure an API key.")
            val model = store.get("model") ?: "gpt-4o-mini"
            val client = LlmClient(endpoint, key, model)
            var cycles = 0
            var unchanged = 0

            while (isActive && cycles++ < 40) {
                val before = AccessibilityBridge.observe() ?: return@launch fail("Enable the Accessibility Service first.")
                emit("● Thinking", "${before.packageName}: ${before.elements.size} UI elements")

                val action = try {
                    client.nextAction(goal, before, memory.history())
                } catch (e: Exception) {
                    return@launch fail("AI error: ${e.message ?: "request failed"}")
                }
                emit("● Planning", action.type.name)

                if (action.type == ActionType.FINISH_TASK) {
                    emit("✓ Complete", action.message ?: "Task complete")
                    tts.speak(action.message ?: "Done")
                    return@launch
                }

                if (SafetyManager.requiresConfirmation(action) && !SafetyManager.confirm(context, action)) {
                    emit("■ Cancelled", "User cancelled the sensitive action")
                    tts.speak("Cancelled")
                    return@launch
                }

                if (action.type == ActionType.WAIT) {
                    delay(action.durationMs.coerceIn(250, 5000))
                    memory.add(action, ActionResult(true, "Waited ${action.durationMs.coerceIn(250, 5000)} ms"))
                } else {
                    val result = AccessibilityBridge.executeAsync(action)
                    memory.add(action, result)
                    emit(if (result.success) "● Action complete" else "⚠ Action failed", result.message)
                    if (!result.success) {
                        delay(500)
                        continue
                    }
                    delay(action.durationMs.coerceIn(350, 1500))
                }

                val after = AccessibilityBridge.observe()
                if (after == null) return@launch fail("Lost the active Accessibility window.")
                if (after.fingerprint == before.fingerprint && action.type != ActionType.OBSERVE) {
                    unchanged++
                    emit("↻ No UI change", "Attempt $unchanged; planner will adapt")
                    if (unchanged >= 4) return@launch fail("The screen did not change after several actions; stopping safely.")
                } else {
                    unchanged = 0
                    emit("● Observed", "UI changed; continuing")
                }
            }
            fail("Stopped after the agent safety limit.")
        }
    }

    fun stop() {
        job?.cancel()
        job = null
        tts.stop()
    }

    fun back() {
        AccessibilityBridge.execute( com.trex.agenticaccessibility.ai.AgentAction(ActionType.BACK) )
    }

    fun clearHistory() {
        memory.clear()
        SafetyManager.beginTask()
        state.value = UiState("● Ready", emptyList())
    }

    private fun emit(status: String, line: String) {
        state.value = UiState(status, (state.value.log + line).takeLast(80))
    }

    private fun fail(message: String) {
        emit("⚠ Error", message)
        tts.speak(message)
    }
}
