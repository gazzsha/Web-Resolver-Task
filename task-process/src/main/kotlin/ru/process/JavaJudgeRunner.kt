import java.io.IOException
import java.nio.file.Files
import java.nio.file.Paths
import java.util.concurrent.TimeUnit

class JavaJudgeRunner {
    fun runUserCode(userCode: String, className: String, input: String, expected: String): String {
        val workDir = Paths.get("/tmp/judge")
        Files.createDirectories(workDir)

        // Сохраняем код
        val javaFile = workDir.resolve("$className.java")
        Files.writeString(javaFile, userCode)

        // Входные данные
        val inputFile = workDir.resolve("input.txt")
        Files.writeString(inputFile, input)

        // Ожидаемый результат
        val expectedFile = workDir.resolve("expected.txt")
        Files.writeString(expectedFile, expected)

        // Команда запуска Docker
        val pb = ProcessBuilder(
            "docker", "run", "--rm",
            "-v", workDir.toAbsolutePath().toString() + ":/app",
            "java-judge",
            className,
            "input.txt",
            "expected.txt"
        )

        pb.directory(workDir.toFile())
        val process = pb.start()

        val finished = process.waitFor(5, TimeUnit.SECONDS)
        if (!finished) {
            process.destroyForcibly()
            return "TLE"
        }

        val output = String(process.inputStream.readAllBytes()).trim { it <= ' ' }
        val error = String(process.errorStream.readAllBytes()).trim { it <= ' ' }

        if (!error.isEmpty()) {
            return "Runtime Error:\n$error"
        }
        return output
    }

    fun main(args: Array<String>) {
        val code = """
        import java.io.*;
        public class OneInRow {
            public static void main(String[] args) throws IOException {
                System.out.println(42);
            }
        }
        
        """.trimIndent()

        val result = runUserCode(code, "OneInRow", "", "42")
        println("Verdict: $result")
    }
}
