package com.inspiredandroid.kai.events

import com.inspiredandroid.kai.agent.AgentPreferences
import com.inspiredandroid.kai.data.NotificationRecord
import com.inspiredandroid.kai.data.SmsMessage

/** Conservative local exclusion rules, not a claim that arbitrary text can be perfectly redacted. */
object EventPrivacyPolicy {
    private val auth = Regex("\\b(?:otp|code|codigo|kodigo|password|passcode|passphrase|pin|cvv|cvc|2fa|mfa|verification|authentication|authenticator|recovery code|security code|login code|sign.in code|one.time|api[ _-]?key|access[ _-]?token|bearer|authorization)\\b", RegexOption.IGNORE_CASE)
    private val secret = Regex("sk-[A-Za-z0-9_-]{8,}|gh[pousr]_[A-Za-z0-9]{8,}|-----BEGIN|\\b(?:iban|routing number|account number|card number)\\b|(?:\\d[ -]?){12,19}", RegexOption.IGNORE_CASE)
    private val numericCode = Regex("(?<![\\w])\\p{N}{4,8}(?![\\w])")
    private val sensitiveSources = Regex("bank|wallet|authenticator|password|bitwarden|lastpass|1password|dashlane|paypal|paymaya|gcash|credential", RegexOption.IGNORE_CASE)
    fun normalize(value: String): String = value.replace(Regex("[\\u200B-\\u200F\\u202A-\\u202E\\u2060-\\u206F\\uFEFF]"), "")
        .replace(Regex("\\s+"), " ").trim()
    fun safeText(value: String): Boolean {
        if (value.length > 32_000) return false
        val text = normalize(value)
        return !auth.containsMatchIn(text) && !secret.containsMatchIn(text) && numericCode.findAll(text).none {
            val number = it.value.toIntOrNull()
            number == null || number !in 1900..2099
        }
    }
    fun notification(record: NotificationRecord): NotificationRecord? {
        if (record.id.length > 2048 || record.packageName.length > 200) return null
        if (sensitiveSources.containsMatchIn(record.packageName) ||
            !safeText(record.title + "\n" + record.text + "\n" + record.subtext + "\n" + record.appLabel + "\n" + record.preview)
        ) {
            return null
        }
        return record.copy(
            title = normalize(record.title).take(500),
            text = normalize(record.text).take(4000),
            subtext = normalize(record.subtext).take(500),
            appLabel = normalize(record.appLabel).take(200),
            category = record.category.take(80),
            preview = normalize(record.text).take(NotificationRecord.PREVIEW_CHARS),
        )
    }
    fun sms(message: SmsMessage): SmsMessage? {
        val senderSafe = Regex("[+0-9 ()-]{3,25}").matches(message.address) || safeText(message.address)
        if (!senderSafe || sensitiveSources.containsMatchIn(message.address) || !safeText(message.body + "\n" + message.preview)) return null
        val body = normalize(message.body.ifBlank { message.preview }).take(4000)
        return message.copy(body = body, preview = body.take(200), address = message.address.trim().take(80))
    }
    fun cloudNotification(record: NotificationRecord, preferences: AgentPreferences): NotificationRecord? {
        if (!preferences.notificationCloud || record.packageName !in preferences.notificationSources) return null
        return notification(record)
    }
    fun cloudSms(message: SmsMessage, preferences: AgentPreferences): SmsMessage? {
        if (!preferences.smsCloud || message.address.trim().lowercase() !in preferences.smsSources) return null
        return sms(message)
    }
    fun sameNotification(a: NotificationRecord, b: NotificationRecord): Boolean = a.packageName == b.packageName &&
        normalize(a.title).lowercase() == normalize(b.title).lowercase() && normalize(a.text).lowercase() == normalize(b.text).lowercase() && normalize(a.subtext).lowercase() == normalize(b.subtext).lowercase()
}
