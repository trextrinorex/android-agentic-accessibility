package com.trex.agenticaccessibility.accessibility

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.graphics.Path
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.trex.agenticaccessibility.agent.ActionResult
import com.trex.agenticaccessibility.ai.*

object AccessibilityBridge {
    @Volatile private var service: AccessibilityService? = null
    @Volatile private var lastEvent: AccessibilityEvent? = null
    fun attach(s: AccessibilityService) { service = s }
    fun detach() { service = null }
    fun onEvent(e: AccessibilityEvent?) { lastEvent = e }
    fun observe(): UiSnapshot? { val s=service ?: return null; val root=s.rootInActiveWindow ?: return null; return try { UiObserver.snapshot(root, s.rootInActiveWindow?.packageName?.toString() ?: "unknown") } finally { root.recycle() } }
    fun execute(action: AgentAction): ActionResult {
        val s=service ?: return ActionResult(false,"Accessibility service unavailable")
        return try {
            when (action.type) {
                ActionType.TAP -> tap(s, action.target)
                ActionType.TYPE -> type(s, action.text ?: "")
                ActionType.CLEAR_TEXT -> clear(s)
                ActionType.SCROLL -> scroll(s, action.direction ?: "down")
                ActionType.SWIPE -> swipe(s, action.x ?: 500f, action.y ?: 500f, action.durationMs)
                ActionType.BACK -> ActionResult(s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_BACK), "Back")
                ActionType.HOME -> ActionResult(s.performGlobalAction(AccessibilityService.GLOBAL_ACTION_HOME), "Home")
                ActionType.OPEN_APP -> openApp(s, action.packageName ?: action.target ?: "")
                ActionType.WAIT, ActionType.OBSERVE, ActionType.SCREENSHOT -> ActionResult(true,"Observed")
                ActionType.REQUEST_USER_CONFIRMATION -> ActionResult(false,"Confirmation required")
                ActionType.FINISH_TASK -> ActionResult(true,"Complete")
            }
        } catch (e: Exception) { ActionResult(false, e.message ?: "Action failed") }
    }
    private fun tap(s: AccessibilityService, target: String?): ActionResult {
        if (target.isNullOrBlank()) return ActionResult(false,"Missing tap target")
        val root=s.rootInActiveWindow ?: return ActionResult(false,"No active window")
        val nodes=mutableListOf<AccessibilityNodeInfo>(); find(root,target,nodes); root.recycle()
        val n=nodes.firstOrNull() ?: return ActionResult(false,"UI target not found: $target")
        val ok = (if (n.isClickable) n.performAction(AccessibilityNodeInfo.ACTION_CLICK) else n.parent?.performAction(AccessibilityNodeInfo.ACTION_CLICK) ?: false)
        nodes.drop(1).forEach { it.recycle() }; n.recycle()
        return ActionResult(ok,"Tapped $target")
    }
    private fun find(n: AccessibilityNodeInfo, target: String, out: MutableList<AccessibilityNodeInfo>) {
        if (out.size>=8) return
        val match=listOf(n.text?.toString(), n.contentDescription?.toString(), n.viewIdResourceName).any { it?.equals(target,true)==true }
        if (match) out += AccessibilityNodeInfo.obtain(n)
        for(i in 0 until n.childCount) n.getChild(i)?.let { c -> find(c,target,out); c.recycle() }
    }
    private fun type(s: AccessibilityService, text:String): ActionResult {
        val root=s.rootInActiveWindow ?: return ActionResult(false,"No active window")
        val focused=root.findFocus(AccessibilityNodeInfo.FOCUS_INPUT) ?: root.findFocus(AccessibilityNodeInfo.FOCUS_ACCESSIBILITY)
        root.recycle(); if(focused==null) return ActionResult(false,"No editable field focused")
        val b=android.os.Bundle().apply { putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,text) }
        val ok=focused.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT,b); focused.recycle(); return ActionResult(ok,"Entered text")
    }
    private fun clear(s:AccessibilityService):ActionResult { return type(s,"") }
    private fun scroll(s:AccessibilityService,direction:String):ActionResult { val r=s.rootInActiveWindow ?: return ActionResult(false,"No active window"); val nodes=mutableListOf<AccessibilityNodeInfo>(); findScrollable(r,nodes); r.recycle(); val n=nodes.firstOrNull() ?: return ActionResult(false,"No scrollable UI"); val ok=n.performAction(if(direction.equals("up",true)) AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD else AccessibilityNodeInfo.ACTION_SCROLL_FORWARD); nodes.drop(1).forEach{it.recycle()}; n.recycle(); return ActionResult(ok,"Scrolled $direction") }
    private fun findScrollable(n:AccessibilityNodeInfo,out:MutableList<AccessibilityNodeInfo>){if(out.isNotEmpty())return;if(n.isScrollable)out+=AccessibilityNodeInfo.obtain(n);else for(i in 0 until n.childCount)n.getChild(i)?.let{c->findScrollable(c,out);c.recycle()}}
    private fun swipe(s:AccessibilityService,x:Float,y:Float,d:Long):ActionResult { val path=Path().apply{moveTo(x,y);lineTo(x,y-400)}; val gesture=GestureDescription.Builder().addStroke(GestureDescription.StrokeDescription(path,0,d.coerceIn(100,2000))).build(); val ok=s.dispatchGesture(gesture,null,null); return ActionResult(ok,"Swipe dispatched") }
    private fun openApp(s:AccessibilityService,pkg:String):ActionResult { if(pkg.isBlank())return ActionResult(false,"Missing package"); val i=s.packageManager.getLaunchIntentForPackage(pkg) ?: return ActionResult(false,"App not installed: $pkg"); i.addFlags(android.content.Intent.FLAG_ACTIVITY_NEW_TASK); s.startActivity(i); return ActionResult(true,"Opening $pkg") }
}
