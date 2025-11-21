package com.thesingular.syntheticquotatracker

import com.intellij.credentialStore.CredentialAttributes
import com.intellij.credentialStore.Credentials
import com.intellij.credentialStore.generateServiceName
import com.intellij.ide.passwordSafe.PasswordSafe
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.PersistentStateComponent
import com.intellij.openapi.components.Service
import com.intellij.openapi.components.State
import com.intellij.openapi.components.Storage
import com.intellij.openapi.components.service
import com.intellij.util.messages.Topic

@Service(Service.Level.APP)
@State(
    name = "SyntheticQuotaTrackerSettings",
    storages = [Storage("synthetic-quota-tracker.xml")]
)
class SettingsState : PersistentStateComponent<SettingsState.State> {
    data class State(
        var intervalSeconds: Int = 60,
    )

    private var myState = State()

    override fun getState(): State = myState

    override fun loadState(state: State) {
        this.myState = state
    }

    var apiToken: String?
        get() {
            val credentials = PasswordSafe.instance.get(CREDENTIAL_ATTRIBUTES)
            return credentials?.getPasswordAsString()
        }
        set(value) {
            val credentials = if (value != null) Credentials(null, value) else null
            PasswordSafe.instance.set(CREDENTIAL_ATTRIBUTES, credentials)
        }

    var intervalSeconds: Int
        get() = myState.intervalSeconds
        set(value) { myState.intervalSeconds = value }

    companion object {
        fun getInstance(): SettingsState = service()

        val SETTINGS_CHANGED: Topic<SettingsChangedListener> =
            Topic.create("SyntheticQuotaTrackerSettingsChanged", SettingsChangedListener::class.java)

        private val CREDENTIAL_ATTRIBUTES = CredentialAttributes(
            generateServiceName("SyntheticQuotaTracker", "apiToken")
        )
    }
}

interface SettingsChangedListener {
    fun onSettingsChanged()
}

fun notifySettingsChanged() {
    ApplicationManager.getApplication().messageBus.syncPublisher(SettingsState.SETTINGS_CHANGED).onSettingsChanged()
}
