package com.example.ui.util

import com.example.data.model.StoreSettings
import java.security.MessageDigest
import java.security.SecureRandom
import java.util.Base64

object PinSecurityManager {
    const val MAX_FAILED_ATTEMPTS = 5
    const val LOCKOUT_DURATION_MS = 30_000L

    private val WEAK_PINS = setOf(
        "1234", "0000", "1111", "2222", "3333", "4444", "5555",
        "6666", "7777", "8888", "9999", "4321", "12345", "123456",
        "00000", "000000", "654321", "54321"
    )

    fun generateSalt(): String {
        val bytes = ByteArray(16)
        SecureRandom().nextBytes(bytes)
        return Base64.getEncoder().encodeToString(bytes)
    }

    fun hashPin(pin: String, salt: String): String {
        val digest = MessageDigest.getInstance("SHA-256")
        val input = "$salt:${pin.trim()}".toByteArray(Charsets.UTF_8)
        val hashed = digest.digest(input)
        return Base64.getEncoder().encodeToString(hashed)
    }

    fun verifyHash(pin: String, salt: String, expectedHash: String): Boolean {
        if (pin.isBlank() || salt.isBlank() || expectedHash.isBlank()) return false
        val actualBytes = hashPin(pin, salt).toByteArray(Charsets.UTF_8)
        val expectedBytes = expectedHash.toByteArray(Charsets.UTF_8)
        return MessageDigest.isEqual(actualBytes, expectedBytes)
    }

    fun validatePinStrength(pin: String): String? {
        val trimmed = pin.trim()
        if (trimmed.length < 4) {
            return "يجب أن يتكون رمز المدير (PIN) من 4 أرقام على الأقل."
        }
        if (!trimmed.all { it.isDigit() }) {
            return "يجب أن يحتوي رمز المدير (PIN) على أرقام فقط."
        }
        if (trimmed in WEAK_PINS || trimmed.toSet().size <= 1) {
            return "الرمز ضعيف جداً (مثل 1234 أو أرقام مكررة). يرجى اختيار رمز أقوى."
        }
        return null
    }

    fun isPinConfigured(settings: StoreSettings?): Boolean {
        if (settings == null) return false
        if (settings.managerPinHash.isNotBlank() && settings.managerPinSalt.isNotBlank()) return true
        return settings.managerPin.isNotBlank() && settings.managerPin != "1234"
    }

    fun verifyPinAgainstSettings(pin: String, settings: StoreSettings): Boolean {
        val trimmed = pin.trim()
        if (trimmed.isBlank()) return false
        if (settings.managerPinHash.isNotBlank() && settings.managerPinSalt.isNotBlank()) {
            return verifyHash(trimmed, settings.managerPinSalt, settings.managerPinHash)
        }
        // Legacy migration compatibility: only accept non-empty non-default legacy PIN if hash not yet populated
        return settings.managerPin.isNotBlank() && settings.managerPin != "1234" && trimmed == settings.managerPin.trim()
    }

    fun withNewPin(settings: StoreSettings, rawPin: String): StoreSettings {
        val error = validatePinStrength(rawPin)
        if (error != null) throw IllegalArgumentException(error)
        val salt = generateSalt()
        val hash = hashPin(rawPin.trim(), salt)
        return settings.copy(
            managerPin = "",
            managerPinHash = hash,
            managerPinSalt = salt,
            mustChangePin = false,
            pinFailedAttempts = 0,
            pinLockUntilTimestamp = 0L
        )
    }
}
