package de.frinshy.utils

import de.frinshy.commands.impl.TaskManager
import de.frinshy.commands.impl.TaskState
import de.frinshy.commands.impl.Task
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.datetime.Clock

fun EmbedBuilder.addTaskFields(task: Task) {
    field {
        name = "Status"
        value = when (task.state) {
            TaskState.PENDING -> "⏳ Pending"
            TaskState.IN_PROGRESS -> "🔄 In Progress"
            TaskState.COMPLETED -> "✅ Completed"
        }
        inline = true
    }
    field {
        name = "Task ID"
        value = "`${task.id}`"
        inline = true
    }
    if (task.assignedUsers.isNotEmpty()) {
        field {
            name = "Assigned Users"
            value = TaskManager.formatAssignedUsers(task.assignedUsers)
            inline = false
        }
    }
}

suspend fun updateChannelSummary(bot: Kord, channelId: String, taskState: TaskState) {
    try {
        val channel = bot.getChannelOf<TextChannel>(dev.kord.common.entity.Snowflake(channelId))
        if (channel == null) return

        val config = de.frinshy.config.BotConfig.getInstance()

        val tasks = TaskManager.getTasksByState(taskState)
        val (title, colorValue) = when (taskState) {
            TaskState.PENDING -> Pair("📋 Pending Tasks Summary", 0xFFD700)
            TaskState.IN_PROGRESS -> Pair("⚠️ Tasks In Progress Summary", 0xFF8C00)
            TaskState.COMPLETED -> Pair("✅ Completed Tasks Summary", 0x43B581)
        }

        val summaryMessageId = when (taskState) {
            TaskState.PENDING -> config.pendingTasksSummaryMessageId
            TaskState.IN_PROGRESS -> config.inProgressTasksSummaryMessageId
            TaskState.COMPLETED -> config.completedTasksSummaryMessageId
        }

        var summaryMessage: dev.kord.core.entity.Message? = null
        if (summaryMessageId != null) {
            try {
                summaryMessage = channel.getMessage(dev.kord.common.entity.Snowflake(summaryMessageId))
                summaryMessage.edit {
                    embeds = mutableListOf()
                    embed {
                        this.title = title
                        this.color = dev.kord.common.Color(colorValue)
                        this.description = "Total ${taskState.name.lowercase().replace("_", " ")} tasks: **${tasks.size}**"
                        this.timestamp = Clock.System.now()
                    }
                }
            } catch (_: Exception) {
                summaryMessage = null
            }
        }

        if (summaryMessage == null) {
            summaryMessage = channel.createMessage {
                embed {
                    this.title = title
                    this.color = dev.kord.common.Color(colorValue)
                    this.description = "Total ${taskState.name.lowercase().replace("_", " ")} tasks: **${tasks.size}**"
                    this.timestamp = Clock.System.now()
                }
            }

            val newConfig = when (taskState) {
                TaskState.PENDING -> config.copy(pendingTasksSummaryMessageId = summaryMessage.id.toString())
                TaskState.IN_PROGRESS -> config.copy(inProgressTasksSummaryMessageId = summaryMessage.id.toString())
                TaskState.COMPLETED -> config.copy(completedTasksSummaryMessageId = summaryMessage.id.toString())
            }
            de.frinshy.config.BotConfig.updateInstance(newConfig)
        }

    } catch (e: Exception) {
        println("❌ Failed to update channel summary: ${e.message}")
    }
}
