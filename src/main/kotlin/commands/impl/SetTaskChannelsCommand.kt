package de.frinshy.commands.impl

import de.frinshy.commands.Command
import de.frinshy.commands.SlashCommand
import de.frinshy.config.BotConfig
import de.frinshy.utils.updateChannelSummary
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.rest.builder.interaction.channel

@SlashCommand(name = "settaskchannels", description = "Set the channels for pending, in-progress, and completed tasks")
class SetTaskChannelsCommand : Command {

    override fun defineOptions(builder: ChatInputCreateBuilder) {
        builder.apply {
            channel("pending", "Channel for pending tasks") {
                required = true
            }
            channel("inprogress", "Channel for tasks currently being worked on") {
                required = true
            }
            channel("completed", "Channel for completed tasks") {
                required = true
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction

        val pendingChannelId = interaction.command.options["pending"]?.value?.toString()
        val inProgressChannelId = interaction.command.options["inprogress"]?.value?.toString()
        val completedChannelId = interaction.command.options["completed"]?.value?.toString()

        if (pendingChannelId == null || inProgressChannelId == null || completedChannelId == null) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ All channels must be specified."
            }
            return
        }

        val currentConfig = BotConfig.getInstance()
        val newConfig = currentConfig.copy(
            pendingTasksChannelId = pendingChannelId,
            inProgressTasksChannelId = inProgressChannelId,
            completedTasksChannelId = completedChannelId
        )

        BotConfig.updateInstance(newConfig)

        try {
            updateChannelSummary(interaction.kord, pendingChannelId, TaskState.PENDING)
            updateChannelSummary(interaction.kord, inProgressChannelId, TaskState.IN_PROGRESS)
            updateChannelSummary(interaction.kord, completedChannelId, TaskState.COMPLETED)

            interaction.deferEphemeralResponse().respond {
                content = "✅ Task channels have been configured successfully!\n" +
                        "📋 Pending: <#$pendingChannelId>\n" +
                        "🔄 In Progress: <#$inProgressChannelId>\n" +
                        "✅ Completed: <#$completedChannelId>"
            }
        } catch (e: Exception) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ Failed to initialize channels: ${e.message}"
            }
        }
    }
}
