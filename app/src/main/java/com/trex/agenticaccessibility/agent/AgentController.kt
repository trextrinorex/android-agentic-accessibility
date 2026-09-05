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
    data class UiState(val status:String,val log:List<String>)
    private val state=MutableStateFlow(UiState("● Ready",emptyList()));val events:StateFlow<UiState>=state.asStateFlow();private val scope=CoroutineScope(SupervisorJob()+Dispatchers.Main.immediate);private var job:Job?=null;private val memory=TaskMemory()
    fun start(goal:String){if(goal.isBlank())return;stop();job=scope.launch{memory.start(goal);emit("● Running","Goal: $goal");val endpoint=store.get("endpoint")?:return@launch fail("Configure an LLM endpoint.");val key=store.get("api_key")?:return@launch fail("Configure an API key.");val model=store.get("model")?:"gpt-4o-mini";val client=LlmClient(endpoint,key,model);var cycles=0;while(isActive&&cycles++<40){val screen=AccessibilityBridge.observe()?:return@launch fail("Enable the Accessibility Service first.");emit("● Thinking","${screen.packageName}: ${screen.elements.size} UI elements");val action=try{client.nextAction(goal,screen,memory.history())}catch(e:Exception){return@launch fail("AI error: ${e.message?:"request failed"}")};emit("● Planning",action.type.name);if(action.type==ActionType.FINISH_TASK){emit("✓ Complete",action.message?:"Task complete");tts.speak(action.message?:"Done");return@launch};if(SafetyManager.requiresConfirmation(action)&&!SafetyManager.confirm(context,action)){emit("■ Cancelled","User cancelled the sensitive action");tts.speak("Cancelled");return@launch};val result=AccessibilityBridge.execute(action);memory.add(action,result);emit(if(result.success)"● Action complete" else "⚠ Action failed",result.message);delay(if(result.success)450 else 700)};fail("Stopped after the agent safety limit.")}}
    fun stop(){job?.cancel();job=null;tts.stop()}
    fun clearHistory(){memory.clear();state.value=UiState("● Ready",emptyList())}
    private fun emit(status:String,line:String){state.value=UiState(status,(state.value.log+line).takeLast(80))}
    private fun fail(message:String){emit("⚠ Error",message);tts.speak(message)}
}
