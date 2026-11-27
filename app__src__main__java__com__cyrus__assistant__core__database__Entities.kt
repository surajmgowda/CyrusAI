package com.cyrus.assistant.core.database

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "conversations")
data class ConversationEntity(
    @PrimaryKey val id: String,
    val title: String,
    val createdAt: Long,
    val updatedAt: Long,
    val modelMode: String,
    val archivedAt: Long? = null
)

@Entity(tableName = "messages")
data class MessageEntity(
    @PrimaryKey val id: String,
    val conversationId: String,
    val role: String, // user | assistant | tool | approval | system
    val content: String,
    val createdAt: Long,
    val status: String, // streaming | complete | stopped | error
    val attachmentsJson: String = "[]",
    val citationsJson: String = "[]",
    val routeSummary: String = ""
)

@Entity(tableName = "provider_configs")
data class ProviderConfigEntity(
    @PrimaryKey val id: String,
    val providerType: String,
    val displayName: String,
    val endpoint: String,
    val modelId: String,
    val enabled: Boolean,
    val secretReference: String // Keystore alias only, never raw secret
)

@Entity(tableName = "memory_items")
data class MemoryItemEntity(
    @PrimaryKey val id: String,
    val content: String,
    val category: String,
    val sourceRef: String,
    val confidence: Float,
    val createdAt: Long,
    val updatedAt: Long,
    val userApproved: Boolean,
    val pinned: Boolean = false
)

@Entity(tableName = "documents")
data class DocumentEntity(
    @PrimaryKey val id: String,
    val uriReference: String,
    val displayName: String,
    val mimeType: String,
    val indexedState: String,
    val extractedSummary: String = "",
    val createdAt: Long
)

@Entity(tableName = "agent_tasks")
data class AgentTaskEntity(
    @PrimaryKey val id: String,
    val prompt: String,
    val status: String,
    val planJson: String,
    val currentStep: String,
    val permissionScope: String,
    val createdAt: Long,
    val updatedAt: Long
)

@Entity(tableName = "tool_executions")
data class ToolExecutionEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val toolName: String,
    val inputRedacted: String,
    val outputSummary: String,
    val status: String,
    val approvalId: String? = null,
    val timestamp: Long
)

@Entity(tableName = "approvals")
data class ApprovalEntity(
    @PrimaryKey val id: String,
    val taskId: String,
    val action: String,
    val permissionClass: String,
    val explanation: String,
    val requestedAt: Long,
    val decision: String, // PENDING | APPROVED | DENIED
    val decidedAt: Long? = null
)

@Entity(tableName = "automations")
data class AutomationEntity(
    @PrimaryKey val id: String,
    val name: String,
    val owner: String,
    val scope: String,
    val triggerConfig: String,
    val actionConfig: String,
    val enabled: Boolean,
    val lastRunAt: Long? = null
)

@Entity(tableName = "audit_events")
data class AuditEventEntity(
    @PrimaryKey val id: String,
    val actor: String,
    val action: String,
    val resource: String,
    val timestamp: Long,
    val outcome: String,
    val metadataRedacted: String
)
