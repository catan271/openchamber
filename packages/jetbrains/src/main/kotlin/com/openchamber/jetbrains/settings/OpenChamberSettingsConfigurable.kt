package com.openchamber.jetbrains.settings

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.project.Project
import com.intellij.openapi.ui.TextFieldWithBrowseButton
import com.intellij.ui.components.JBTextField
import com.intellij.util.ui.FormBuilder
import com.openchamber.jetbrains.OpenChamberProjectService
import com.openchamber.jetbrains.util.OpenChamberSettings
import javax.swing.JComponent
import javax.swing.JPanel

class OpenChamberSettingsConfigurable(private val project: Project) : Configurable {

    private val settings = OpenChamberSettings.getInstance(project)
    private lateinit var apiUrlField: JBTextField
    private lateinit var opencodeBinaryField: TextFieldWithBrowseButton
    private lateinit var themeVariantField: JBTextField

    override fun getDisplayName(): String = "OpenChamber"

    override fun createComponent(): JComponent {
        apiUrlField = JBTextField(settings.apiUrl ?: "")
        opencodeBinaryField = TextFieldWithBrowseButton().apply {
            text = settings.opencodeBinary ?: ""
        }
        themeVariantField = JBTextField(settings.themeVariant)

        return FormBuilder.createFormBuilder()
            .addLabeledComponent("OpenCode API URL (leave empty to auto-start CLI):", apiUrlField)
            .addLabeledComponent("OpenCode binary path:", opencodeBinaryField)
            .addLabeledComponent("Theme variant (light/dark/system):", themeVariantField)
            .addComponentFillVertically(JPanel(), 0)
            .panel
    }

    override fun isModified(): Boolean {
        return apiUrlField.text != (settings.apiUrl ?: "") ||
                opencodeBinaryField.text != (settings.opencodeBinary ?: "") ||
                themeVariantField.text != settings.themeVariant
    }

    override fun apply() {
        settings.apiUrl = apiUrlField.text
        settings.opencodeBinary = opencodeBinaryField.text
        settings.themeVariant = themeVariantField.text
        OpenChamberProjectService.getInstance(project).getOpenCodeManager().restart()
    }

    override fun reset() {
        apiUrlField.text = settings.apiUrl ?: ""
        opencodeBinaryField.text = settings.opencodeBinary ?: ""
        themeVariantField.text = settings.themeVariant
    }
}
