package com.cyrus.assistant.ai.router

import com.cyrus.assistant.domain.model.RouteDecision
import com.cyrus.assistant.domain.model.RoutingMode
import javax.inject.Inject
import javax.inject.Singleton

@Singleton
class RoutingPolicyEngine @Inject constructor() {

    fun decideRoute(
        requestedMode: RoutingMode,
        globalLocalOnly: Boolean,
        isOnline: Boolean,
        localModelInstalled: Boolean,
        hasCloudKey: Boolean,
        cloudProviderName: String,
        cloudModelId: String,
        requiresNetworkTool: Boolean
    ): RouteDecision {
        // Local-only policy overrides everything else; never silently fall back to cloud.
        if (globalLocalOnly || requestedMode == RoutingMode.LOCAL) {
            return if (localModelInstalled) {
                RouteDecision(
                    selectedMode = RoutingMode.LOCAL,
                    providerName = "Cyrus On-Device",
                    modelId = "local-quantized-4bit",
                    reason = "Local-only policy active; executing on-device."
                )
            } else {
                RouteDecision(
                    selectedMode = RoutingMode.LOCAL,
                    providerName = "Cyrus Deterministic Local",
                    modelId = "rule-engine-v1",
                    reason = "No local LLM installed; using deterministic local tools (cloud blocked by policy)."
                )
            }
        }

        if (requestedMode == RoutingMode.CLOUD) {
            return if (isOnline && hasCloudKey) {
                RouteDecision(
                    selectedMode = RoutingMode.CLOUD,
                    providerName = cloudProviderName,
                    modelId = cloudModelId,
                    reason = "User selected Cloud mode."
                )
            } else {
                RouteDecision(
                    selectedMode = RoutingMode.LOCAL,
                    providerName = "Offline Guard",
                    modelId = "unavailable",
                    reason = if (!isOnline) "Device is offline; cloud request cannot run." else "No cloud API key configured.",
                    isOfflineFallback = true
                )
            }
        }

        // AUTOMATIC routing
        return when {
            !isOnline -> RouteDecision(
                selectedMode = RoutingMode.LOCAL,
                providerName = if (localModelInstalled) "Cyrus On-Device" else "Cyrus Offline",
                modelId = if (localModelInstalled) "local-quantized-4bit" else "offline-local-tools",
                reason = "Offline detected; routed locally.",
                isOfflineFallback = true
            )
            !requiresNetworkTool && localModelInstalled -> RouteDecision(
                selectedMode = RoutingMode.LOCAL,
                providerName = "Cyrus On-Device",
                modelId = "local-quantized-4bit",
                reason = "Automatic mode: fast local execution."
            )
            hasCloudKey -> RouteDecision(
                selectedMode = RoutingMode.CLOUD,
                providerName = cloudProviderName,
                modelId = cloudModelId,
                reason = "Automatic mode: routed to configured cloud provider."
            )
            else -> RouteDecision(
                selectedMode = RoutingMode.LOCAL,
                providerName = "Cyrus Deterministic Local",
                modelId = "rule-engine-v1",
                reason = "Automatic mode: no cloud key configured; running local tools."
            )
        }
    }
}
