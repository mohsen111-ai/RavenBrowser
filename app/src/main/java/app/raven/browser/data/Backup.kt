package app.raven.browser.data

import java.io.ByteArrayInputStream
import java.io.ByteArrayOutputStream
import java.security.SecureRandom
import java.util.zip.GZIPInputStream
import java.util.zip.GZIPOutputStream
import javax.crypto.AEADBadTagException
import javax.crypto.Cipher
import javax.crypto.SecretKeyFactory
import javax.crypto.spec.GCMParameterSpec
import javax.crypto.spec.PBEKeySpec
import javax.crypto.spec.SecretKeySpec

/**
 * Raven's backup file: tabs, history, bookmarks, home sites, settings and profiles in one file, locked with a
 * password you choose. Sign-ins to websites and the VPN's location files (they hold private keys) are never in it.
 *
 * The file: "RAVENBAK" and a version byte, then the salt (16 bytes) and nonce (12 bytes), then the backup (JSON,
 * gzipped) sealed with AES-256-GCM under a key made from the password with PBKDF2-SHA256 (210 000 rounds). The
 * seal also covers the start of the file, so nothing in it can be changed unnoticed.
 */
object Backup {
    private val MAGIC = "RAVENBAK".toByteArray()
    private const val VERSION: Byte = 1
    private const val ROUNDS = 210_000
    const val EXTENSION = "raven"

    class NotABackup : Exception("This isn't a Raven backup")
    class WrongPassword : Exception("Wrong password")

    fun seal(plain: ByteArray, password: CharArray): ByteArray {
        val random = SecureRandom()
        val salt = ByteArray(16).also(random::nextBytes)
        val nonce = ByteArray(12).also(random::nextBytes)
        val head = MAGIC + VERSION + salt + nonce
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.ENCRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(head)
        return head + cipher.doFinal(gzip(plain))
    }

    fun open(file: ByteArray, password: CharArray): ByteArray {
        val headSize = MAGIC.size + 1 + 16 + 12
        if (file.size < headSize + 16 || !file.copyOfRange(0, MAGIC.size).contentEquals(MAGIC) || file[MAGIC.size] != VERSION) throw NotABackup()
        val salt = file.copyOfRange(MAGIC.size + 1, MAGIC.size + 17)
        val nonce = file.copyOfRange(MAGIC.size + 17, headSize)
        val cipher = Cipher.getInstance("AES/GCM/NoPadding")
        cipher.init(Cipher.DECRYPT_MODE, key(password, salt), GCMParameterSpec(128, nonce))
        cipher.updateAAD(file.copyOfRange(0, headSize))
        val sealed = try {
            cipher.doFinal(file, headSize, file.size - headSize)
        } catch (e: AEADBadTagException) {
            throw WrongPassword()
        }
        return gunzip(sealed)
    }

    private fun key(password: CharArray, salt: ByteArray): SecretKeySpec {
        val spec = PBEKeySpec(password, salt, ROUNDS, 256)
        try {
            return SecretKeySpec(SecretKeyFactory.getInstance("PBKDF2WithHmacSHA256").generateSecret(spec).encoded, "AES")
        } finally {
            spec.clearPassword()
        }
    }

    private fun gzip(b: ByteArray): ByteArray = ByteArrayOutputStream().also { out -> GZIPOutputStream(out).use { it.write(b) } }.toByteArray()
    private fun gunzip(b: ByteArray): ByteArray = GZIPInputStream(ByteArrayInputStream(b)).use { it.readBytes() }
}
