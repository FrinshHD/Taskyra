package de.frinshy

import de.frinshy.commands.CommandHandler
import dev.kord.core.Kord
import dev.kord.core.event.gateway.ReadyEvent
import dev.kord.core.event.interaction.ChatInputCommandInteractionCreateEvent
import dev.kord.core.event.interaction.ComponentInteractionCreateEvent
import dev.kord.core.event.interaction.ModalSubmitInteractionCreateEvent
import dev.kord.core.behavior.edit
import dev.kord.core.on
import io.github.cdimascio.dotenv.dotenv
import de.frinshy.commands.impl.TaskManager
import de.frinshy.commands.impl.TaskState
import de.frinshy.utils.updateChannelSummary
import dev.kord.common.entity.ButtonStyle
import dev.kord.core.behavior.channel.createMessage
import dev.kord.core.behavior.interaction.respondEphemeral
import dev.kord.core.behavior.interaction.modal
import dev.kord.rest.builder.message.actionRow
import dev.kord.rest.builder.message.embed
import dev.kord.common.entity.TextInputStyle

// Helper to add embed fields for a task
fun addTaskEmbedFields(
    task: de.frinshy.commands.impl.Task,
    embedBuilder: dev.kord.rest.builder.message.EmbedBuilder
) {
    embedBuilder.field {
        name = "Status"
        value = when (task.state) {
            TaskState.PENDING -> "⏳ Pending"
            TaskState.IN_PROGRESS -> "🔄 In Progress"
            TaskState.COMPLETED -> "✅ Completed"
        }
        inline = true
    }
    embedBuilder.field {
        name = "Task ID"
        value = "`" + task.id + "`"
        inline = true
    }
    embedBuilder.field {
        name = "Assigned Users"
        value = if (task.assignedUsers.isNotEmpty()) {
            TaskManager.formatAssignedUsers(task.assignedUsers)
        } else {
            "_No users assigned_"
        }
        inline = false
    }
}

// Helper to create action rows for a task
fun addTaskActionRows(taskId: String, state: TaskState, builder: dev.kord.rest.builder.message.MessageBuilder) {
    when (state) {
        TaskState.PENDING -> builder.actionRow {
            interactionButton(ButtonStyle.Primary, "start-task-$taskId") { label = "Start Task" }
            interactionButton(ButtonStyle.Success, "complete-task-$taskId") { label = "Complete" }
            interactionButton(ButtonStyle.Secondary, "select-users-$taskId") { label = "Select Users" }
            interactionButton(ButtonStyle.Danger, "delete-task-$taskId") { label = "Delete" }
        }
        TaskState.IN_PROGRESS -> builder.actionRow {
            interactionButton(ButtonStyle.Success, "complete-task-$taskId") { label = "Complete" }
            interactionButton(ButtonStyle.Secondary, "pause-task-$taskId") { label = "Pause" }
            interactionButton(ButtonStyle.Secondary, "select-users-$taskId") { label = "Select Users" }
            interactionButton(ButtonStyle.Secondary, "assign-me-$taskId") { label = "Assign to Me" }
            interactionButton(ButtonStyle.Danger, "delete-task-$taskId") { label = "Delete" }
        }
        TaskState.COMPLETED -> builder.actionRow {
            interactionButton(ButtonStyle.Secondary, "reopen-task-$taskId") { label = "Reopen" }
            interactionButton(ButtonStyle.Danger, "delete-task-$taskId") { label = "Delete" }
        }
    }
}

// Helper to send a task embed message to a channel
suspend fun sendTaskEmbedMessage(
    channel: dev.kord.core.entity.channel.TextChannel,
    task: de.frinshy.commands.impl.Task,
    state: TaskState,
    timestamp: kotlinx.datetime.Instant = kotlinx.datetime.Clock.System.now()
): dev.kord.core.entity.Message {
    return channel.createMessage {
        embed {
            this.title = task.title
            this.color = when (state) {
                TaskState.PENDING -> dev.kord.common.Color(0xFFD700)
                TaskState.IN_PROGRESS -> dev.kord.common.Color(0xFF8C00)
                TaskState.COMPLETED -> dev.kord.common.Color(0x43B581)
            }
            this.description = task.description
            this.timestamp = timestamp
            addTaskEmbedFields(task, this)
        }
        addTaskActionRows(task.id, state, this)
    }
}

suspend fun main() {
    val dotenv = dotenv()
    val token = dotenv["BOT_TOKEN"] ?: error("DISCORD_TOKEN not found in .env")

    val bot = Kord(token)
    val commandHandler = CommandHandler(bot)

    bot.on<dev.kord.core.event.message.MessageDeleteEvent> {
        val deletedId = messageId.toString()
        TaskManager.removeTaskByMessageId(deletedId)
    }

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

            // Check if interaction is too old (Discord interactions expire after 15 minutes)
            val interactionAge = kotlinx.datetime.Clock.System.now() - interaction.id.timestamp
            if (interactionAge.inWholeMinutes > 14) {
                println("⚠️  Ignoring expired interaction: $componentId")
                return@on
            }

            // Add a small delay to prevent rapid-fire button clicks
            kotlinx.coroutines.delay(100)

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
                            sendTaskEmbedMessage(channel, task, TaskState.IN_PROGRESS)
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
                            sendTaskEmbedMessage(channel, task, TaskState.COMPLETED)
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
                            sendTaskEmbedMessage(channel, task, TaskState.PENDING)
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
                            updateChannelSummary(bot, config.pendingTasksChannelId, TaskState.PENDING)
                        }
                    }
                }

                componentId.startsWith("delete-task-") -> {
                    val taskId = componentId.removePrefix("delete-task-")
                    interaction.deferPublicMessageUpdate()

                    val task = TaskManager.getTasks().find { it.id == taskId }
                    val taskState = task?.state ?: TaskState.PENDING

                    TaskManager.removeTask(taskId)
                    interaction.message.delete()

                    val config = de.frinshy.config.BotConfig.getInstance()
                    when (taskState) {
                        TaskState.PENDING -> config.pendingTasksChannelId?.let { channelId ->
                            updateChannelSummary(bot, channelId, TaskState.PENDING)
                        }

                        TaskState.IN_PROGRESS -> config.inProgressTasksChannelId?.let { channelId ->
                            updateChannelSummary(bot, channelId, TaskState.IN_PROGRESS)
                        }

                        TaskState.COMPLETED -> config.completedTasksChannelId?.let { channelId ->
                            updateChannelSummary(bot, channelId, TaskState.COMPLETED)
                        }
                    }
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

                componentId.startsWith("select-users-") -> {
                    val taskId = componentId.removePrefix("select-users-")
                    val task = TaskManager.getTaskById(taskId)

                    if (task != null) {
                        interaction.modal("Assign User to Task", "assign-user-modal-$taskId") {
                            actionRow {
                                textInput(TextInputStyle.Short, "user-id", "User ID, Mention, or Name") {
                                    placeholder = "@username, user ID, or any text"
                                    required = true
                                    allowedLength = 1..100
                                }
                            }
                        }
                    } else {
                        interaction.respondEphemeral {
                            content = "❌ Task not found."
                        }
                    }
                }

                componentId.startsWith("toggle-assignment-") -> {
                    val taskId = componentId.removePrefix("toggle-assignment-")
                    val task = TaskManager.getTaskById(taskId)
                    val userId = interaction.user.id.toString()

                    if (task == null) {
                        interaction.respondEphemeral {
                            content = "❌ Task not found."
                        }
                        return@on
                    }

                    // Check if user is currently assigned
                    val isCurrentlyAssigned = task.assignedUsers.contains(userId)

                    if (isCurrentlyAssigned) {
                        // Unassign the user
                        val success = TaskManager.unassignUserFromTask(taskId, userId)
                        if (success) {
                            // Update the task embed to show the change
                            val updatedTask = TaskManager.getTaskById(taskId)
                            if (updatedTask != null) {
                                TaskManager.updateTaskEmbed(bot, updatedTask)
                            }

                            interaction.respondEphemeral {
                                content = "✅ Successfully unassigned yourself from task \"${task.title}\"!"
                            }
                        } else {
                            interaction.respondEphemeral {
                                content = "❌ Failed to unassign yourself from task \"${task.title}\"."
                            }
                        }
                    } else {
                        // Assign the user
                        val success = TaskManager.assignUserToTask(taskId, userId)
                        if (success) {
                            // Update the task embed to show the new assignment
                            val updatedTask = TaskManager.getTaskById(taskId)
                            if (updatedTask != null) {
                                TaskManager.updateTaskEmbed(bot, updatedTask)
                            }

                            interaction.respondEphemeral {
                                content = "✅ Successfully assigned yourself to task \"${task.title}\"!"
                            }
                        } else {
                            interaction.respondEphemeral {
                                content = "⚠️ You are already assigned to task \"${task.title}\"."
                            }
                        }
                    }
                }

                componentId.startsWith("mark-in-progress-") -> {
                    val taskId = componentId.removePrefix("mark-in-progress-")
                    val task = TaskManager.getTaskById(taskId)

                    if (task == null) {
                        interaction.respondEphemeral {
                            content = "❌ Task not found."
                        }
                        return@on
                    }

                    println("📋 Moving task $taskId to in-progress...")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.IN_PROGRESS)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    val inProgressChannelId = config.inProgressTasksChannelId

                    if (inProgressChannelId == null) {
                        println("❌ In-progress channel ID not configured")
                        interaction.respondEphemeral {
                            content = "❌ In-progress channel not configured."
                        }
                        return@on
                    }

                    try {
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(
                            dev.kord.common.entity.Snowflake(inProgressChannelId)
                        )
                        val updatedTask = TaskManager.getTaskById(taskId)

                        if (channel == null) {
                            println("❌ Could not find in-progress channel with ID: $inProgressChannelId")
                            interaction.respondEphemeral {
                                content = "❌ Could not find in-progress channel."
                            }
                            return@on
                        }

                        if (updatedTask == null) {
                            println("❌ Updated task not found after state change")
                            interaction.respondEphemeral {
                                content = "❌ Task not found after update."
                            }
                            return@on
                        }

                        println("✅ Creating new message in in-progress channel...")
                        val newMessage = channel.createMessage {
                            embed {
                                this.title = updatedTask.title
                                this.color = dev.kord.common.Color(0xFF8C00)
                                this.description = updatedTask.description
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
                                field {
                                    name = "Assigned Users"
                                    value = if (updatedTask.assignedUsers.isNotEmpty()) {
                                        TaskManager.formatAssignedUsers(updatedTask.assignedUsers)
                                    } else {
                                        "_No users assigned_"
                                    }
                                    inline = false
                                }
                            }

                            actionRow {
                                interactionButton(
                                    style = ButtonStyle.Primary,
                                    customId = "toggle-assignment-$taskId"
                                ) {
                                    label = "Assign/Unassign Me"
                                }
                                interactionButton(
                                    style = ButtonStyle.Secondary,
                                    customId = "select-users-$taskId"
                                ) {
                                    label = "Select Users"
                                }
                                interactionButton(
                                    style = ButtonStyle.Success,
                                    customId = "mark-completed-$taskId"
                                ) {
                                    label = "Mark Completed"
                                }
                            }
                            actionRow {
                                interactionButton(
                                    style = ButtonStyle.Danger,
                                    customId = "delete-task-$taskId"
                                ) {
                                    label = "Delete Task"
                                }
                                interactionButton(
                                    style = ButtonStyle.Secondary,
                                    customId = "edit-task-$taskId"
                                ) {
                                    label = "Edit Task"
                                }
                            }
                        }

                        println("✅ New message created with ID: ${newMessage.id}")

                        // Update task with new message ID
                        TaskManager.updateTaskMessageId(taskId, newMessage.id.toString())

                        // Only delete the original message after successful creation
                        interaction.message.delete()
                        println("✅ Original message deleted, task moved successfully")

                        updateChannelSummary(bot, inProgressChannelId, TaskState.IN_PROGRESS)

                    } catch (e: Exception) {
                        println("❌ Error moving task to in-progress: ${e.message}")
                        e.printStackTrace()
                        interaction.respondEphemeral {
                            content = "❌ Failed to move task to in-progress channel: ${e.message}"
                        }
                    }
                }

                componentId.startsWith("mark-completed-") -> {
                    val taskId = componentId.removePrefix("mark-completed-")
                    val task = TaskManager.getTaskById(taskId)

                    if (task == null) {
                        interaction.respondEphemeral {
                            content = "❌ Task not found."
                        }
                        return@on
                    }

                    println("📋 Moving task $taskId to completed...")
                    interaction.deferPublicMessageUpdate()
                    TaskManager.updateTaskState(taskId, TaskState.COMPLETED)

                    val config = de.frinshy.config.BotConfig.getInstance()
                    val completedChannelId = config.completedTasksChannelId

                    if (completedChannelId == null) {
                        println("❌ Completed channel ID not configured")
                        interaction.respondEphemeral {
                            content = "❌ Completed channel not configured."
                        }
                        return@on
                    }

                    try {
                        val channel = bot.getChannelOf<dev.kord.core.entity.channel.TextChannel>(
                            dev.kord.common.entity.Snowflake(completedChannelId)
                        )
                        val updatedTask = TaskManager.getTaskById(taskId)

                        if (channel == null) {
                            println("❌ Could not find completed channel with ID: $completedChannelId")
                            interaction.respondEphemeral {
                                content = "❌ Could not find completed channel."
                            }
                            return@on
                        }

                        if (updatedTask == null) {
                            println("❌ Updated task not found after state change")
                            interaction.respondEphemeral {
                                content = "❌ Task not found after update."
                            }
                            return@on
                        }

                        println("✅ Creating new message in completed channel...")
                        val newMessage = channel.createMessage {
                            embed {
                                this.title = updatedTask.title
                                this.color = dev.kord.common.Color(0x43B581)
                                this.description = updatedTask.description
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
                                field {
                                    name = "Assigned Users"
                                    value = if (updatedTask.assignedUsers.isNotEmpty()) {
                                        TaskManager.formatAssignedUsers(updatedTask.assignedUsers)
                                    } else {
                                        "_No users assigned_"
                                    }
                                    inline = false
                                }
                            }

                            actionRow {
                                interactionButton(
                                    style = ButtonStyle.Secondary,
                                    customId = "reopen-task-$taskId"
                                ) {
                                    label = "Reopen"
                                }
                                interactionButton(
                                    style = ButtonStyle.Danger,
                                    customId = "delete-task-$taskId"
                                ) {
                                    label = "Delete Task"
                                }
                                interactionButton(
                                    style = ButtonStyle.Secondary,
                                    customId = "edit-task-$taskId"
                                ) {
                                    label = "Edit Task"
                                }
                            }
                        }

                        println("✅ New message created with ID: ${newMessage.id}")

                        // Update task with new message ID
                        TaskManager.updateTaskMessageId(taskId, newMessage.id.toString())

                        // Only delete the original message after successful creation
                        interaction.message.delete()
                        println("✅ Original message deleted, task moved successfully")

                        updateChannelSummary(bot, completedChannelId, TaskState.COMPLETED)

                    } catch (e: Exception) {
                        println("❌ Error moving task to completed: ${e.message}")
                        e.printStackTrace()
                        interaction.respondEphemeral {
                            content = "❌ Failed to move task to completed channel: ${e.message}"
                        }
                    }
                }

                componentId.startsWith("edit-task-") -> {
                    val taskId = componentId.removePrefix("edit-task-")
                    val task = TaskManager.getTaskById(taskId)

                    if (task == null) {
                        interaction.respondEphemeral {
                            content = "❌ Task not found."
                        }
                        return@on
                    }

                    // Show a modal for editing the task
                    interaction.modal("Edit Task", "edit-task-modal-$taskId") {
                        actionRow {
                            textInput(dev.kord.common.entity.TextInputStyle.Short, "title", "Task Title") {
                                this.placeholder = "Enter task title"
                                this.value = task.title
                                this.required = true
                            }
                        }
                        actionRow {
                            textInput(
                                dev.kord.common.entity.TextInputStyle.Paragraph,
                                "description",
                                "Task Description"
                            ) {
                                this.placeholder = "Enter task description"
                                this.value = task.description
                                this.required = true
                            }
                        }
                    }
                }

                else -> {
                    interaction.deferPublicMessageUpdate()
                }
            }
        } catch (e: Exception) {
            println("❌ Error executing command '${interaction.componentId}': ${e.message}")
            e.printStackTrace()

            // Check if this is an interaction already acknowledged error
            if (e.message?.contains("already been acknowledged") == true ||
                e.message?.contains("Unknown interaction") == true
            ) {
                println("⚠️  Interaction already handled or expired")
                return@on
            }

            try {
                interaction.respondEphemeral {
                    content = "❌ An error occurred while processing your request."
                }
            } catch (responseError: Exception) {
                println("⚠️  Could not send error response to user: ${responseError.message}")
            }
        }
    }

    // Helper for error handling
    suspend fun handleInteractionError(e: Exception, interactionId: String, respond: suspend () -> Unit) {
        println("❌ Error processing interaction '$interactionId': ${e.message}")
        e.printStackTrace()
        if (e.message?.contains("already been acknowledged") == true ||
            e.message?.contains("Unknown interaction") == true
        ) {
            println("⚠️  Interaction already handled or expired")
            return
        }
        try {
            respond()
        } catch (responseError: Exception) {
            println("⚠️  Could not send error response to user: ${responseError.message}")
        }
    }

    // Modal Handlers
    suspend fun handleAssignUserModal(event: ModalSubmitInteractionCreateEvent, bot: Kord) {
        val interaction = event.interaction
        val taskId = interaction.modalId.removePrefix("assign-user-modal-")
        val userInput = interaction.textInputs["user-id"]?.value?.trim()
        if (userInput.isNullOrBlank()) {
            interaction.respondEphemeral { content = "❌ Please provide a valid user ID, mention, or name." }
            return
        }
        val userId = if (userInput.startsWith("<@") && userInput.endsWith(">")) {
            userInput.removePrefix("<@").removeSuffix(">").removePrefix("!")
        } else userInput
        val task = TaskManager.getTaskById(taskId)
        if (task == null) {
            interaction.respondEphemeral { content = "❌ Task not found." }
            return
        }
        val success = TaskManager.assignUserToTask(taskId, userId)
        val displayName = if (userId.matches(Regex("\\d+"))) "<@$userId>" else "\"$userId\""
        if (success) {
            TaskManager.getTaskById(taskId)?.let { TaskManager.updateTaskEmbed(bot, it) }
            interaction.respondEphemeral { content = "✅ Successfully assigned $displayName to task \"${task.title}\"!" }
        } else {
            interaction.respondEphemeral { content = "⚠️ $displayName is already assigned to task \"${task.title}\"." }
        }
    }

    suspend fun handleEditTaskModal(event: ModalSubmitInteractionCreateEvent, bot: Kord) {
        val interaction = event.interaction
        val taskId = interaction.modalId.removePrefix("edit-task-modal-")
        val newTitle = interaction.textInputs["title"]?.value?.trim()
        val newDescription = interaction.textInputs["description"]?.value?.trim()
        if (newTitle.isNullOrBlank() || newDescription.isNullOrBlank()) {
            interaction.respondEphemeral { content = "❌ Please provide both title and description." }
            return
        }
        val task = TaskManager.getTaskById(taskId)
        if (task == null) {
            interaction.respondEphemeral { content = "❌ Task not found." }
            return
        }
        TaskManager.updateTask(taskId, newTitle, newDescription)
        TaskManager.getTaskById(taskId)?.let { TaskManager.updateTaskEmbed(bot, it) }
        interaction.respondEphemeral { content = "✅ Task \"${newTitle}\" has been updated successfully!" }
    }

    bot.on<ModalSubmitInteractionCreateEvent> {
        try {
            val modalId = interaction.modalId
            val interactionAge = kotlinx.datetime.Clock.System.now() - interaction.id.timestamp
            if (interactionAge.inWholeMinutes > 14) {
                println("⚠️  Ignoring expired modal interaction: $modalId")
                return@on
            }
            when {
                modalId.startsWith("assign-user-modal-") -> handleAssignUserModal(this, bot)
                modalId.startsWith("edit-task-modal-") -> handleEditTaskModal(this, bot)
                else -> interaction.respondEphemeral { content = "❌ Unknown modal submission." }
            }
        } catch (e: Exception) {
            handleInteractionError(e, interaction.modalId) {
                interaction.respondEphemeral {
                    content = "❌ An error occurred while processing your submission."
                }
            }
        }
    }
    bot.login()
}
