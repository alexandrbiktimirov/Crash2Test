package com.crash2test.ui

import com.crash2test.services.ProjectFileResolver
import com.intellij.openapi.project.Project
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Dimension
import javax.swing.BorderFactory
import javax.swing.JButton
import javax.swing.JPanel

class Crash2TestPanel(
    project: Project,
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

    private val statusLabel = JBLabel()
    private val analyzeButton = JButton("Analyze").apply {
        addActionListener { render(formatter.analyze(inputArea.text)) }
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
            .panel

        add(formPanel, BorderLayout.CENTER)
    }

    private fun render(state: Crash2TestViewState) {
        statusLabel.text = state.statusMessage
        resultArea.text = state.resultText
        analyzeButton.isEnabled = state.canAnalyze
    }
}
