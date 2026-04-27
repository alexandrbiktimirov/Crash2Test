package com.crash2test.services

import com.crash2test.model.RegressionTestLanguage
import com.crash2test.model.ResolvedFrame

class RegressionTestLanguageResolver {
    fun resolve(resolvedFrames: List<ResolvedFrame>): RegressionTestLanguage {
        val candidateFileName = resolvedFrames.asSequence()
            .filter { it.status == ResolvedFrame.ResolutionStatus.RESOLVED }
            .mapNotNull { it.resolvedPath ?: it.frame.fileName }
            .firstOrNull()
            ?: resolvedFrames.firstOrNull()?.frame?.fileName

        val extension = candidateFileName
            ?.substringAfterLast('.', "")
            ?.lowercase()
            ?.takeIf { it.isNotBlank() }

        return when (extension) {
            "kt", "kts" -> RegressionTestLanguage("Kotlin", "kotlin", "GeneratedRegressionTest.kt")
            "java" -> RegressionTestLanguage("Java", "java", "GeneratedRegressionTest.java")
            "py" -> RegressionTestLanguage("Python", "python", "generated_regression_test.py")
            "js", "mjs", "cjs" -> RegressionTestLanguage("JavaScript", "javascript", "generatedRegressionTest.js")
            "ts", "tsx" -> RegressionTestLanguage("TypeScript", "typescript", "generatedRegressionTest.ts")
            "go" -> RegressionTestLanguage("Go", "go", "generated_regression_test.go")
            "rb" -> RegressionTestLanguage("Ruby", "ruby", "generated_regression_test.rb")
            "php" -> RegressionTestLanguage("PHP", "php", "GeneratedRegressionTest.php")
            "cs" -> RegressionTestLanguage("C#", "csharp", "GeneratedRegressionTest.cs")
            "cpp", "cc", "cxx", "hpp", "hh", "hxx", "h" -> RegressionTestLanguage("C++", "cpp", "generated_regression_test.cpp")
            "c" -> RegressionTestLanguage("C", "c", "generated_regression_test.c")
            "rs" -> RegressionTestLanguage("Rust", "rust", "generated_regression_test.rs")
            "swift" -> RegressionTestLanguage("Swift", "swift", "GeneratedRegressionTest.swift")
            "scala" -> RegressionTestLanguage("Scala", "scala", "GeneratedRegressionTest.scala")
            "groovy" -> RegressionTestLanguage("Groovy", "groovy", "GeneratedRegressionTest.groovy")
            else -> RegressionTestLanguage("Kotlin", "kotlin", "GeneratedRegressionTest.kt")
        }
    }
}
