package ru.taskresolver.repository.jpa.model

import jakarta.persistence.Entity
import jakarta.persistence.EnumType
import jakarta.persistence.Enumerated
import jakarta.persistence.GeneratedValue
import jakarta.persistence.GenerationType
import jakarta.persistence.Id
import java.util.UUID

@Entity
data class Test(
    @Id
    @GeneratedValue(strategy = GenerationType.IDENTITY)
    var id: Long = 0,

    var testId: UUID,

    var description: String,

    var title: String,

    @Enumerated(value = EnumType.STRING)
    var difficulty: Difficulty
)

enum class Difficulty {
    Easy,
    Medium,
    Hard
}
