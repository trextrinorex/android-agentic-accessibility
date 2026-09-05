package com.trex.agenticaccessibility.ai

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import org.json.JSONArray
import org.json.JSONObject
import java.net.HttpURLConnection
import java.net.URL

class LlmClient(private val endpoint: String, private val apiKey: String, private val model: String) {
    suspend fun nextAction(goal: String, state: UiSnapshot, history: List<String>): AgentAction = withContext(Dispatchers.IO) {
        val system = """You are an Android UI agent. Choose exactly ONE next action as JSON. Never invent UI state. Prefer semantic targets. Valid types: TAP, TYPE, CLEAR_TEXT, SCROLL, SWIPE, BACK, HOME, OPEN_APP, WAIT, OBSERVE, SCREENSHOT, FINISH_TASK, REQUEST_USER_CONFIRMATION. TAP target should be the exact visible text, content description, or resource id. For irreversible/public communication, return REQUEST_USER_CONFIRMATION. If the goal is complete, return FINISH_TASK with message. JSON only: {\"type\":\"TAP\",\"target\":\"...\",\"explain\":\"...\"}"""
        val stateJson = JSONObject().apply { put("package", state.packageName); put("window", state.windowTitle ?: ""); put("elements", JSONArray(state.elements.map { JSONObject().apply { put("index",it.index); put("type",it.type); put("text",it.text ?: ""); put("hint",it.hint ?: ""); put("contentDescription",it.contentDescription ?: ""); put("resourceId",it.resourceId ?: ""); put("clickable",it.clickable); put("editable",it.editable); put("scrollable",it.scrollable); put("bounds",it.bounds) } })) }
        val user = JSONObject().apply { put("goal", goal); put("screen", stateJson); put("history", JSONArray(history)) }.toString()
        val body = JSONObject().apply { put("model", model); put("temperature", 0); put("response_format", JSONObject().put("type", "json_object")); put("messages", JSONArray().put(JSONObject().put("role","system").put("content",system)).put(JSONObject().put("role","user").put("content",user))) }.toString()
        val conn = (URL(endpoint).openConnection() as HttpURLConnection).apply { requestMethod="POST"; doOutput=true; connectTimeout=15000; readTimeout=30000; setRequestProperty("Authorization","Bearer $apiKey"); setRequestProperty("Content-Type","application/json") }
        conn.outputStream.use { it.write(body.toByteArray(Charsets.UTF_8)) }
        val text = (if (conn.responseCode in 200..299) conn.inputStream else conn.errorStream).bufferedReader().use { it.readText() }
        if (conn.responseCode !in 200..299) error("HTTP ${conn.responseCode}: ${text.take(300)}")
        parseResponse(JSONObject(text))
    }

    private fun parseResponse(root: JSONObject): AgentAction {
        val content = root.optJSONArray("choices")?.optJSONObject(0)?.optJSONObject("message")?.optString("content") ?: error("LLM returned no action")
        val j = JSONObject(content.trim().removePrefix("```").removePrefix("json").removeSuffix("```").trim())
        val type = runCatching { ActionType.valueOf(j.optString("type").uppercase()) }.getOrElse { error("Invalid action type") }
        return AgentAction(type=type, target=j.optString("target").takeIf { it.isNotBlank() }, text=j.optString("text").takeIf { it.isNotBlank() }, direction=j.optString("direction").takeIf { it.isNotBlank() }, x=j.optDouble("x", Double.NaN).takeUnless { it.isNaN() }?.toFloat(), y=j.optDouble("y", Double.NaN).takeUnless { it.isNaN() }?.toFloat(), packageName=j.optString("package").takeIf { it.isNotBlank() }, durationMs=j.optLong("durationMs",400), message=j.optString("message").takeIf { it.isNotBlank() }, explain=j.optString("explain").takeIf { it.isNotBlank() })
    }
}
