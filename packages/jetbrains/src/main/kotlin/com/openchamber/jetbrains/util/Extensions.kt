package com.openchamber.jetbrains.util

import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.application.invokeLater
import com.intellij.openapi.project.Project
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext

suspend fun <T> runReadAction(action: () -> T): T {
    return withContext(Dispatchers.IO) {
        com.intellij.openapi.application.ReadAction.compute<T, Throwable>(action)
    }
}

fun runOnUiThread(action: () -> Unit) {
    ApplicationManager.getApplication().invokeLater(action)
}
