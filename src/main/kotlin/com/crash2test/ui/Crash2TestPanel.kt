package com.crash2test.ui

import com.crash2test.model.CrashAnalysisResult
import com.crash2test.model.ResolvedFrame
import com.crash2test.services.CrashAnalyzer
import com.crash2test.services.SettingsBackedCrashAnalyzer
import com.intellij.openapi.Disposable
import com.intellij.openapi.fileEditor.OpenFileDescriptor
import com.intellij.openapi.ide.CopyPasteManager
import com.intellij.openapi.project.Project
import com.intellij.openapi.vfs.LocalFileSystem
import com.intellij.ui.JBColor
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBList
import com.intellij.ui.components.JBScrollPane
import com.intellij.ui.components.JBTextArea
import com.intellij.util.ui.FormBuilder
import java.awt.BorderLayout
import java.awt.Component
import java.awt.Cursor
import java.awt.Dimension
import java.awt.FlowLayout
import java.awt.datatransfer.StringSelection
import java.awt.event.MouseAdapter
import java.awt.event.MouseEvent
import javax.swing.BorderFactory
import javax.swing.DefaultListCellRenderer
import javax.swing.DefaultListModel
import javax.swing.JButton
import javax.swing.JEditorPane
import javax.swing.JList
import javax.swing.JPanel
import javax.swing.KeyStroke
import javax.swing.ListSelectionModel
import javax.swing.ScrollPaneConstants
import javax.swing.SwingWorker

class Crash2TestPanel(
    private val project: Project,
    private val analysisService: CrashAnalyzer = SettingsBackedCrashAnalyzer(project),
    private val renderer: AnalysisResultRenderer = AnalysisResultRenderer(),
) : JPanel(BorderLayout()), Disposable {
    private val inputArea = JBTextArea().apply {
        lineWrap = true
        wrapStyleWord = true
        toolTipText = "Paste a Java or Kotlin stack trace to generate a debugging summary."
    }

    private val markdownRenderer = MarkdownToHtmlRenderer()
    private var streamedMarkdown = ""
    private var currentRegressionTestCode: String? = null
    private val analysisResultLabel = JBLabel("Analysis Result").apply {
        alignmentX = Component.LEFT_ALIGNMENT
        isVisible = false
    }
    private val resultScrollPane = JBScrollPane()
    private val rootScrollPane: JBScrollPane
    private val resultArea = JEditorPane("text/html", "").apply {
        isEditable = false
        background = JBColor.PanelBackground
        border = BorderFactory.createEmptyBorder(8, 8, 8, 8)
        putClientProperty("JEditorPane.HONOR_DISPLAY_PROPERTIES", true)
    }
    private val regressionTestTitleLabel = JBLabel("Regression Test Code")
    private val copyRegressionTestButton = JButton("Copy").apply {
        addActionListener {
            currentRegressionTestCode?.takeIf { it.isNotBlank() }?.let { code ->
                CopyPasteManager.getInstance().setContents(StringSelection(code))
            }
        }
    }
    private val regressionTestHeader = JPanel(FlowLayout(FlowLayout.LEFT, 8, 0)).apply {
        background = JBColor.PanelBackground
        add(regressionTestTitleLabel)
        add(copyRegressionTestButton)
        isVisible = false
    }
    private val regressionTestEditor = RegressionTestEditor(project)
    private val statusLabel = JBLabel()
    private val resolvedFramesLabel = JBLabel("Resolved Frames (Double-click or press Enter to open)").apply {
        isVisible = false
    }
    private val resolvedFrameModel = DefaultListModel<ResolvedFrame>()
    private val resolvedFramesList = JBList(resolvedFrameModel).apply {
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        visibleRowCount = 4
        selectionMode = ListSelectionModel.SINGLE_SELECTION
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
                if (event.clickCount >= 2) {
                    selectedValue?.let(::openResolvedFrame)
                }
            }
        })
        inputMap.put(KeyStroke.getKeyStroke("ENTER"), "openResolvedFrame")
        actionMap.put(
            "openResolvedFrame",
            object : javax.swing.AbstractAction() {
                override fun actionPerformed(event: java.awt.event.ActionEvent?) {
                    selectedValue?.let(::openResolvedFrame)
                }
            },
        )
    }
    private val resolvedFramesScrollPane = JBScrollPane(resolvedFramesList).apply {
        preferredSize = Dimension(320, 96)
        isVisible = false
    }
    private val analyzeButton = JButton("Analyze").apply {
        addActionListener {
            startAnalysis()
        }
    }

    init {
        border = BorderFactory.createEmptyBorder(12, 12, 12, 12)

        inputArea.minimumSize = Dimension(320, 180)
        inputArea.rows = 12

        resultScrollPane.apply {
            setViewportView(resultArea)
            isVisible = false
            horizontalScrollBarPolicy = ScrollPaneConstants.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER
            border = BorderFactory.createEmptyBorder()
        }

        val formPanel = FormBuilder.createFormBuilder()
            .addLabeledComponentFillVertically("Stack Trace / Error", JBScrollPane(inputArea))
            .addComponent(analyzeButton)
            .addComponent(statusLabel)
            .addVerticalGap(8)
            .addComponent(analysisResultLabel)
            .addComponentFillVertically(resultScrollPane, 0)
            .addVerticalGap(8)
            .addComponent(regressionTestHeader)
            .addComponentFillVertically(regressionTestEditor, 0)
            .addVerticalGap(8)
            .addComponent(resolvedFramesLabel)
            .addComponentFillVertically(resolvedFramesScrollPane, 0)
            .panel

        rootScrollPane = JBScrollPane(formPanel).apply {
            border = BorderFactory.createEmptyBorder()
            horizontalScrollBarPolicy = JBScrollPane.HORIZONTAL_SCROLLBAR_NEVER
            verticalScrollBar.unitIncrement = 24
        }
        resolvedFramesScrollPane.verticalScrollBarPolicy = ScrollPaneConstants.VERTICAL_SCROLLBAR_NEVER

        add(rootScrollPane, BorderLayout.CENTER)
        render(renderer.initialState())
    }

    private fun render(state: Crash2TestViewState) {
        statusLabel.text = state.statusMessage
        streamedMarkdown = state.resultText
        resultArea.text = markdownRenderer.toHtml(state.resultText)
        resultArea.caretPosition = 0
        analyzeButton.isEnabled = state.canAnalyze
        updateResultVisibility(state.resultText)
        currentRegressionTestCode = state.regressionTestCode
        val hasRegressionCode = !state.regressionTestCode.isNullOrBlank()
        regressionTestHeader.isVisible = hasRegressionCode
        regressionTestEditor.isVisible = hasRegressionCode
        regressionTestEditor.showCode(
            code = state.regressionTestCode.orEmpty(),
            fileName = state.regressionTestFileName ?: "GeneratedRegressionTest.txt",
            fenceTag = state.regressionTestFenceTag,
        )
        resolvedFrameModel.removeAllElements()
        state.clickableFrames.forEach(resolvedFrameModel::addElement)
        val hasClickableFrames = state.clickableFrames.isNotEmpty()
        resolvedFramesLabel.isVisible = hasClickableFrames
        resolvedFramesScrollPane.isVisible = hasClickableFrames
        updateResolvedFramesHeight()
        rootScrollPane.revalidate()
        rootScrollPane.repaint()
    }

    private fun startAnalysis() {
        render(renderer.loadingState())
        val rawInput = inputArea.text
        object : SwingWorker<CrashAnalysisResult, String>() {
            override fun doInBackground(): CrashAnalysisResult = analysisService.analyzeStreaming(rawInput) { chunk ->
                publish(chunk)
            }

            override fun process(chunks: MutableList<String>) {
                if (chunks.isNotEmpty()) {
                    streamedMarkdown += chunks.joinToString(separator = "")
                    resultArea.text = markdownRenderer.toHtml(streamedMarkdown)
                    updateResultVisibility(streamedMarkdown)
                    rootScrollPane.revalidate()
                }
            }

            override fun done() {
                val state = try {
                    renderer.render(get())
                } catch (exception: Exception) {
                    renderer.render(
                        CrashAnalysisResult.Failure(
                            message = exception.cause?.message ?: exception.message ?: "Unexpected analysis error.",
                        ),
                    )
                }
                render(state)
            }
        }.execute()
    }

    private fun openResolvedFrame(resolvedFrame: ResolvedFrame) {
        val file = resolvedFrame.navigationPath
            ?.let(LocalFileSystem.getInstance()::findFileByPath)
            ?: return

        val line = (resolvedFrame.lineNumber ?: 1).coerceAtLeast(1) - 1
        OpenFileDescriptor(project, file, line, 0).navigate(true)
    }

    private fun updateResultVisibility(markdown: String) {
        val hasResult = markdown.isNotBlank()
        analysisResultLabel.isVisible = hasResult
        resultScrollPane.isVisible = hasResult
        updateResultAreaHeight()
    }

    private fun updateResultAreaHeight() {
        val availableWidth = rootScrollPane.viewport.extentSize.width.coerceAtLeast(360) - 32
        resultArea.setSize(availableWidth, Int.MAX_VALUE)
        val preferredHeight = resultArea.preferredSize.height.coerceAtLeast(80)
        resultScrollPane.preferredSize = Dimension(availableWidth, preferredHeight + 4)
        resultScrollPane.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight + 4)
    }

    private fun updateResolvedFramesHeight() {
        val rowHeight = resolvedFramesList.fixedCellHeight.takeIf { it > 0 }
            ?: (resolvedFramesList.getFontMetrics(resolvedFramesList.font).height + 6)
        val visibleRows = resolvedFrameModel.size().coerceAtLeast(1)
        val preferredHeight = (visibleRows * rowHeight) + 8
        resolvedFramesScrollPane.preferredSize = Dimension(320, preferredHeight)
        resolvedFramesScrollPane.maximumSize = Dimension(Int.MAX_VALUE, preferredHeight)
    }

    override fun dispose() {
        regressionTestEditor.dispose()
    }
}
