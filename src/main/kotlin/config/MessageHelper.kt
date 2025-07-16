package config

import de.frinshy.Main
import dev.kord.common.entity.Snowflake
import dev.kord.core.entity.Message
import dev.kord.core.entity.channel.TextChannel

object MessageHelper {

    suspend fun getMessage(channelId: String?, messageId: String?): Message? = try {
        val channel: TextChannel = Main.bot.getChannelOf<TextChannel>(Snowflake(channelId ?: "")) ?: return null
        channel.getMessage(Snowflake(messageId ?: ""))
    } catch (e: Exception) {
        println("❌ Failed to get message: ${e.message}")
        null

    }

}