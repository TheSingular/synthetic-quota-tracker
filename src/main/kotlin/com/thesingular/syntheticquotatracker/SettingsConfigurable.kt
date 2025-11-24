package com.thesingular.syntheticquotatracker

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

class SettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var tokenField: JBPasswordField? = null
    private var intervalField: JBTextField? = null

    override fun getDisplayName(): String = "Synthetic Quota Tracker"

    override fun createComponent(): JComponent {
        val p = JPanel(GridBagLayout())
        val c = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            insets = Insets(4, 4, 4, 4)
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }

        // Token label and field
        p.add(JLabel("API Token:"), c)
        c.gridx = 1
        tokenField = JBPasswordField()
        tokenField!!.columns = 30
        p.add(tokenField, c)

        // Interval label and field
        c.gridx = 0
        c.gridy = 1
        p.add(JLabel("Update interval (minutes):"), c)
        c.gridx = 1
        intervalField = JBTextField()
        p.add(intervalField, c)

        panel = p
        reset()
        return p
    }

    override fun isModified(): Boolean {
        val settings = SettingsState.getInstance()
        val tokenText = String(tokenField?.password ?: CharArray(0))
        val intervalText = intervalField?.text?.trim().orEmpty()
        val intervalVal = intervalText.toIntOrNull() ?: settings.intervalMinutes
        return tokenText != (settings.apiToken ?: "") || intervalVal != settings.intervalMinutes
    }

    override fun apply() {
        val settings = SettingsState.getInstance()
        val tokenText = String(tokenField?.password ?: CharArray(0)).trim().ifEmpty { null }
        val intervalText = intervalField?.text?.trim().orEmpty()
        val intervalVal = intervalText.toIntOrNull()
            ?: throw ConfigurationException("Interval must be a number")
        if (intervalVal <= 0) throw ConfigurationException("Interval must be positive")
        settings.apiToken = tokenText
        settings.intervalMinutes = intervalVal
        notifySettingsChanged()
    }

    override fun reset() {
        val settings = SettingsState.getInstance()
        tokenField?.text = settings.apiToken ?: ""
        intervalField?.text = settings.intervalMinutes.toString()
    }

    override fun disposeUIResources() {
        panel = null
        tokenField = null
        intervalField = null
    }
}
