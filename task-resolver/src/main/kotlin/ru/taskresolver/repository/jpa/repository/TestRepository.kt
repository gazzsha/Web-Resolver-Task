package ru.taskresolver.repository.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import ru.common.NOT_FOUND_EXCEPTION_MESSAGE
import ru.taskresolver.model.exception.NotFoundException
import ru.taskresolver.repository.jpa.model.Test
import java.util.UUID

interface TestRepository : JpaRepository<Test, Long> {

    fun findTestByTestId(testId: UUID): Test?

    fun getTestByTestId(problemId: UUID): Test =
        findTestByTestId(problemId) ?: throw NotFoundException(NOT_FOUND_EXCEPTION_MESSAGE)


}

