package ru.taskresolver.service

import model.TaskInfo
import org.springframework.stereotype.Service
import ru.taskresolver.repository.jpa.model.Test
import ru.taskresolver.repository.jpa.repository.TestRepository
import java.util.UUID

@Service
class TestService(
    private val testRepository: TestRepository
) {
    fun getTestById(testId: UUID): Test =
        testRepository.getTestByTestId(testId)

    fun getAllTasks(): List<TaskInfo> {
        return testRepository.findAllByOrderByDifficulty().map { test ->
            TaskInfo(test.testId, test.title, test.description, model.Difficulty.fromValue(test.difficulty.name))
        }
    }

    fun getTaskById(testId: UUID): TaskInfo {
        val test = getTestById(testId)
        return TaskInfo(test.testId, test.title, test.description, model.Difficulty.fromValue(test.difficulty.name))
    }
}
