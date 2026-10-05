package com.alram.mail.engine

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import com.alram.mail.core.Backoff
import com.alram.mail.core.MailComposer
import com.alram.mail.core.MailPlanner
import com.alram.mail.data.AlramDb
import com.alram.mail.data.MailLogEntity
import com.alram.mail.data.SecretStore
import com.alram.mail.data.SettingsRepository
import com.alram.mail.mail.MailException
import com.alram.mail.mail.SmtpSender
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Job
import kotlinx.coroutines.channels.Channel
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.asStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.sync.Mutex
import kotlinx.coroutines.sync.withLock
import kotlinx.coroutines.withTimeoutOrNull
import java.time.ZoneId

data class DispatchState(
    val lastError: String? = null,
    /** 오류는 아니지만 알려야 할 상태(예: 메일이 몰려 묶음으로 전환). */
    val notice: String? = null,
    val lastOkAt: Long = 0L,
    val sending: Boolean = false,
)

/**
 * 대기열(DB)에서 발송 시각이 된 알림을 모아 Gmail 로 보내는 루프.
 * - 다음 발송 시각까지 잠들어 있다가, 새 알림/설정 변경/네트워크 복구 때 깨어난다.
 * - 실패하면 지수 백오프로 재시도하고, 계속 실패하면 FAILED 로 표시한다.
 */
class Dispatcher(
    private val context: Context,
    private val db: AlramDb,
    private val settings: SettingsRepository,
    private val secrets: SecretStore,
    private val sender: SmtpSender,
    private val scope: CoroutineScope,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val flushLock = Mutex()
    private var job: Job? = null
    private var networkRegistered = false
    @Volatile private var lastNetworkRetry = 0L

    private val _state = MutableStateFlow(DispatchState())
    val state: StateFlow<DispatchState> = _state.asStateFlow()

    @Synchronized
    fun start() {
        if (job?.isActive == true) return
        job = scope.launch { loop() }
        registerNetworkCallback()
    }

    fun poke() {
        start()
        wake.trySend(Unit)
    }

    private suspend fun loop() {
        while (true) {
            val hold: Long? = try {
                val next = db.notifications().nextDueAt()
                // 보낼 게 없으면 "메일이 몰림" 안내는 더 이상 의미가 없다.
                if (next == null && _state.value.notice != null) _state.value = _state.value.copy(notice = null)
                val wait = if (next == null) Long.MAX_VALUE else next - System.currentTimeMillis()
                if (wait <= 0) flush() else wait
            } catch (t: Throwable) {
                _state.value = _state.value.copy(lastError = "내부 오류: ${t.message}")
                30_000L
            }
            if (hold == null) {
                delay(50) // 연속 발송 사이에 숨 고르기
                continue
            }
            if (hold == Long.MAX_VALUE) wake.receive() else withTimeoutOrNull(hold) { wake.receive() }
        }
    }

    /** 발송 가능한 항목을 보낸다. 진행했으면 null, 막혔으면 다시 시도할 때까지의 대기(ms). */
    private suspend fun flush(): Long? = flushLock.withLock {
        val prefs = settings.current()
        val core = prefs.core
        val user = prefs.gmailAddress.trim()
        val password = secrets.load()
        if (user.isEmpty() || password.isNullOrEmpty()) {
            _state.value = _state.value.copy(lastError = "Gmail 계정이 연결되지 않았어요.")
            return@withLock 60_000L
        }
        val recipients = core.recipients.ifEmpty { listOf(user) }
        val now = System.currentTimeMillis()
        val zone = ZoneId.systemDefault()

        val rows = db.notifications().due(now, 300)
        if (rows.isEmpty()) return@withLock 1_000L

        val startOfDay = java.time.LocalDate.now(zone).atStartOfDay(zone).toInstant().toEpochMilli()
        val budget = core.dailyMailCap - db.mails().countSince(startOfDay)
        if (budget <= 0) {
            _state.value = _state.value.copy(lastError = "하루 발송 한도(${core.dailyMailCap}통)에 도달했어요. 내일 이어서 보냅니다.")
            return@withLock 10 * 60_000L
        }

        // 짧은 시간에 메일이 몰리면(예상 못 한 반복 포함) 즉시 발송을 멈추고 묶음 메일로 합친다.
        val windowBudget = BURST_MAX_MAILS - db.mails().countSince(now - BURST_WINDOW_MS)
        val busyNotice = "짧은 시간에 메일이 많이 나가서 잠시 모아서 보내는 중이에요."
        if (windowBudget <= 0) {
            _state.value = _state.value.copy(notice = busyNotice)
            return@withLock 60_000L
        }
        _state.value = _state.value.copy(notice = if (windowBudget <= BURST_MAX_MAILS / 3) busyNotice else null)

        val rowById = rows.associateBy { it.id }
        val plan = MailPlanner.plan(
            rows.map { it.toQueued() },
            recipients,
            minOf(budget, windowBudget),
            core.maxItemsPerMail,
        )
        if (plan.isEmpty()) return@withLock 60_000L

        _state.value = _state.value.copy(sending = true)
        try {
            val handled = HashSet<Long>()
            for (mail in plan) {
                val content = MailComposer.compose(mail.kind, mail.items, zone, System.currentTimeMillis())
                val result = sender.send(
                    username = user,
                    appPassword = password,
                    recipients = mail.recipients,
                    content = content,
                    extraHeaders = mapOf("X-Alram-Kind" to mail.kind.name),
                )
                if (result.isSuccess) {
                    val sentAt = System.currentTimeMillis()
                    val mailId = db.mails().insert(
                        MailLogEntity(
                            sentAt = sentAt,
                            subject = content.subject,
                            itemCount = mail.itemIds.size,
                            recipients = mail.recipients.joinToString(","),
                        ),
                    )
                    db.notifications().markSent(mail.itemIds, sentAt, mailId)
                    handled += mail.itemIds
                    _state.value = _state.value.copy(lastError = null, lastOkAt = sentAt)
                } else {
                    val e = result.exceptionOrNull()
                    val message = (e as? MailException)?.userMessage ?: (e?.message ?: "전송 실패")
                    val nextAttempt = (mail.itemIds.maxOf { rowById[it]?.attempts ?: 0 }) + 1
                    val retryAt = System.currentTimeMillis() + Backoff.delayMs(nextAttempt)
                    db.notifications().markRetry(mail.itemIds, retryAt, message)
                    db.notifications().failExhausted(Backoff.MAX_ATTEMPTS)
                    handled += mail.itemIds
                    _state.value = _state.value.copy(lastError = message)
                    if ((e as? MailException)?.isAuth == true) {
                        // 로그인/주소 문제는 나머지도 전부 실패한다. Gmail 에 로그인 시도를 연달아 하지 않도록
                        // 이번 라운드의 남은 항목도 함께 미루고 멈춘다.
                        val rest = rows.map { it.id }.filterNot { it in handled }
                        if (rest.isNotEmpty()) db.notifications().postpone(rest, retryAt, message)
                        return@withLock 60_000L
                    }
                }
            }
        } finally {
            _state.value = _state.value.copy(sending = false)
        }
        null
    }

    /** 설정 화면의 "테스트 메일". 대기열과 무관하게 바로 보낸다. */
    suspend fun sendTest(address: String, appPassword: String): Result<Unit> {
        val content = MailComposer.compose(
            com.alram.mail.core.MailKind.INSTANT,
            listOf(
                com.alram.mail.core.CapturedNotification(
                    key = "test", packageName = context.packageName, appLabel = "Alram Mail",
                    title = "연결 테스트", text = "이 메일이 보이면 설정이 끝난 거예요.\n이제 폰에 오는 알림이 이 주소로 전달됩니다.",
                    postedAt = System.currentTimeMillis(),
                ),
            ),
            ZoneId.systemDefault(),
            System.currentTimeMillis(),
        )
        val recipients = settings.current().core.recipients.ifEmpty { listOf(address) }
        return sender.send(address, appPassword, recipients, content).also {
            if (it.isSuccess) _state.value = _state.value.copy(lastError = null)
        }
    }

    private companion object {
        const val BURST_WINDOW_MS = 10 * 60_000L
        const val BURST_MAX_MAILS = 30
    }

    private fun registerNetworkCallback() {
        if (networkRegistered) return
        networkRegistered = true
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    // 네트워크가 돌아오면 백오프 중인 항목도 바로 재시도한다. (연결이 깜빡여도 30초에 한 번만)
                    val now = System.currentTimeMillis()
                    if (now - lastNetworkRetry < 30_000L) return
                    lastNetworkRetry = now
                    scope.launch {
                        db.notifications().retryPendingNow(System.currentTimeMillis())
                        wake.trySend(Unit)
                    }
                }
            })
        }
    }
}
