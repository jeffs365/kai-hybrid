package com.inspiredandroid.kai.agent

import android.content.Context
import app.cash.sqldelight.driver.android.AndroidSqliteDriver
import com.inspiredandroid.kai.agentdb.AgentDatabase
import org.koin.java.KoinJavaComponent.get

actual fun createAgentExecutionStore(): AgentExecutionStore {
    val context: Context = get(Context::class.java)
    return AgentExecutionStore(AgentDatabase(AndroidSqliteDriver(AgentDatabase.Schema, context, "agent_execution.db")), get<com.inspiredandroid.kai.data.AppSettings>(com.inspiredandroid.kai.data.AppSettings::class.java).getAgentPreferences().retentionDays)
}
