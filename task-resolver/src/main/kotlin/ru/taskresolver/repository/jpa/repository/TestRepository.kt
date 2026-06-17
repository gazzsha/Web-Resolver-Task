package ru.taskresolver.repository.jpa.repository

import org.springframework.data.jpa.repository.JpaRepository
import org.springframework.data.jpa.repository.Query
import org.springframework.data.repository.query.Param
import ru.common.NOT_FOUND_EXCEPTION_MESSAGE
import ru.db.entity.Difficulty
import ru.db.entity.Test
import ru.taskresolver.model.exception.NotFoundException
import java.util.UUID

interface TestRepository : JpaRepository<Test, Long> {

    fun findTestByTestId(testId: UUID): Test?

    fun getTestByTestId(problemId: UUID): Test =
        findTestByTestId(problemId) ?: throw NotFoundException(NOT_FOUND_EXCEPTION_MESSAGE)

    fun findAllByOrderByDifficulty(): List<Test>

    @Query(
        """
        SELECT t FROM Test t
        WHERE (:category IS NULL OR t.category = :category)
          AND (:difficulty IS NULL OR t.difficulty = :difficulty)
        ORDER BY t.difficulty
        """
    )
    fun searchByFilters(
        @Param("category") category: String?,
        @Param("difficulty") difficulty: Difficulty?,
    ): List<Test>

    @Query("SELECT DISTINCT t.category FROM Test t WHERE t.category IS NOT NULL ORDER BY t.category")
    fun findAllCategories(): List<String>

    // F-5: проекция вместо findAll() для дедупликации в TaskImportService.
    // Избегаем загрузки всей сущности (description, difficulty, category) в heap
    // при импорте больших CSV.
    @Query("SELECT LOWER(t.title) FROM Test t WHERE t.title IS NOT NULL")
    fun findAllTitlesLowercase(): List<String>
}
