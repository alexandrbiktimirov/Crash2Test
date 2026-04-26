package com.crash2test.services

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.StackFrameInfo

class StackTraceParser {
    fun parse(rawInput: String): ParsedStackTrace {
        var exceptionType: String? = null
        var exceptionMessage: String? = null
        val frames = mutableListOf<StackFrameInfo>()

        rawInput.lineSequence().forEach { line ->
            val trimmedLine = line.trim()
            if (trimmedLine.isEmpty()) {
                return@forEach
            }

            if (exceptionType == null) {
                parseExceptionHeader(trimmedLine)?.let { (type, message) ->
                    exceptionType = type
                    exceptionMessage = message
                }
            }

            parseFrame(trimmedLine)?.let(frames::add)
        }

        return ParsedStackTrace(
            exceptionType = exceptionType,
            exceptionMessage = exceptionMessage,
            frames = frames,
        )
    }

    private fun parseExceptionHeader(line: String): Pair<String, String?>? {
        val normalizedLine = line
            .removePrefix("Caused by: ")
            .removePrefix("Suppressed: ")
            .replace(EXCEPTION_IN_THREAD_PREFIX, "")

        val separatorIndex = normalizedLine.indexOf(':')
        val candidateType = normalizedLine.substringBefore(':').trim()
        if (!isLikelyThrowableName(candidateType)) {
            return null
        }

        val message = if (separatorIndex >= 0) {
            normalizedLine.substring(separatorIndex + 1).trim().ifEmpty { null }
        } else {
            null
        }

        return candidateType to message
    }

    private fun parseFrame(line: String): StackFrameInfo? {
        val match = STACK_FRAME_PATTERN.matchEntire(line) ?: return null
        val ownerAndMethod = match.groupValues[1]
        val source = match.groupValues[2]

        val methodSeparator = ownerAndMethod.lastIndexOf('.')
        if (methodSeparator <= 0 || methodSeparator == ownerAndMethod.lastIndex) {
            return null
        }

        val className = ownerAndMethod.substring(0, methodSeparator)
        val methodName = ownerAndMethod.substring(methodSeparator + 1)
        val (fileName, lineNumber) = parseSource(source)

        return StackFrameInfo(
            className = className,
            methodName = methodName,
            fileName = fileName,
            lineNumber = lineNumber,
        )
    }

    private fun parseSource(source: String): Pair<String?, Int?> {
        if (source == "Unknown Source" || source == "Native Method") {
            return null to null
        }

        val separatorIndex = source.lastIndexOf(':')
        if (separatorIndex < 0) {
            return source.ifBlank { null } to null
        }

        val fileName = source.substring(0, separatorIndex).ifBlank { null }
        val lineNumber = source.substring(separatorIndex + 1).toIntOrNull()

        return fileName to lineNumber
    }

    private fun isLikelyThrowableName(candidate: String): Boolean {
        if (candidate.isBlank() || candidate.contains(' ')) {
            return false
        }

        return candidate.endsWith("Exception") ||
            candidate.endsWith("Error") ||
            candidate.endsWith("Throwable") ||
            '.' in candidate
    }

    companion object {
        private val EXCEPTION_IN_THREAD_PREFIX = Regex("""^Exception in thread "[^"]+"\s+""")
        private val STACK_FRAME_PATTERN = Regex("""^at\s+(.+?)\((.+)\)$""")
    }
}
