package com.cyrus.assistant.ai.memory

import android.content.Context
import android.net.Uri
import dagger.hilt.android.qualifiers.ApplicationContext
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import javax.inject.Inject
import javax.inject.Singleton

data class IndexedDocumentSummary(
    val displayName: String,
    val mimeType: String,
    val excerpt: String,
    val chunkCount: Int
)

@Singleton
class DocumentIndexer @Inject constructor(
    @ApplicationContext private val context: Context
) {
    suspend fun extractAndChunk(uri: Uri, displayName: String, mimeType: String): IndexedDocumentSummary =
        withContext(Dispatchers.IO) {
            val text = runCatching {
                if (mimeType.startsWith("text/") || displayName.endsWith(".md") || displayName.endsWith(".json") || displayName.endsWith(".csv")) {
                    context.contentResolver.openInputStream(uri)?.bufferedReader()?.use { it.readText() }.orEmpty()
                } else {
                    "Binary/PDF file ($mimeType) registered via Android SAF. Content URI: $uri"
                }
            }.getOrElse { "Unable to read URI: ${it.message}" }

            val chunks = text.chunked(600).filter { it.isNotBlank() }
            val excerpt = chunks.firstOrNull()?.take(240) ?: "Empty document"
            IndexedDocumentSummary(
                displayName = displayName,
                mimeType = mimeType,
                excerpt = excerpt,
                chunkCount = maxOf(1, chunks.size)
            )
        }
}
