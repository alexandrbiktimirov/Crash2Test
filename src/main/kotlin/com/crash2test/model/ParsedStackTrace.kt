package com.crash2test.model

data class ParsedStackTrace(
    val exceptionType: String?,
    val exceptionMessage: String?,
    val frames: List<StackFrameInfo>,
)
