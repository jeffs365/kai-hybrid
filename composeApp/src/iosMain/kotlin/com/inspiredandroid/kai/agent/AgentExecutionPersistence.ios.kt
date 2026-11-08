package com.inspiredandroid.kai.agent

// Safe phone mode is Android-only; desktop tests inject an isolated SQL database.
actual fun createAgentExecutionStore(): AgentExecutionStore = AgentExecutionStore()
