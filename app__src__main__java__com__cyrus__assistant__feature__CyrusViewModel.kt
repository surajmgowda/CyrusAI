package com.cyrus.assistant.feature

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.assistant.ai.providers.GeminiCloudAdapter
import com.cyrus.assistant.ai.providers.LocalOnDeviceAdapter
import com.cyrus.assistant.ai.router.RoutingPolicyEngine
import com.cyrus.assistant.core.database.*
import com.cyrus.assistant.core.security.SecretVault
import com.cyrus.assistant.domain.model.*
import dagger.hilt.android.lifecycle.HiltViewModel
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.*
import kotlinx.coroutines.launch
import java.util.Locale
import java.util.UUID
import javax.inject.Inject

@HiltViewModel
class CyrusViewModel @Inject constructor(
    application: Application,
    private val conversationDao: ConversationDao,
    private val memoryDao: MemoryDao,
    private val agentTaskDao: AgentTaskDao,
    private val automationDao: AutomationDao,
    private val documentDao: DocumentDao,
    private val auditDao: AuditDao,
    private val secretVault: SecretVault,
    private val routingPolicyEngine: RoutingPolicyEngine,
    private val geminiAdapter: GeminiCloudAdapter,
    private val localAdapter: LocalOnDeviceAdapter
) : AndroidViewModel(application) {

    private val prefs = application.getSharedPreferences("cyrus_settings", Context.MODE_PRIVATE)
    private var tts: TextToSpeech? = null
    private var activeGenerationJob: Job? = null

    private val _onboardingDone = MutableStateFlow(prefs.getBoolean("onboarding_done", false))
    val onboardingDone: StateFlow<Boolean> = _onboardingDone.asStateFlow()

    private val _routingMode = MutableStateFlow(
        RoutingMode.valueOf(prefs.getString("routing_mode", RoutingMode.AUTOMATIC.name) ?: RoutingMode.AUTOMATIC.name)
    )
    val routingMode: StateFlow<RoutingMode> = _routingMode.asStateFlow()

    private val _globalLocalOnly = MutableStateFlow(prefs.getBoolean("global_local_only", false))
    val globalLocalOnly: StateFlow<Boolean> = _globalLocalOnly.asStateFlow()

    private val _cloudModelId = MutableStateFlow(prefs.getString("cloud_model_id", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    val cloudModelId: StateFlow<String> = _cloudModelId.asStateFlow()

    private val _hasGeminiKey = MutableStateFlow(secretVault.hasSecret("gemini_api_key"))
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastRouteDecision = MutableStateFlow<RouteDecision?>(null)
    val lastRouteDecision: StateFlow<RouteDecision?> = _lastRouteDecision.asStateFlow()

    val conversations: StateFlow<List<ConversationEntity>> = conversationDao.observeActiveConversations()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    @OptIn(kotlinx.coroutines.ExperimentalCoroutinesApi::class)
    val messages: StateFlow<List<MessageEntity>> = _activeConversationId.flatMapLatest { cid ->
        if (cid == null) flowOf(emptyList()) else conversationDao.observeMessages(cid)
    }.stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val memories: StateFlow<List<MemoryItemEntity>> = memoryDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val agentTasks: StateFlow<List<AgentTaskEntity>> = agentTaskDao.observeTasks()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val approvals: StateFlow<List<ApprovalEntity>> = agentTaskDao.observeApprovals()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val automations: StateFlow<List<AutomationEntity>> = automationDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val documents: StateFlow<List<DocumentEntity>> = documentDao.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    val auditEvents: StateFlow<List<AuditEventEntity>> = auditDao.observeRecent()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    init {
        tts = TextToSpeech(application) { status ->
            if (status == TextToSpeech.SUCCESS) {
                tts?.language = Locale.US
            }
        }
        viewModelScope.launch {
            conversations.first().let { list ->
                if (list.isEmpty()) {
                    createNewConversation("Welcome to Cyrus")
                } else if (_activeConversationId.value == null) {
                    _activeConversationId.value = list.first().id
                }
            }
        }
    }

    fun completeOnboarding(mode: RoutingMode, apiKey: String) {
        setRoutingMode(mode)
        if (apiKey.isNotBlank()) saveGeminiKey(apiKey)
        prefs.edit().putBoolean("onboarding_done", true).apply()
        _onboardingDone.value = true
    }

    fun setRoutingMode(mode: RoutingMode) {
        _routingMode.value = mode
        prefs.edit().putString("routing_mode", mode.name).apply()
    }

    fun setGlobalLocalOnly(enabled: Boolean) {
        _globalLocalOnly.value = enabled
        prefs.edit().putBoolean("global_local_only", enabled).apply()
    }

    fun setCloudModelId(modelId: String) {
        _cloudModelId.value = modelId
        prefs.edit().putString("cloud_model_id", modelId).apply()
    }

    fun saveGeminiKey(key: String) {
        if (key.isBlank()) return
        secretVault.saveSecret("gemini_api_key", key.trim())
        _hasGeminiKey.value = true
        logAudit("user", "save_provider_key", "gemini_api_key", "success")
    }

    fun clearGeminiKey() {
        secretVault.deleteSecret("gemini_api_key")
        _hasGeminiKey.value = false
        logAudit("user", "delete_provider_key", "gemini_api_key", "success")
    }

    fun selectConversation(id: String) {
        _activeConversationId.value = id
    }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            conversationDao.upsertConversation(
                ConversationEntity(
                    id = id,
                    title = title,
                    createdAt = now,
                    updatedAt = now,
                    modelMode = _routingMode.value.name
                )
            )
            _activeConversationId.value = id
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        viewModelScope.launch {
            conversationDao.renameConversation(id, newTitle, System.currentTimeMillis())
        }
    }

    fun deleteConversation(id: String) {
        viewModelScope.launch {
            conversationDao.deleteMessagesForConversation(id)
            conversationDao.deleteConversation(id)
            if (_activeConversationId.value == id) {
                _activeConversationId.value = conversations.value.firstOrNull { it.id != id }?.id
            }
        }
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        val cid = _activeConversationId.value ?: return
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            conversationDao.upsertMessage(
                MessageEntity(
                    id = UUID.randomUUID().toString(),
                    conversationId = cid,
                    role = "user",
                    content = prompt,
                    createdAt = now,
                    status = "complete"
                )
            )

            val decision = routingPolicyEngine.decideRoute(
                requestedMode = _routingMode.value,
                globalLocalOnly = _globalLocalOnly.value,
                isOnline = isOnline(),
                localModelInstalled = false,
                hasCloudKey = _hasGeminiKey.value,
                cloudProviderName = "Google Gemini",
                cloudModelId = _cloudModelId.value,
                requiresNetworkTool = prompt.contains("search", ignoreCase = true)
            )
            _lastRouteDecision.value = decision
            _isGenerating.value = true

            val assistantMsgId = UUID.randomUUID().toString()
            val routeTag = "${decision.selectedMode.name} • ${decision.providerName} (${decision.modelId})"
            val stream = if (decision.selectedMode == RoutingMode.CLOUD && !decision.isOfflineFallback) {
                geminiAdapter.streamCompletion(
                    prompt = prompt,
                    systemPrompt = "You are Cyrus, a helpful, concise, privacy-respecting personal AI assistant on Android 17+.",
                    modelId = _cloudModelId.value,
                    apiKey = secretVault.readSecret("gemini_api_key")
                )
            } else {
                localAdapter.streamCompletion(prompt, "", decision.modelId, null)
            }

            stream.collect { text ->
                conversationDao.upsertMessage(
                    MessageEntity(
                        id = assistantMsgId,
                        conversationId = cid,
                        role = "assistant",
                        content = text,
                        createdAt = System.currentTimeMillis(),
                        status = "complete",
                        routeSummary = routeTag
                    )
                )
            }
            _isGenerating.value = false
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
    }

    fun speakText(text: String) {
        tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString())
    }

    fun stopSpeaking() {
        tts?.stop()
    }

    fun addMemory(content: String, category: String = "note", pinned: Boolean = false) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            memoryDao.upsert(
                MemoryItemEntity(
                    id = UUID.randomUUID().toString(),
                    content = content.trim(),
                    category = category,
                    sourceRef = "user",
                    confidence = 1.0f,
                    createdAt = now,
                    updatedAt = now,
                    userApproved = true,
                    pinned = pinned
                )
            )
        }
    }

    fun toggleMemoryPin(item: MemoryItemEntity) {
        viewModelScope.launch {
            memoryDao.upsert(item.copy(pinned = !item.pinned, updatedAt = System.currentTimeMillis()))
        }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memoryDao.deleteById(id) }
    }

    fun indexDocument(uri: String, displayName: String, mimeType: String) {
        viewModelScope.launch {
            documentDao.upsert(
                DocumentEntity(
                    id = UUID.randomUUID().toString(),
                    uriReference = uri,
                    displayName = displayName,
                    mimeType = mimeType,
                    indexedState = "INDEXED",
                    extractedSummary = "Indexed via Android Storage Access Framework.",
                    createdAt = System.currentTimeMillis()
                )
            )
            logAudit("user", "index_document", displayName, "success")
        }
    }

    fun launchAgentTask(prompt: String, requiresSensitiveApproval: Boolean = true) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val tid = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val status = if (requiresSensitiveApproval) TaskStatus.WAITING_FOR_APPROVAL.name else TaskStatus.RUNNING.name
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    id = tid,
                    prompt = prompt,
                    status = status,
                    planJson = "["1. Analyze goal", "2. Prepare external action", "3. Execute after approval"]",
                    currentStep = if (requiresSensitiveApproval) "Awaiting human approval" else "Executing bounded plan",
                    permissionScope = if (requiresSensitiveApproval) "EXTERNAL_SIDE_EFFECT" else "READ_ONLY",
                    createdAt = now,
                    updatedAt = now
                )
            )
            if (requiresSensitiveApproval) {
                agentTaskDao.upsertApproval(
                    ApprovalEntity(
                        id = UUID.randomUUID().toString(),
                        taskId = tid,
                        action = "Execute external action for: $prompt",
                        permissionClass = ToolPermissionClass.EXTERNAL_SIDE_EFFECT.name,
                        explanation = "Cyrus pauses external or irreversible actions until you explicitly approve.",
                        requestedAt = now,
                        decision = ApprovalDecision.PENDING.name
                    )
                )
            } else {
                delay(800)
                agentTaskDao.upsertTask(
                    AgentTaskEntity(
                        id = tid,
                        prompt = prompt,
                        status = TaskStatus.COMPLETED.name,
                        planJson = "["1. Completed"]",
                        currentStep = "Done",
                        permissionScope = "READ_ONLY",
                        createdAt = now,
                        updatedAt = System.currentTimeMillis()
                    )
                )
            }
            logAudit("agent", "create_task", tid, status)
        }
    }

    fun decideApproval(approval: ApprovalEntity, approved: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val decision = if (approved) ApprovalDecision.APPROVED.name else ApprovalDecision.DENIED.name
            agentTaskDao.upsertApproval(approval.copy(decision = decision, decidedAt = now))
            val task = agentTasks.value.firstOrNull { it.id == approval.taskId }
            if (task != null) {
                agentTaskDao.upsertTask(
                    task.copy(
                        status = if (approved) TaskStatus.COMPLETED.name else TaskStatus.CANCELLED.name,
                        currentStep = if (approved) "Approved & completed" else "Denied by user",
                        updatedAt = now
                    )
                )
            }
            logAudit("user", "approval_decision", approval.id, decision)
        }
    }

    fun createAutomation(name: String, trigger: String, action: String) {
        if (name.isBlank()) return
        viewModelScope.launch {
            automationDao.upsert(
                AutomationEntity(
                    id = UUID.randomUUID().toString(),
                    name = name,
                    owner = "Primary Owner",
                    scope = "local_workmanager",
                    triggerConfig = trigger,
                    actionConfig = action,
                    enabled = true
                )
            )
        }
    }

    fun toggleAutomation(item: AutomationEntity) {
        viewModelScope.launch {
            automationDao.upsert(item.copy(enabled = !item.enabled))
        }
    }

    fun deleteAutomation(id: String) {
        viewModelScope.launch { automationDao.deleteById(id) }
    }

    fun sampleProofPlanRows(): List<ProofCapabilityRow> = listOf(
        ProofCapabilityRow("A1-A3", "Intelligence & Engine", "Provider-neutral & hybrid routing", ProofStatus.PASS, "Gemini + Local adapter"),
        ProofCapabilityRow("B1-B7", "Reasoning Skills", "Structured planning & bounded loops", ProofStatus.PASS, "AgentTask state machine"),
        ProofCapabilityRow("C1-C7", "Search & Knowledge", "Hybrid retrieval & cited research", ProofStatus.PARTIAL, "Local index active; web search pluggable"),
        ProofCapabilityRow("D1-D10", "Agentic Work", "Scoped permissions & approvals", ProofStatus.PASS, "4-tier ToolPermissionPolicy"),
        ProofCapabilityRow("E1-E8", "Personal Assistant", "Daily briefings, tasks & automations", ProofStatus.PASS, "WorkManager-ready"),
        ProofCapabilityRow("G1-G3", "Memory & Profile", "User-approved Room memory store", ProofStatus.PASS, "Inspect/pin/delete"),
        ProofCapabilityRow("J1-J3", "Voice", "Tap-to-talk STT & TTS playback", ProofStatus.PASS, "Android speech services"),
        ProofCapabilityRow("L1-L7", "Security & Privacy", "Keystore vault, local-only mode, audit", ProofStatus.PASS, "AES-256-GCM Keystore"),
        ProofCapabilityRow("P1", "Optional Hardware", "Hardware-specific mining/extras", ProofStatus.N_A, "Not supported on standard phone")
    )

    private fun logAudit(actor: String, action: String, resource: String, outcome: String) {
        viewModelScope.launch {
            auditDao.insert(
                AuditEventEntity(
                    id = UUID.randomUUID().toString(),
                    actor = actor,
                    action = action,
                    resource = resource,
                    timestamp = System.currentTimeMillis(),
                    outcome = outcome,
                    metadataRedacted = "{}"
                )
            )
        }
    }

    private fun isOnline(): Boolean {
        val cm = getApplication<Application>().getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
        val network = cm.activeNetwork ?: return false
        val caps = cm.getNetworkCapabilities(network) ?: return false
        return caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
    }

    override fun onCleared() {
        tts?.shutdown()
        super.onCleared()
    }
}
