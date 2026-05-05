package ru.taskresolver.repository.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.common.NOT_FOUND_EXCEPTION_MESSAGE
import ru.db.entity.TestResolve
import ru.taskresolver.model.exception.NotFoundException
import java.util.UUID

interface TestResolveRepository : JpaRepository<TestResolve, Long> {
    fun findTestResolveByProblemId(problemId: UUID): TestResolve?

    fun getTestResolveByProblemId(problemId: UUID): TestResolve =
        findTestResolveByProblemId(problemId)
            ?: throw NotFoundException(NOT_FOUND_EXCEPTION_MESSAGE)
}
