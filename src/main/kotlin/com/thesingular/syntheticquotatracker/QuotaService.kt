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
import java.time.Instant
import java.time.ZoneId
import java.time.format.DateTimeFormatter
import java.util.concurrent.ScheduledFuture
import java.util.concurrent.TimeUnit
import java.util.regex.Pattern

data class QuotaSection(
    val requests: Double?,
    val limit: Double?,
    val renewsAt: String?
)

data class QuotaInfo(
    val subscription: QuotaSection?,
    val search: QuotaSection?,
    val toolCalls: QuotaSection?
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
        val interval = (SettingsState.getInstance().intervalSeconds)
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
        ApplicationManager.getApplication().invokeLater {
            ApplicationManager.getApplication().messageBus.syncPublisher(QUOTA_TOPIC).onQuotaUpdated(info)
        }
    }

    override fun dispose() {
        future?.cancel(true)
        connection?.disconnect()
    }

    companion object {
        val QUOTA_TOPIC = com.intellij.util.messages.Topic.create("SyntheticQuotaUpdated", QuotaListener::class.java)

        private val SUBSCRIPTION_PATTERN = Pattern.compile(
            """"subscription"\s*:\s*\{[^}]*"limit"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"requests"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"renewsAt"\s*:\s*"([^"]+)"""",
            Pattern.DOTALL
        )
        private val SEARCH_PATTERN = Pattern.compile(
            """"search"\s*:\s*\{[^}]*"hourly"\s*:\s*\{[^}]*"limit"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"requests"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"renewsAt"\s*:\s*"([^"]+)"""",
            Pattern.DOTALL
        )
        private val TOOLCALLS_PATTERN = Pattern.compile(
            """"toolCalls"\s*:\s*\{[^}]*"limit"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"requests"\s*:\s*(?:"([^"]*)"|([^,}\s]+))[^}]*"renewsAt"\s*:\s*"([^"]+)"""",
            Pattern.DOTALL
        )

        fun parseQuota(json: String): QuotaInfo {
            val subscription = parseSection(SUBSCRIPTION_PATTERN.matcher(json))
            val search = parseSection(SEARCH_PATTERN.matcher(json))
            val toolCalls = parseSection(TOOLCALLS_PATTERN.matcher(json))
            return QuotaInfo(subscription, search, toolCalls)
        }

        private fun parseSection(matcher: java.util.regex.Matcher): QuotaSection? {
            return if (matcher.find()) {
                val limit = (matcher.group(1) ?: matcher.group(2))?.toDoubleOrNull()
                val requests = (matcher.group(3) ?: matcher.group(4))?.toDoubleOrNull()
                val renewsAt = matcher.group(5)
                QuotaSection(requests, limit, renewsAt)
            } else null
        }

        fun formatToLocalTime(isoTimestamp: String?): String? {
            if (isoTimestamp == null) return null
            return try {
                val instant = Instant.parse(isoTimestamp)
                val localDateTime = instant.atZone(ZoneId.systemDefault())
                val dateFormat = SettingsState.getInstance().dateFormat

                when (dateFormat) {
                    SettingsState.DATE_FORMAT_LOCALE -> {
                        // Use locale-specific date/time format (short date + medium time)
                        val locale = java.util.Locale.getDefault()
                        val dateFormatter = DateTimeFormatter.ofLocalizedDate(java.time.format.FormatStyle.SHORT).withLocale(locale)
                        val timeFormatter = DateTimeFormatter.ofLocalizedTime(java.time.format.FormatStyle.MEDIUM).withLocale(locale)
                        "${localDateTime.format(timeFormatter)} ${localDateTime.format(dateFormatter)}"
                    }
                    SettingsState.DATE_FORMAT_ISO -> {
                        // ISO 8601 format
                        val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                        localDateTime.format(formatter)
                    }
                    else -> {
                        // Custom pattern
                        try {
                            val formatter = DateTimeFormatter.ofPattern(dateFormat)
                            localDateTime.format(formatter)
                        } catch (e: Exception) {
                            // Fallback to ISO if custom pattern is invalid
                            val formatter = DateTimeFormatter.ofPattern("yyyy-MM-dd HH:mm:ss")
                            localDateTime.format(formatter)
                        }
                    }
                }
            } catch (e: Exception) {
                isoTimestamp
            }
        }
    }
}
