package de.frinshy.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File

/**
 * Configuration data for the bot
 */
@Serializable
data class BotConfig(
    val pendingTasksChannelId: String? = null,
    val inProgressTasksChannelId: String? = null,
    val completedTasksChannelId: String? = null,

    val pendingTasksSummaryMessageId: String? = null,
    val inProgressTasksSummaryMessageId: String? = null,
    val completedTasksSummaryMessageId: String? = null,
) {
    companion object {
        private val configFile = File("config/config.json")
        var instance: BotConfig = loadFromFile()
            private set

        fun updateInstance(newConfig: BotConfig) {
            instance = newConfig
            saveToFile(newConfig)
        }

        private fun saveToFile(config: BotConfig) {
            val json = Json { prettyPrint = true }
            configFile.writeText(json.encodeToString(serializer(), config))
        }

        private fun loadFromFile(): BotConfig {
            return if (configFile.exists()) {
                val json = Json { ignoreUnknownKeys = true }
                try {
                    json.decodeFromString(serializer(), configFile.readText())
                } catch (e: Exception) {
                    BotConfig()
                }
            } else {
                BotConfig()
            }
        }
    }
}
