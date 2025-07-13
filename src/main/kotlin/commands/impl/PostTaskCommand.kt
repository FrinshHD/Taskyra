package de.frinshy.commands.impl

import de.frinshy.commands.Command
import de.frinshy.commands.SlashCommand
import de.frinshy.config.BotConfig
import de.frinshy.utils.updateChannelSummary
import dev.kord.common.Color
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.interaction.response.respond
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.rest.builder.interaction.ChatInputCreateBuilder
import dev.kord.common.entity.Snowflake
import dev.kord.core.behavior.channel.createMessage
import dev.kord.rest.builder.interaction.string
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.rest.builder.message.create.MessageCreateBuilder
import dev.kord.rest.builder.message.actionRow
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.rest.builder.message.embed
import kotlinx.datetime.Clock

@SlashCommand(name = "posttask", description = "Post a new task to the pending tasks channel")
class PostTaskCommand : Command {

    override fun defineOptions(builder: ChatInputCreateBuilder) {
        builder.apply {
            string("title", "The task title/name") {
                required = true
            }
            string("description", "The detailed task description") {
                required = false
            }
        }
    }

    override suspend fun execute(event: ChatInputCommandInteractionCreateEvent) {
        val interaction = event.interaction
        val config = BotConfig.getInstance()
        val pendingChannelId = config.pendingTasksChannelId

        if (pendingChannelId == null) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ No pending tasks channel is set. Please use `/settaskchannels` first to configure all task channels."
            }
            return
        }

        val titleOption = interaction.command.options["title"]
        val titleText = titleOption?.value?.toString()?.trim()
        if (titleText.isNullOrBlank()) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ Please provide a task title."
            }
            return
        }

        val descriptionOption = interaction.command.options["description"]
        val descriptionText = descriptionOption?.value?.toString()?.trim()

        try {
            val channel = interaction.kord.getChannelOf<TextChannel>(Snowflake(pendingChannelId))
            if (channel == null) {
                interaction.deferEphemeralResponse().respond {
                    content = "❌ Pending tasks channel not found. Please check your channel configuration."
                }
                return
            }

            val taskId = "${Clock.System.now().epochSeconds}_${(1000..9999).random()}"

            val task = Task(
                id = taskId,
                title = titleText,
                description = descriptionText ?: "No description provided",
                state = TaskState.PENDING
            )
            TaskManager.addTask(task)

            channel.createMessage {
                embed {
                    this.title = titleText
                    this.color = Color(0xFFD700)
                    this.description = descriptionText ?: "No description provided"
                    this.timestamp = Clock.System.now()

                    field {
                        name = "Status"
                        value = "⏳ Pending"
                        inline = true
                    }
                    field {
                        name = "Task ID"
                        value = "`$taskId`"
                        inline = true
                    }
                }

                actionRow {
                    interactionButton(
                        style = ButtonStyle.Primary,
                        customId = "start-task-$taskId"
                    ) {
                        label = "Start Task"
                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "🔄")
                    }
                    interactionButton(
                        style = ButtonStyle.Success,
                        customId = "complete-task-$taskId"
                    ) {
                        label = "Complete"
                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "✅")
                    }
                }
            }

            updateChannelSummary(interaction.kord, pendingChannelId, TaskState.PENDING)

            interaction.deferEphemeralResponse().respond {
                content = "✅ Task \"$titleText\" has been created and posted to the pending tasks channel!"
            }

        } catch (e: Exception) {
            interaction.deferEphemeralResponse().respond {
                content = "❌ Failed to create task: ${e.message}"
            }
        }
    }
}
