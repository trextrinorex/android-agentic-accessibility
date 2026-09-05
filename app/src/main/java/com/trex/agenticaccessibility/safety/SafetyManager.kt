package com.trex.agenticaccessibility.safety

import android.content.Context
import androidx.appcompat.app.AlertDialog
import com.trex.agenticaccessibility.ai.ActionType
import com.trex.agenticaccessibility.ai.AgentAction
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlin.coroutines.resume

object SafetyManager {
    fun requiresConfirmation(action: AgentAction): Boolean {
        if (action.type == ActionType.REQUEST_USER_CONFIRMATION) return true
        val text = listOf(action.type.name, action.text, action.target, action.explain).joinToString(" ").lowercase()
        return listOf("post", "publish", "send", "delete", "purchase", "buy", "security", "password", "payment").any(text::contains)
    }
    suspend fun confirm(context: Context, action: AgentAction): Boolean = suspendCancellableCoroutine { cont ->
        val dialog=AlertDialog.Builder(context)
            .setTitle("Agent confirmation")
            .setMessage(action.explain ?: action.message ?: "The agent wants to perform: ${action.type.name}")
            .setNegativeButton("Cancel") { _,_ -> if(cont.isActive)cont.resume(false) }
            .setPositiveButton("Allow once") { _,_ -> if(cont.isActive)cont.resume(true) }
            .create()
        dialog.setOnCancelListener { if(cont.isActive)cont.resume(false) }
        dialog.show()
        cont.invokeOnCancellation { dialog.dismiss() }
    }
}
