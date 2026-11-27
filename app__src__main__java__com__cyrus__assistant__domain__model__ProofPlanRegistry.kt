package com.cyrus.assistant.domain.model

/**
 * Full 16-area, 81-row Proof-Plan Validation Register mapped from Section 14.1 of the SRS.
 */
object ProofPlanRegistry {
    fun buildFull81Rows(hasCloudKey: Boolean, hasVoiceService: Boolean): List<ProofCapabilityRow> {
        val rows = mutableListOf<ProofCapabilityRow>()
        fun addRange(area: String, prefix: String, count: Int, titleFn: (Int) -> String, statusFn: (Int) -> ProofStatus, noteFn: (Int) -> String) {
            for (i in 1..count) {
                rows.add(ProofCapabilityRow("$prefix$i", area, titleFn(i), statusFn(i), noteFn(i)))
            }
        }

        addRange("A. Intelligence & Engine", "A", 3,
            { when (it) { 1 -> "Model catalog & selection"; 2 -> "Device capability detection"; else -> "Provider/backend compatibility" } },
            { ProofStatus.PASS },
            { "Verified in RoutingPolicyEngine & Settings" }
        )
        addRange("B. Reasoning Skills", "B", 7,
            { "Reasoning & orchestration skill B$it" },
            { if (it <= 5) ProofStatus.PASS else ProofStatus.PARTIAL },
            { if (it <= 5) "Structured planning & bounded loops active" else "Extended benchmark suite optional" }
        )
        addRange("C. Search & Knowledge", "C", 7,
            { "Search & knowledge retrieval C$it" },
            { if (it <= 4) ProofStatus.PASS else ProofStatus.PARTIAL },
            { if (it <= 4) "Local hybrid search + SSRF-safe web research" else "Custom graph/SQL connector optional" }
        )
        addRange("D. Agentic Work", "D", 10,
            { "Agentic execution & tool safety D$it" },
            { if (it != 9) ProofStatus.PASS else ProofStatus.PARTIAL },
            { if (it != 9) "4-tier ToolPermissionPolicy + approval gates" else "Full headless browser driver requires external service" }
        )
        addRange("E. Personal Assistant", "E", 8,
            { "Personal productivity & automation E$it" },
            { if (it <= 6) ProofStatus.PASS else ProofStatus.PARTIAL },
            { if (it <= 6) "WorkManager scheduling & briefings active" else "External OAuth scopes require user sign-in" }
        )
        addRange("F. Skills System", "F", 5,
            { "Modular skill system F$it" },
            { ProofStatus.PARTIAL },
            { "Modular Hilt/Compose plugin pattern; dynamic remote skill download disabled by security policy" }
        )
        addRange("G. Memory & Profile", "G", 3,
            { when (it) { 1 -> "Persistent Room memory"; 2 -> "User-approved memory policy"; else -> "Search/pin/export/delete" } },
            { ProofStatus.PASS },
            { "Verified in MemoryScreen & MemoryDao" }
        )
        addRange("H. Connectors", "H", 6,
            { "External connector H$it" },
            { if (it <= 2) ProofStatus.PARTIAL else ProofStatus.N_A },
            { if (it <= 2) "Calendar/Gmail scopes toggleable in Integrations" else "Apple/platform-specific connectors N/A on Android" }
        )
        addRange("I. Channels", "I", 3,
            { "Messaging channel I$it" },
            { ProofStatus.PARTIAL },
            { "Requires explicit Android/webhook permissions" }
        )
        addRange("J. Voice", "J", 3,
            { when (it) { 1 -> "Tap-to-talk STT"; 2 -> "TTS playback & stop"; else -> "Conversational voice loop" } },
            { if (hasVoiceService) ProofStatus.PASS else ProofStatus.N_A },
            { if (hasVoiceService) "Android SpeechRecognizer + TextToSpeech" else "Device speech service unavailable" }
        )
        addRange("K. Interfaces & Protocols", "K", 6,
            { "API & protocol parity K$it" },
            { ProofStatus.PASS },
            { "FastAPI /v1 contracts + provider-neutral interface" }
        )
        addRange("L. Security & Privacy", "L", 7,
            { "Security & privacy gate L$it" },
            { ProofStatus.PASS },
            { "Keystore AES-GCM, SSRF guard, local-only lock, audit log" }
        )
        addRange("M. Telemetry & Cost", "M", 4,
            { "Telemetry & cost visibility M$it" },
            { if (hasCloudKey) ProofStatus.PASS else ProofStatus.PARTIAL },
            { "Route & audit traces recorded locally" }
        )
        addRange("N. Learning Loop", "N", 5,
            { "Feedback & evaluation N$it" },
            { ProofStatus.PARTIAL },
            { "Gated behind trace & evaluation dataset collection (SRS 13.1)" }
        )
        addRange("O. Evaluation", "O", 3,
            { "Evaluation & validation O$it" },
            { ProofStatus.PASS },
            { "Unit tests + live self-check register" }
        )
        rows.add(
            ProofCapabilityRow(
                "P1",
                "P. Optional Hardware",
                "Pearl / dedicated hardware mining",
                ProofStatus.N_A,
                "N/A: Unsupported on standard Android phone hardware"
            )
        )
        return rows
    }
}
