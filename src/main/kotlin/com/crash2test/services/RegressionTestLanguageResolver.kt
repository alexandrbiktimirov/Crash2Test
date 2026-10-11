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

        return RegressionTestLanguage.fromFileName(candidateFileName)
    }
}
