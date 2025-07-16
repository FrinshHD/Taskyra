package de.frinshy.commands.impl

import commands.impl.TaskState
import de.frinshy.commands.Command
import de.frinshy.commands.SlashCommand
import de.frinshy.config.BotConfig
import de.frinshy.utils.updateChannelSummary
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent

@SlashCommand(name = "updateinfo", description = "Update the info embeds in all task channels")
class UpdateInfoCommand : Command {
    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction
        val deferredResponse = interaction.deferEphemeralResponse()
        val config = BotConfig.instance
        val pendingChannelId = config.pendingTasksChannelId
        val inProgressChannelId = config.inProgressTasksChannelId
        val completedChannelId = config.completedTasksChannelId
        if (pendingChannelId == null || inProgressChannelId == null || completedChannelId == null) {
            deferredResponse.respond {
                content =
                    "❌ Task channels are not configured. Please use `/settaskchannels` first to configure all task channels."
            }
            return
        }
        try {
            updateChannelSummary(TaskState.PENDING)
            updateChannelSummary(TaskState.IN_PROGRESS)
            updateChannelSummary(TaskState.COMPLETED)
            deferredResponse.respond {
                content = "✅ Successfully updated info embeds in all task channels!\n" +
                        "📋 Pending: <#$pendingChannelId>\n" +
                        "🔄 In Progress: <#$inProgressChannelId>\n" +
                        "✅ Completed: <#$completedChannelId>"
            }
        } catch (e: Exception) {
            deferredResponse.respond {
                content = "❌ Failed to update info embeds: ${e.message}"
            }
        }
    }
}
