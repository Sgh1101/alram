package com.alram.mail.agent

import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.IOException
import java.net.HttpURLConnection
import java.net.SocketTimeoutException
import java.net.URL
import java.net.UnknownHostException
import javax.net.ssl.SSLException

/**
 * @param isAuth 토큰/주소 문제라 나머지도 전부 실패할 오류
 * @param permanent 다시 보내도 같은 결과가 나올 오류(서버가 내용을 거부)
 */
class AgentException(
    val userMessage: String,
    val isAuth: Boolean = false,
    val permanent: Boolean = false,
    cause: Throwable? = null,
) : Exception(userMessage, cause)

/** 에이전트 서버의 `POST /ingest` 로 JSON 을 보낸다. 외부 라이브러리 없이 HttpURLConnection 만 쓴다. */
class AgentSender {
    suspend fun post(baseUrl: String, token: String, json: String): Result<Unit> = withContext(Dispatchers.IO) {
        val url = runCatching { URL(ingestUrl(baseUrl)) }.getOrElse {
            return@withContext Result.failure(AgentException("서버 주소 형식이 올바르지 않아요.", isAuth = true, cause = it))
        }
        if (url.protocol != "https") {
            return@withContext Result.failure(AgentException("알림 내용이 오가므로 https:// 주소만 쓸 수 있어요.", isAuth = true))
        }
        var conn: HttpURLConnection? = null
        try {
            conn = (url.openConnection() as HttpURLConnection).apply {
                requestMethod = "POST"
                connectTimeout = 15_000
                readTimeout = 20_000
                doOutput = true
                instanceFollowRedirects = false
                setRequestProperty("Content-Type", "application/json; charset=utf-8")
                setRequestProperty("Authorization", "Bearer $token")
                setRequestProperty("User-Agent", "AlramMail")
            }
            val bytes = json.toByteArray(Charsets.UTF_8)
            conn.setFixedLengthStreamingMode(bytes.size)
            conn.outputStream.use { it.write(bytes) }
            val code = conn.responseCode
            val body = runCatching {
                (if (code >= 400) conn.errorStream else conn.inputStream)?.bufferedReader()?.use { it.readText() }
            }.getOrNull().orEmpty().take(200)
            when {
                code in 200..299 -> Result.success(Unit)
                code == 401 || code == 403 ->
                    Result.failure(AgentException("서버가 토큰을 거부했어요. 기기 토큰(INGEST_TOKEN)을 확인해 주세요.", isAuth = true))
                code == 404 || code == 405 ->
                    Result.failure(AgentException("서버 주소에 /ingest 가 없어요. 주소를 확인해 주세요.", isAuth = true))
                code in 300..399 ->
                    Result.failure(AgentException("서버가 다른 주소로 넘기려 해요($code). 주소 끝까지 정확히 적어 주세요.", isAuth = true))
                code == 413 || code == 400 || code == 422 ->
                    Result.failure(AgentException("서버가 내용을 거부했어요($code) $body".trim(), permanent = true))
                else -> Result.failure(AgentException("서버 오류($code). 잠시 후 다시 시도합니다."))
            }
        } catch (t: Throwable) {
            Result.failure(classify(t))
        } finally {
            conn?.disconnect()
        }
    }

    private fun classify(t: Throwable): AgentException = when (t) {
        is UnknownHostException -> AgentException("서버를 찾을 수 없어요. 인터넷 연결이나 주소를 확인해 주세요.", cause = t)
        is SocketTimeoutException -> AgentException("서버 응답이 늦어요. 잠시 후 다시 시도합니다.", cause = t)
        is SSLException -> AgentException("보안 연결(HTTPS)에 실패했어요.", cause = t)
        is IOException -> AgentException("서버에 연결할 수 없어요: ${t.message?.take(80) ?: t.javaClass.simpleName}", cause = t)
        else -> AgentException("알 수 없는 오류: ${t.message?.take(80) ?: t.javaClass.simpleName}", cause = t)
    }

    companion object {
        /** 사용자가 기본 주소만 적어도, /ingest 까지 적어도 같은 곳으로 보낸다. */
        fun ingestUrl(base: String): String {
            val b = base.trim().trimEnd('/')
            return if (b.endsWith("/ingest")) b else "$b/ingest"
        }
    }
}
