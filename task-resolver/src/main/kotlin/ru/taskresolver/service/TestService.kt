package ru.taskresolver.service

import org.springframework.stereotype.Service
import ru.taskresolver.repository.jpa.repository.TestRepository
import java.util.UUID

@Service
class TestService(
    private val testRepository: TestRepository
) {
    fun getTestById(testId: UUID) =
        testRepository.getTestByTestId(testId)
}
