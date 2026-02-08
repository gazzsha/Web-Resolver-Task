package ru.taskresolver.repository.jpa.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import org.hibernate.annotations.JdbcTypeCode
import org.hibernate.type.SqlTypes
import java.util.UUID

@Entity
data class TestResolve(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    @JdbcTypeCode(SqlTypes.JSON)
    var arguments: List<ArgumentTest> = emptyList(),

    @Enumerated(value = EnumType.STRING)
    var returnType: Type,

    @JdbcTypeCode(SqlTypes.JSON)
    var tests: List<Tests>,

    var problemId: UUID
)

data class Tests(
    val input: String,
    val expectedOutput: String,
)

data class ArgumentTest(
    val position: Int,
    @Enumerated(value = EnumType.STRING)
    val type: Type
)
