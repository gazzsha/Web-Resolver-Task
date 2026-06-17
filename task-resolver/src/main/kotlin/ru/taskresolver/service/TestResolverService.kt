package ru.taskresolver.service

import org.springframework.stereotype.Service
import ru.taskresolver.repository.jpa.repository.TestResolveRepository
import java.util.UUID

@Service
class TestResolverService(
    private val testResolveRepository: TestResolveRepository
) {
    fun getTestResolvesByTestId(testId: UUID) =
        testResolveRepository.getTestResolveByProblemId(testId)
}
