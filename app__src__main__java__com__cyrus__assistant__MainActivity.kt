package com.cyrus.assistant

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.activity.enableEdgeToEdge
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.filled.*
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Modifier
import androidx.hilt.navigation.compose.hiltViewModel
import com.cyrus.assistant.core.design.CyrusTheme
import com.cyrus.assistant.feature.*
import dagger.hilt.android.AndroidEntryPoint

@AndroidEntryPoint
class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()
        setContent {
            CyrusTheme {
                val vm: CyrusViewModel = hiltViewModel()
                val onboardingDone by vm.onboardingDone.collectAsState()
                if (!onboardingDone) {
                    OnboardingScreen(onComplete = { mode, key -> vm.completeOnboarding(mode, key) })
                } else {
                    CyrusMainShell(vm)
                }
            }
        }
    }
}

@Composable
fun CyrusMainShell(vm: CyrusViewModel) {
    var currentTab by remember { mutableIntStateOf(0) }
    val tabs = listOf(
        "Chat" to Icons.Default.Chat,
        "Agents" to Icons.Default.AutoAwesome,
        "Memory" to Icons.Default.Psychology,
        "Tools" to Icons.Default.Build
    )

    Scaffold(
        bottomBar = {
            NavigationBar {
                tabs.forEachIndexed { index, (label, icon) ->
                    NavigationBarItem(
                        selected = currentTab == index,
                        onClick = { currentTab = index },
                        icon = { Icon(icon, contentDescription = label) },
                        label = { Text(label) }
                    )
                }
            }
        }
    ) { padding ->
        Box(modifier = Modifier.padding(padding)) {
            when (currentTab) {
                0 -> HomeChatScreen(vm)
                1 -> AgentsScreen(vm)
                2 -> MemoryScreen(vm)
                else -> ToolsHubScreen(vm)
            }
        }
    }
}
