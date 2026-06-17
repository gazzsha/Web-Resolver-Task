package ru.taskresolver.web

import model.Lang
import model.ResultResponse
import model.StartTaskRequest
import model.StartTaskSuccessResponse
import org.springframework.http.HttpStatus
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import org.springframework.web.server.ResponseStatusException
import ru.taskresolver.service.process.TaskResolverProcessService
import web.TaskResolverApi

@Controller
class TaskResolverController(
    private val taskResolverProcessService: TaskResolverProcessService
) : TaskResolverApi {
    override fun startTask(startTaskRequest: StartTaskRequest): ResponseEntity<StartTaskSuccessResponse> {
        // P0-1: Kotlin исключён из MVP (kotlinc cold start превышает sandbox-timeout).
        // OpenAPI enum lang оставлен совместимым с историческими submissions в БД;
        // отказ выполняется на уровне контроллера, без правки спеки.
        if (startTaskRequest.language == Lang.KOTLIN) {
            throw ResponseStatusException(
                HttpStatus.BAD_REQUEST,
                "Kotlin не поддерживается в текущей версии. Используйте Java или Python."
            )
        }
        val idTask = taskResolverProcessService.processStartTaskToResolve(startTaskRequest)
        return ResponseEntity.ok().body(StartTaskSuccessResponse()
            .id(idTask)
            .result(ResultResponse().success(true))
        )
    }
}


