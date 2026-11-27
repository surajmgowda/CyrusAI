package com.cyrus.assistant

import com.cyrus.assistant.ai.orchestration.ToolPermissionPolicy
import com.cyrus.assistant.ai.router.RoutingPolicyEngine
import com.cyrus.assistant.ai.tools.WebResearchTool
import com.cyrus.assistant.domain.model.ProofPlanRegistry
import com.cyrus.assistant.domain.model.RoutingMode
import com.cyrus.assistant.domain.model.ToolPermissionClass
import org.junit.Assert.*
import org.junit.Test

class RoutingAndPolicyTest {

    private val router = RoutingPolicyEngine()
    private val policy = ToolPermissionPolicy()
    private val webTool = WebResearchTool()

    @Test
    fun localOnlyNeverFallsBackToCloud() {
        val decision = router.decideRoute(
            requestedMode = RoutingMode.CLOUD,
            globalLocalOnly = true,
            isOnline = true,
            localModelInstalled = false,
            hasCloudKey = true,
            cloudProviderName = "Google Gemini",
            cloudModelId = "gemini-2.5-flash",
            requiresNetworkTool = true
        )
        assertEquals(RoutingMode.LOCAL, decision.selectedMode)
    }

    @Test
    fun externalAndHighImpactToolsRequireApproval() {
        assertFalse(policy.requiresHumanApproval(policy.classifyTool("local_memory_search")))
        assertTrue(policy.requiresHumanApproval(policy.classifyTool("send_email")))
        assertEquals(ToolPermissionClass.HIGH_IMPACT, policy.classifyTool("unknown_tool"))
    }

    @Test
    fun ssrfGuardBlocksPrivateAndHttpUrls() {
        assertFalse(webTool.isSafePublicHttpsUrl("http://example.com"))
        assertFalse(webTool.isSafePublicHttpsUrl("https://127.0.0.1/admin"))
        assertFalse(webTool.isSafePublicHttpsUrl("https://192.168.1.1/"))
    }

    @Test
    fun proofPlanContainsAll81Rows() {
        val rows = ProofPlanRegistry.buildFull81Rows(hasCloudKey = true, hasVoiceService = true)
        assertEquals(81, rows.size)
    }
}
