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
        
        // Add debugging to track command executions
        println("🔄 PostTaskCommand.execute() called - User: ${interaction.user.id}, Command: ${interaction.invokedCommandName}")
        
        val deferredResponse = interaction.deferEphemeralResponse()
        val config = BotConfig.getInstance()
        val pendingChannelId = config.pendingTasksChannelId

        if (pendingChannelId == null) {
            deferredResponse.respond {
                content = "❌ No pending tasks channel is set. Please use `/settaskchannels` first to configure all task channels."
            }
            return
        }

        val titleOption = interaction.command.options["title"]
        val titleText = titleOption?.value?.toString()?.trim()
        if (titleText.isNullOrBlank()) {
            deferredResponse.respond {
                content = "❌ Please provide a task title."
            }
            return
        }

        val descriptionOption = interaction.command.options["description"]
        val descriptionText = descriptionOption?.value?.toString()?.trim()

        try {
            val channel = interaction.kord.getChannelOf<TextChannel>(Snowflake(pendingChannelId))
            if (channel == null) {
                deferredResponse.respond {
                    content = "❌ Pending tasks channel not found. Please check your channel configuration."
                }
                return
            }

            val taskId = "${Clock.System.now().epochSeconds}_${(1000..9999).random()}"
            println("📝 Creating task with ID: $taskId, Title: \"$titleText\"")

            val task = Task(
                id = taskId,
                title = titleText,
                description = descriptionText ?: "No description provided",
                state = TaskState.PENDING,
                messageId = null
            )

            var postedMessageId: String? = null
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
                    field {
                        name = "Assigned Users"
                        value = "_No users assigned_"
                        inline = false
                    }
                }

                actionRow {
                    if (task.state != TaskState.COMPLETED) {
                        interactionButton(
                            style = dev.kord.common.entity.ButtonStyle.Primary,
                            customId = "toggle-assignment-${task.id}"
                        ) {
                            label = "Assign/Unassign Me"
                        }
                        interactionButton(
                            style = dev.kord.common.entity.ButtonStyle.Secondary,
                            customId = "select-users-${task.id}"
                        ) {
                            label = "Select Users"
                        }
                        interactionButton(
                            style = dev.kord.common.entity.ButtonStyle.Secondary,
                            customId = "mark-in-progress-${task.id}"
                        ) {
                            label = "Mark In Progress"
                        }
                        interactionButton(
                            style = dev.kord.common.entity.ButtonStyle.Success,
                            customId = "mark-completed-${task.id}"
                        ) {
                            label = "Mark Completed"
                        }
                    }
                }
                actionRow {
                    interactionButton(
                        style = dev.kord.common.entity.ButtonStyle.Danger,
                        customId = "delete-task-${task.id}"
                    ) {
                        label = "Delete Task"
                    }
                    
                    interactionButton(
                        style = dev.kord.common.entity.ButtonStyle.Secondary,
                        customId = "edit-task-${task.id}"
                    ) {
                        label = "Edit Task"
                    }
                }
            }.let { msg ->
                postedMessageId = msg.id.toString()
                println("✅ Message posted with ID: $postedMessageId")
            }

            // Update task with message ID
            task.messageId = postedMessageId
            TaskManager.addTask(task)
            println("💾 Task added to TaskManager")

            updateChannelSummary(interaction.kord, pendingChannelId, TaskState.PENDING)

            deferredResponse.respond {
                content = "✅ Task \"$titleText\" has been created and posted to the pending tasks channel!"
            }
            println("🎉 PostTaskCommand.execute() completed successfully for task: $taskId")

        } catch (e: Exception) {
            println("❌ PostTaskCommand.execute() failed: ${e.message}")
            deferredResponse.respond {
                content = "❌ Failed to create task: ${e.message}"
            }
        }
    }
}
