package de.frinshy.commands.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File
import dev.kord.core.behavior.edit
import dev.kord.rest.builder.message.embed
import dev.kord.rest.builder.message.EmbedBuilder
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.Kord
import dev.kord.rest.builder.message.actionRow

@Serializable
enum class TaskState {
    PENDING,
    IN_PROGRESS,
    COMPLETED
}

@Serializable
data class Task(
    val id: String,
    val title: String,
    val description: String,
    var state: TaskState = TaskState.PENDING,
    var assignedUsers: MutableList<String> = mutableListOf(),
    var messageId: String? = null, // Discord message ID for embed
    @Deprecated("Use state instead")
    var completed: Boolean = false
) {
    fun migrateToState(): Task {
        if (completed && state == TaskState.PENDING) {
            return this.copy(state = TaskState.COMPLETED)
        }
        return this
    }
}

object TaskManager {
    private val file = File("tasks.json")
    private val json = Json { prettyPrint = true }
    private var tasks: MutableList<Task> = loadTasks().toMutableList()

    fun addTask(task: Task) {
        tasks.add(task.migrateToState())
        saveTasks()
    }

    fun updateTaskState(id: String, newState: TaskState) {
        tasks.find { it.id == id }?.let {
            it.state = newState
            it.completed = (newState == TaskState.COMPLETED)
            saveTasks()
        }
    }

    fun assignUserToTask(taskId: String, userId: String): Boolean {
        tasks.find { it.id == taskId }?.let { task ->
            if (!task.assignedUsers.contains(userId)) {
                task.assignedUsers.add(userId)
                saveTasks()
                return true
            }
        }
        return false
    }

    fun unassignUserFromTask(taskId: String, userId: String): Boolean {
        tasks.find { it.id == taskId }?.let { task ->
            if (task.assignedUsers.remove(userId)) {
                saveTasks()
                return true
            }
        }
        return false
    }

    fun getTaskById(id: String): Task? = tasks.find { it.id == id }?.migrateToState()

    fun removeTask(id: String) {
        tasks.removeAll { it.id == id }
        saveTasks()
    }

    fun removeTaskByMessageId(messageId: String) {
        tasks.removeAll { it.messageId == messageId }
        saveTasks()
    }

    @Deprecated("Use updateTaskState instead")
    fun markTaskCompleted(id: String) {
        updateTaskState(id, TaskState.COMPLETED)
    }

    fun getTasks(): List<Task> = tasks.map { it.migrateToState() }

    fun getTasksByState(state: TaskState): List<Task> = getTasks().filter { it.state == state }

    private fun saveTasks() {
        file.writeText(json.encodeToString(tasks))
    }

    private fun loadTasks(): List<Task> {
        return if (file.exists()) {
            try {
                val loadedTasks: List<Task> = json.decodeFromString(file.readText())
                loadedTasks.map { it.migrateToState() }
            } catch (e: Exception) {
                emptyList()
            }
        } else emptyList()
    }

    fun formatAssignedUsers(assignedUsers: List<String>): String {
        return assignedUsers.joinToString(", ") { 
            if (it.matches(Regex("\\d+"))) "<@$it>" else it
        }
    }

    suspend fun updateTaskEmbed(kord: Kord, task: Task) {
        if (task.messageId == null) return
        
        try {
            val config = de.frinshy.config.BotConfig.getInstance()
            val channelId = when (task.state) {
                TaskState.PENDING -> config.pendingTasksChannelId
                TaskState.IN_PROGRESS -> config.inProgressTasksChannelId
                TaskState.COMPLETED -> config.completedTasksChannelId
            }
            
            if (channelId == null) return
            
            val channel = kord.getChannelOf<dev.kord.core.entity.channel.TextChannel>(
                dev.kord.common.entity.Snowflake(channelId)
            ) ?: return
            
            val message = channel.getMessage(dev.kord.common.entity.Snowflake(task.messageId!!))
            
            message.edit {
                embeds = mutableListOf()
                embed {
                    this.title = task.title
                    this.color = when (task.state) {
                        TaskState.PENDING -> dev.kord.common.Color(0xFFD700)
                        TaskState.IN_PROGRESS -> dev.kord.common.Color(0xFF8C00)
                        TaskState.COMPLETED -> dev.kord.common.Color(0x43B581)
                    }
                    this.description = task.description
                    this.timestamp = kotlinx.datetime.Clock.System.now()

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
                    // Always include assigned users field, even if empty
                    field {
                        name = "Assigned Users"
                        value = if (task.assignedUsers.isNotEmpty()) {
                            formatAssignedUsers(task.assignedUsers)
                        } else {
                            "_No users assigned_"
                        }
                        inline = false
                    }
                }
                
                // Update buttons as well
                components = mutableListOf()
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
            }
        } catch (e: Exception) {
            println("❌ Failed to update task embed: ${e.message}")
            e.printStackTrace()
        }
    }

    fun updateTask(id: String, newTitle: String? = null, newDescription: String? = null) {
        tasks.find { it.id == id }?.let { task ->
            val updatedTask = task.copy(
                title = newTitle ?: task.title,
                description = newDescription ?: task.description
            )
            val index = tasks.indexOf(task)
            tasks[index] = updatedTask
            saveTasks()
        }
    }

    fun updateTaskMessageId(id: String, newMessageId: String) {
        tasks.find { it.id == id }?.let { task ->
            task.messageId = newMessageId
            saveTasks()
        }
    }
}
