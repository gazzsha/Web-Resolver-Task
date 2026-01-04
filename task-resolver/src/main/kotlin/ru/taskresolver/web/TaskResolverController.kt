package ru.taskresolver.web

import model.StartTaskSuccessResponse
import org.springframework.http.ResponseEntity
import org.springframework.stereotype.Controller
import web.TaskResolverApi

@Controller
class TaskResolverController : TaskResolverApi {
    override fun startTask(): ResponseEntity<StartTaskSuccessResponse> {
        TODO("Not yet implemented")
    }
}
