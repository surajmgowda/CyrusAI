package com.cyrus.assistant.core.database

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface ConversationDao {
    @Query("SELECT * FROM conversations WHERE archivedAt IS NULL ORDER BY updatedAt DESC")
    fun observeActiveConversations(): Flow<List<ConversationEntity>>

    @Query("SELECT * FROM conversations WHERE title LIKE '%' || :query || '%' AND archivedAt IS NULL ORDER BY updatedAt DESC")
    fun searchConversations(query: String): Flow<List<ConversationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertConversation(item: ConversationEntity)

    @Query("UPDATE conversations SET title = :title, updatedAt = :updatedAt WHERE id = :id")
    suspend fun renameConversation(id: String, title: String, updatedAt: Long)

    @Query("UPDATE conversations SET archivedAt = :archivedAt WHERE id = :id")
    suspend fun archiveConversation(id: String, archivedAt: Long)

    @Query("DELETE FROM conversations WHERE id = :id")
    suspend fun deleteConversation(id: String)

    @Query("SELECT * FROM messages WHERE conversationId = :conversationId ORDER BY createdAt ASC")
    fun observeMessages(conversationId: String): Flow<List<MessageEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertMessage(message: MessageEntity)

    @Query("DELETE FROM messages WHERE conversationId = :conversationId")
    suspend fun deleteMessagesForConversation(conversationId: String)
}

@Dao
interface MemoryDao {
    @Query("SELECT * FROM memory_items ORDER BY pinned DESC, updatedAt DESC")
    fun observeAll(): Flow<List<MemoryItemEntity>>

    @Query("SELECT * FROM memory_items WHERE content LIKE '%' || :query || '%' OR category LIKE '%' || :query || '%' ORDER BY pinned DESC, updatedAt DESC")
    fun search(query: String): Flow<List<MemoryItemEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: MemoryItemEntity)

    @Query("DELETE FROM memory_items WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface AgentTaskDao {
    @Query("SELECT * FROM agent_tasks ORDER BY updatedAt DESC")
    fun observeTasks(): Flow<List<AgentTaskEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertTask(task: AgentTaskEntity)

    @Query("SELECT * FROM approvals ORDER BY requestedAt DESC")
    fun observeApprovals(): Flow<List<ApprovalEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsertApproval(approval: ApprovalEntity)

    @Query("SELECT * FROM tool_executions WHERE taskId = :taskId ORDER BY timestamp ASC")
    fun observeExecutionsForTask(taskId: String): Flow<List<ToolExecutionEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertExecution(exec: ToolExecutionEntity)
}

@Dao
interface AutomationDao {
    @Query("SELECT * FROM automations ORDER BY name ASC")
    fun observeAll(): Flow<List<AutomationEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(item: AutomationEntity)

    @Query("DELETE FROM automations WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Dao
interface DocumentDao {
    @Query("SELECT * FROM documents ORDER BY createdAt DESC")
    fun observeAll(): Flow<List<DocumentEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(doc: DocumentEntity)
}

@Dao
interface ProviderConfigDao {
    @Query("SELECT * FROM provider_configs")
    fun observeAll(): Flow<List<ProviderConfigEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(config: ProviderConfigEntity)
}

@Dao
interface AuditDao {
    @Query("SELECT * FROM audit_events ORDER BY timestamp DESC LIMIT 200")
    fun observeRecent(): Flow<List<AuditEventEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(event: AuditEventEntity)
}
