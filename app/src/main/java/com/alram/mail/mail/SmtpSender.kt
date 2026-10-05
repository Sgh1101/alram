package com.alram.mail.mail

import com.alram.mail.core.MailContent
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.net.ConnectException
import java.net.SocketTimeoutException
import java.net.UnknownHostException
import java.util.Date
import java.util.Properties
import javax.mail.Authenticator
import javax.mail.AuthenticationFailedException
import javax.mail.Message
import javax.mail.MessagingException
import javax.mail.PasswordAuthentication
import javax.mail.Session
import javax.mail.Transport
import javax.mail.internet.InternetAddress
import javax.mail.internet.MimeBodyPart
import javax.mail.internet.MimeMessage
import javax.mail.internet.MimeMultipart

class MailException(val userMessage: String, val isAuth: Boolean, cause: Throwable?) : Exception(userMessage, cause)

/** Gmail SMTP(465/SSL)로 메일을 보낸다. 비밀번호는 Google 계정의 "앱 비밀번호"여야 한다. */
class SmtpSender {
    suspend fun send(
        username: String,
        appPassword: String,
        recipients: List<String>,
        content: MailContent,
        extraHeaders: Map<String, String> = emptyMap(),
    ): Result<Unit> = withContext(Dispatchers.IO) {
        try {
            // android-mail 이 mailcap(MIME 핸들러)을 앱 클래스로더에서 찾도록 맞춘다.
            Thread.currentThread().contextClassLoader = SmtpSender::class.java.classLoader

            val props = Properties().apply {
                put("mail.smtp.host", "smtp.gmail.com")
                put("mail.smtp.port", "465")
                put("mail.smtp.ssl.enable", "true")
                put("mail.smtp.auth", "true")
                put("mail.smtp.connectiontimeout", "15000")
                put("mail.smtp.timeout", "20000")
                put("mail.smtp.writetimeout", "20000")
            }
            val session = Session.getInstance(props, object : Authenticator() {
                override fun getPasswordAuthentication() = PasswordAuthentication(username, appPassword)
            })
            val msg = MimeMessage(session).apply {
                setFrom(InternetAddress(username, "Alram Mail", "UTF-8"))
                setRecipients(Message.RecipientType.TO, recipients.map { InternetAddress(it) }.toTypedArray())
                setSubject(content.subject, "UTF-8")
                sentDate = Date()
                extraHeaders.forEach { (k, v) -> setHeader(k, v) }
                val alt = MimeMultipart("alternative")
                alt.addBodyPart(MimeBodyPart().apply { setText(content.text, "UTF-8") })
                alt.addBodyPart(MimeBodyPart().apply { setContent(content.html, "text/html; charset=UTF-8") })
                setContent(alt)
            }
            Transport.send(msg)
            Result.success(Unit)
        } catch (t: Throwable) {
            Result.failure(classify(t))
        }
    }

    private fun classify(t: Throwable): MailException {
        val chain = generateSequence(t) { it.cause }.toList()
        return when {
            chain.any { it is AuthenticationFailedException } ->
                MailException("Gmail 로그인 실패: 앱 비밀번호(16자리)와 주소를 확인해 주세요.", true, t)
            chain.any { it is UnknownHostException || it is ConnectException } ->
                MailException("인터넷에 연결할 수 없어요.", false, t)
            chain.any { it is SocketTimeoutException } ->
                MailException("Gmail 서버 응답이 늦어요. 잠시 후 다시 시도합니다.", false, t)
            t is javax.mail.internet.AddressException ->
                MailException("수신 주소 형식이 올바르지 않아요.", true, t)
            t is MessagingException ->
                MailException("메일 전송 오류: ${t.message?.take(120) ?: t.javaClass.simpleName}", false, t)
            else -> MailException("알 수 없는 오류: ${t.message?.take(120) ?: t.javaClass.simpleName}", false, t)
        }
    }
}
