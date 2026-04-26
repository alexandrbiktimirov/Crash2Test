package com.crash2test.model

data class ResolvedFrame(
    val frame: StackFrameInfo,
    val resolvedPath: String?,
    val lineNumber: Int?,
    val status: ResolutionStatus,
    val details: String? = null,
) {
    enum class ResolutionStatus {
        RESOLVED,
        UNRESOLVED,
        AMBIGUOUS,
    }
}
