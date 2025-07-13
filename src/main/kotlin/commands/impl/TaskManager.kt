package de.frinshy.commands.impl

import kotlinx.serialization.Serializable
import kotlinx.serialization.encodeToString
import kotlinx.serialization.decodeFromString
import kotlinx.serialization.json.Json
import java.io.File

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

    fun removeTask(id: String) {
        tasks.removeAll { it.id == id }
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
}
