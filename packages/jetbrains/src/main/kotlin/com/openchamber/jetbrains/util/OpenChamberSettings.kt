package com.openchamber.jetbrains.util

import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.openapi.project.Project
import com.intellij.util.xmlb.XmlSerializerUtil

@Service(Service.Level.PROJECT)
@State(name = "OpenChamberSettings", storages = [Storage("OpenChamberSettings.xml")])
class OpenChamberSettings : PersistentStateComponent<OpenChamberSettings.State> {

    data class State(
        var apiUrl: String = "",
        var opencodeBinary: String = "",
        var themeVariant: String = "system"
    )

    private var state = State()

    var apiUrl: String?
        get() = state.apiUrl.takeIf { it.isNotBlank() }
        set(value) { state.apiUrl = value ?: "" }

    var opencodeBinary: String?
        get() = state.opencodeBinary.takeIf { it.isNotBlank() }
        set(value) { state.opencodeBinary = value ?: "" }

    var themeVariant: String
        get() = state.themeVariant
        set(value) { state.themeVariant = value }

    override fun getState(): State = state

    override fun loadState(state: State) {
        XmlSerializerUtil.copyBean(state, this.state)
    }

    companion object {
        fun getInstance(project: Project): OpenChamberSettings = project.service()
    }
}
