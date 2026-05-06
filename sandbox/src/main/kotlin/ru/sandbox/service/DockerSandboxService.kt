package ru.sandbox.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.model.*
import java.nio.file.Files
import java.nio.file.Paths
import java.util.UUID
import java.util.concurrent.TimeUnit

private val logger = KotlinLogging.logger {}

/**
 * Sandbox service for secure code execution using Docker containers
 * 
 * Security features:
 * - Isolated Docker containers
 * - CPU and memory limits
 * - Network disabled
 * - Read-only filesystem
 * - Timeout protection
 */
class DockerSandboxService(
    private val imageManager: SandboxImageManager
) {
    
    private val workDir = Paths.get("/tmp/web-resolver-sandbox")

    init {
        // Create work directory
        Files.createDirectories(workDir)
        logger.info { "DockerSandboxService initialized" }
    }
    
    /**
     * Execute user code in isolated Docker container
     */
    fun execute(request: SandboxExecutionRequest): SandboxExecutionResult {
        logger.info { "Executing code for request ${request.requestId}, language: ${request.language}" }
        
        return try {
            when (request.language.lowercase()) {
                "java" -> executeJavaCode(request)
                "python" -> executePythonCode(request)
                "kotlin" -> executeKotlinCode(request)
                else -> SandboxExecutionResult(
                    requestId = request.requestId,
                    status = ExecutionStatus.INTERNAL_ERROR,
                    output = null,
                    error = "Unsupported language: ${request.language}",
                    executionTimeMs = 0,
                    memoryUsedKb = 0
                )
            }
        } catch (e: Exception) {
            logger.error(e) { "Sandbox execution failed for request ${request.requestId}" }
            SandboxExecutionResult(
                requestId = request.requestId,
                status = ExecutionStatus.INTERNAL_ERROR,
                output = null,
                error = e.message,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        }
    }
    
    private fun executeJavaCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val startTime = System.currentTimeMillis()
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)

        try {
            // Extract class name from code or use provided
            val className = extractClassName(request.code) ?: request.className
            
            // Write user code (full code with main method)
            val javaFile = requestDir.resolve("$className.java")
            Files.writeString(javaFile, request.code)

            // Write test input to stdin file
            val inputFile = requestDir.resolve("input.txt")
            Files.writeString(inputFile, request.testInput)

            // Write expected output for comparison
            val expectedFile = requestDir.resolve("expected.txt")
            Files.writeString(expectedFile, request.expectedOutput)

            // Compile and execute using Docker with JDK
            // User code should have main method that reads from stdin
            val runResult = runInDocker(
                requestId = request.requestId,
                requestDir = requestDir,
                image = "eclipse-temurin:21-jdk-alpine",
                command = listOf("sh", "-c", "javac $className.java && java $className < input.txt"),
                timeoutSeconds = request.timeoutSeconds,
                memoryLimitMb = request.memoryLimitMb,
                cpuLimit = request.cpuLimit
            )

            val endTime = System.currentTimeMillis()

            // Compare output with expected
            val verdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else null

            return runResult.copy(
                executionTimeMs = endTime - startTime,
                verdict = verdict
            )
        } finally {
            // Cleanup
            requestDir.toFile().deleteRecursively()
        }
    }
    
    private fun executePythonCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val startTime = System.currentTimeMillis()
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)

        try {
            // Write Python code
            val pyFile = requestDir.resolve("solution.py")
            Files.writeString(pyFile, request.code)

            // Write test input to stdin file
            val inputFile = requestDir.resolve("input.txt")
            Files.writeString(inputFile, request.testInput)

            // Write expected output
            val expectedFile = requestDir.resolve("expected.txt")
            Files.writeString(expectedFile, request.expectedOutput)

            // Run Python code in container; pipe input.txt → stdin
            val runResult = runInDocker(
                requestId = request.requestId,
                requestDir = requestDir,
                image = "python:3.11-alpine",
                command = listOf("sh", "-c", "python solution.py < input.txt"),
                timeoutSeconds = request.timeoutSeconds,
                memoryLimitMb = request.memoryLimitMb,
                cpuLimit = request.cpuLimit
            )

            val endTime = System.currentTimeMillis()

            val verdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else null

            return runResult.copy(
                executionTimeMs = endTime - startTime,
                verdict = verdict
            )
        } finally {
            requestDir.toFile().deleteRecursively()
        }
    }
    
    private fun executeKotlinCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val startTime = System.currentTimeMillis()
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)

        try {
            val className = extractClassName(request.code) ?: "Solution"
            val ktFile = requestDir.resolve("$className.kt")
            Files.writeString(ktFile, request.code)

            // Write test input to stdin file
            val inputFile = requestDir.resolve("input.txt")
            Files.writeString(inputFile, request.testInput)

            // Write expected output
            val expectedFile = requestDir.resolve("expected.txt")
            Files.writeString(expectedFile, request.expectedOutput)

            // Compile and run Kotlin code in container; pipe input.txt → stdin
            val runResult = runInDocker(
                requestId = request.requestId,
                requestDir = requestDir,
                image = "gradle:8.5-jdk21",
                command = listOf("sh", "-c", "kotlinc $className.kt -include-runtime -d solution.jar && java -jar solution.jar < input.txt"),
                timeoutSeconds = request.timeoutSeconds * 3,
                memoryLimitMb = request.memoryLimitMb,
                cpuLimit = request.cpuLimit
            )

            val endTime = System.currentTimeMillis()

            val verdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else null

            return runResult.copy(
                executionTimeMs = endTime - startTime,
                verdict = verdict
            )
        } finally {
            requestDir.toFile().deleteRecursively()
        }
    }
    
    private fun runInDocker(
        requestId: UUID,
        requestDir: java.nio.file.Path,
        image: String,
        command: List<String>,
        timeoutSeconds: Long,
        memoryLimitMb: Int,
        cpuLimit: Double
    ): SandboxExecutionResult {
        try {
            if (!imageManager.ensureImage(image)) {
                return SandboxExecutionResult(
                    requestId = requestId,
                    status = ExecutionStatus.INTERNAL_ERROR,
                    output = null,
                    error = "Image not available: $image",
                    executionTimeMs = 0,
                    memoryUsedKb = 0
                )
            }

            // Build Docker run command
            val dockerCmd = buildList {
                add("docker")
                add("run")
                add("--rm")
                add("--network=none")
                add("--read-only")
                add("--tmpfs")
                add("/tmp:rw,noexec,nosuid,nodev,size=64m")
                add("--cap-drop=ALL")
                add("--security-opt=no-new-privileges:true")
                add("--pids-limit=64")
                add("-m")
                add("${memoryLimitMb}m")
                add("--cpus")
                add("$cpuLimit")
                add("-v")
                add("${requestDir.toAbsolutePath()}:/app")
                add("-w")
                add("/app")
                add(image)
                addAll(command)
            }

            logger.debug { "Executing: ${dockerCmd.joinToString(" ")}" }

            val process = ProcessBuilder(dockerCmd)
                .directory(requestDir.toFile())
                .redirectErrorStream(true)
                .start()

            val finished = process.waitFor(timeoutSeconds, java.util.concurrent.TimeUnit.SECONDS)

            val output = if (finished) {
                process.inputStream.bufferedReader().readText().trim()
            } else {
                process.destroyForcibly()
                ""
            }

            val exitCode = if (finished) process.exitValue() else 124

            val status = when {
                exitCode == 0 -> ExecutionStatus.SUCCESS
                exitCode == 137 -> ExecutionStatus.MEMORY_LIMIT_EXCEEDED
                exitCode == 124 -> ExecutionStatus.TIME_LIMIT_EXCEEDED
                else -> ExecutionStatus.RUNTIME_ERROR
            }

            return SandboxExecutionResult(
                requestId = requestId,
                status = status,
                output = output,
                error = null,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        } catch (e: Exception) {
            logger.error(e) { "Docker execution failed" }
            return SandboxExecutionResult(
                requestId = requestId,
                status = ExecutionStatus.INTERNAL_ERROR,
                output = null,
                error = e.message,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        }
    }
    
    private fun extractClassName(code: String): String? {
        val classPattern = Regex("""(?:public\s+)?class\s+(\w+)""")
        return classPattern.find(code)?.groupValues?.get(1)
    }
    
    private fun compareOutput(actual: String, expected: String): Verdict {
        val normalizedActual = actual.trim().replace(Regex("\\s+"), " ")
        val normalizedExpected = expected.trim().replace(Regex("\\s+"), " ")
        
        return when {
            normalizedActual == normalizedExpected -> Verdict.OK
            normalizedActual.replace(" ", "") == normalizedExpected.replace(" ", "") -> Verdict.PRESENTATION_ERROR
            else -> Verdict.WRONG_ANSWER
        }
    }
}
