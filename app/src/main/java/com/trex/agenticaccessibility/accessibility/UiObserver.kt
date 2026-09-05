package com.trex.agenticaccessibility.accessibility

import android.graphics.Rect
import android.view.accessibility.AccessibilityNodeInfo
import com.trex.agenticaccessibility.ai.UiElement
import com.trex.agenticaccessibility.ai.UiSnapshot
import java.security.MessageDigest

object UiObserver {
    fun snapshot(root: AccessibilityNodeInfo, packageName: String): UiSnapshot {
        val list = ArrayList<UiElement>(); collect(root, list, 0)
        val compact = list.take(180)
        val fingerprint = sha256(compact.joinToString("|") { "${it.type}:${it.text}:${it.contentDescription}:${it.clickable}:${it.bounds}" })
        return UiSnapshot(packageName, root.window?.toString(), compact, fingerprint)
    }
    private fun collect(node: AccessibilityNodeInfo, out: MutableList<UiElement>, depth: Int) {
        if (out.size >= 220 || depth > 25) return
        val text = node.text?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val hint = node.hintText?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val desc = node.contentDescription?.toString()?.trim()?.takeIf { it.isNotEmpty() }
        val id = node.viewIdResourceName
        if (text != null || hint != null || desc != null || node.isClickable || node.isEditable || node.isScrollable) {
            val r = Rect(); node.getBoundsInScreen(r)
            out += UiElement(out.size, node.className?.toString()?.substringAfterLast('.') ?: "Node", text, hint, desc, id, node.isClickable, node.isEditable, node.isScrollable, "${r.left},${r.top},${r.right},${r.bottom}")
        }
        for (i in 0 until node.childCount) node.getChild(i)?.let { child -> collect(child, out, depth + 1); child.recycle() }
    }
    private fun sha256(s: String): String = MessageDigest.getInstance("SHA-256").digest(s.toByteArray()).joinToString("") { "%02x".format(it) }
}
