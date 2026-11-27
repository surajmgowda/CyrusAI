package com.cyrus.assistant.feature

import android.app.Application
import android.content.Context
import android.net.ConnectivityManager
import android.net.NetworkCapabilities
import android.net.Uri
import android.speech.SpeechRecognizer
import android.speech.tts.TextToSpeech
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import com.cyrus.assistant.ai.memory.DocumentIndexer
import com.cyrus.assistant.ai.providers.*
import com.cyrus.assistant.ai.router.RoutingPolicyEngine
import com.cyrus.assistant.ai.tools.ResearchResult
import com.cyrus.assistant.ai.tools.WebResearchTool
import com.cyrus.assistant.core.database.*
import com.cyrus.assistant.core.security.SecretVault
import com.cyrus.assistant.domain.model.*
import com.cyrus.assistant.workers.CyrusAutomationWorker
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
    private val openAiAdapter: OpenAiCompatibleAdapter,
    private val fastApiAdapter: FastApiBackendAdapter,
    private val localAdapter: LocalOnDeviceAdapter,
    private val webResearchTool: WebResearchTool,
    private val documentIndexer: DocumentIndexer
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

    private val _cloudProviderChoice = MutableStateFlow(prefs.getString("cloud_provider", "GEMINI") ?: "GEMINI")
    val cloudProviderChoice: StateFlow<String> = _cloudProviderChoice.asStateFlow()

    private val _cloudModelId = MutableStateFlow(prefs.getString("cloud_model_id", "gemini-2.5-flash") ?: "gemini-2.5-flash")
    val cloudModelId: StateFlow<String> = _cloudModelId.asStateFlow()

    private val _openAiEndpoint = MutableStateFlow(prefs.getString("openai_endpoint", "https://api.openai.com/v1") ?: "https://api.openai.com/v1")
    val openAiEndpoint: StateFlow<String> = _openAiEndpoint.asStateFlow()

    private val _backendUrl = MutableStateFlow(prefs.getString("backend_url", "") ?: "")
    val backendUrl: StateFlow<String> = _backendUrl.asStateFlow()

    private val _hasGeminiKey = MutableStateFlow(secretVault.hasSecret("gemini_api_key"))
    val hasGeminiKey: StateFlow<Boolean> = _hasGeminiKey.asStateFlow()

    private val _hasOpenAiKey = MutableStateFlow(secretVault.hasSecret("openai_api_key"))
    val hasOpenAiKey: StateFlow<Boolean> = _hasOpenAiKey.asStateFlow()

    private val _activeConversationId = MutableStateFlow<String?>(null)
    val activeConversationId: StateFlow<String?> = _activeConversationId.asStateFlow()

    private val _isGenerating = MutableStateFlow(false)
    val isGenerating: StateFlow<Boolean> = _isGenerating.asStateFlow()

    private val _lastRouteDecision = MutableStateFlow<RouteDecision?>(null)
    val lastRouteDecision: StateFlow<RouteDecision?> = _lastRouteDecision.asStateFlow()

    private val _researchResult = MutableStateFlow<ResearchResult?>(null)
    val researchResult: StateFlow<ResearchResult?> = _researchResult.asStateFlow()

    private val _devDiffPreview = MutableStateFlow("")
    val devDiffPreview: StateFlow<String> = _devDiffPreview.asStateFlow()

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
            if (status == TextToSpeech.SUCCESS) tts?.language = Locale.US
        }
        viewModelScope.launch {
            conversations.first().let { list ->
                if (list.isEmpty()) createNewConversation("Welcome to Cyrus")
                else if (_activeConversationId.value == null) _activeConversationId.value = list.first().id
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

    fun setCloudProvider(provider: String, modelId: String, endpoint: String) {
        _cloudProviderChoice.value = provider
        _cloudModelId.value = modelId
        _openAiEndpoint.value = endpoint
        prefs.edit()
            .putString("cloud_provider", provider)
            .putString("cloud_model_id", modelId)
            .putString("openai_endpoint", endpoint)
            .apply()
    }

    fun setBackendUrl(url: String) {
        _backendUrl.value = url.trim()
        prefs.edit().putString("backend_url", url.trim()).apply()
    }

    fun saveGeminiKey(key: String) {
        if (key.isBlank()) return
        secretVault.saveSecret("gemini_api_key", key.trim())
        _hasGeminiKey.value = true
        logAudit("user", "save_provider_key", "gemini_api_key", "success")
    }

    fun saveOpenAiKey(key: String) {
        if (key.isBlank()) return
        secretVault.saveSecret("openai_api_key", key.trim())
        _hasOpenAiKey.value = true
        logAudit("user", "save_provider_key", "openai_api_key", "success")
    }

    fun clearAllSecrets() {
        secretVault.deleteSecret("gemini_api_key")
        secretVault.deleteSecret("openai_api_key")
        _hasGeminiKey.value = false
        _hasOpenAiKey.value = false
        logAudit("user", "clear_all_secrets", "keystore", "success")
    }

    fun selectConversation(id: String) { _activeConversationId.value = id }

    fun createNewConversation(title: String = "New Chat") {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val id = UUID.randomUUID().toString()
            conversationDao.upsertConversation(
                ConversationEntity(id, title, now, now, _routingMode.value.name)
            )
            _activeConversationId.value = id
        }
    }

    fun renameConversation(id: String, newTitle: String) {
        if (newTitle.isBlank()) return
        viewModelScope.launch { conversationDao.renameConversation(id, newTitle.trim(), System.currentTimeMillis()) }
    }

    fun archiveConversation(id: String) {
        viewModelScope.launch {
            conversationDao.archiveConversation(id, System.currentTimeMillis())
            if (_activeConversationId.value == id) {
                _activeConversationId.value = conversations.value.firstOrNull { it.id != id }?.id
            }
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

    fun retryLastPrompt() {
        val lastUser = messages.value.lastOrNull { it.role == "user" } ?: return
        sendMessage(lastUser.content)
    }

    fun sendMessage(prompt: String) {
        if (prompt.isBlank()) return
        val cid = _activeConversationId.value ?: return
        activeGenerationJob?.cancel()
        activeGenerationJob = viewModelScope.launch {
            val now = System.currentTimeMillis()
            conversationDao.upsertMessage(
                MessageEntity(UUID.randomUUID().toString(), cid, "user", prompt, now, "complete")
            )

            val useOpenAi = _cloudProviderChoice.value == "OPENAI"
            val hasCloudKey = if (useOpenAi) _hasOpenAiKey.value else _hasGeminiKey.value
            val providerLabel = if (useOpenAi) "OpenAI-Compatible" else "Google Gemini"

            val decision = routingPolicyEngine.decideRoute(
                requestedMode = _routingMode.value,
                globalLocalOnly = _globalLocalOnly.value,
                isOnline = isOnline(),
                localModelInstalled = false,
                hasCloudKey = hasCloudKey || _backendUrl.value.isNotBlank(),
                cloudProviderName = if (_backendUrl.value.isNotBlank()) "FastAPI Backend" else providerLabel,
                cloudModelId = _cloudModelId.value,
                requiresNetworkTool = prompt.contains("search", ignoreCase = true)
            )
            _lastRouteDecision.value = decision
            _isGenerating.value = true

            val assistantMsgId = UUID.randomUUID().toString()
            val routeTag = "${decision.selectedMode.name} • ${decision.providerName} (${decision.modelId})"

            if (decision.selectedMode == RoutingMode.CLOUD && !decision.isOfflineFallback) {
                if (_backendUrl.value.isNotBlank()) {
                    val res = fastApiAdapter.sendChat(_backendUrl.value, prompt, decision.selectedMode.name.lowercase())
                    val text = res.getOrElse { "Backend unreachable: ${it.message}" }
                    conversationDao.upsertMessage(
                        MessageEntity(assistantMsgId, cid, "assistant", text, System.currentTimeMillis(), "complete", routeSummary = routeTag)
                    )
                } else {
                    val stream = if (useOpenAi) {
                        openAiAdapter.streamWithEndpoint(
                            _openAiEndpoint.value,
                            prompt,
                            "You are Cyrus, a concise, privacy-respecting personal AI assistant on Android 17+.",
                            _cloudModelId.value,
                            secretVault.readSecret("openai_api_key")
                        )
                    } else {
                        geminiAdapter.streamCompletion(
                            prompt,
                            "You are Cyrus, a concise, privacy-respecting personal AI assistant on Android 17+.",
                            _cloudModelId.value,
                            secretVault.readSecret("gemini_api_key")
                        )
                    }
                    stream.collect { text ->
                        conversationDao.upsertMessage(
                            MessageEntity(assistantMsgId, cid, "assistant", text, System.currentTimeMillis(), "complete", routeSummary = routeTag)
                        )
                    }
                }
            } else {
                // Local deterministic execution + local memory & document retrieval
                val memHits = memories.value.filter { it.content.contains(prompt.take(20), ignoreCase = true) }.take(2)
                val docHits = documents.value.filter { it.displayName.contains(prompt.take(20), ignoreCase = true) }.take(2)
                val localPrefix = buildString {
                    if (memHits.isNotEmpty()) {
                        append("Local Memory Hits:
")
                        memHits.forEach { append("• ${it.content} [${it.sourceRef}]
") }
                        append("
")
                    }
                    if (docHits.isNotEmpty()) {
                        append("Indexed Document Matches:
")
                        docHits.forEach { append("• ${it.displayName}: ${it.extractedSummary}
") }
                        append("
")
                    }
                }
                localAdapter.streamCompletion(prompt, "", decision.modelId, null).collect { text ->
                    conversationDao.upsertMessage(
                        MessageEntity(assistantMsgId, cid, "assistant", localPrefix + text, System.currentTimeMillis(), "complete", routeSummary = routeTag)
                    )
                }
            }
            _isGenerating.value = false
        }
    }

    fun stopGeneration() {
        activeGenerationJob?.cancel()
        _isGenerating.value = false
    }

    fun speakText(text: String) { tts?.speak(text, TextToSpeech.QUEUE_FLUSH, null, UUID.randomUUID().toString()) }
    fun stopSpeaking() { tts?.stop() }

    fun addMemory(content: String, category: String = "note", pinned: Boolean = false) {
        if (content.isBlank()) return
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            memoryDao.upsert(
                MemoryItemEntity(UUID.randomUUID().toString(), content.trim(), category, "user", 1.0f, now, now, true, pinned)
            )
        }
    }

    fun toggleMemoryPin(item: MemoryItemEntity) {
        viewModelScope.launch { memoryDao.upsert(item.copy(pinned = !item.pinned, updatedAt = System.currentTimeMillis())) }
    }

    fun deleteMemory(id: String) {
        viewModelScope.launch { memoryDao.deleteById(id) }
    }

    fun indexDocument(uri: Uri, displayName: String, mimeType: String) {
        viewModelScope.launch {
            val summary = documentIndexer.extractAndChunk(uri, displayName, mimeType)
            documentDao.upsert(
                DocumentEntity(
                    id = UUID.randomUUID().toString(),
                    uriReference = uri.toString(),
                    displayName = summary.displayName,
                    mimeType = summary.mimeType,
                    indexedState = "INDEXED (${summary.chunkCount} chunks)",
                    extractedSummary = summary.excerpt,
                    createdAt = System.currentTimeMillis()
                )
            )
            logAudit("user", "index_document", displayName, "success")
        }
    }

    fun runWebResearch(query: String) {
        if (query.isBlank()) return
        viewModelScope.launch {
            val result = webResearchTool.searchAndCite(query)
            _researchResult.value = result
            logAudit("research", "web_search", query, "citations=${result.citations.size}")
        }
    }

    fun generateCodeDiffPreview(request: String) {
        if (request.isBlank()) return
        _devDiffPreview.value = "--- a/SampleModule.kt
+++ b/SampleModule.kt
@@ -1,3 +1,5 @@
 // Isolated Sandbox Diff for: $request
+// Verified: No host shell access
+fun cyrusGeneratedHelper(): String = "Ready for review""
        logAudit("dev_studio", "generate_diff", request, "sandboxed")
    }

    fun launchAgentTask(prompt: String, requiresSensitiveApproval: Boolean = true) {
        if (prompt.isBlank()) return
        viewModelScope.launch {
            val tid = UUID.randomUUID().toString()
            val now = System.currentTimeMillis()
            val status = if (requiresSensitiveApproval) TaskStatus.WAITING_FOR_APPROVAL.name else TaskStatus.RUNNING.name
            agentTaskDao.upsertTask(
                AgentTaskEntity(
                    tid, prompt, status,
                    "["1. Analyze goal", "2. Check permissions", "3. Execute"]",
                    if (requiresSensitiveApproval) "Awaiting human approval" else "Executing bounded plan",
                    if (requiresSensitiveApproval) "EXTERNAL_SIDE_EFFECT" else "READ_ONLY",
                    now, now
                )
            )
            if (requiresSensitiveApproval) {
                agentTaskDao.upsertApproval(
                    ApprovalEntity(
                        UUID.randomUUID().toString(), tid,
                        "Execute external action for: $prompt",
                        ToolPermissionClass.EXTERNAL_SIDE_EFFECT.name,
                        "Cyrus pauses external or irreversible actions until you explicitly approve.",
                        now, ApprovalDecision.PENDING.name
                    )
                )
            } else {
                delay(600)
                agentTaskDao.upsertTask(
                    AgentTaskEntity(tid, prompt, TaskStatus.COMPLETED.name, "["1. Completed"]", "Done", "READ_ONLY", now, System.currentTimeMillis())
                )
            }
            logAudit("agent", "create_task", tid, status)
        }
    }

    fun pauseOrCancelTask(task: AgentTaskEntity, cancel: Boolean) {
        viewModelScope.launch {
            val newStatus = if (cancel) TaskStatus.CANCELLED.name else TaskStatus.PAUSED.name
            agentTaskDao.upsertTask(task.copy(status = newStatus, updatedAt = System.currentTimeMillis()))
            logAudit("user", if (cancel) "cancel_task" else "pause_task", task.id, newStatus)
        }
    }

    fun decideApproval(approval: ApprovalEntity, approved: Boolean) {
        viewModelScope.launch {
            val now = System.currentTimeMillis()
            val decision = if (approved) ApprovalDecision.APPROVED.name else ApprovalDecision.DENIED.name
            agentTaskDao.upsertApproval(approval.copy(decision = decision, decidedAt = now))
            agentTaskDao.observeTasks().first().firstOrNull { it.id == approval.taskId }?.let { task ->
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
            val id = UUID.randomUUID().toString()
            automationDao.upsert(
                AutomationEntity(id, name, "Primary Owner", "local_workmanager", trigger, action, true)
            )
            CyrusAutomationWorker.schedulePeriodic(getApplication(), id, name)
            logAudit("user", "create_automation", name, "scheduled")
        }
    }

    fun runAutomationNow(item: AutomationEntity) {
        CyrusAutomationWorker.runOnceNow(getApplication(), item.id, item.name)
        viewModelScope.launch {
            automationDao.upsert(item.copy(lastRunAt = System.currentTimeMillis()))
        }
        logAudit("user", "run_automation_now", item.name, "enqueued")
    }

    fun toggleAutomation(item: AutomationEntity) {
        viewModelScope.launch {
            val next = !item.enabled
            automationDao.upsert(item.copy(enabled = next))
            if (next) CyrusAutomationWorker.schedulePeriodic(getApplication(), item.id, item.name)
            else CyrusAutomationWorker.cancel(getApplication(), item.id)
        }
    }

    fun deleteAutomation(id: String) {
        CyrusAutomationWorker.cancel(getApplication(), id)
        viewModelScope.launch { automationDao.deleteById(id) }
    }

    fun full81ProofRows(): List<ProofCapabilityRow> {
        val voiceOk = SpeechRecognizer.isRecognitionAvailable(getApplication())
        return ProofPlanRegistry.buildFull81Rows(_hasGeminiKey.value || _hasOpenAiKey.value, voiceOk)
    }

    private fun logAudit(actor: String, action: String, resource: String, outcome: String) {
        viewModelScope.launch {
            auditDao.insert(AuditEventEntity(UUID.randomUUID().toString(), actor, action, resource, System.currentTimeMillis(), outcome, "{}"))
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
