package com.inspiredandroid.kai.agent

import app.cash.sqldelight.driver.jdbc.sqlite.JdbcSqliteDriver
import com.inspiredandroid.kai.agentdb.AgentDatabase
import kotlinx.coroutines.test.runTest
import java.nio.file.Files
import kotlin.test.*

class AgentExecutionStoreTest {
    @Test fun reopeningRecoversInterruptedMetadataWithoutResumingAnAction() = runTest {
        val file = Files.createTempFile("kai-agent-journal", ".db")
        try {
            val driver = JdbcSqliteDriver("jdbc:sqlite:$file")
            AgentDatabase.Schema.create(driver)
            val store = AgentExecutionStore(AgentDatabase(driver))
            store.put(ExecutionEntry("run", "run", "run", "phone_task", "FOREGROUND_USER", ExecutionStatus.RUNNING, agentNow()))
            store.put(ExecutionEntry("call", "run", "call", "phone_flashlight", "FOREGROUND_USER", ExecutionStatus.RUNNING, agentNow()))
            driver.close()
            val reopened = JdbcSqliteDriver("jdbc:sqlite:$file")
            try {
                val recovered = AgentExecutionStore(AgentDatabase(reopened))
                assertEquals(2, recovered.entries.value.size)
                assertTrue(recovered.entries.value.all { it.status == ExecutionStatus.INTERRUPTED && it.errorCode == "process_interrupted" })
                assertEquals(setOf("executionEntry"), AgentDatabase.allTableNames().toSet())
                recovered.clearCompleted()
                assertTrue(recovered.entries.value.isEmpty())
                assertTrue(AgentDatabase(reopened).agent_executionQueries.selectRecent().executeAsList().isEmpty())
            } finally {
                reopened.close()
            }
        } finally {
            Files.deleteIfExists(file)
        }
    }

    @Test fun retentionDeleteAndClearPreserveActiveRecords() = runTest {
        val driver = JdbcSqliteDriver(JdbcSqliteDriver.IN_MEMORY)
        AgentDatabase.Schema.create(driver)
        try {
            val store = AgentExecutionStore(AgentDatabase(driver), retentionDays = 14)
            val now = agentNow()
            store.put(ExecutionEntry("active", "active", "run", "phone_task", "DIRECT_USER", ExecutionStatus.RUNNING, now))
            store.put(ExecutionEntry("old", "old", "run", "phone_task", "DIRECT_USER", ExecutionStatus.SUCCEEDED, now - 10 * 86_400_000L))
            assertNotNull(store.find("old"))
            store.prune(7)
            assertNull(store.find("old"))
            store.delete("active")
            assertNotNull(store.find("active"))
            store.clearCompleted()
            assertNotNull(store.find("active"))
        } finally {
            driver.close()
        }
    }
}
