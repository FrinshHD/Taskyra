package de.frinshy.commands.impl

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
        val config = BotConfig.getInstance()

        val pendingChannelId = config.pendingTasksChannelId
        val inProgressChannelId = config.inProgressTasksChannelId
        val completedChannelId = config.completedTasksChannelId

        if (pendingChannelId == null || inProgressChannelId == null || completedChannelId == null) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ Task channels are not configured. Please use `/settaskchannels` first to configure all task channels."
            }
            return
        }

        try {
            updateChannelSummary(interaction.kord, pendingChannelId, TaskState.PENDING)
            updateChannelSummary(interaction.kord, inProgressChannelId, TaskState.IN_PROGRESS)
            updateChannelSummary(interaction.kord, completedChannelId, TaskState.COMPLETED)

            interaction.deferEphemeralResponse().respond {
                content = "✅ Successfully updated info embeds in all task channels!\n" +
                        "📋 Pending: <#$pendingChannelId>\n" +
                        "🔄 In Progress: <#$inProgressChannelId>\n" +
                        "✅ Completed: <#$completedChannelId>"
            }

        } catch (e: Exception) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ Failed to update info embeds: ${e.message}"
            }
        }
    }
}
