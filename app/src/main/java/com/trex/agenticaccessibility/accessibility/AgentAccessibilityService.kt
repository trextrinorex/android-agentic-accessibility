package com.trex.agenticaccessibility.accessibility

import android.accessibilityservice.AccessibilityService
import android.view.accessibility.AccessibilityEvent

class AgentAccessibilityService : AccessibilityService() {
    override fun onServiceConnected() { super.onServiceConnected(); AccessibilityBridge.attach(this) }
    override fun onAccessibilityEvent(event: AccessibilityEvent?) { AccessibilityBridge.onEvent(event) }
    override fun onInterrupt() {}
    override fun onDestroy() { AccessibilityBridge.detach(); super.onDestroy() }
}
