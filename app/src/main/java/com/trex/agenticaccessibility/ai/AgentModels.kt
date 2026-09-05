package com.trex.agenticaccessibility.ai

enum class ActionType { TAP, TYPE, CLEAR_TEXT, SCROLL, SWIPE, BACK, HOME, OPEN_APP, WAIT, OBSERVE, SCREENSHOT, FINISH_TASK, REQUEST_USER_CONFIRMATION }

data class AgentAction(
    val type: ActionType,
    val target: String? = null,
    val text: String? = null,
    val direction: String? = null,
    val x: Float? = null,
    val y: Float? = null,
    val packageName: String? = null,
    val durationMs: Long = 400,
    val message: String? = null,
    val explain: String? = null
)

data class UiElement(val index: Int, val type: String, val text: String?, val hint: String?, val contentDescription: String?, val resourceId: String?, val clickable: Boolean, val editable: Boolean, val scrollable: Boolean, val bounds: String)
data class UiSnapshot(val packageName: String, val windowTitle: String?, val elements: List<UiElement>, val fingerprint: String)
