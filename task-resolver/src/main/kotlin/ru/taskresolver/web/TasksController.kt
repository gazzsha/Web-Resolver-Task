package ru.taskresolver.web

import model.TaskInfo
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import ru.taskresolver.service.TestService
import web.TasksApi
import java.util.UUID

@Controller
class TasksController(
    private val testService: TestService
) : TasksApi {

    override fun getAllTasks(): ResponseEntity<List<TaskInfo>> {
        val tasks = testService.getAllTasks()
        return ResponseEntity.ok(tasks)
    }

    override fun getTaskById(taskId: UUID): ResponseEntity<TaskInfo> {
        val task = testService.getTaskById(taskId)
        return ResponseEntity.ok(task)
    }
}
