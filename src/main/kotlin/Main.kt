package de.frinshy

import de.frinshy.commands.CommandHandler
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.core.behavior.edit
import dev.kord.core.on
import io.github.cdimascio.dotenv.dotenv
import de.frinshy.commands.impl.TaskManager
import de.frinshy.commands.impl.TaskState
import de.frinshy.utils.updateChannelSummary
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed

suspend fun main() {
    val dotenv = dotenv()
    val token = dotenv["BOT_TOKEN"] ?: error("DISCORD_TOKEN not found in .env")

    val bot = Kord(token)
    val commandHandler = CommandHandler(bot)

    bot.on<ReadyEvent> {
        println("✅ Logged in as ${self.username}")
        commandHandler.registerCommands()
        println("🔧 Command registration complete!")
    }

    bot.on<ChatInputCommandInteractionCreateEvent> {
        commandHandler.handleCommand(this)
    }

    bot.on<ComponentInteractionCreateEvent> {
        try {
            val componentId = interaction.componentId

            when {
                componentId.startsWith("start-task-") -> {
                    val taskId = componentId.removePrefix("start-task-")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.IN_PROGRESS)

                    interaction.message.delete()

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.inProgressTasksChannelId?.let { channelId ->
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(dev.kord.common.entity.Snowflake(channelId))
                        val task = TaskManager.getTasks().find { it.id == taskId }
                        if (channel != null && task != null) {
                            channel.createMessage {
                                embed {
                                    this.title = task.title
                                    this.color = dev.kord.common.Color(0xFF8C00)
                                    this.description = task.description
                                    this.timestamp = kotlinx.datetime.Clock.System.now()

                                    field {
                                        name = "Status"
                                        value = "🔄 In Progress"
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
                                        style = dev.kord.common.entity.ButtonStyle.Success,
                                        customId = "complete-task-$taskId"
                                    ) {
                                        label = "Complete"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "✅")
                                    }
                                    interactionButton(
                                        style = dev.kord.common.entity.ButtonStyle.Secondary,
                                        customId = "pause-task-$taskId"
                                    ) {
                                        label = "Pause"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "⏸️")
                                    }
                                }
                            }

                            updateChannelSummary(bot, config.inProgressTasksChannelId, TaskState.IN_PROGRESS)
                        }
                    }
                }
                componentId.startsWith("complete-task-") -> {
                    val taskId = componentId.removePrefix("complete-task-")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.COMPLETED)

                    interaction.message.delete()

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.completedTasksChannelId?.let { channelId ->
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(dev.kord.common.entity.Snowflake(channelId))
                        val task = TaskManager.getTasks().find { it.id == taskId }
                        if (channel != null && task != null) {
                            channel.createMessage {
                                embed {
                                    this.title = task.title
                                    this.color = dev.kord.common.Color(0x43B581)
                                    this.description = task.description
                                    this.timestamp = kotlinx.datetime.Clock.System.now()

                                    field {
                                        name = "Status"
                                        value = "✅ Completed"
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
                                        style = dev.kord.common.entity.ButtonStyle.Secondary,
                                        customId = "reopen-task-$taskId"
                                    ) {
                                        label = "Reopen"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "🔄")
                                    }
                                    interactionButton(
                                        style = dev.kord.common.entity.ButtonStyle.Danger,
                                        customId = "delete-task-$taskId"
                                    ) {
                                        label = "Delete"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "🗑️")
                                    }
                                }
                            }

                            updateChannelSummary(bot, config.completedTasksChannelId, TaskState.COMPLETED)
                        }
                    }
                }
                componentId.startsWith("pause-task-") -> {
                    val taskId = componentId.removePrefix("pause-task-")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.PENDING)

                    interaction.message.delete()

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.pendingTasksChannelId?.let { channelId ->
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(dev.kord.common.entity.Snowflake(channelId))
                        val task = TaskManager.getTasks().find { it.id == taskId }
                        if (channel != null && task != null) {
                            channel.createMessage {
                                embed {
                                    this.title = task.title
                                    this.color = dev.kord.common.Color(0xFFD700)
                                    this.description = task.description
                                    this.timestamp = kotlinx.datetime.Clock.System.now()

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
                                        style = dev.kord.common.entity.ButtonStyle.Primary,
                                        customId = "start-task-$taskId"
                                    ) {
                                        label = "Start Task"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "🔄")
                                    }
                                    interactionButton(
                                        style = dev.kord.common.entity.ButtonStyle.Success,
                                        customId = "complete-task-$taskId"
                                    ) {
                                        label = "Complete"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "✅")
                                    }
                                }
                            }

                            updateChannelSummary(bot, config.pendingTasksChannelId, TaskState.PENDING)
                        }
                    }
                }
                componentId.startsWith("reopen-task-") -> {
                    val taskId = componentId.removePrefix("reopen-task-")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.PENDING)

                    interaction.message.delete()

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.pendingTasksChannelId?.let { channelId ->
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(dev.kord.common.entity.Snowflake(channelId))
                        val task = TaskManager.getTasks().find { it.id == taskId }
                        if (channel != null && task != null) {
                            channel.createMessage {
                                embed {
                                    this.title = task.title
                                    this.color = dev.kord.common.Color(0xFFD700)
                                    this.description = task.description
                                    this.timestamp = kotlinx.datetime.Clock.System.now()

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
                                        style = dev.kord.common.entity.ButtonStyle.Primary,
                                        customId = "start-task-$taskId"
                                    ) {
                                        label = "Start Task"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "🔄")
                                    }
                                    interactionButton(
                                        style = dev.kord.common.entity.ButtonStyle.Success,
                                        customId = "complete-task-$taskId"
                                    ) {
                                        label = "Complete"
                                        emoji = dev.kord.common.entity.DiscordPartialEmoji(name = "✅")
                                    }
                                }
                            }

                            updateChannelSummary(bot, config.pendingTasksChannelId, TaskState.PENDING)
                        }
                    }
                }
                componentId.startsWith("delete-task-") -> {
                    val taskId = componentId.removePrefix("delete-task-")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.removeTask(taskId)

                    interaction.message.delete()
                }

                componentId == "resolve-task" || componentId == "complete-task" -> {
                    interaction.deferPublicMessageUpdate()
                    val message = interaction.message
                    message.edit {
                        content = "~~${message.content}~~\n✅ Task completed!"
                        components = mutableListOf()
                    }
                    TaskManager.updateTaskState(message.id.toString(), TaskState.COMPLETED)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.completedTasksChannelId?.let { channelId ->
                        updateChannelSummary(bot, channelId, TaskState.COMPLETED)
                    }
                }
                componentId == "start-task" -> {
                    interaction.deferPublicMessageUpdate()
                    val message = interaction.message
                    TaskManager.updateTaskState(message.id.toString(), TaskState.IN_PROGRESS)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.inProgressTasksChannelId?.let { channelId ->
                        updateChannelSummary(bot, channelId, TaskState.IN_PROGRESS)
                    }
                }
                componentId == "pause-task" -> {
                    interaction.deferPublicMessageUpdate()
                    val message = interaction.message
                    TaskManager.updateTaskState(message.id.toString(), TaskState.PENDING)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.pendingTasksChannelId?.let { channelId ->
                        updateChannelSummary(bot, channelId, TaskState.PENDING)
                    }
                }
                componentId == "reopen-task" -> {
                    interaction.deferPublicMessageUpdate()
                    val message = interaction.message
                    TaskManager.updateTaskState(message.id.toString(), TaskState.PENDING)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    config.pendingTasksChannelId?.let { channelId ->
                        updateChannelSummary(bot, channelId, TaskState.PENDING)
                    }
                }
                componentId == "add-task" -> {
                    interaction.respondEphemeral {
                        content = "Use the `/posttask` command to add a new task!"
                    }
                }
                componentId == "show-completed" -> {
                    interaction.respondEphemeral {
                        content = "Check the completed tasks channel for all finished tasks!"
                    }
                }
                else -> {
                    interaction.deferPublicMessageUpdate()
                }
            }
        } catch (e: Exception) {
            println("❌ Error executing command '${interaction.componentId}': ${e.message}")
            e.printStackTrace()
            try {
                interaction.respondEphemeral {
                    content = "❌ An error occurred while processing your request."
                }
            } catch (responseError: Exception) {
                println("⚠️  Could not send error response to user: ${responseError.message}")
            }
        }
    }

    bot.login()
}