package com.thesingular.syntheticquotatracker

import com.intellij.openapi.options.Configurable
import com.intellij.openapi.options.ConfigurationException
import com.intellij.ui.components.JBPasswordField
import com.intellij.ui.components.JBTextField
import java.awt.GridBagConstraints
import java.awt.GridBagLayout
import java.awt.Insets
import javax.swing.JButton
import javax.swing.JComboBox
import javax.swing.JComponent
import javax.swing.JLabel
import javax.swing.JPanel

// Import the notifySettingsChanged function
import com.thesingular.syntheticquotatracker.notifySettingsChanged

class SettingsConfigurable : Configurable {
    private var panel: JPanel? = null
    private var tokenField: JBPasswordField? = null
    private var intervalField: JBTextField? = null
    private var dateFormatCombo: JComboBox<String>? = null
    private var customFormatField: JBTextField? = null
    private var resetButton: JButton? = null

    // Available date format options
    private val dateFormatOptions = arrayOf(
        "System Locale (default)",
        "ISO 8601 (yyyy-MM-dd HH:mm:ss)",
        "Custom pattern..."
    )

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
        p.add(JLabel("Update interval (seconds):"), c)
        c.gridx = 1
        intervalField = JBTextField()
        p.add(intervalField, c)

        // Date format label and combo
        c.gridx = 0
        c.gridy = 2
        p.add(JLabel("Date format:"), c)
        c.gridx = 1
        val formatPanel = JPanel(GridBagLayout())
        val formatC = GridBagConstraints().apply {
            anchor = GridBagConstraints.WEST
            fill = GridBagConstraints.HORIZONTAL
            weightx = 1.0
            gridx = 0
            gridy = 0
        }
        dateFormatCombo = JComboBox(dateFormatOptions)
        formatPanel.add(dateFormatCombo, formatC)

        // Help button with tooltip
        formatC.gridx = 1
        formatC.weightx = 0.0
        formatC.fill = GridBagConstraints.NONE
        formatC.insets = Insets(0, 4, 0, 0)
        val helpButton = JButton("?")
        helpButton.toolTipText = "<html>" +
            "<b>Date Format Options:</b><br>" +
            "• <b>System Locale</b> - Uses your system's locale settings<br>" +
            "• <b>ISO 8601</b> - Standard format: yyyy-MM-dd HH:mm:ss<br>" +
            "• <b>Custom pattern</b> - Use Java DateTimeFormatter patterns:<br><br>" +
            "<b>Date Patterns:</b><br>" +
            "&nbsp;&nbsp;yyyy = year (4 digits)&nbsp;&nbsp;yy = year (2 digits)<br>" +
            "&nbsp;&nbsp;MM = month (01-12)&nbsp;&nbsp;MMM = month short&nbsp;&nbsp;MMMM = month full<br>" +
            "&nbsp;&nbsp;dd = day of month (01-31)&nbsp;&nbsp;EEE = weekday short&nbsp;&nbsp;EEEE = weekday full<br><br>" +
            "<b>Time Patterns:</b><br>" +
            "&nbsp;&nbsp;HH = hour (0-23)&nbsp;&nbsp;hh = hour (1-12)<br>" +
            "&nbsp;&nbsp;mm = minute&nbsp;&nbsp;ss = second<br>" +
            "&nbsp;&nbsp;a = AM/PM marker&nbsp;&nbsp;SSS = milliseconds<br><br>" +
            "<b>Examples:</b><br>" +
            "&nbsp;&nbsp;HH:mm:ss dd.MM.yyyy = 17:27:40 29.01.2026<br>" +
            "&nbsp;&nbsp;yyyy-MM-dd HH:mm:ss = 2026-01-29 17:27:40<br>" +
            "&nbsp;&nbsp;hh:mm:ss a, MMM dd = 05:27:40 PM, Jan 29" +
            "</html>"
        helpButton.preferredSize = java.awt.Dimension(24, 24)
        helpButton.margin = Insets(0, 0, 0, 0)
        formatPanel.add(helpButton, formatC)
        p.add(formatPanel, c)

        // Custom format field (shown only when "Custom pattern..." is selected)
        c.gridx = 0
        c.gridy = 3
        p.add(JLabel("Custom pattern:"), c)
        c.gridx = 1
        customFormatField = JBTextField()
        customFormatField!!.isEnabled = false
        p.add(customFormatField, c)

        // Buttons panel
        c.gridx = 0
        c.gridy = 4
        c.gridwidth = 2
        c.anchor = GridBagConstraints.CENTER
        c.fill = GridBagConstraints.NONE
        val buttonsPanel = JPanel()

        // Reset to Defaults button (doesn't clear API token)
        resetButton = JButton("Reset to Defaults")
        resetButton!!.toolTipText = "Reset interval and date format to default values"
        resetButton!!.addActionListener {
            intervalField?.text = "30"
            dateFormatCombo?.selectedIndex = 0
            customFormatField?.text = ""
            customFormatField?.isEnabled = false
        }
        buttonsPanel.add(resetButton)

        p.add(buttonsPanel, c)

        // Add listener to combo box to enable/disable custom field
        dateFormatCombo!!.addActionListener {
            val selected = dateFormatCombo!!.selectedIndex
            customFormatField!!.isEnabled = selected == 2 // "Custom pattern..."
        }

        panel = p
        reset()
        return p
    }

    override fun isModified(): Boolean {
        val settings = SettingsState.getInstance()
        val tokenText = String(tokenField?.password ?: CharArray(0)).trim().ifEmpty { null }
        val storedToken = settings.apiToken?.trim()
        val intervalText = intervalField?.text?.trim().orEmpty()
        val intervalVal = intervalText.toIntOrNull() ?: settings.intervalSeconds

        val dateFormatValue = when (dateFormatCombo!!.selectedIndex) {
            0 -> SettingsState.DATE_FORMAT_LOCALE
            1 -> SettingsState.DATE_FORMAT_ISO
            else -> customFormatField!!.text.trim()
        }

        return tokenText != storedToken ||
                intervalVal != settings.intervalSeconds ||
                dateFormatValue != settings.dateFormat
    }

    override fun apply() {
        val settings = SettingsState.getInstance()
        val tokenText = String(tokenField?.password ?: CharArray(0)).trim().ifEmpty { null }
        val intervalText = intervalField?.text?.trim().orEmpty()
        val intervalVal = intervalText.toIntOrNull()
            ?: throw ConfigurationException("Interval must be a number")
        if (intervalVal < 10) throw ConfigurationException("Interval must be at least 10")

        val dateFormatValue = when (dateFormatCombo!!.selectedIndex) {
            0 -> SettingsState.DATE_FORMAT_LOCALE
            1 -> SettingsState.DATE_FORMAT_ISO
            else -> {
                val custom = customFormatField!!.text.trim()
                if (custom.isEmpty()) {
                    throw ConfigurationException("Custom date format pattern cannot be empty")
                }
                custom
            }
        }

        settings.apiToken = tokenText
        settings.intervalSeconds = intervalVal
        settings.dateFormat = dateFormatValue
        notifySettingsChanged()
    }

    override fun reset() {
        val settings = SettingsState.getInstance()
        tokenField?.text = settings.apiToken ?: ""
        intervalField?.text = settings.intervalSeconds.toString()

        when (settings.dateFormat) {
            SettingsState.DATE_FORMAT_LOCALE -> dateFormatCombo!!.selectedIndex = 0
            SettingsState.DATE_FORMAT_ISO -> dateFormatCombo!!.selectedIndex = 1
            else -> {
                dateFormatCombo!!.selectedIndex = 2
                customFormatField!!.text = settings.dateFormat
            }
        }
        customFormatField!!.isEnabled = dateFormatCombo!!.selectedIndex == 2
    }

    override fun disposeUIResources() {
        panel = null
        tokenField = null
        intervalField = null
        dateFormatCombo = null
        customFormatField = null
        resetButton = null
    }
}
