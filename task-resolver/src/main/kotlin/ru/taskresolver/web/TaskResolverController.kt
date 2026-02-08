package ru.taskresolver.web

import model.ResultResponse
import model.StartTaskRequest
import model.StartTaskSuccessResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import ru.taskresolver.service.process.TaskResolverProcessService
import web.TaskResolverApi

@Controller
class TaskResolverController(
    private val taskResolverProcessService: TaskResolverProcessService
) : TaskResolverApi {
    override fun startTask(startTaskRequest: StartTaskRequest): ResponseEntity<StartTaskSuccessResponse> {
        val idTask = taskResolverProcessService.processStartTaskToResolve(startTaskRequest)
        return ResponseEntity.ok().body(StartTaskSuccessResponse()
            .id(idTask)
            .result(ResultResponse().success(true))
        )
    }
}


