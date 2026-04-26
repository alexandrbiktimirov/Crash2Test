package com.crash2test.model

data class StackFrameInfo(
    val className: String,
    val methodName: String,
    val fileName: String?,
    val lineNumber: Int?,
)
