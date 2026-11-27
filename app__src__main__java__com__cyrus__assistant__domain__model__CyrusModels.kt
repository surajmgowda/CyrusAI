package com.cyrus.assistant.domain.model

enum class RoutingMode { LOCAL, CLOUD, AUTOMATIC }

enum class ProviderType { LOCAL_ON_DEVICE, GEMINI_CLOUD, OPENAI_COMPATIBLE, FASTAPI_BACKEND }

enum class TaskStatus { QUEUED, RUNNING, WAITING_FOR_APPROVAL, PAUSED, COMPLETED, FAILED, CANCELLED }

enum class ToolPermissionClass {
    READ_ONLY,
    REVERSIBLE_WRITE,
    EXTERNAL_SIDE_EFFECT,
    HIGH_IMPACT
}

enum class ApprovalDecision { PENDING, APPROVED, DENIED }

enum class ProofStatus { PASS, PARTIAL, FAIL, N_A }

data class RouteDecision(
    val selectedMode: RoutingMode,
    val providerName: String,
    val modelId: String,
    val reason: String,
    val isOfflineFallback: Boolean = false
)

data class ProofCapabilityRow(
    val id: String,
    val area: String,
    val title: String,
    val status: ProofStatus,
    val notes: String
)
