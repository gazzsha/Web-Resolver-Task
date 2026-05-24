package ru.sandbox.service

import io.github.oshai.kotlinlogging.KotlinLogging
import ru.sandbox.metrics.SandboxMetrics
import ru.sandbox.model.*
import java.nio.file.Files
import java.nio.file.Paths
import java.time.Instant
import java.time.Duration
import java.util.UUID
import java.util.concurrent.Executors
import java.util.concurrent.Future
import java.util.concurrent.ScheduledExecutorService
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.concurrent.atomic.AtomicBoolean
import java.util.concurrent.atomic.AtomicLong

private val logger = KotlinLogging.logger {}

/**
 * Sandbox service for secure code execution using Docker containers.
 *
 * Metrics are measured entirely on the host — no program-internal markers,
 * no stdout/stderr scraping for timing data:
 *
 *   wallTimeMs      — docker inspect .State.StartedAt / .FinishedAt delta
 *   peakMemoryBytes — 100 ms docker stats polling; running maximum in AtomicLong
 *   exitCode        — docker wait return value
 *
 * Containers are NOT started with --rm so we can inspect them post-exit.
 * Cleanup (docker rm -f) is always performed in the finally block.
 *
 * Security features:
 * - Isolated Docker containers
 * - CPU and memory limits
 * - Network disabled
 * - Read-only filesystem with noexec tmpfs
 * - Timeout protection with SIGKILL
 * - Capability drop (ALL)
 */
class DockerSandboxService(
    private val imageManager: SandboxImageManager,
    private val metrics: SandboxMetrics? = null
) {

    private val workDir = Paths.get("/tmp/web-resolver-sandbox")

    /**
     * Shared scheduled thread pool for memory-polling tasks.
     * One poll task per running container; tasks are cancelled on container exit.
     */
    private val pollScheduler: ScheduledExecutorService =
        Executors.newScheduledThreadPool(4)

    init {
        Files.createDirectories(workDir)
        logger.info { "DockerSandboxService initialized" }
    }

    // -----------------------------------------------------------------------
    // Public API
    // -----------------------------------------------------------------------

    /**
     * Execute user code in isolated Docker container.
     * Supported languages: java, python, kotlin.
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

    // -----------------------------------------------------------------------
    // Language-specific launchers
    // -----------------------------------------------------------------------

    private fun executeJavaCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)
        try {
            val className = extractClassName(request.code) ?: request.className
            Files.writeString(requestDir.resolve("$className.java"), request.code)
            Files.writeString(requestDir.resolve("input.txt"), request.testInput)

            val runResult = runInDocker(
                requestId = request.requestId,
                language = "java",
                requestDir = requestDir,
                image = "eclipse-temurin:21-jdk-alpine",
                command = listOf("sh", "-c", "javac $className.java && java $className < input.txt"),
                timeoutSeconds = request.timeoutSeconds,
                memoryLimitMb = request.memoryLimitMb,
                cpuLimit = request.cpuLimit
            )

            // Only compare output when execution succeeded with no error signals
            val finalVerdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else {
                // Preserve the execution-failure verdict already set in runResult
                runResult.verdict
            }

            return runResult.copy(verdict = finalVerdict)
        } finally {
            requestDir.toFile().deleteRecursively()
        }
    }

    private fun executeKotlinCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)
        try {
            // Kotlin source file name doesn't need to match a "main class" — kotlinc bundles into a jar.
            Files.writeString(requestDir.resolve("Solution.kt"), request.code)
            Files.writeString(requestDir.resolve("input.txt"), request.testInput)

            val runResult = runInDocker(
                requestId = request.requestId,
                language = "kotlin",
                requestDir = requestDir,
                image = "web-resolver/kotlin:1.9.22",
                // Reduce JVM startup overhead via -J-Xmx; allocate up to 384m to kotlinc (compile-time only).
                command = listOf(
                    "sh", "-c",
                    "kotlinc -J-Xmx384m Solution.kt -include-runtime -d solution.jar 2>&1 && java -jar solution.jar < input.txt"
                ),
                timeoutSeconds = request.timeoutSeconds,
                memoryLimitMb = maxOf(request.memoryLimitMb, 512), // kotlinc + JVM bundle needs more headroom
                cpuLimit = request.cpuLimit
            )

            val finalVerdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else {
                runResult.verdict
            }

            return runResult.copy(verdict = finalVerdict)
        } finally {
            requestDir.toFile().deleteRecursively()
        }
    }

    private fun executePythonCode(request: SandboxExecutionRequest): SandboxExecutionResult {
        val requestDir = workDir.resolve(request.requestId.toString())
        Files.createDirectories(requestDir)
        try {
            Files.writeString(requestDir.resolve("solution.py"), request.code)
            Files.writeString(requestDir.resolve("input.txt"), request.testInput)

            val runResult = runInDocker(
                requestId = request.requestId,
                language = "python",
                requestDir = requestDir,
                image = "python:3.11-alpine",
                command = listOf("sh", "-c", "python solution.py < input.txt"),
                timeoutSeconds = request.timeoutSeconds,
                memoryLimitMb = request.memoryLimitMb,
                cpuLimit = request.cpuLimit
            )

            val finalVerdict = if (runResult.status == ExecutionStatus.SUCCESS) {
                compareOutput(runResult.output ?: "", request.expectedOutput)
            } else {
                runResult.verdict
            }

            return runResult.copy(verdict = finalVerdict)
        } finally {
            requestDir.toFile().deleteRecursively()
        }
    }

    // -----------------------------------------------------------------------
    // Core Docker runner — host-side metrics only
    // -----------------------------------------------------------------------

    /**
     * Runs user code in a Docker container and returns execution results with
     * host-measured metrics (wall time, peak memory, exit code).
     *
     * Container lifecycle:
     *   1. docker run -d (detached, no --rm) → container id
     *   2. background poll: docker stats every 100 ms → peakMemoryBytes
     *   3. docker wait <id> (blocking, with host-side timeout guard)
     *   4. docker inspect <id> → StartedAt/FinishedAt → wallTimeMs; OOMKilled flag
     *   5. docker logs <id> → separate stdout / stderr streams
     *   6. docker rm -f <id>  (always, in finally block)
     *
     * The returned [SandboxExecutionResult.verdict] reflects execution outcome only
     * (RUNTIME_ERROR / TIME_LIMIT_EXCEEDED / MEMORY_LIMIT_EXCEEDED or null when
     * successful). The language launchers overwrite it with an output-comparison
     * verdict (OK / WRONG_ANSWER / PRESENTATION_ERROR) when status == SUCCESS.
     */
    private fun runInDocker(
        requestId: UUID,
        language: String,
        requestDir: java.nio.file.Path,
        image: String,
        command: List<String>,
        timeoutSeconds: Long,
        memoryLimitMb: Int,
        cpuLimit: Double
    ): SandboxExecutionResult {

        var containerId: String? = null
        val timerSample = metrics?.startExecutionTimer()
        var finalVerdict: Verdict = Verdict.RUNTIME_ERROR

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

            // ---- Step 1: start container in detached mode (no --rm) ----
            // --stop-timeout=0 гарантирует, что любой docker stop НЕ даёт grace-period;
            // основной hard-timeout всё равно реализуется внешним watchdog ниже через
            // docker kill -s SIGKILL, но флаг защищает от утечки на случай побочных stop.
            val dockerRunCmd = buildList {
                add("docker"); add("run"); add("-d")
                add("--platform=linux/amd64")
                add("--network=none")
                add("--read-only")
                add("--tmpfs"); add("/tmp:rw,noexec,nosuid,nodev,size=128m")
                add("--cap-drop=ALL")
                add("--security-opt=no-new-privileges:true")
                add("--pids-limit=64")
                add("--stop-timeout=0")
                add("-m"); add("${memoryLimitMb}m")
                add("--cpus"); add("$cpuLimit")
                add("-v"); add("${requestDir.toAbsolutePath()}:/app")
                add("-w"); add("/app")
                add(image)
                addAll(wrapForPeakMemoryCapture(command))
            }

            logger.debug { "docker run: ${dockerRunCmd.joinToString(" ")}" }

            val runProc = ProcessBuilder(dockerRunCmd)
                .redirectErrorStream(true)
                .start()
            val runFinished = runProc.waitFor(15, TimeUnit.SECONDS)
            if (!runFinished) {
                runProc.destroyForcibly()
                return SandboxExecutionResult(
                    requestId = requestId,
                    status = ExecutionStatus.INTERNAL_ERROR,
                    output = null,
                    error = "docker run did not respond within 15 s (image pull or daemon issue)",
                    executionTimeMs = 0,
                    memoryUsedKb = 0
                )
            }

            val runStdout = runProc.inputStream.bufferedReader().readText().trim()
            if (runProc.exitValue() != 0) {
                logger.error { "docker run failed (exit ${runProc.exitValue()}): $runStdout" }
                return SandboxExecutionResult(
                    requestId = requestId,
                    status = ExecutionStatus.INTERNAL_ERROR,
                    output = null,
                    error = "docker run failed: $runStdout",
                    executionTimeMs = 0,
                    memoryUsedKb = 0
                )
            }

            containerId = runStdout.lines().lastOrNull { it.isNotBlank() } ?: runStdout
            logger.debug { "Container started: $containerId" }

            // ---- Step 2: start background memory sampler (100 ms interval) ----
            val peakMemoryBytes = AtomicLong(0L)
            val pollFuture: Future<*> = pollScheduler.scheduleAtFixedRate(
                { pollMemory(containerId, peakMemoryBytes) },
                0L, 100L, TimeUnit.MILLISECONDS
            )

            // ---- Step 3: wait for container exit with hard host-side watchdog ----
            // Внешний watchdog: ровно через timeoutSeconds*1000ms послать SIGKILL.
            // Это даёт wall-time контейнера ≤ timeoutSeconds (+малую погрешность планировщика),
            // в отличие от прежней схемы с grace 5с, где wall достигал timeoutSeconds+5.
            // docker wait после SIGKILL мгновенно возвращает exit-code (обычно 137),
            // и мы маркируем результат как TIME_LIMIT_EXCEEDED по флагу timedOutFlag.
            val exitCode: Int
            val timedOutFlag = AtomicBoolean(false)
            val watchdog: ScheduledFuture<*> = pollScheduler.schedule(
                {
                    timedOutFlag.set(true)
                    logger.debug { "Watchdog firing SIGKILL for container $containerId (timeout ${timeoutSeconds}s)" }
                    killContainer(containerId)
                },
                timeoutSeconds * 1000L, TimeUnit.MILLISECONDS
            )

            try {
                val waitProc = ProcessBuilder("docker", "wait", containerId)
                    .redirectErrorStream(true)
                    .start()

                // Жёсткий host-side limit: timeoutSeconds + 2с страховки на случай
                // если ScheduledExecutorService задержался под нагрузкой. Watchdog
                // обязан сработать раньше; этот ветка — крайний случай.
                val waitFinished = waitProc.waitFor(timeoutSeconds + 2, TimeUnit.SECONDS)

                if (!waitFinished) {
                    waitProc.destroyForcibly()
                    if (timedOutFlag.compareAndSet(false, true)) {
                        killContainer(containerId)
                    }
                    exitCode = 124
                } else {
                    val waitOut = waitProc.inputStream.bufferedReader().readText().trim()
                    exitCode = waitOut.toIntOrNull() ?: -1
                }
            } finally {
                watchdog.cancel(false)
                pollFuture.cancel(true)
            }
            val timedOut = timedOutFlag.get()

            // ---- Step 4: inspect for wall time and OOMKilled flag ----
            val inspectResult = inspectContainer(containerId)
            val wallTimeMs = inspectResult.wallTimeMs
            val oomKilled = inspectResult.oomKilled

            // ---- Step 5: collect stdout and stderr separately ----
            val stdout = dockerLogs(containerId, stderr = false).trim()
            val stderr = dockerLogs(containerId, stderr = true).trim()

            // P1-6: для коротких программ (<500мс) polling каждые 100мс не успевает
            // снять реальный peak; читаем cgroup-файл memory.peak, сохранённый
            // самим контейнером перед exit в /app/.peak_memory_bytes (см. wrapForPeakMemoryCapture).
            val cgroupPeakBytes = readCgroupPeakMemoryFile(requestDir)
            val effectivePeakBytes = maxOf(peakMemoryBytes.get(), cgroupPeakBytes)

            logger.debug {
                "Container $containerId finished: exit=$exitCode oom=$oomKilled " +
                "wall=${wallTimeMs}ms polledPeak=${peakMemoryBytes.get()}B cgroupPeak=${cgroupPeakBytes}B"
            }

            // ---- Step 6: resolve verdict from host signals ----
            val verdict = resolveVerdict(
                exitCode = exitCode,
                oomKilled = oomKilled,
                timedOut = timedOut,
                stderr = stderr,
                language = language
            )
            finalVerdict = verdict
            if (oomKilled || verdict == Verdict.MEMORY_LIMIT_EXCEEDED) {
                metrics?.recordOomKilled()
            }
            val status = verdictToExecutionStatus(verdict)

            return SandboxExecutionResult(
                requestId = requestId,
                status = status,
                output = stdout.ifBlank { null },
                error = stderr.ifBlank { null },
                executionTimeMs = wallTimeMs,
                memoryUsedKb = effectivePeakBytes / 1024,
                verdict = verdict
            )
        } catch (e: Exception) {
            logger.error(e) { "Docker execution failed" }
            finalVerdict = Verdict.RUNTIME_ERROR
            return SandboxExecutionResult(
                requestId = requestId,
                status = ExecutionStatus.INTERNAL_ERROR,
                output = null,
                error = e.message,
                executionTimeMs = 0,
                memoryUsedKb = 0
            )
        } finally {
            // Always remove the container (we did NOT use --rm)
            containerId?.let { removeContainer(it) }
            if (timerSample != null) {
                metrics.stopExecutionTimer(timerSample, language, finalVerdict)
            }
        }
    }

    // -----------------------------------------------------------------------
    // Verdict resolution (Stage B fail-fast signals)
    // -----------------------------------------------------------------------

    /**
     * Resolves the canonical execution verdict from host-observable signals only.
     *
     * Priority (highest to lowest):
     *   1. OOMKilled flag from docker inspect   → MEMORY_LIMIT_EXCEEDED
     *   2. exit code 137 (SIGKILL, often OOM)   → MEMORY_LIMIT_EXCEEDED
     *   3. Host-side timeout / exit 124          → TIME_LIMIT_EXCEEDED
     *   4. exit code 139 (SIGSEGV)               → RUNTIME_ERROR (segfault)
     *   5. exit code != 0                        → RUNTIME_ERROR
     *   6. stderr matches per-language pattern   → RUNTIME_ERROR
     *   7. Otherwise                             → OK (placeholder; overwritten by
     *                                              compareOutput in language launchers)
     *
     * Note: WRONG_ANSWER / PRESENTATION_ERROR are output-comparison verdicts set
     * by compareOutput(), not by this method.
     */
    private fun resolveVerdict(
        exitCode: Int,
        oomKilled: Boolean,
        timedOut: Boolean,
        stderr: String,
        language: String
    ): Verdict = when {
        // timedOut должен быть раньше exit==137: watchdog шлёт SIGKILL, контейнер
        // отдаёт код 137, который без флага был бы интерпретирован как OOM.
        timedOut                                           -> Verdict.TIME_LIMIT_EXCEEDED
        oomKilled                                          -> Verdict.MEMORY_LIMIT_EXCEEDED
        exitCode == 137                                    -> Verdict.MEMORY_LIMIT_EXCEEDED
        exitCode == 124                                    -> Verdict.TIME_LIMIT_EXCEEDED
        exitCode == 139                                    -> Verdict.RUNTIME_ERROR   // SIGSEGV
        exitCode != 0                                      -> Verdict.RUNTIME_ERROR
        LanguageErrorPattern.stderrIndicatesError(language, stderr) -> Verdict.RUNTIME_ERROR
        else                                               -> Verdict.OK
    }

    /**
     * Maps execution verdict to [ExecutionStatus] for the result DTO.
     * Output-comparison verdicts (OK / WRONG_ANSWER / PRESENTATION_ERROR)
     * all map to SUCCESS — the caller decides if output matched.
     */
    private fun verdictToExecutionStatus(verdict: Verdict): ExecutionStatus = when (verdict) {
        Verdict.OK, Verdict.WRONG_ANSWER, Verdict.PRESENTATION_ERROR -> ExecutionStatus.SUCCESS
        Verdict.RUNTIME_ERROR                                         -> ExecutionStatus.RUNTIME_ERROR
        Verdict.TIME_LIMIT_EXCEEDED                                   -> ExecutionStatus.TIME_LIMIT_EXCEEDED
        Verdict.MEMORY_LIMIT_EXCEEDED                                 -> ExecutionStatus.MEMORY_LIMIT_EXCEEDED
        Verdict.COMPILATION_ERROR                                     -> ExecutionStatus.COMPILATION_ERROR
    }

    // -----------------------------------------------------------------------
    // Docker helper calls
    // -----------------------------------------------------------------------

    /** Send SIGKILL to a running container. */
    private fun killContainer(id: String) {
        try {
            ProcessBuilder("docker", "kill", "--signal=SIGKILL", id)
                .redirectErrorStream(true)
                .start()
                .waitFor(5, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn(e) { "docker kill failed for $id" }
        }
    }

    /** Force-remove a container. Always called in finally. */
    private fun removeContainer(id: String) {
        try {
            ProcessBuilder("docker", "rm", "-f", id)
                .redirectErrorStream(true)
                .start()
                .waitFor(10, TimeUnit.SECONDS)
        } catch (e: Exception) {
            logger.warn(e) { "docker rm -f failed for $id" }
        }
    }

    /**
     * Collect stdout or stderr from a stopped container via docker logs.
     * [stderr] = true  → returns only stderr stream
     * [stderr] = false → returns only stdout stream
     *
     * Note: docker logs sends stdout to its own stdout and stderr to its own
     * stderr, so we must NOT use redirectErrorStream here.
     */
    /**
     * `docker logs <id>` writes the container's stdout to the docker process's
     * stdout, and the container's stderr to the docker process's stderr (with
     * --tty unset, which is our case). So a single invocation captures both —
     * we just read the right stream. The previous version used `--stdout=true
     * --stderr=false` flags that don't exist in docker CLI and silently
     * produced empty output.
     */
    private fun dockerLogs(id: String, stderr: Boolean): String {
        return try {
            val proc = ProcessBuilder("docker", "logs", id)
                .redirectErrorStream(false)
                .start()
            // Read both streams to drain the pipe before waitFor.
            val out = proc.inputStream.bufferedReader().readText()
            val err = proc.errorStream.bufferedReader().readText()
            proc.waitFor(10, TimeUnit.SECONDS)
            if (stderr) err else out
        } catch (e: Exception) {
            logger.warn(e) { "docker logs failed for $id (stderr=$stderr)" }
            ""
        }
    }

    /**
     * Inspect a (stopped) container for wall-clock time and OOMKilled flag.
     * Uses a single docker inspect call with a combined format string.
     *
     * Expected output format: "2025-05-07T10:00:00.123456789Z 2025-05-07T10:00:02.456789012Z false"
     */
    private data class InspectResult(val wallTimeMs: Long, val oomKilled: Boolean)

    private fun inspectContainer(id: String): InspectResult {
        return try {
            val proc = ProcessBuilder(
                "docker", "inspect",
                "--format={{.State.StartedAt}} {{.State.FinishedAt}} {{.State.OOMKilled}}",
                id
            ).redirectErrorStream(true).start()
            proc.waitFor(10, TimeUnit.SECONDS)
            val line = proc.inputStream.bufferedReader().readText().trim()

            val parts = line.split(" ")
            val startedAt = parts.getOrNull(0)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val finishedAt = parts.getOrNull(1)?.let { runCatching { Instant.parse(it) }.getOrNull() }
            val oomKilled = parts.getOrNull(2)?.trim()?.lowercase() == "true"

            val wallMs = if (startedAt != null && finishedAt != null && finishedAt > startedAt) {
                Duration.between(startedAt, finishedAt).toMillis()
            } else {
                0L
            }

            InspectResult(wallTimeMs = wallMs, oomKilled = oomKilled)
        } catch (e: Exception) {
            logger.warn(e) { "docker inspect failed for $id" }
            InspectResult(wallTimeMs = 0L, oomKilled = false)
        }
    }

    /**
     * Polls peak memory for a running container via `docker stats --no-stream`.
     *
     * Parses the MemUsage field (e.g. "42.3MiB / 256MiB") and updates [peak]
     * atomically if the current sample exceeds the stored maximum.
     *
     * Called from [pollScheduler] every 100 ms. Must not throw — exceptions
     * are swallowed (container may have exited between scheduling and execution).
     *
     * macOS / Docker Desktop caveat: Docker Desktop on macOS runs containers
     * inside a hidden Linux VM. docker stats communicates with the Docker daemon
     * over the VM socket and reflects the container's cgroup memory inside the
     * VM. Numbers match what a native Linux host would report. The 100 ms
     * granularity is sufficient for thesis-grade peak-memory measurement; rapid
     * sub-10 ms allocation spikes will not be captured.
     */
    private fun pollMemory(containerId: String, peak: AtomicLong) {
        try {
            val proc = ProcessBuilder(
                "docker", "stats", "--no-stream",
                "--format={{.MemUsage}}",
                containerId
            ).redirectErrorStream(true).start()

            val finished = proc.waitFor(2, TimeUnit.SECONDS)
            if (!finished) {
                proc.destroyForcibly()
                return
            }
            val line = proc.inputStream.bufferedReader().readText().trim()
            // line looks like: "42.3MiB / 256MiB"
            val usedPart = line.substringBefore("/").trim()
            val bytes = parseMemoryString(usedPart)
            if (bytes > 0L) {
                // Atomically update maximum
                var cur = peak.get()
                while (bytes > cur) {
                    if (peak.compareAndSet(cur, bytes)) break
                    cur = peak.get()
                }
            }
        } catch (_: Exception) {
            // Container may have stopped between poll cycles — ignore silently
        }
    }

    /**
     * Оборачивает пользовательскую команду так, чтобы перед exit контейнер сам
     * прочитал peak memory из cgroup v2 (или v1 как fallback) и записал в
     * /app/.peak_memory_bytes на bind-mount'е. Хост-сторона затем читает файл.
     *
     * Решает проблему "memoryUsedKb=0 для программ <500мс": docker stats имеет
     * default sampling ~1с, а наш polling 100мс всё равно может пропустить
     * короткие программы. cgroup memory.peak — точное значение, обновляется
     * ядром в реальном времени.
     *
     * Поддерживается только форма команды `sh -c "<script>"` (все языки в этом
     * сервисе её используют). Для других форм — return as-is (graceful degrade
     * к polled value).
     */
    private fun wrapForPeakMemoryCapture(command: List<String>): List<String> {
        if (command.size < 3 || command[0] != "sh" || command[1] != "-c") return command
        val original = command[2]
        // Жёстко: даже если original упадёт, мы должны сохранить peak и вернуть
        // оригинальный rc. echo 0 на крайний случай, чтобы файл всегда существовал.
        val wrapped = "$original; __rc=\$?; { " +
            "cat /sys/fs/cgroup/memory.peak 2>/dev/null || " +
            "cat /sys/fs/cgroup/memory/memory.max_usage_in_bytes 2>/dev/null || " +
            "echo 0; } > /app/.peak_memory_bytes 2>/dev/null; exit \$__rc"
        return listOf("sh", "-c", wrapped)
    }

    /**
     * Читает peak memory bytes из файла, который сам контейнер сохранил перед exit.
     * Возвращает 0 при отсутствии файла или неразборном содержимом
     * (graceful degrade — упадёт обратно на polled value).
     */
    private fun readCgroupPeakMemoryFile(requestDir: java.nio.file.Path): Long {
        return try {
            val file = requestDir.resolve(".peak_memory_bytes").toFile()
            if (!file.exists()) return 0L
            file.readText().trim().toLongOrNull() ?: 0L
        } catch (e: Exception) {
            logger.debug(e) { "readCgroupPeakMemoryFile failed" }
            0L
        }
    }

    /**
     * Parses a Docker memory string into bytes.
     * Handles: GiB, MiB, KiB, GB, MB, KB, B
     * Returns 0 on parse failure or blank/dash input.
     */
    private fun parseMemoryString(s: String): Long {
        if (s.isBlank() || s == "--") return 0L
        return try {
            when {
                s.endsWith("GiB") -> (s.removeSuffix("GiB").toDouble() * 1024L * 1024L * 1024L).toLong()
                s.endsWith("MiB") -> (s.removeSuffix("MiB").toDouble() * 1024L * 1024L).toLong()
                s.endsWith("KiB") -> (s.removeSuffix("KiB").toDouble() * 1024L).toLong()
                s.endsWith("GB")  -> (s.removeSuffix("GB").toDouble()  * 1_000_000_000L).toLong()
                s.endsWith("MB")  -> (s.removeSuffix("MB").toDouble()  * 1_000_000L).toLong()
                s.endsWith("KB")  -> (s.removeSuffix("KB").toDouble()  * 1_000L).toLong()
                s.endsWith("B")   -> s.removeSuffix("B").toDouble().toLong()
                else              -> 0L
            }
        } catch (_: NumberFormatException) {
            0L
        }
    }

    // -----------------------------------------------------------------------
    // Utilities
    // -----------------------------------------------------------------------

    private fun extractClassName(code: String): String? {
        val classPattern = Regex("""(?:public\s+)?class\s+(\w+)""")
        return classPattern.find(code)?.groupValues?.get(1)
    }

    /**
     * Compares actual container stdout against the expected output.
     * Both sides are trimmed and consecutive whitespace collapsed to a single
     * space before comparison (normalised comparison).
     */
    private fun compareOutput(actual: String, expected: String): Verdict {
        val normalizedActual = actual.trim().replace(Regex("\\s+"), " ")
        val normalizedExpected = expected.trim().replace(Regex("\\s+"), " ")

        return when {
            normalizedActual == normalizedExpected ->
                Verdict.OK
            normalizedActual.replace(" ", "") == normalizedExpected.replace(" ", "") ->
                Verdict.PRESENTATION_ERROR
            else ->
                Verdict.WRONG_ANSWER
        }
    }
}
