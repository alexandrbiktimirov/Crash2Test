package com.crash2test.services

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ResolvedFrame
import com.intellij.openapi.application.ReadAction

fun interface CrashAnalyzer {
    fun analyzeStreaming(
        rawInput: String,
        onChunk: (String) -> Unit,
    ): CrashAnalysisResult
}

class CrashAnalysisService(
    private val stackTraceParser: StackTraceParser,
    private val frameResolver: FrameResolver,
    private val regressionTestLanguageResolver: RegressionTestLanguageResolver = RegressionTestLanguageResolver(),
    private val sourceSnippetExtractor: SourceSnippetExtractor = SourceSnippetExtractor(),
    private val promptBuilder: PromptBuilder = PromptBuilder(),
    private val analysisGenerator: CrashAnalysisGenerator = OllamaClient(),
    private val resolveInReadAction: (() -> List<ResolvedFrame>) -> List<ResolvedFrame> = { computation ->
        ReadAction.computeBlocking<List<ResolvedFrame>, RuntimeException> {
            computation()
        }
    },
) : CrashAnalyzer {
    fun analyze(rawInput: String): CrashAnalysisResult = analyzeStreaming(
        rawInput = rawInput,
        onChunk = {},
    )

    override fun analyzeStreaming(
        rawInput: String,
        onChunk: (String) -> Unit,
    ): CrashAnalysisResult {
        val normalizedInput = rawInput.trim()
        if (normalizedInput.isEmpty()) {
            return CrashAnalysisResult.Failure(
                message = "Enter a stack trace or runtime error before running analysis.",
            )
        }

        val parsed = stackTraceParser.parse(normalizedInput)
        if (parsed.exceptionType == null && parsed.frames.isEmpty()) {
            return CrashAnalysisResult.Failure(
                message = "Could not recognize a Java or Kotlin stack trace.",
            )
        }

        val resolvedFrames = resolveInReadAction {
            frameResolver.resolve(parsed)
        }
        val regressionTestLanguage = regressionTestLanguageResolver.resolve(resolvedFrames)
        val codeSnippets = sourceSnippetExtractor.extract(resolvedFrames)
        val prompt = promptBuilder.build(parsed, resolvedFrames, codeSnippets, regressionTestLanguage)

        return when (val ollamaResult = analysisGenerator.generateStreaming(prompt, onChunk)) {
            is OllamaResult.Success -> CrashAnalysisResult.Success(
                parsedStackTrace = parsed,
                resolvedFrames = resolvedFrames,
                regressionTestLanguage = regressionTestLanguage,
                prompt = prompt,
                analysisText = ollamaResult.response,
            )

            is OllamaResult.Failure -> CrashAnalysisResult.Failure(
                message = ollamaResult.message,
                parsedStackTrace = parsed,
                resolvedFrames = resolvedFrames,
                regressionTestLanguage = regressionTestLanguage,
            )
        }
    }
}
