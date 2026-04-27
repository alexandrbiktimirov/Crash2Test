package com.crash2test.model

data class ResolvedCodeSnippet(
    val filePath: String,
    val focusLineNumber: Int,
    val startLineNumber: Int,
    val endLineNumber: Int,
    val content: String,
)
