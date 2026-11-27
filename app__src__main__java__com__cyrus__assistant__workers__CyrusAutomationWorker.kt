package com.cyrus.assistant.workers

import android.content.Context
import androidx.work.*
import java.util.concurrent.TimeUnit

class CyrusAutomationWorker(
    appContext: Context,
    params: WorkerParameters
) : CoroutineWorker(appContext, params) {

    override suspend fun doWork(): Result {
        val automationName = inputData.getString("automation_name") ?: "Daily Briefing"
        val prefs = applicationContext.getSharedPreferences("cyrus_automations_log", Context.MODE_PRIVATE)
        prefs.edit().putLong("last_run_$automationName", System.currentTimeMillis()).apply()
        return Result.success()
    }

    companion object {
        fun schedulePeriodic(context: Context, automationId: String, name: String, repeatHours: Long = 24) {
            val constraints = Constraints.Builder()
                .setRequiresBatteryNotLow(true)
                .build()
            val req = PeriodicWorkRequestBuilder<CyrusAutomationWorker>(maxOf(15L, repeatHours * 60), TimeUnit.MINUTES)
                .setConstraints(constraints)
                .setInputData(workDataOf("automation_id" to automationId, "automation_name" to name))
                .build()
            WorkManager.getInstance(context).enqueueUniquePeriodicWork(
                "cyrus_auto_$automationId",
                ExistingPeriodicWorkPolicy.UPDATE,
                req
            )
        }

        fun runOnceNow(context: Context, automationId: String, name: String) {
            val req = OneTimeWorkRequestBuilder<CyrusAutomationWorker>()
                .setInputData(workDataOf("automation_id" to automationId, "automation_name" to name))
                .build()
            WorkManager.getInstance(context).enqueue(req)
        }

        fun cancel(context: Context, automationId: String) {
            WorkManager.getInstance(context).cancelUniqueWork("cyrus_auto_$automationId")
        }
    }
}
