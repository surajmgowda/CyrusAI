package com.cyrus.assistant.core.di

import android.content.Context
import androidx.room.Room
import com.cyrus.assistant.core.database.*
import dagger.Module
import dagger.Provides
import dagger.hilt.InstallIn
import dagger.hilt.android.qualifiers.ApplicationContext
import dagger.hilt.components.SingletonComponent
import javax.inject.Singleton

@Module
@InstallIn(SingletonComponent::class)
object AppModule {

    @Provides
    @Singleton
    fun provideDatabase(@ApplicationContext context: Context): CyrusDatabase =
        Room.databaseBuilder(context, CyrusDatabase::class.java, "cyrus_db")
            .fallbackToDestructiveMigration()
            .build()

    @Provides fun provideConversationDao(db: CyrusDatabase): ConversationDao = db.conversationDao()
    @Provides fun provideMemoryDao(db: CyrusDatabase): MemoryDao = db.memoryDao()
    @Provides fun provideAgentTaskDao(db: CyrusDatabase): AgentTaskDao = db.agentTaskDao()
    @Provides fun provideAutomationDao(db: CyrusDatabase): AutomationDao = db.automationDao()
    @Provides fun provideDocumentDao(db: CyrusDatabase): DocumentDao = db.documentDao()
    @Provides fun provideProviderConfigDao(db: CyrusDatabase): ProviderConfigDao = db.providerConfigDao()
    @Provides fun provideAuditDao(db: CyrusDatabase): AuditDao = db.auditDao()
}
