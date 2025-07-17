package de.frinshy.config

import kotlinx.serialization.Serializable
import kotlinx.serialization.json.Json
import java.io.File
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString

/**
 * Configuration data for the bot
 */
@Serializable
data class GuildConfig(
    val guildId: String,
    val pendingTasksChannelId: String? = null,
    val inProgressTasksChannelId: String? = null,
    val completedTasksChannelId: String? = null,
    val pendingTasksSummaryMessageId: String? = null,
    val inProgressTasksSummaryMessageId: String? = null,
    val completedTasksSummaryMessageId: String? = null
)

@Serializable
data class BotConfig(
    val guilds: List<GuildConfig> = emptyList()
) {
    companion object {
        private val configDir = File("config")
        private val configFile = File(configDir, "config.json")
        var instance: BotConfig = loadFromFile()
            private set

        fun updateInstance(newConfig: BotConfig) {
            instance = newConfig
            saveToFile(newConfig)
        }

        private fun saveToFile(config: BotConfig) {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            if (!configFile.exists()) {
                configFile.createNewFile()
            }
            val json = Json { prettyPrint = true }
            configFile.writeText(json.encodeToString(config))
        }

        private fun loadFromFile(): BotConfig {
            if (!configDir.exists()) {
                configDir.mkdirs()
            }
            if (!configFile.exists()) {
                configFile.createNewFile()
                val json = Json { prettyPrint = true }
                configFile.writeText(json.encodeToString(BotConfig()))
            }
            val json = Json { ignoreUnknownKeys = true }
            return try {
                json.decodeFromString<BotConfig>(configFile.readText())
            } catch (e: Exception) {
                BotConfig()
            }
        }
    }
}
