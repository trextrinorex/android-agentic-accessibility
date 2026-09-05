package com.trex.agenticaccessibility.agent

import com.trex.agenticaccessibility.ai.AgentAction

class TaskMemory {
    private var goal: String = ""
    private val entries = mutableListOf<String>()
    fun start(newGoal: String) { goal = newGoal; entries.clear() }
    fun add(action: AgentAction, result: ActionResult) { entries += "${action.type.name}: ${action.target ?: action.text ?: ""} -> ${result.message}" }
    fun history(): List<String> = entries.takeLast(12)
    fun clear() { goal = ""; entries.clear() }
}

data class ActionResult(val success: Boolean, val message: String)
