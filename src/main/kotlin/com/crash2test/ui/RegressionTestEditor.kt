package com.crash2test.ui

import com.crash2test.model.RegressionTestLanguage
import com.intellij.openapi.Disposable
import com.intellij.openapi.editor.EditorFactory
import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.ex.EditorEx
import com.intellij.openapi.editor.highlighter.EditorHighlighterFactory
import com.intellij.openapi.fileTypes.FileType
import com.intellij.openapi.fileTypes.FileTypeManager
import com.intellij.openapi.fileTypes.PlainTextFileType
import com.intellij.openapi.fileTypes.UnknownFileType
import com.intellij.openapi.project.Project
import com.intellij.testFramework.LightVirtualFile
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBScrollPane
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JPanel
import javax.swing.ScrollPaneConstants

internal class RegressionTestEditor(private val project: Project) : JBScrollPane(), Disposable {
    private val host = JPanel(BorderLayout()).apply {
        background = JBColor(0x171717, 0x171717)
        border = BorderFactory.createLineBorder(JBColor(0x30363d, 0x30363d))
    }
    private var editor = createEditor("", RegressionTestLanguage.DEFAULT.suggestedFileName, "kotlin")

    init {
        setViewportView(host)
        host.add(editor.component, BorderLayout.CENTER)
        preferredSize = Dimension(320, 180)
        verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        isVisible = false
    }

    fun showCode(code: String, fileName: String, fenceTag: String?) {
        val newEditor = createEditor(code, fileName, fenceTag)
        host.removeAll()
        host.add(newEditor.component, BorderLayout.CENTER)
        EditorFactory.getInstance().releaseEditor(editor)
        editor = newEditor

        val height = code.lineSequence().count().coerceAtLeast(1) * editor.lineHeight + 24
        preferredSize = Dimension(320, height)
        maximumSize = Dimension(Int.MAX_VALUE, height)
        editor.scrollPane.preferredSize = Dimension(320, height)
        host.revalidate()
        host.repaint()
    }

    private fun createEditor(code: String, fileName: String, fenceTag: String?): EditorEx {
        val document = EditorFactory.getInstance().createDocument(code)
        val editor = EditorFactory.getInstance().createViewer(document, project) as EditorEx
        editor.setHorizontalScrollbarVisible(true)
        editor.setVerticalScrollbarVisible(false)
        editor.colorsScheme = EditorColorsManager.getInstance().globalScheme
        editor.settings.apply {
            isLineNumbersShown = true
            isFoldingOutlineShown = false
            isCaretRowShown = false
            additionalLinesCount = 0
            additionalColumnsCount = 0
            isRightMarginShown = false
            isIndentGuidesShown = true
            isWhitespacesShown = false
            isLineMarkerAreaShown = false
        }
        val virtualFile = LightVirtualFile(fileName, resolveFileType(fileName, fenceTag), code)
        editor.highlighter = EditorHighlighterFactory.getInstance().createEditorHighlighter(project, virtualFile)
        editor.scrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
        return editor
    }

    private fun resolveFileType(fileName: String, fenceTag: String?): FileType {
        val manager = FileTypeManager.getInstance()
        val extensions = listOfNotNull(
            fenceTag?.trim()?.lowercase()?.takeIf(String::isNotEmpty)?.let(::extensionForFenceTag),
            fileName.substringAfterLast('.', "").trim().lowercase().takeIf(String::isNotEmpty),
        )
        return extensions.asSequence()
            .map(manager::getFileTypeByExtension)
            .firstOrNull { it !is UnknownFileType }
            ?: manager.getFileTypeByFileName(fileName).takeUnless { it is UnknownFileType }
            ?: PlainTextFileType.INSTANCE
    }

    private fun extensionForFenceTag(tag: String): String =
        RegressionTestLanguage.fromFenceTag(tag)?.suggestedFileName?.substringAfterLast('.') ?: when (tag) {
            "shell", "bash" -> "sh"
            "yaml" -> "yml"
            "markdown" -> "md"
            else -> tag
        }

    override fun dispose() {
        EditorFactory.getInstance().releaseEditor(editor)
    }
}
