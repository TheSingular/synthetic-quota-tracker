package com.thesingular.syntheticquotatracker

import com.intellij.openapi.Disposable
import com.intellij.openapi.application.ApplicationManager
import com.intellij.openapi.components.Service
import com.intellij.openapi.diagnostic.Logger
import com.intellij.util.concurrency.AppExecutorUtil
import com.intellij.util.messages.MessageBusConnection
import java.net.URI
import java.net.http.HttpClient
import java.net.http.HttpRequest
import java.net.http.HttpResponse
import java.time.Duration
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class QuotaInfo(
    val requests: Double?,
    val limit: Double?,
    val renewsAt: String?
)

interface QuotaListener {
    fun onQuotaUpdated(info: QuotaInfo?)
}

@Service(Service.Level.APP)
class QuotaService : Disposable {
    private val log = Logger.getInstance(QuotaService::class.java)
    private val client: HttpClient = HttpClient.newBuilder()
        .connectTimeout(Duration.ofSeconds(10))
        .build()

    @Volatile
    private var lastInfo: QuotaInfo? = null

    private var future: ScheduledFuture<*>? = null
    private var connection: MessageBusConnection? = null

    init {
        // Subscribe to settings changes to reschedule
        connection = ApplicationManager.getApplication().messageBus.connect()
        connection!!.subscribe(SettingsState.SETTINGS_CHANGED, object : SettingsChangedListener {
            override fun onSettingsChanged() {
                reschedule()
            }
        })
        // Start schedule
        reschedule()
    }

    fun getLastInfo(): QuotaInfo? = lastInfo

    fun addListener(parent: Disposable, listener: QuotaListener) {
        val conn = ApplicationManager.getApplication().messageBus.connect(parent)
        conn.subscribe(QUOTA_TOPIC, listener)
        // push current value if present
        listener.onQuotaUpdated(lastInfo)
    }

    private fun reschedule() {
        future?.cancel(false)
        val interval = (SettingsState.getInstance().intervalSeconds).coerceAtLeast(5)
        future = AppExecutorUtil.getAppScheduledExecutorService().scheduleWithFixedDelay({
            try {
                pollOnce()
            } catch (t: Throwable) {
                log.warn("Quota poll failed", t)
            }
        }, 0, interval.toLong(), TimeUnit.SECONDS)
    }

    private fun pollOnce() {
        val token = SettingsState.getInstance().apiToken?.trim().orEmpty()
        if (token.isEmpty()) {
            updateInfo(null)
            return
        }

        val req = HttpRequest.newBuilder()
            .uri(URI.create("https://api.synthetic.new/v2/quotas"))
            .timeout(Duration.ofSeconds(20))
            .header("Authorization", "Bearer $token")
            .GET()
            .build()

        val resp = client.send(req, HttpResponse.BodyHandlers.ofString())
        if (resp.statusCode() in 200..299) {
            val info = parseQuota(resp.body())
            updateInfo(info)
        } else {
            log.warn("Quota API returned status ${resp.statusCode()}")
            updateInfo(null)
        }
    }

    private fun updateInfo(info: QuotaInfo?) {
        lastInfo = info
        ApplicationManager.getApplication().messageBus.syncPublisher(QUOTA_TOPIC).onQuotaUpdated(info)
    }

    override fun dispose() {
        future?.cancel(true)
        connection?.disconnect()
    }

    companion object {
        val QUOTA_TOPIC = com.intellij.util.messages.Topic.create("SyntheticQuotaUpdated", QuotaListener::class.java)

        private val LIMIT_PATTERN = Pattern.compile("\\\"limit\\\"\\s*:\\s*([0-9]+(?:\\\\.[0-9]+)?)")
        private val REQUESTS_PATTERN = Pattern.compile("\\\"requests\\\"\\s*:\\s*([0-9]+(?:\\\\.[0-9]+)?)")
        private val RENEWS_PATTERN = Pattern.compile("\\\"renewsAt\\\"\\s*:\\s*\\\"([^\\\"]+)\\\"")

        fun parseQuota(json: String): QuotaInfo {
            val limit = LIMIT_PATTERN.matcher(json).let { m -> if (m.find()) m.group(1)?.toDoubleOrNull() else null }
            val requests = REQUESTS_PATTERN.matcher(json).let { m -> if (m.find()) m.group(1)?.toDoubleOrNull() else null }
            val renewsAt = RENEWS_PATTERN.matcher(json).let { m -> if (m.find()) m.group(1) else null }
            return QuotaInfo(requests, limit, renewsAt)
        }
    }
}
