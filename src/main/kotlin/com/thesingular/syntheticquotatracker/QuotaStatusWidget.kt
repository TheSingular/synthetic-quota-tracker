package com.thesingular.syntheticquotatracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.options.ShowSettingsUtil
import com.intellij.ui.components.JBLabel
import com.intellij.ui.components.JBPanel
import java.awt.BorderLayout
import java.awt.Color
import java.awt.Cursor

class QuotaStatusWidgetFactory : StatusBarWidgetFactory {
    override fun getId(): String = "synthetic.quota.widget"

    override fun getDisplayName(): String = "Synthetic Quota"

    override fun isAvailable(project: Project): Boolean = true

    override fun createWidget(project: Project): StatusBarWidget = QuotaStatusWidget()

    override fun disposeWidget(widget: StatusBarWidget) {
        Disposer.dispose(widget)
    }

    override fun canBeEnabledOn(statusBar: StatusBar): Boolean = true
}

class QuotaStatusWidget : CustomStatusBarWidget, Disposable, QuotaListener {
    private var statusBar: StatusBar? = null
    private val label = JBLabel("Synthetic: Requests: --/-- | Search: --/--").apply {
        isFocusable = false
    }
    private val panel = JBPanel<JBPanel<*>>(BorderLayout()).apply {
        isOpaque = false
        isFocusable = false
        add(label)
        toolTipText = "Synthetic quota"
        cursor = Cursor.getPredefinedCursor(Cursor.HAND_CURSOR)
        addMouseListener(object : java.awt.event.MouseAdapter() {
            override fun mouseClicked(e: java.awt.event.MouseEvent) {
                ShowSettingsUtil.getInstance().showSettingsDialog(null as Project?, "Synthetic Quota Tracker")
            }
        })
    }

    private val service: QuotaService = com.intellij.openapi.components.service()

    override fun ID(): String = "synthetic.quota.widget"

    override fun install(statusBar: StatusBar) {
        this.statusBar = statusBar
        service.addListener(this, this)
    }

    override fun getComponent(): javax.swing.JComponent = panel

    override fun dispose() {
        statusBar = null
    }

    override fun onQuotaUpdated(info: QuotaInfo?) {
        if (statusBar == null) return
        if (info == null) {
            label.text = "Synthetic: Requests: --/-- | Search: --/--"
            label.foreground = null
            panel.toolTipText = "Synthetic quota (no data)"
        } else {
            // Main display: subscription as "requests" and search as "search"
            val subRequests = info.subscription?.requests
            val subLimit = info.subscription?.limit
            val searchRequests = info.search?.requests
            val searchLimit = info.search?.limit

            val requestsStr = if (subRequests != null && subLimit != null) "${formatNumber(subRequests)}/${formatNumber(subLimit)}" else "--/--"
            val searchStr = if (searchRequests != null && searchLimit != null) "${formatNumber(searchRequests)}/${formatNumber(searchLimit)}" else "--/--"

            label.text = "Synthetic: Requests: $requestsStr | Search: $searchStr"

            // Check if any quota is low (< 10% remaining) and turn text red
            val isLowQuota = isQuotaLow(info.subscription) || isQuotaLow(info.search) || isQuotaLow(info.toolCalls)
            label.foreground = if (isLowQuota) Color.RED else null

            // Build tooltip with all sections
            val tooltipBuilder = StringBuilder("<html>")

            // Requests section (subscription)
            tooltipBuilder.append("<b>Requests</b><br>")
            if (info.subscription != null) {
                val remaining = calculateRemaining(info.subscription.requests, info.subscription.limit)
                tooltipBuilder.append("&nbsp;&nbsp;Used: ${formatNumber(info.subscription.requests) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Limit: ${formatNumber(info.subscription.limit) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Remaining: ${formatNumber(remaining) ?: "--"}<br>")
                val renewsLocal = QuotaService.formatToLocalTime(info.subscription.renewsAt)
                tooltipBuilder.append("&nbsp;&nbsp;Renews: ${renewsLocal ?: "--"}")
            } else {
                tooltipBuilder.append("&nbsp;&nbsp;No data")
            }
            tooltipBuilder.append("<br><br>")

            // Search section
            tooltipBuilder.append("<b>Search</b><br>")
            if (info.search != null) {
                val remaining = calculateRemaining(info.search.requests, info.search.limit)
                tooltipBuilder.append("&nbsp;&nbsp;Used: ${formatNumber(info.search.requests) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Limit: ${formatNumber(info.search.limit) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Remaining: ${formatNumber(remaining) ?: "--"}<br>")
                val renewsLocal = QuotaService.formatToLocalTime(info.search.renewsAt)
                tooltipBuilder.append("&nbsp;&nbsp;Renews: ${renewsLocal ?: "--"}")
            } else {
                tooltipBuilder.append("&nbsp;&nbsp;No data")
            }
            tooltipBuilder.append("<br><br>")

            // Discounted Tool Calls section (toolCalls)
            tooltipBuilder.append("<b>Discounted Tool Calls</b><br>")
            if (info.toolCalls != null) {
                val remaining = calculateRemaining(info.toolCalls.requests, info.toolCalls.limit)
                tooltipBuilder.append("&nbsp;&nbsp;Used: ${formatNumber(info.toolCalls.requests) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Limit: ${formatNumber(info.toolCalls.limit) ?: "--"}<br>")
                tooltipBuilder.append("&nbsp;&nbsp;Remaining: ${formatNumber(remaining) ?: "--"}<br>")
                val renewsLocal = QuotaService.formatToLocalTime(info.toolCalls.renewsAt)
                tooltipBuilder.append("&nbsp;&nbsp;Renews: ${renewsLocal ?: "--"}")
            } else {
                tooltipBuilder.append("&nbsp;&nbsp;No data")
            }

            tooltipBuilder.append("</html>")
            panel.toolTipText = tooltipBuilder.toString()
        }
        statusBar?.updateWidget(ID())
    }

    private fun calculateRemaining(requests: Double?, limit: Double?): Double? {
        if (requests == null || limit == null) return null
        return limit - requests
    }

    private fun isQuotaLow(section: QuotaSection?): Boolean {
        if (section == null) return false
        val requests = section.requests ?: return false
        val limit = section.limit ?: return false
        if (limit <= 0) return false
        val remaining = limit - requests
        val percentage = remaining / limit
        return percentage < 0.10 // Less than 10% remaining
    }

    private fun formatNumber(value: Double?): String? {
        if (value == null) return null
        return if (value == value.toLong().toDouble()) {
            value.toLong().toString()
        } else {
            value.toString()
        }
    }
}
