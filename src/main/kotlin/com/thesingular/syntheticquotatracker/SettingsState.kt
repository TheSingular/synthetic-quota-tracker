package com.thesingular.syntheticquotatracker

import com.intellij.openapi.components.*
import com.intellij.openapi.application.ApplicationManager
import com.intellij.util.messages.Topic

@Service(Service.Level.APP)
@State(
    name = "SyntheticQuotaTrackerSettings",
    storages = [Storage("synthetic-quota-tracker.xml")]
)
class SettingsState : PersistentStateComponent<SettingsState.State> {
    data class State(
        var apiToken: String? = null,
        var intervalSeconds: Int = 60,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        this.myState = state
    }

    var apiToken: String?
        get() = myState.apiToken
        set(value) { myState.apiToken = value }

    var intervalSeconds: Int
        get() = myState.intervalSeconds
        set(value) { myState.intervalSeconds = value }

    companion object {
        fun getInstance(): SettingsState = service()

        val SETTINGS_CHANGED: Topic<SettingsChangedListener> =
            Topic.create("SyntheticQuotaTrackerSettingsChanged", SettingsChangedListener::class.java)
    }
}

interface SettingsChangedListener {
    fun onSettingsChanged()
}

fun notifySettingsChanged() {
    ApplicationManager.getApplication().messageBus.syncPublisher(SettingsState.SETTINGS_CHANGED).onSettingsChanged()
}
