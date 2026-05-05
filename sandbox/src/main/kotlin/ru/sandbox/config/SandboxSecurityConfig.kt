package ru.sandbox.config

import io.github.oshai.kotlinlogging.KotlinLogging

/**
 * Security configuration for sandbox execution
 * 
 * This class documents the security measures implemented
 * to protect the system from malicious user code.
 */
class SandboxSecurityConfig {
    
    companion object {
        private val logger = KotlinLogging.logger {}
        
        // Resource limits for security
        const val DEFAULT_CPU_LIMIT = 1.0 // 1 CPU core
        const val DEFAULT_MEMORY_LIMIT_MB = 256L
        const val DEFAULT_TIMEOUT_SECONDS = 5L
        const val MAX_MEMORY_LIMIT_MB = 512L
        const val MAX_TIMEOUT_SECONDS = 10L
        
        // Forbidden operations
        val FORBIDDEN_PATTERNS = listOf(
            // Network access
            "Socket",
            "ServerSocket",
            "URL(",
            "HttpURLConnection",
            "HttpClient",
            
            // File system access
            "FileWriter",
            "FileOutputStream",
            "Files.write",
            "File.delete",
            "File.mkdirs",
            
            // Process execution
            "Runtime.getRuntime().exec",
            "ProcessBuilder",
            "System.exit",
            
            // Reflection (potential security risk)
            "Class.forName",
            "Method.invoke",
            "AccessibleObject.setAccessible",
            
            // System properties
            "System.getProperty",
            "System.setProperty",
            "System.getenv",
            
            // Thread manipulation
            "Thread.stop",
            "Thread.suspend",
            "Thread.destroy",
            
            // Native code
            "System.load",
            "System.loadLibrary",
            "Native"
        )
        
        // Docker security options
        val DOCKER_SECURITY_OPTS = listOf(
            "--network=none",           // Disable network access
            "--read-only",              // Read-only filesystem
            "--no-new-privileges",      // Prevent privilege escalation
            "--cap-drop=ALL",          // Drop all capabilities
            "--security-opt=no-new-privileges:true"
        )
    }
    
    /**
     * Check code for forbidden patterns
     */
    fun validateCode(code: String): ValidationResult {
        val violations = mutableListOf<String>()
        
        FORBIDDEN_PATTERNS.forEach { pattern ->
            if (code.contains(pattern, ignoreCase = true)) {
                violations.add("Forbidden pattern detected: $pattern")
            }
        }
        
        // Check for infinite loops (basic heuristic)
        if (containsSuspiciousLoop(code)) {
            violations.add("Suspicious loop detected - may cause timeout")
        }
        
        return ValidationResult(
            isValid = violations.isEmpty(),
            violations = violations
        )
    }
    
    private fun containsSuspiciousLoop(code: String): Boolean {
        // Basic heuristic: while(true) without break
        val whileTruePattern = Regex("""while\s*\(\s*true\s*\)\s*\{""")
        val hasWhileTrue = whileTruePattern.containsMatchIn(code)
        
        val hasBreak = code.contains("break", ignoreCase = true)
        
        return hasWhileTrue && !hasBreak
    }
}

/**
 * Result of code validation
 */
data class ValidationResult(
    val isValid: Boolean,
    val violations: List<String>
)
