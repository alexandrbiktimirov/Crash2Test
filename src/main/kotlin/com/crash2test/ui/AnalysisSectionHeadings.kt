package com.crash2test.ui

internal object AnalysisSectionHeadings {
    fun isHeading(line: String): Boolean = canonicalTitle(line) in headings

    fun isRegressionHeading(line: String): Boolean = canonicalTitle(line) == REGRESSION_TEST

    fun displayTitle(line: String): String = canonicalTitle(line) ?: stripDecorators(line)

    private fun canonicalTitle(line: String): String? {
        val title = stripDecorators(line)
        return when (title) {
            "Regression Tests" -> REGRESSION_TEST
            in headings -> title
            else -> null
        }
    }

    private fun stripDecorators(line: String): String {
        val withoutMarkdownPrefix = MARKDOWN_HEADING_PATTERN
            .matchEntire(line.trim())
            ?.groupValues
            ?.get(1)
            ?.trim()
            ?: line.trim()

        return withoutMarkdownPrefix
            .removeSurrounding("**")
            .removeSurrounding("__")
            .removeSuffix(":")
            .trim()
    }

    private const val REGRESSION_TEST = "Regression Test"
    private val MARKDOWN_HEADING_PATTERN = Regex("""^#{1,6}\s+(.+?)\s*#*$""")
    private val headings = setOf(
        "Summary",
        "Likely Root Cause",
        "Proposed Fix",
        REGRESSION_TEST,
        "Files to Inspect",
        "Bug Report Draft",
        "Observed Failure Path",
        "Description",
        "Steps to Reproduce",
        "Impact",
        "Additional Information",
        "Possible Mitigations",
        "Title",
    )
}
