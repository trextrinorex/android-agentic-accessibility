package com.trex.agenticaccessibility.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.graphics.Rect
import android.os.Build
import android.os.Bundle
import android.view.accessibility.AccessibilityNodeInfo
import com.trex.agenticaccessibility.agent.ActionResult
import com.trex.agenticaccessibility.ai.*
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume
import java.util.concurrent.Executor

object AccessibilityBridge {
    @Volatile private var service: AccessibilityService? = null
    fun attach(s: AccessibilityService) { service = s }
    fun detach() { service = null }
    fun onEvent(event: android.view.accessibility.AccessibilityEvent?) { }

    fun observe(): UiSnapshot? {
        val s = service ?: return null
        val root = s.rootInActiveWindow ?: return null
        return try { UiObserver.snapshot(root, root.packageName?.toString() ?: "unknown") }
        finally { root.recycle() }
    }

    suspend fun executeAsync(a: AgentAction): ActionResult {
        if (a.type != ActionType.SCREENSHOT) return execute(a)
        val s = service ?: return ActionResult(false, "Accessibility service unavailable")
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.R) return ActionResult(false, "Screenshot capture requires Android 11+")
        return suspendCancellableCoroutine { cont ->
            try {
                s.takeScreenshot(
                    AccessibilityService.SCREENSHOT_TYPE_FULL_SCREEN,
                    Executor { command -> command.run() }
                ) { result ->
                    if (cont.isActive) {
                        result?.let { it.bitmap.recycle() }
                        cont.resume(ActionResult(true, "Screenshot captured for agent use; it was not persisted"))
                    }
                }
                cont.invokeOnCancellation { }
            } catch (e: Exception) {
                if (cont.isActive) cont.resume(ActionResult(false, e.message ?: "Screenshot failed"))
            }
        }
    }

    fun execute(a: AgentAction): ActionResult {
        val s = service ?: return ActionResult(false, "Accessibility service unavailable")
        return try {
            when (a.type) {
                ActionType.TAP -> tap(s, a)
                ActionType.TYPE -> type(s, a)
                ActionType.CLEAR_TEXT -> type(s, a.copy(text = ""))
                ActionType.SCROLL -> scroll(s, a.direction ?: "down")
                ActionType.SWIPE -> swipe(s, a.x ?: 500f, a.y ?: 900f, a.durationMs)
                ActionType.BACK -> ActionResult(s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK), "Back")
                ActionType.HOME -> ActionResult(s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME), "Home")
                ActionType.OPEN_APP -> openApp(s, a.packageName ?: a.target ?: "")
                ActionType.WAIT -> ActionResult(true, "Wait requested")
                ActionType.OBSERVE -> ActionResult(true, "Observe requested")
                ActionType.SCREENSHOT -> ActionResult(false, "Use executeAsync for screenshot capture")
                ActionType.REQUEST_USER_CONFIRMATION -> ActionResult(false, "Confirmation required")
                ActionType.FINISH_TASK -> ActionResult(true, "Complete")
            }
        } catch (e: Exception) { ActionResult(false, e.message ?: "Action failed") }
    }

    private fun tap(s: AccessibilityService, a: AgentAction): ActionResult {
        val target = a.target?.trim()
        if (!target.isNullOrBlank()) {
            val r = s.rootInActiveWindow ?: return ActionResult(false, "No active window")
            val n = findBest(r, target)
            r.recycle()
            if (n != null) {
                val ok = if (n.isClickable || n.isFocusable) n.performAction(AccessibilityNodeInfo.ACTION_CLICK)
                else n.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false
                val bounds = Rect(); n.getBoundsInScreen(bounds); n.recycle()
                if (ok) return ActionResult(true, "Tapped $target")
                if (a.x == null || a.y == null) return tapAt(s, bounds.centerX().toFloat(), bounds.centerY().toFloat(), "Tapped $target by bounds")
            }
        }
        if (a.x != null && a.y != null) return tapAt(s, a.x, a.y, "Tapped at coordinates")
        return ActionResult(false, "UI target not found: ${target ?: "missing target"}")
    }

    private fun tapAt(s: AccessibilityService, x: Float, y: Float, message: String): ActionResult {
        val p = Path().apply { moveTo(x, y) }
        val gesture = GestureDescription.Builder()
            .addStroke(GestureDescription.StrokeDescription(p, 0, 120))
            .build()
        return ActionResult(s.dispatchGesture(gesture, null, null), message)
    }

    private fun findBest(n: AccessibilityNodeInfo, target: String): AccessibilityNodeInfo? {
        val candidates = ArrayList<Pair<Int, AccessibilityNodeInfo>>()
        collectMatches(n, target, candidates)
        return candidates.maxByOrNull { it.first }?.second.also { selected ->
            candidates.filter { it.second !== selected }.forEach { it.second.recycle() }
        }
    }

    private fun collectMatches(n: AccessibilityNodeInfo, target: String, out: MutableList<Pair<Int, AccessibilityNodeInfo>>) {
        if (out.size >= 20) return
        val values = listOf(n.text?.toString(), n.contentDescription?.toString(), n.viewIdResourceName)
        values.forEachIndexed { index, value ->
            val v = value?.trim() ?: return@forEachIndexed
            val score = when {
                v.equals(target, true) -> 100 - index * 5
                v.contains(target, true) -> 60 - index * 5
                target.contains(v, true) && v.length >= 3 -> 35 - index * 5
                else -> -1
            }
            if (score >= 0) out += score to AccessibilityNodeInfo.obtain(n)
        }
        for (i in 0 until n.childCount) n.getChild(i)?.let { child -> collectMatches(child, target, out); child.recycle() }
    }

    private fun type(s: AccessibilityService, a: AgentAction): ActionResult {
        if (!a.target.isNullOrBlank()) {
            val r = s.rootInActiveWindow ?: return ActionResult(false, "No active window")
            val target = findBest(r, a.target!!.trim()); r.recycle()
            if (target != null) {
                target.performAction(AccessibilityNodeInfo.ACTION_FOCUS)
                target.recycle()
            }
        }
        val r = s.rootInActiveWindow ?: return ActionResult(false, "No active window")
        val f = r.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: r.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        r.recycle()
        if (f == null || !f.isEditable) { f?.recycle(); return ActionResult(false, "No editable field focused") }
        val b = Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE, a.text ?: "") }
        val ok = f.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, b); f.recycle()
        return ActionResult(ok, if (ok) "Text field updated" else "Unable to set text")
    }

    private fun scroll(s: AccessibilityService, direction: String): ActionResult {
        val r = s.rootInActiveWindow ?: return ActionResult(false, "No active window")
        val n = findScrollable(r); r.recycle()
        if (n == null) return ActionResult(false, "No scrollable UI")
        val ok = n.performAction(if (direction.equals("up", true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD)
        n.recycle(); return ActionResult(ok, if (ok) "Scrolled $direction" else "Scroll failed")
    }

    private fun findScrollable(n: AccessibilityNodeInfo): AccessibilityNodeInfo? {
        if (n.isScrollable) return AccessibilityNodeInfo.obtain(n)
        for (i in 0 until n.childCount) n.getChild(i)?.let { c -> val f = findScrollable(c); c.recycle(); if (f != null) return f }
        return null
    }

    private fun swipe(s: AccessibilityService, x: Float, y: Float, d: Long): ActionResult {
        val p = Path().apply { moveTo(x, y); lineTo(x, y - 400) }
        val g = GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(p, 0, d.coerceIn(100, 2000))).build()
        return ActionResult(s.dispatchGesture(g, null, null), "Swipe dispatched")
    }

    private fun openApp(s: AccessibilityService, nameOrPackage: String): ActionResult {
        if (nameOrPackage.isBlank()) return ActionResult(false, "Missing app name")
        val pm = s.packageManager
        var pkg = nameOrPackage
        if (pm.getLaunchIntentForPackage(pkg) == null) {
            pkg = pm.getInstalledApplications(0).firstOrNull { pm.getApplicationLabel(it).toString().equals(nameOrPackage, true) }?.packageName
                ?: return ActionResult(false, "App not installed: $nameOrPackage")
        }
        val i = pm.getLaunchIntentForPackage(pkg) ?: return ActionResult(false, "No launcher activity: $pkg")
        i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); s.startActivity(i)
        return ActionResult(true, "Opening $nameOrPackage")
    }
}
