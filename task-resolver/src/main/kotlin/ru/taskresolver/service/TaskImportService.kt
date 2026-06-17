package ru.taskresolver.service

import com.fasterxml.jackson.core.type.TypeReference
import com.fasterxml.jackson.databind.ObjectMapper
import io.github.oshai.kotlinlogging.KotlinLogging
import model.TaskImportResult
import model.TaskImportResultErrorsInner
import org.apache.commons.csv.CSVFormat
import org.apache.commons.csv.CSVParser
import org.apache.commons.csv.CSVRecord
import org.springframework.dao.DataIntegrityViolationException
import org.springframework.stereotype.Service
import org.springframework.transaction.annotation.Transactional
import org.springframework.web.multipart.MultipartFile
import ru.db.entity.ArgumentTest
import ru.db.entity.Difficulty
import ru.db.entity.Test
import ru.db.entity.TestResolve
import ru.db.entity.Tests
import ru.db.entity.Type
import ru.taskresolver.repository.jpa.repository.TestRepository
import ru.taskresolver.repository.jpa.repository.TestResolveRepository
import java.io.InputStreamReader
import java.nio.charset.StandardCharsets
import java.util.UUID

/**
 * CSV-формат входного файла (заголовок обязателен):
 *   title,difficulty,category,description,return_type,arguments_json,tests_json
 *
 * Пример строки:
 *   "Sum","Easy","Math","Add two numbers","Integer","[{\"position\":0,\"type\":\"Integer\"},{\"position\":1,\"type\":\"Integer\"}]","[{\"input\":\"1 2\",\"expectedOutput\":\"3\"}]"
 *
 * Дубликаты определяются по совпадению title (case-insensitive). Дубликаты пропускаются и считаются в skippedCount.
 */
@Service
class TaskImportService(
    private val testRepository: TestRepository,
    private val testResolveRepository: TestResolveRepository,
    private val objectMapper: ObjectMapper,
) {
    private val log = KotlinLogging.logger {}

    @Transactional
    fun importFromCsv(file: MultipartFile): TaskImportResult {
        if (file.isEmpty) {
            return TaskImportResult(0, 0, listOf(error(0, "Файл пуст")))
        }
        val errors = mutableListOf<TaskImportResultErrorsInner>()
        var imported = 0
        var skipped = 0

        // F-5: проекция (только колонка title) вместо findAll() — не тащим
        // description/difficulty/category в heap при большом каталоге.
        val existingTitles = testRepository.findAllTitlesLowercase()
            .filter(String::isNotBlank)
            .toMutableSet()

        InputStreamReader(file.inputStream, StandardCharsets.UTF_8).use { reader ->
            val format = CSVFormat.DEFAULT.builder()
                .setHeader()
                .setSkipHeaderRecord(true)
                .setTrim(true)
                .setIgnoreEmptyLines(true)
                .build()
            CSVParser.parse(reader, format).use { parser ->
                for (record in parser) {
                    val lineNo = record.recordNumber.toInt() + 1
                    try {
                        val parsed = parseRecord(record)
                        if (parsed.title.lowercase() in existingTitles) {
                            skipped++
                            errors += error(lineNo, "Задача с title '${parsed.title}' уже существует")
                            continue
                        }
                        persist(parsed)
                        existingTitles += parsed.title.lowercase()
                        imported++
                    } catch (e: IllegalArgumentException) {
                        skipped++
                        errors += error(lineNo, e.message ?: "Ошибка парсинга строки")
                    } catch (e: DataIntegrityViolationException) {
                        // F-10: concurrent import committed the same title between our snapshot
                        // and persist(). uq_test_title_lower (V9) caught it; count as skipped.
                        skipped++
                        errors += error(lineNo, "Задача с таким title была импортирована параллельно")
                        log.info { "CSV import: dedup race on line $lineNo, skipping" }
                    } catch (e: Exception) {
                        // F-11: do not echo e.message to clients — it may carry JPA/Jackson
                        // internals (column names, type info). Full stack trace stays in logs.
                        skipped++
                        errors += error(lineNo, "Внутренняя ошибка обработки строки")
                        log.warn(e) { "CSV import failure on line $lineNo" }
                    }
                }
            }
        }
        log.info { "CSV import done: imported=$imported skipped=$skipped errors=${errors.size}" }
        return TaskImportResult(imported, skipped, errors)
    }

    private fun parseRecord(record: CSVRecord): ParsedTask {
        // F-7: deformula() guards against CSV/Excel formula-injection if the data is
        // ever exported. Fields starting with =/+/-/@/tab/CR get a leading apostrophe
        // before persist; Excel treats the result as plain text.
        val title = record.requiredField("title").deformula()
        val difficulty = Difficulty.entries.firstOrNull { it.name.equals(record.requiredField("difficulty"), true) }
            ?: throw IllegalArgumentException("difficulty: ожидается Easy/Medium/Hard")
        // F-12: schema-level length cap matching VARCHAR(64) in V8 migration.
        val category = record.optionalField("category")?.deformula()
            ?.also { require(it.length <= 64) { "category: не должно превышать 64 символа" } }
        val description = record.requiredField("description").deformula()
        val returnType = Type.entries.firstOrNull { it.name.equals(record.requiredField("return_type"), true) }
            ?: throw IllegalArgumentException("return_type: ожидается ${Type.entries.joinToString("/") { it.name }}")
        val args: List<ArgumentTest> = try {
            objectMapper.readValue(record.requiredField("arguments_json"), ARG_LIST_TYPE)
        } catch (e: Exception) {
            throw IllegalArgumentException("arguments_json: ${e.message}")
        }
        val tests: List<Tests> = try {
            objectMapper.readValue(record.requiredField("tests_json"), TESTS_LIST_TYPE)
        } catch (e: Exception) {
            throw IllegalArgumentException("tests_json: ${e.message}")
        }
        if (tests.isEmpty()) throw IllegalArgumentException("tests_json: должен содержать хотя бы один тест")
        return ParsedTask(title, difficulty, category, description, returnType, args, tests)
    }

    private fun persist(p: ParsedTask) {
        val problemId = UUID.randomUUID()
        testRepository.save(
            Test(
                testId = problemId,
                description = p.description,
                title = p.title,
                difficulty = p.difficulty,
                category = p.category?.takeUnless(String::isBlank),
            )
        )
        testResolveRepository.save(
            TestResolve(
                arguments = p.arguments,
                returnType = p.returnType,
                tests = p.tests,
                problemId = problemId,
            )
        )
    }

    private fun CSVRecord.requiredField(name: String): String =
        runCatching { get(name) }.getOrNull()?.trim()?.takeUnless { it.isEmpty() }
            ?: throw IllegalArgumentException("Поле '$name' пустое или отсутствует")

    private fun CSVRecord.optionalField(name: String): String? =
        runCatching { get(name) }.getOrNull()?.trim()?.takeUnless { it.isEmpty() }

    // F-7: defuse CSV-formula-injection. Latent risk today (no export path), but
    // becomes exploitable the moment an "export catalog to CSV" endpoint ships.
    private fun String.deformula(): String =
        if (firstOrNull() in FORMULA_INJECTION_PREFIXES) "'" + this else this

    private fun error(line: Int, message: String) = TaskImportResultErrorsInner().apply {
        this.line = line
        this.message = message
    }

    companion object {
        private val ARG_LIST_TYPE = object : TypeReference<List<ArgumentTest>>() {}
        private val TESTS_LIST_TYPE = object : TypeReference<List<Tests>>() {}
        private val FORMULA_INJECTION_PREFIXES = setOf('=', '+', '-', '@', '\t', '\r')
    }

    private data class ParsedTask(
        val title: String,
        val difficulty: Difficulty,
        val category: String?,
        val description: String,
        val returnType: Type,
        val arguments: List<ArgumentTest>,
        val tests: List<Tests>,
    )
}
