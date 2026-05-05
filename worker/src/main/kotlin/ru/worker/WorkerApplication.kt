package ru.worker

import org.springframework.boot.autoconfigure.SpringBootApplication
import org.springframework.boot.runApplication

@SpringBootApplication
class WorkerApplication {
    // Worker services will be configured via @Configuration classes
}

fun main(args: Array<String>) {
    runApplication<WorkerApplication>(*args)
}
