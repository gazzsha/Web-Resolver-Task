package ru.taskresolver.service

import model.Difficulty
import model.TaskInfo
import org.springframework.stereotype.Service
import ru.db.entity.Test
import ru.taskresolver.repository.jpa.repository.TestRepository
import java.util.UUID

@Service
class TestService(
    private val testRepository: TestRepository
) {
    fun getTestById(testId: UUID): Test =
        testRepository.getTestByTestId(testId)

    fun getAllTasks(category: String? = null, difficulty: Difficulty? = null): List<TaskInfo> {
        val dbDifficulty = difficulty?.let { ru.db.entity.Difficulty.valueOf(it.value) }
        val items = if (category == null && dbDifficulty == null) {
            testRepository.findAllByOrderByDifficulty()
        } else {
            testRepository.searchByFilters(category?.trim()?.takeIf { it.isNotEmpty() }, dbDifficulty)
        }
        return items.map(::toTaskInfo)
    }

    fun getTaskById(testId: UUID): TaskInfo {
        val test = getTestById(testId)
        return toTaskInfo(test)
    }

    fun getAllCategories(): List<String> = testRepository.findAllCategories()

    private fun toTaskInfo(test: Test): TaskInfo =
        TaskInfo(
            test.testId,
            test.title,
            test.description,
            Difficulty.fromValue(test.difficulty.name),
        ).apply { category = test.category }
}
