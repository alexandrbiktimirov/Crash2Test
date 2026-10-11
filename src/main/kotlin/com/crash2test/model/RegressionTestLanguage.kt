package com.crash2test.model

data class RegressionTestLanguage(
    val displayName: String,
    val fenceTag: String,
    val suggestedFileName: String,
) {
    companion object {
        val DEFAULT = RegressionTestLanguage("Kotlin", "kotlin", "GeneratedRegressionTest.kt")

        private val languages = listOf(
            DEFAULT,
            RegressionTestLanguage("Java", "java", "GeneratedRegressionTest.java"),
            RegressionTestLanguage("Python", "python", "generated_regression_test.py"),
            RegressionTestLanguage("JavaScript", "javascript", "generatedRegressionTest.js"),
            RegressionTestLanguage("TypeScript", "typescript", "generatedRegressionTest.ts"),
            RegressionTestLanguage("Go", "go", "generated_regression_test.go"),
            RegressionTestLanguage("Ruby", "ruby", "generated_regression_test.rb"),
            RegressionTestLanguage("PHP", "php", "GeneratedRegressionTest.php"),
            RegressionTestLanguage("C#", "csharp", "GeneratedRegressionTest.cs"),
            RegressionTestLanguage("C++", "cpp", "generated_regression_test.cpp"),
            RegressionTestLanguage("C", "c", "generated_regression_test.c"),
            RegressionTestLanguage("Rust", "rust", "generated_regression_test.rs"),
            RegressionTestLanguage("Swift", "swift", "GeneratedRegressionTest.swift"),
            RegressionTestLanguage("Scala", "scala", "GeneratedRegressionTest.scala"),
            RegressionTestLanguage("Groovy", "groovy", "GeneratedRegressionTest.groovy"),
        )
        private val byFenceTag = languages.associateBy { it.fenceTag }
        private val byExtension = buildMap {
            languages.forEach { put(it.suggestedFileName.substringAfterLast('.'), it) }
            put("kts", DEFAULT)
            put("mjs", byFenceTag.getValue("javascript"))
            put("cjs", byFenceTag.getValue("javascript"))
            put("tsx", byFenceTag.getValue("typescript"))
            listOf("cc", "cxx", "hpp", "hh", "hxx", "h").forEach { put(it, byFenceTag.getValue("cpp")) }
        }

        fun fromFileName(fileName: String?): RegressionTestLanguage =
            byExtension[fileName?.substringAfterLast('.', "")?.lowercase()] ?: DEFAULT

        fun fromFenceTag(fenceTag: String): RegressionTestLanguage? = byFenceTag[fenceTag.lowercase()]
    }
}
