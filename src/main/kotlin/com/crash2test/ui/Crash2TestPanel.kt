package com.crash2test.ui

import com.crash2test.model.ResolvedFrame
import com.crash2test.services.ProjectFileResolver
import com.intellij.openapi.application.ReadAction
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Cursor
import java.awt.Dimension
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListModel
import javax.swing.DefaultListCellRenderer
import javax.swing.JButton
import javax.swing.JList
import javax.swing.JPanel

class Crash2TestPanel(
    private val project: Project,
    private val formatter: PlaceholderAnalysisFormatter = PlaceholderAnalysisFormatter(ProjectFileResolver(project)),
) : JPanel(BorderLayout()) {
    private val inputArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Paste a Java or Kotlin stack trace to generate a debugging summary."
    }

    private val resultArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        isEditable = false
        background = JBColor.PanelBackground
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
    }
    private val resolvedFrameModel = DefaultListModel<ResolvedFrame>()
    private val resolvedFramesList = JBList(resolvedFrameModel).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        visibleRowCount = 4
        cellRenderer = object : DefaultListCellRenderer() {
            override fun getListCellRendererComponent(
                list: JList<*>?,
                value: Any?,
                index: Int,
                isSelected: Boolean,
                cellHasFocus: Boolean,
            ) = super.getListCellRendererComponent(list, value, index, isSelected, cellHasFocus).apply {
                val resolvedFrame = value as? ResolvedFrame ?: return@apply
                val path = resolvedFrame.resolvedPath ?: resolvedFrame.frame.fileName ?: resolvedFrame.frame.className
                val lineSuffix = resolvedFrame.lineNumber?.let { ":$it" }.orEmpty()
                text = "$path$lineSuffix"
            }
        }
        addMouseListener(object : MouseAdapter() {
            override fun mouseClicked(event: MouseEvent) {
                if (event.clickCount == 1) {
                    selectedValue?.let(::openResolvedFrame)
                }
            }
        })
    }
    private val resolvedFramesScrollPane = JBScrollPane(resolvedFramesList).apply {
        preferredSize = Dimension(320, 96)
        isVisible = false
    }

    private val statusLabel = JBLabel()
    private val analyzeButton = JButton("Analyze").apply {
        addActionListener {
            val state = ReadAction.compute<Crash2TestViewState, RuntimeException> {
                formatter.analyze(inputArea.text)
            }
            render(state)
        }
    }

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        inputArea.minimumSize = Dimension(320, 180)
        inputArea.rows = 12
        resultArea.rows = 16

        render(formatter.initialState(project.name))

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponentFillVertically("Stack Trace / Error", JBScrollPane(inputArea))
            .addComponent(statusLabel)
            .addComponent(analyzeButton)
            .addVerticalGap(8)
            .addLabeledComponentFillVertically("Analysis Result", JBScrollPane(resultArea))
            .addVerticalGap(8)
            .addLabeledComponentFillVertically("Resolved Frames", resolvedFramesScrollPane)
            .panel

        add(formPanel, BorderLayout.CENTER)
    }

    private fun render(state: Crash2TestViewState) {
        statusLabel.text = state.statusMessage
        resultArea.text = state.resultText
        analyzeButton.isEnabled = state.canAnalyze
        resolvedFrameModel.removeAllElements()
        state.clickableFrames.forEach(resolvedFrameModel::addElement)
        resolvedFramesScrollPane.isVisible = state.clickableFrames.isNotEmpty()
    }

    private fun openResolvedFrame(resolvedFrame: ResolvedFrame) {
        val file = resolvedFrame.navigationPath
            ?.let(LocalFileSystem.getInstance()::findFileByPath)
            ?: return

        val line = (resolvedFrame.lineNumber ?: 1).coerceAtLeast(1) - 1
        OpenFileDescriptor(project, file, line, 0).navigate(true)
    }
}
