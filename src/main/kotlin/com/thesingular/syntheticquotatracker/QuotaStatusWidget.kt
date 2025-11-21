package com.thesingular.syntheticquotatracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.project.Project
import com.intellij.openapi.util.Disposer
import com.intellij.openapi.wm.CustomStatusBarWidget
import com.intellij.openapi.wm.StatusBar
import com.intellij.openapi.wm.StatusBarWidget
import com.intellij.openapi.wm.StatusBarWidgetFactory
import com.intellij.openapi.options.ShowSettingsUtil
import java.awt.Cursor
import javax.swing.JLabel
import javax.swing.JPanel

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
    private val label = JLabel("Synthetic: --/--")
    private val panel = JPanel().apply {
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
        if (info == null) {
            label.text = "Synthetic: --/--"
            panel.toolTipText = "Synthetic quota (no data)"
        } else {
            val reqStr = info.requests?.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.2f", it) } ?: "--"
            val limStr = info.limit?.let { if (it % 1.0 == 0.0) it.toInt().toString() else String.format("%.2f", it) } ?: "--"
            label.text = "Synthetic: $reqStr/$limStr"
            panel.toolTipText = info.renewsAt?.let { "Renews at: $it" } ?: "Synthetic quota"
        }
        statusBar?.updateWidget(ID())
    }
}
