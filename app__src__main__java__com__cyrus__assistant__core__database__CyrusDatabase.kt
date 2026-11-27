package com.cyrus.assistant.core.database

import androidx.room.Database
import androidx.room.RoomDatabase

@Database(
    entities = [
        ConversationEntity::class,
        MessageEntity::class,
        ProviderConfigEntity::class,
        MemoryItemEntity::class,
        DocumentEntity::class,
        AgentTaskEntity::class,
        ToolExecutionEntity::class,
        ApprovalEntity::class,
        AutomationEntity::class,
        AuditEventEntity::class
    ],
    version = 1,
    exportSchema = false
)
abstract class CyrusDatabase : RoomDatabase() {
    abstract fun conversationDao(): ConversationDao
    abstract fun memoryDao(): MemoryDao
    abstract fun agentTaskDao(): AgentTaskDao
    abstract fun automationDao(): AutomationDao
    abstract fun documentDao(): DocumentDao
    abstract fun providerConfigDao(): ProviderConfigDao
    abstract fun auditDao(): AuditDao
}
