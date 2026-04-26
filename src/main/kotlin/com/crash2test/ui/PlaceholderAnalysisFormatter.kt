package com.crash2test.ui

class PlaceholderAnalysisFormatter {
    fun initialState(projectName: String): Crash2TestViewState = Crash2TestViewState(
        statusMessage = "Paste a stack trace and run placeholder analysis.",
        resultText = """
            Summary
            Crash2Test is ready for project "$projectName".

            Timeline
            Paste a stack trace to see a placeholder debugging flow.

            Likely Root Cause
            Not analyzed yet.

            Files to Inspect
            No files selected yet.

            Regression Tests
            No suggestions yet.

            Bug Report Draft
            Add crash details to generate a draft.
        """.trimIndent(),
        canAnalyze = true,
    )

    fun analyze(rawInput: String): Crash2TestViewState {
        val normalizedInput = rawInput.trim()
        if (normalizedInput.isEmpty()) {
            return Crash2TestViewState(
                statusMessage = "Enter a stack trace or runtime error before running analysis.",
                resultText = "",
                canAnalyze = true,
            )
        }

        val previewLine = normalizedInput.lineSequence().firstOrNull().orEmpty().take(140)
        val lineCount = normalizedInput.lineSequence().count()

        return Crash2TestViewState(
            statusMessage = "Placeholder analysis complete.",
            resultText = """
                Summary
                Placeholder analysis captured $lineCount line(s) of crash text.

                Timeline
                1. User pasted crash output into Crash2Test.
                2. Plugin validated that input was present.
                3. Placeholder analysis generated a structured response.

                Likely Root Cause
                Parser and AI integration are not implemented yet in this milestone.

                Files to Inspect
                Waiting for stack frame parsing and project file resolution.

                Regression Tests
                Add a regression test around the scenario described by:
                $previewLine

                Bug Report Draft
                Reproduced a runtime failure from pasted crash output. Detailed parsing and root-cause analysis will be added in later milestones.
            """.trimIndent(),
            canAnalyze = true,
        )
    }
}
