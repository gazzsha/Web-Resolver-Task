package ru.taskresolver.service.process

import model.StartTaskRequest
import org.springframework.stereotype.Service
import ru.taskresolver.kafka.producer.TaskClusterProducer
import ru.taskresolver.kafka.producer.model.TaskMessage
import ru.taskresolver.service.TestService
import java.util.UUID

@Service
class TaskResolverProcessService(
    private val testService: TestService,
    private val taskClusterProducer: TaskClusterProducer
) {

    fun processStartTaskToResolve(request: StartTaskRequest): UUID {
        val test = testService.getTestById(request.testId)
        val taskMessage = TaskMessage(
            taskId = UUID.randomUUID(),
            code = request.code,
            language = request.language.value,
            testId = test.testId
        )
        taskClusterProducer.publish(taskMessage)
        return taskMessage.taskId
    }
}
