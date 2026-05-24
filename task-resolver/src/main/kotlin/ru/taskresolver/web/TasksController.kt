package ru.taskresolver.web

import model.Difficulty
import model.TaskImportResult
import model.TaskInfo
import org.springframework.http.ResponseEntity
import org.springframework.security.access.prepost.PreAuthorize
import org.springframework.stereotype.Controller
import org.springframework.web.multipart.MultipartFile
import ru.taskresolver.service.TaskImportService
import ru.taskresolver.service.TestService
import web.TasksApi
import java.util.UUID

@Controller
class TasksController(
    private val testService: TestService,
    private val taskImportService: TaskImportService,
) : TasksApi {

    override fun getAllTasks(category: String?, difficulty: Difficulty?): ResponseEntity<List<TaskInfo>> {
        val tasks = testService.getAllTasks(category, difficulty)
        return ResponseEntity.ok(tasks)
    }

    override fun getTaskCategories(): ResponseEntity<List<String>> =
        ResponseEntity.ok(testService.getAllCategories())

    override fun getTaskById(taskId: UUID): ResponseEntity<TaskInfo> {
        val task = testService.getTaskById(taskId)
        return ResponseEntity.ok(task)
    }

    @PreAuthorize("hasRole('TEACHER')")
    override fun importTasksFromCsv(file: MultipartFile): ResponseEntity<TaskImportResult> {
        val result = taskImportService.importFromCsv(file)
        return ResponseEntity.ok(result)
    }
}
