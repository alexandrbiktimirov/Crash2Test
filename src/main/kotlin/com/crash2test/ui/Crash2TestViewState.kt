package com.crash2test.ui

import com.crash2test.model.ResolvedFrame

data class Crash2TestViewState(
    val statusMessage: String,
    val resultText: String,
    val canAnalyze: Boolean,
    val clickableFrames: List<ResolvedFrame> = emptyList(),
)
