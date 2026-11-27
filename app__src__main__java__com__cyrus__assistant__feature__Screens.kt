package com.cyrus.assistant.feature

import android.Manifest
import android.content.Intent
import android.speech.RecognizerIntent
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.layout.*
import androidx.compose.foundation.lazy.LazyColumn
import androidx.compose.foundation.lazy.items
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalClipboardManager
import androidx.compose.ui.text.AnnotatedString
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.unit.dp
import com.cyrus.assistant.domain.model.ApprovalDecision
import com.cyrus.assistant.domain.model.RoutingMode

@Composable
fun OnboardingScreen(onComplete: (RoutingMode, String) -> Unit) {
    var selectedMode by remember { mutableStateOf(RoutingMode.AUTOMATIC) }
    var apiKey by remember { mutableStateOf("") }

    Column(
        modifier = Modifier.fillMaxSize().padding(24.dp),
        verticalArrangement = Arrangement.spacedBy(16.dp, Alignment.CenterVertically)
    ) {
        Text("Meet Cyrus", style = MaterialTheme.typography.headlineLarge, fontWeight = FontWeight.Bold)
        Text(
            "Your hybrid, privacy-first personal AI assistant for Android 17+. Choose how Cyrus routes your requests. You can change this anytime.",
            style = MaterialTheme.typography.bodyLarge
        )
        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = selectedMode == mode,
                    onClick = { selectedMode = mode },
                    label = { Text(mode.name) }
                )
            }
        }
        OutlinedTextField(
            value = apiKey,
            onValueChange = { apiKey = it },
            label = { Text("Gemini API Key (optional)") },
            modifier = Modifier.fillMaxWidth(),
            singleLine = true
        )
        Button(
            onClick = { onComplete(selectedMode, apiKey) },
            modifier = Modifier.fillMaxWidth()
        ) {
            Text("Start Using Cyrus")
        }
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun HomeChatScreen(vm: CyrusViewModel) {
    val messages by vm.messages.collectAsState()
    val conversations by vm.conversations.collectAsState()
    val routingMode by vm.routingMode.collectAsState()
    val isGenerating by vm.isGenerating.collectAsState()
    val lastRoute by vm.lastRouteDecision.collectAsState()
    val globalLocalOnly by vm.globalLocalOnly.collectAsState()
    var input by remember { mutableStateOf("") }
    var showConversations by remember { mutableStateOf(false) }
    val clipboard = LocalClipboardManager.current

    val speechLauncher = rememberLauncherForActivityResult(ActivityResultContracts.StartActivityForResult()) { res ->
        val spoken = res.data?.getStringArrayListExtra(RecognizerIntent.EXTRA_RESULTS)?.firstOrNull()
        if (!spoken.isNullOrBlank()) {
            input = spoken
        }
    }
    val micPermLauncher = rememberLauncherForActivityResult(ActivityResultContracts.RequestPermission()) { granted ->
        if (granted) {
            val intent = Intent(RecognizerIntent.ACTION_RECOGNIZE_SPEECH).apply {
                putExtra(RecognizerIntent.EXTRA_LANGUAGE_MODEL, RecognizerIntent.LANGUAGE_MODEL_FREE_FORM)
                putExtra(RecognizerIntent.EXTRA_PROMPT, "Speak to Cyrus")
            }
            speechLauncher.launch(intent)
        }
    }

    Column(modifier = Modifier.fillMaxSize()) {
        TopAppBar(
            title = {
                Column {
                    Text("Cyrus", fontWeight = FontWeight.Bold)
                    Text(
                        text = if (globalLocalOnly) "Local-Only Lock" else lastRoute?.let { "${it.selectedMode.name} • ${it.providerName}" } ?: "Mode: ${routingMode.name}",
                        style = MaterialTheme.typography.labelSmall
                    )
                }
            },
            actions = {
                IconButton(onClick = { showConversations = !showConversations }) {
                    Icon(Icons.Default.History, contentDescription = "Conversations")
                }
                IconButton(onClick = { vm.createNewConversation() }) {
                    Icon(Icons.Default.AddComment, contentDescription = "New Chat")
                }
            }
        )

        if (showConversations) {
            Card(modifier = Modifier.fillMaxWidth().padding(8.dp)) {
                Column(modifier = Modifier.padding(8.dp)) {
                    Text("Conversations", style = MaterialTheme.typography.titleSmall)
                    conversations.take(5).forEach { conv ->
                        Row(
                            modifier = Modifier.fillMaxWidth(),
                            horizontalArrangement = Arrangement.SpaceBetween,
                            verticalAlignment = Alignment.CenterVertically
                        ) {
                            TextButton(onClick = { vm.selectConversation(conv.id); showConversations = false }) {
                                Text(conv.title)
                            }
                            IconButton(onClick = { vm.deleteConversation(conv.id) }) {
                                Icon(Icons.Default.Delete, contentDescription = "Delete")
                            }
                        }
                    }
                }
            }
        }

        Row(modifier = Modifier.padding(horizontal = 12.dp, vertical = 4.dp), horizontalArrangement = Arrangement.spacedBy(8.dp)) {
            RoutingMode.entries.forEach { mode ->
                FilterChip(
                    selected = routingMode == mode,
                    onClick = { vm.setRoutingMode(mode) },
                    label = { Text(mode.name) }
                )
            }
        }

        LazyColumn(
            modifier = Modifier.weight(1f).padding(horizontal = 12.dp),
            verticalArrangement = Arrangement.spacedBy(8.dp)
        ) {
            items(messages, key = { it.id }) { msg ->
                val isUser = msg.role == "user"
                Card(
                    colors = CardDefaults.cardColors(
                        containerColor = if (isUser) MaterialTheme.colorScheme.primaryContainer else MaterialTheme.colorScheme.surfaceVariant
                    ),
                    modifier = Modifier.fillMaxWidth()
                ) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(if (isUser) "You" else "Cyrus", style = MaterialTheme.typography.labelMedium, fontWeight = FontWeight.Bold)
                        Spacer(Modifier.height(4.dp))
                        Text(msg.content)
                        if (!isUser) {
                            if (msg.routeSummary.isNotBlank()) {
                                Spacer(Modifier.height(4.dp))
                                Text(msg.routeSummary, style = MaterialTheme.typography.labelSmall)
                            }
                            Row {
                                TextButton(onClick = { clipboard.setText(AnnotatedString(msg.content)) }) { Text("Copy") }
                                TextButton(onClick = { vm.speakText(msg.content) }) { Text("Speak") }
                            }
                        }
                    }
                }
            }
        }

        Row(
            modifier = Modifier.fillMaxWidth().padding(8.dp),
            verticalAlignment = Alignment.CenterVertically,
            horizontalArrangement = Arrangement.spacedBy(6.dp)
        ) {
            IconButton(onClick = { micPermLauncher.launch(Manifest.permission.RECORD_AUDIO) }) {
                Icon(Icons.Default.Mic, contentDescription = "Voice Input")
            }
            OutlinedTextField(
                value = input,
                onValueChange = { input = it },
                placeholder = { Text("Ask Cyrus anything...") },
                modifier = Modifier.weight(1f)
            )
            if (isGenerating) {
                IconButton(onClick = { vm.stopGeneration() }) {
                    Icon(Icons.Default.Stop, contentDescription = "Stop")
                }
            } else {
                IconButton(onClick = {
                    val prompt = input
                    input = ""
                    vm.sendMessage(prompt)
                }) {
                    Icon(Icons.Default.Send, contentDescription = "Send")
                }
            }
        }
    }
}

@Composable
fun AgentsScreen(vm: CyrusViewModel) {
    val tasks by vm.agentTasks.collectAsState()
    val approvals by vm.approvals.collectAsState()
    var taskPrompt by remember { mutableStateOf("") }
    var sensitive by remember { mutableStateOf(true) }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(12.dp)
    ) {
        item {
            Text("Cyrus Agent Workspace", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            Spacer(Modifier.height(8.dp))
            OutlinedTextField(
                value = taskPrompt,
                onValueChange = { taskPrompt = it },
                label = { Text("New multi-step task") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(verticalAlignment = Alignment.CenterVertically) {
                Checkbox(checked = sensitive, onCheckedChange = { sensitive = it })
                Text("Requires external approval gate")
                Spacer(Modifier.weight(1f))
                Button(onClick = { vm.launchAgentTask(taskPrompt, sensitive); taskPrompt = "" }) {
                    Text("Run Agent")
                }
            }
        }

        val pending = approvals.filter { it.decision == ApprovalDecision.PENDING.name }
        if (pending.isNotEmpty()) {
            item { Text("Pending Approvals", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
            items(pending, key = { it.id }) { item ->
                Card(colors = CardDefaults.cardColors(containerColor = MaterialTheme.colorScheme.tertiaryContainer)) {
                    Column(modifier = Modifier.padding(12.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
                        Text(item.action, fontWeight = FontWeight.Bold)
                        Text("${item.permissionClass}: ${item.explanation}", style = MaterialTheme.typography.bodySmall)
                        Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                            Button(onClick = { vm.decideApproval(item, true) }) { Text("Approve") }
                            OutlinedButton(onClick = { vm.decideApproval(item, false) }) { Text("Deny") }
                        }
                    }
                }
            }
        }

        item { Text("Task Queue (${tasks.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(tasks, key = { it.id }) { task ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(12.dp)) {
                    Text(task.prompt, fontWeight = FontWeight.SemiBold)
                    Text("Status: ${task.status} • Step: ${task.currentStep}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }
    }
}

@Composable
fun MemoryScreen(vm: CyrusViewModel) {
    val memories by vm.memories.collectAsState()
    val documents by vm.documents.collectAsState()
    var query by remember { mutableStateOf("") }
    var newMemory by remember { mutableStateOf("") }

    val docPicker = rememberLauncherForActivityResult(ActivityResultContracts.OpenDocument()) { uri ->
        if (uri != null) {
            vm.indexDocument(uri.toString(), uri.lastPathSegment ?: "Selected Document", "application/pdf")
        }
    }

    LazyColumn(
        modifier = Modifier.fillMaxSize().padding(16.dp),
        verticalArrangement = Arrangement.spacedBy(10.dp)
    ) {
        item {
            Text("Memory & Knowledge", style = MaterialTheme.typography.headlineSmall, fontWeight = FontWeight.Bold)
            OutlinedTextField(
                value = query,
                onValueChange = { query = it },
                label = { Text("Search memories") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp), verticalAlignment = Alignment.CenterVertically) {
                OutlinedTextField(
                    value = newMemory,
                    onValueChange = { newMemory = it },
                    label = { Text("Add approved memory") },
                    modifier = Modifier.weight(1f)
                )
                Button(onClick = { vm.addMemory(newMemory); newMemory = "" }) { Text("Save") }
            }
            OutlinedButton(onClick = { docPicker.launch(arrayOf("application/pdf", "text/*")) }) {
                Text("Index Document via Android SAF")
            }
        }

        val filtered = memories.filter { it.content.contains(query, ignoreCase = true) }
        items(filtered, key = { it.id }) { mem ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(mem.content)
                        Text("${mem.category} • Source: ${mem.sourceRef}", style = MaterialTheme.typography.labelSmall)
                    }
                    IconButton(onClick = { vm.toggleMemoryPin(mem) }) {
                        Icon(if (mem.pinned) Icons.Default.PushPin else Icons.Default.OutlinedFlag, contentDescription = "Pin")
                    }
                    IconButton(onClick = { vm.deleteMemory(mem.id) }) {
                        Icon(Icons.Default.Delete, contentDescription = "Delete")
                    }
                }
            }
        }

        if (documents.isNotEmpty()) {
            item { Text("Indexed Documents (${documents.size})", style = MaterialTheme.typography.titleMedium) }
            items(documents, key = { it.id }) { doc ->
                Card(modifier = Modifier.fillMaxWidth()) {
                    Column(modifier = Modifier.padding(12.dp)) {
                        Text(doc.displayName, fontWeight = FontWeight.SemiBold)
                        Text("${doc.indexedState} • ${doc.uriReference}", style = MaterialTheme.typography.bodySmall)
                    }
                }
            }
        }
    }
}

@Composable
fun ToolsHubScreen(vm: CyrusViewModel) {
    var selectedTool by remember { mutableStateOf("Research") }
    val tabs = listOf("Research", "Automation", "Dev Studio", "Integrations", "Settings")

    Column(modifier = Modifier.fillMaxSize()) {
        ScrollableTabRow(selectedTabIndex = tabs.indexOf(selectedTool)) {
            tabs.forEach { tab ->
                Tab(selected = selectedTool == tab, onClick = { selectedTool = tab }, text = { Text(tab) })
            }
        }
        when (selectedTool) {
            "Research" -> ResearchPanel(vm)
            "Automation" -> AutomationPanel(vm)
            "Dev Studio" -> DeveloperStudioPanel()
            "Integrations" -> IntegrationsPanel()
            "Settings" -> SettingsPanel(vm)
        }
    }
}

@Composable
fun ResearchPanel(vm: CyrusViewModel) {
    var topic by remember { mutableStateOf("") }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Deep Research", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        OutlinedTextField(value = topic, onValueChange = { topic = it }, label = { Text("Research question") }, modifier = Modifier.fillMaxWidth())
        Button(onClick = { vm.launchAgentTask("Research with citations: $topic", requiresSensitiveApproval = false); topic = "" }) {
            Text("Start Cited Research Task")
        }
        Text("Research results treat external web pages as untrusted input and preserve source URLs.", style = MaterialTheme.typography.bodySmall)
    }
}

@Composable
fun AutomationPanel(vm: CyrusViewModel) {
    val automations by vm.automations.collectAsState()
    var name by remember { mutableStateOf("") }
    var trigger by remember { mutableStateOf("Daily 08:00") }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        item {
            Text("Automations & Briefings", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            OutlinedTextField(value = name, onValueChange = { name = it }, label = { Text("Automation name") }, modifier = Modifier.fillMaxWidth())
            OutlinedTextField(value = trigger, onValueChange = { trigger = it }, label = { Text("Schedule / Trigger") }, modifier = Modifier.fillMaxWidth())
            Button(onClick = { vm.createAutomation(name, trigger, "Draft morning briefing"); name = "" }) {
                Text("Create Automation")
            }
        }
        items(automations, key = { it.id }) { auto ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Row(modifier = Modifier.padding(12.dp), verticalAlignment = Alignment.CenterVertically) {
                    Column(modifier = Modifier.weight(1f)) {
                        Text(auto.name, fontWeight = FontWeight.Bold)
                        Text("${auto.triggerConfig} • Owner: ${auto.owner}", style = MaterialTheme.typography.bodySmall)
                    }
                    Switch(checked = auto.enabled, onCheckedChange = { vm.toggleAutomation(auto) })
                    IconButton(onClick = { vm.deleteAutomation(auto.id) }) { Icon(Icons.Default.Delete, contentDescription = "Delete") }
                }
            }
        }
    }
}

@Composable
fun DeveloperStudioPanel() {
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(10.dp)) {
        Text("Developer Studio", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Text("Code execution runs in an isolated sandbox; diffs are reviewed before applying to user projects; Git push requires explicit approval.")
        Card(modifier = Modifier.fillMaxWidth()) {
            Column(modifier = Modifier.padding(12.dp)) {
                Text("Sandbox Status: Isolated", fontWeight = FontWeight.SemiBold)
                Text("Host shell access: Disabled by security policy", style = MaterialTheme.typography.bodySmall)
            }
        }
    }
}

@Composable
fun IntegrationsPanel() {
    var calendarConnected by remember { mutableStateOf(false) }
    var gmailConnected by remember { mutableStateOf(false) }
    Column(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Connected Integrations", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Google Calendar (Read/Draft)", modifier = Modifier.weight(1f))
            Switch(checked = calendarConnected, onCheckedChange = { calendarConnected = it })
        }
        Row(verticalAlignment = Alignment.CenterVertically) {
            Text("Gmail (Summaries & Drafts)", modifier = Modifier.weight(1f))
            Switch(checked = gmailConnected, onCheckedChange = { gmailConnected = it })
        }
    }
}

@Composable
fun SettingsPanel(vm: CyrusViewModel) {
    val globalLocalOnly by vm.globalLocalOnly.collectAsState()
    val hasKey by vm.hasGeminiKey.collectAsState()
    val modelId by vm.cloudModelId.collectAsState()
    val auditEvents by vm.auditEvents.collectAsState()
    var keyInput by remember { mutableStateOf("") }
    var modelInput by remember { mutableStateOf(modelId) }
    val proofRows = remember { vm.sampleProofPlanRows() }

    LazyColumn(modifier = Modifier.fillMaxSize().padding(16.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        item {
            Text("Settings & Diagnostics", style = MaterialTheme.typography.titleLarge, fontWeight = FontWeight.Bold)
            Row(verticalAlignment = Alignment.CenterVertically) {
                Text("Strict Local-Only Mode (Blocks Cloud)", modifier = Modifier.weight(1f))
                Switch(checked = globalLocalOnly, onCheckedChange = { vm.setGlobalLocalOnly(it) })
            }
            OutlinedTextField(
                value = keyInput,
                onValueChange = { keyInput = it },
                label = { Text(if (hasKey) "Gemini API Key (Stored in Keystore)" else "Enter Gemini API Key") },
                modifier = Modifier.fillMaxWidth()
            )
            Row(horizontalArrangement = Arrangement.spacedBy(8.dp)) {
                Button(onClick = { vm.saveGeminiKey(keyInput); keyInput = "" }) { Text("Save Key") }
                if (hasKey) OutlinedButton(onClick = { vm.clearGeminiKey() }) { Text("Clear Key") }
            }
            OutlinedTextField(
                value = modelInput,
                onValueChange = { modelInput = it },
                label = { Text("Cloud Model ID") },
                modifier = Modifier.fillMaxWidth()
            )
            Button(onClick = { vm.setCloudModelId(modelInput) }) { Text("Update Model") }
        }

        item { Text("81-Row Capability Validation Register (Summary)", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(proofRows, key = { it.id }) { row ->
            Card(modifier = Modifier.fillMaxWidth()) {
                Column(modifier = Modifier.padding(10.dp)) {
                    Text("${row.id} • ${row.area} [${row.status.name}]", fontWeight = FontWeight.SemiBold)
                    Text("${row.title}: ${row.notes}", style = MaterialTheme.typography.bodySmall)
                }
            }
        }

        item { Text("Recent Audit Log (${auditEvents.size})", style = MaterialTheme.typography.titleMedium, fontWeight = FontWeight.Bold) }
        items(auditEvents.take(10), key = { it.id }) { ev ->
            Text("${ev.actor}: ${ev.action} -> ${ev.outcome}", style = MaterialTheme.typography.bodySmall)
        }
    }
}
