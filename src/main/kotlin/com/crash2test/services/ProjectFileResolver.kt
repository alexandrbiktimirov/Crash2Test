package com.crash2test.services

import com.crash2test.model.ParsedStackTrace
import com.crash2test.model.ResolvedFrame
import com.crash2test.model.StackFrameInfo
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.VirtualFile
import com.intellij.psi.search.FilenameIndex
import com.intellij.psi.search.GlobalSearchScope

interface FrameResolver {
    fun resolve(parsedStackTrace: ParsedStackTrace): List<ResolvedFrame>
}

class ProjectFileResolver(
    private val project: Project,
) : FrameResolver {
    override fun resolve(parsedStackTrace: ParsedStackTrace): List<ResolvedFrame> {
        val candidateCache = mutableMapOf<String, List<VirtualFile>>()
        return parsedStackTrace.frames.map { frame ->
            resolveFrame(frame, candidateCache)
        }
    }

    private fun resolveFrame(
        frame: StackFrameInfo,
        candidateCache: MutableMap<String, List<VirtualFile>>,
    ): ResolvedFrame {
        val fileName = frame.fileName
            ?: return unresolvedFrame(frame, "No source file was present in the stack frame.")

        val candidates = candidateCache.getOrPut(fileName) {
            FilenameIndex.getVirtualFilesByName(
                fileName,
                GlobalSearchScope.projectScope(project),
            ).toList()
        }

        if (candidates.isEmpty()) {
            return unresolvedFrame(frame, "No matching project file was found.")
        }

        if (candidates.size == 1) {
            return resolvedFrame(frame, candidates.single(), null)
        }

        val chosenPath = selectBestMatch(frame, candidates.map { it.path })
        val chosenFile = chosenPath?.let { path ->
            candidates.firstOrNull { candidate -> normalizePath(candidate.path) == path }
        }

        if (chosenFile != null) {
            return resolvedFrame(frame, chosenFile, "Matched by package path.")
        }

        return ambiguousFrame(frame, candidates)
    }

    private fun resolvedFrame(frame: StackFrameInfo, file: VirtualFile, details: String?) = ResolvedFrame(
        frame = frame,
        resolvedPath = toDisplayPath(file),
        navigationPath = file.path,
        lineNumber = frame.lineNumber,
        status = ResolvedFrame.ResolutionStatus.RESOLVED,
        details = details,
    )

    private fun unresolvedFrame(frame: StackFrameInfo, details: String) = ResolvedFrame(
        frame = frame,
        resolvedPath = null,
        navigationPath = null,
        lineNumber = frame.lineNumber,
        status = ResolvedFrame.ResolutionStatus.UNRESOLVED,
        details = details,
    )

    private fun ambiguousFrame(frame: StackFrameInfo, candidates: List<VirtualFile>) = ResolvedFrame(
        frame = frame,
        resolvedPath = null,
        navigationPath = null,
        lineNumber = frame.lineNumber,
        status = ResolvedFrame.ResolutionStatus.AMBIGUOUS,
        details = buildAmbiguityMessage(candidates),
    )

    private fun toDisplayPath(file: VirtualFile): String {
        val projectPath = project.basePath
        return if (projectPath != null && file.path.startsWith(projectPath)) {
            file.path.removePrefix(projectPath).trimStart('/', '\\')
        } else {
            file.path
        }
    }

    private fun buildAmbiguityMessage(candidates: List<VirtualFile>): String =
        buildString {
            append("Multiple matching project files were found")
            if (candidates.size > 3) {
                append(" (${candidates.size} total)")
            }
            append(": ")
            append(candidates.take(3).joinToString(separator = ", ") { toDisplayPath(it) })
        }

    companion object {
        internal fun selectBestMatch(frame: StackFrameInfo, candidatePaths: List<String>): String? {
            val packagePath = frame.className.substringBeforeLast('.', "").replace('.', '/')
            if (packagePath.isBlank()) {
                return null
            }

            val scoredMatches = candidatePaths.mapNotNull { path ->
                val normalizedPath = normalizePath(path)
                val score = matchScore(normalizedPath, packagePath)
                score.takeIf { it > 0 }?.let { normalizedPath to it }
            }

            if (scoredMatches.isEmpty()) {
                return null
            }

            val bestScore = scoredMatches.maxOf { it.second }
            val bestMatches = scoredMatches.filter { it.second == bestScore }.map { it.first }.distinct()

            return bestMatches.singleOrNull()
        }

        private fun normalizePath(path: String): String = path.replace('\\', '/')

        private fun matchScore(candidatePath: String, packagePath: String): Int = when {
            candidatePath.contains("/$packagePath/") -> packagePath.length + 2
            candidatePath.endsWith("/$packagePath") -> packagePath.length + 1
            candidatePath.contains(packagePath) -> packagePath.length
            else -> 0
        }
    }
}
