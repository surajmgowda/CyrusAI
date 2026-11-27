package com.cyrus.assistant.ai.orchestration

import com.cyrus.assistant.domain.model.ToolPermissionClass
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class ToolPermissionPolicy @Inject constructor() {

    fun classifyTool(toolName: String): ToolPermissionClass {
        return when (toolName.lowercase()) {
            "calculator", "local_memory_search", "read_selected_file", "device_diagnostics" ->
                ToolPermissionClass.READ_ONLY
            "create_draft", "save_memory", "save_generated_file", "create_local_note" ->
                ToolPermissionClass.REVERSIBLE_WRITE
            "send_email", "send_message", "submit_form", "modify_calendar", "git_push" ->
                ToolPermissionClass.EXTERNAL_SIDE_EFFECT
            "delete_data", "financial_transaction", "publish_content", "change_security_settings" ->
                ToolPermissionClass.HIGH_IMPACT
            else -> ToolPermissionClass.HIGH_IMPACT // Deny/gate by default if uncertain
        }
    }

    fun requiresHumanApproval(permissionClass: ToolPermissionClass): Boolean {
        return permissionClass == ToolPermissionClass.EXTERNAL_SIDE_EFFECT ||
            permissionClass == ToolPermissionClass.HIGH_IMPACT
    }
}
