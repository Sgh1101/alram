package com.alram.mail.agent

import android.content.Context
import android.net.ConnectivityManager
import android.net.Network
import android.os.Build
import com.alram.mail.core.AgentItem
import com.alram.mail.core.AgentPayload
import com.alram.mail.core.Backoff
import com.alram.mail.data.AgentQueueEntity
import com.alram.mail.data.AlramDb
import com.alram.mail.data.SecretStore
import com.alram.mail.data.SettingsRepository
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
import java.util.UUID

data class AgentState(
    val lastError: String? = null,
    val lastOkAt: Long = 0L,
    val sending: Boolean = false,
)

/**
 * 에이전트 대기열(agent_queue)을 서버로 올리는 루프. 메일 [com.alram.mail.engine.Dispatcher] 와 같은 방식으로
 * 새 알림/네트워크 복구 때 깨어나고, 실패하면 지수 백오프로 재시도한다. 메일과 달리 묶지 않고 바로 보낸다.
 */
class AgentDispatcher(
    private val context: Context,
    private val db: AlramDb,
    private val settings: SettingsRepository,
    private val token: SecretStore,
    private val sender: AgentSender,
    private val scope: CoroutineScope,
) {
    private val wake = Channel<Unit>(Channel.CONFLATED)
    private val lock = Mutex()
    private var job: Job? = null
    private var networkRegistered = false
    @Volatile private var lastNetworkRetry = 0L

    private val _state = MutableStateFlow(AgentState())
    val state: StateFlow<AgentState> = _state.asStateFlow()

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
                val next = db.agentQueue().nextDueAt()
                val wait = if (next == null) Long.MAX_VALUE else next - System.currentTimeMillis()
                if (wait <= 0) flush() else wait
            } catch (t: Throwable) {
                _state.value = _state.value.copy(lastError = "내부 오류: ${t.message}")
                30_000L
            }
            if (hold == null) {
                delay(50)
                continue
            }
            if (hold == Long.MAX_VALUE) wake.receive() else withTimeoutOrNull(hold) { wake.receive() }
        }
    }

    /** 보낼 수 있는 만큼 보낸다. 진행했으면 null, 막혔으면 다시 볼 때까지의 대기(ms). */
    private suspend fun flush(): Long? = lock.withLock {
        val prefs = settings.current()
        val url = prefs.agentUrl.trim()
        val secret = token.load()
        if (!prefs.core.agentEnabled || url.isEmpty() || secret.isNullOrEmpty()) {
            // 꺼져 있으면 쌓인 항목은 그대로 두고, 다시 켜질 때(poke) 보낸다.
            if (prefs.core.agentEnabled) _state.value = _state.value.copy(lastError = "에이전트 서버가 연결되지 않았어요.")
            return@withLock Long.MAX_VALUE
        }
        val rows = db.agentQueue().due(System.currentTimeMillis(), BATCH)
        if (rows.isEmpty()) return@withLock 1_000L

        val json = AgentPayload.encode(
            deviceId = deviceId(),
            deviceName = deviceName(),
            items = rows.map { AgentItem(itemId(it), it.toCaptured()) },
            zone = ZoneId.systemDefault(),
            now = System.currentTimeMillis(),
        )
        _state.value = _state.value.copy(sending = true)
        try {
            val result = sender.post(url, secret, json)
            val ids = rows.map { it.id }
            if (result.isSuccess) {
                val now = System.currentTimeMillis()
                db.agentQueue().markSent(ids, now)
                _state.value = _state.value.copy(lastError = null, lastOkAt = now)
                return@withLock null
            }
            val e = result.exceptionOrNull() as? AgentException
            val message = e?.userMessage ?: "전송 실패"
            _state.value = _state.value.copy(lastError = message)
            if (e?.permanent == true) {
                db.agentQueue().markFailed(ids, message)
                return@withLock null
            }
            val nextAttempt = rows.maxOf { it.attempts } + 1
            val retryAt = System.currentTimeMillis() + Backoff.delayMs(nextAttempt)
            db.agentQueue().markRetry(ids, retryAt, message)
            db.agentQueue().failExhausted(Backoff.MAX_ATTEMPTS)
            // 다음 항목들도 같은 이유로 실패할 테니 이번 라운드는 여기서 멈춘다.
            Backoff.delayMs(nextAttempt)
        } finally {
            _state.value = _state.value.copy(sending = false)
        }
    }

    /** 연결 화면의 "연결 테스트". 빈 목록을 보내 주소와 토큰만 확인한다. */
    suspend fun test(url: String, secret: String): Result<Unit> {
        val json = AgentPayload.encode(deviceId(), deviceName(), emptyList(), ZoneId.systemDefault(), System.currentTimeMillis())
        return sender.post(url, secret, json)
    }

    private suspend fun deviceId(): String {
        val cur = settings.current().agentDeviceId
        if (cur.isNotEmpty()) return cur
        val id = UUID.randomUUID().toString().replace("-", "").take(16)
        settings.update { if (it.agentDeviceId.isEmpty()) it.copy(agentDeviceId = id) else it }
        return settings.current().agentDeviceId
    }

    private fun deviceName(): String = listOf(Build.MANUFACTURER, Build.MODEL)
        .filter { !it.isNullOrBlank() }.joinToString(" ").ifBlank { "Android" }

    /** 서버에서 같은 알림을 두 번 저장하지 않도록 쓰는 고유 키. DB 를 지워도 겹치지 않게 생성 시각을 섞는다. */
    private suspend fun itemId(e: AgentQueueEntity) = "${deviceId()}-${e.createdAt}-${e.id}"

    private fun registerNetworkCallback() {
        if (networkRegistered) return
        networkRegistered = true
        runCatching {
            val cm = context.getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager
            cm.registerDefaultNetworkCallback(object : ConnectivityManager.NetworkCallback() {
                override fun onAvailable(network: Network) {
                    val now = System.currentTimeMillis()
                    if (now - lastNetworkRetry < 30_000L) return
                    lastNetworkRetry = now
                    scope.launch {
                        db.agentQueue().retryPendingNow(System.currentTimeMillis())
                        wake.trySend(Unit)
                    }
                }
            })
        }
    }

    private companion object {
        const val BATCH = 50
    }
}
