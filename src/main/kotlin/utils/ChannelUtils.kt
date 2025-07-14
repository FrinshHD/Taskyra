package de.frinshy.utils

import de.frinshy.commands.impl.TaskManager
import de.frinshy.commands.impl.TaskState
import de.frinshy.commands.impl.Task
import dev.kord.core.Kord
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.edit
import dev.kord.core.entity.channel.TextChannel
import dev.kord.core.entity.Message
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.EmbedBuilder
import kotlinx.datetime.Clock
import dev.kord.common.entity.Snowflake

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

suspend fun performStartupValidation(bot: Kord) {
    println("🔄 Starting up - Validating task data and cleaning up channels...")

    val config = de.frinshy.config.BotConfig.getInstance()
    val channelsToValidate = listOf(
        Pair(config.inProgressTasksChannelId, TaskState.IN_PROGRESS),
        Pair(config.completedTasksChannelId, TaskState.COMPLETED)
    ).mapNotNull { (channelId, state) ->
        channelId?.let { Pair(it, state) }
    }

    // Step 1: Validate that all task messages still exist
    println("📋 Validating existing task messages...")
    val allTasks = TaskManager.getTasks().toMutableList()
    val tasksToRemove = mutableListOf<Task>()

    for (task in allTasks) {
        if (task.messageId != null) {
            try {
                // Find which channel this task should be in
                val expectedChannelId = when (task.state) {
                    TaskState.PENDING -> null // Pending tasks might not have dedicated channels
                    TaskState.IN_PROGRESS -> config.inProgressTasksChannelId
                    TaskState.COMPLETED -> config.completedTasksChannelId
                }

                if (expectedChannelId != null) {
                    val channel = bot.getChannelOf<TextChannel>(Snowflake(expectedChannelId))
                    if (channel != null) {
                        try {
                            channel.getMessage(Snowflake(task.messageId!!))
                            // Message exists, task is valid
                        } catch (e: Exception) {
                            println("❌ Task message not found: ${task.title} (${task.id}) - removing from system")
                            tasksToRemove.add(task)
                        }
                    } else {
                        println("⚠️  Channel not found for task: ${task.title} (${task.id})")
                    }
                }
            } catch (e: Exception) {
                println("❌ Error validating task ${task.id}: ${e.message}")
                tasksToRemove.add(task)
            }
        }
    }

    // Remove invalid tasks
    tasksToRemove.forEach { task ->
        TaskManager.removeTask(task.id)
    }

    if (tasksToRemove.isNotEmpty()) {
        println("🧹 Removed ${tasksToRemove.size} orphaned tasks from system")
    }

    // Step 2: Clean up channels - remove messages that aren't tracked tasks
    for ((channelId, taskState) in channelsToValidate) {
        try {
            val channel = bot.getChannelOf<TextChannel>(Snowflake(channelId))
            if (channel == null) {
                println("⚠️  Could not find channel with ID: $channelId")
                continue
            }

            println("🧹 Cleaning up ${taskState.name.lowercase().replace("_", " ")} channel...")

            // Get all tracked task message IDs for this state
            val trackedMessageIds = TaskManager.getTasksByState(taskState)
                .mapNotNull { it.messageId }
                .toSet()

            // Get all messages in the channel (excluding summary message)
            val summaryMessageId = when (taskState) {
                TaskState.PENDING -> config.pendingTasksSummaryMessageId
                TaskState.IN_PROGRESS -> config.inProgressTasksSummaryMessageId
                TaskState.COMPLETED -> config.completedTasksSummaryMessageId
            }

            val messages = mutableListOf<Message>()
            try {
                channel.getMessagesBefore(Snowflake.max, 100).collect { message ->
                    // Skip bot's own messages that are summary embeds
                    if (message.id.toString() != summaryMessageId && message.author?.isBot == true) {
                        messages.add(message)
                    }
                }
            } catch (e: Exception) {
                println("⚠️  Could not retrieve messages from channel: ${e.message}")
                continue
            }

            var deletedCount = 0
            for (message in messages) {
                // If this message isn't tracked as a task, delete it
                if (!trackedMessageIds.contains(message.id.toString())) {
                    try {
                        message.delete()
                        deletedCount++
                        kotlinx.coroutines.delay(100) // Rate limiting
                    } catch (e: Exception) {
                        println("⚠️  Could not delete message ${message.id}: ${e.message}")
                    }
                }
            }

            if (deletedCount > 0) {
                println("🧹 Deleted $deletedCount orphaned messages from ${taskState.name.lowercase().replace("_", " ")} channel")
            }

        } catch (e: Exception) {
            println("❌ Error cleaning up channel $channelId: ${e.message}")
        }
    }

    // Step 3: Update all info embeds
    println("📊 Updating all category info embeds...")

    for ((channelId, taskState) in channelsToValidate) {
        try {
            updateChannelSummary(bot, channelId, taskState)
            kotlinx.coroutines.delay(200) // Small delay between updates
        } catch (e: Exception) {
            println("❌ Failed to update summary for ${taskState.name}: ${e.message}")
        }
    }

    // Also update pending if it has a channel configured
    config.pendingTasksChannelId?.let { channelId ->
        try {
            updateChannelSummary(bot, channelId, TaskState.PENDING)
        } catch (e: Exception) {
            println("❌ Failed to update pending summary: ${e.message}")
        }
    }

    println("✅ Startup validation completed successfully!")
}
