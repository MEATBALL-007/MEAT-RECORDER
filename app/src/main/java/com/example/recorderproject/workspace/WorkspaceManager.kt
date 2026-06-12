package com.example.recorderproject.workspace

import com.example.recorderproject.data.SettingsDataStore
import com.example.recorderproject.model.RecorderMode
import com.example.recorderproject.model.WorkspaceLayout
import com.example.recorderproject.model.WorkspaceLayoutJson
import com.example.recorderproject.model.mergedWithDefaults
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.combine
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

/**
 * Resolves and persists the recording workspace layout.
 * Free users share one global layout; Pro users get an independent layout per
 * [RecorderMode]. The active layout is always merged with the current registry
 * so newly added controls appear automatically.
 */
@OptIn(ExperimentalCoroutinesApi::class)
class WorkspaceManager(
    private val settings: SettingsDataStore,
    private val scope: CoroutineScope,
    private val isPro: StateFlow<Boolean>,
    private val mode: StateFlow<RecorderMode>,
) {
    val layout: StateFlow<WorkspaceLayout> =
        combine(isPro, mode) { pro, m -> pro to m }
            .flatMapLatest { (pro, m) ->
                val source = if (pro) settings.modeWorkspaceJson(m) else settings.globalWorkspaceJson()
                source.map { json ->
                    json?.let { WorkspaceLayoutJson.fromJsonString(it) }?.mergedWithDefaults()
                        ?: WorkspaceLayout.DEFAULT
                }
            }
            .stateIn(scope, SharingStarted.WhileSubscribed(5_000), WorkspaceLayout.DEFAULT)

    /** Persist [newLayout] to the appropriate store for the current entitlement. */
    fun save(newLayout: WorkspaceLayout) {
        scope.launch {
            val json = WorkspaceLayoutJson.toJsonString(newLayout)
            if (isPro.value) settings.setModeWorkspaceJson(mode.value, json)
            else settings.setGlobalWorkspaceJson(json)
        }
    }

    fun resetToDefault() = save(WorkspaceLayout.DEFAULT)
}
