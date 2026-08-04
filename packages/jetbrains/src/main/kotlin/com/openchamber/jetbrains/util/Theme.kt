package com.openchamber.jetbrains.util

import com.intellij.openapi.editor.colors.EditorColorsManager
import com.intellij.openapi.editor.colors.impl.AppEditorFontOptions
import com.intellij.ui.JBColor

fun themeVariant(): String {
    return if (JBColor.isBright()) "light" else "dark"
}

fun isDarkTheme(): Boolean = !JBColor.isBright()
