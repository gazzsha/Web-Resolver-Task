package ru.taskresolver

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class TaskResolverApplication

fun main(args: Array<String>) {
    runApplication<TaskResolverApplication>(*args)
}
