package com.watergenius.water

import androidx.datastore.preferences.core.PreferenceDataStoreFactory
import java.nio.file.Files
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.SupervisorJob
import kotlinx.coroutines.cancelAndJoin
import kotlinx.coroutines.flow.first
import kotlinx.coroutines.runBlocking
import org.junit.Assert.*
import org.junit.Test

class ProgressTest {
    @Test fun restoresCompletedLevelsAndSettingsAfterReopening() = runBlocking {
        val directory = Files.createTempDirectory("water-genius-progress").toFile()
        val file = directory.resolve("water.preferences_pb")
        suspend fun runStore(block: suspend (ProgressStore) -> Unit) {
            val job = SupervisorJob()
            try {
                val scope = CoroutineScope(job + Dispatchers.IO)
                block(ProgressStore(PreferenceDataStoreFactory.create(scope = scope, produceFile = { file })))
            } finally {
                job.cancelAndJoin()
            }
        }

        try {
            runStore { store ->
                store.complete(1, 2)
                store.complete(1, 1)
                store.complete(2, 3)
                store.toggleMusic()
                store.toggleEffects()
            }
            runStore { store ->
                val progress = store.progress.first()
                assertEquals(mapOf(1 to 2, 2 to 3), progress.best)
                assertEquals(3, progress.next)
                assertEquals(5, progress.total)
                assertFalse(progress.music)
                assertFalse(progress.effects)
            }
        } finally {
            directory.deleteRecursively()
        }
        Unit
    }
}
