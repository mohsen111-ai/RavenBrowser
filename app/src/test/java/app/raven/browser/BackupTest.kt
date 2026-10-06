package app.raven.browser

import app.raven.browser.data.Backup
import org.junit.Assert.assertArrayEquals
import org.junit.Assert.assertNotEquals
import org.junit.Assert.assertThrows
import org.junit.Test

/** The backup file: what goes in comes out with the right password, and nothing comes out without it. */
class BackupTest {
    private val contents = """{"tabs":[{"url":"https://example.org/"}],"history":[],"note":"ravens ü 🌙"}""".toByteArray()

    @Test fun sealedThenOpened() {
        val file = Backup.seal(contents, "night owl 42".toCharArray())
        assertArrayEquals(contents, Backup.open(file, "night owl 42".toCharArray()))
    }

    @Test fun wrongPassword() {
        val file = Backup.seal(contents, "night owl 42".toCharArray())
        assertThrows(Backup.WrongPassword::class.java) { Backup.open(file, "night owl 43".toCharArray()) }
    }

    @Test fun changedFile() {
        val file = Backup.seal(contents, "night owl 42".toCharArray())
        file[file.size - 5] = (file[file.size - 5] + 1).toByte()
        assertThrows(Backup.WrongPassword::class.java) { Backup.open(file, "night owl 42".toCharArray()) }
    }

    @Test fun notABackup() {
        assertThrows(Backup.NotABackup::class.java) { Backup.open("hello, this is a text file".toByteArray(), "x".toCharArray()) }
    }

    @Test fun neverTheSameTwice() {
        // A fresh salt and nonce each time: the same backup never makes the same file.
        assertNotEquals(Backup.seal(contents, "pw".toCharArray()).toList(), Backup.seal(contents, "pw".toCharArray()).toList())
    }
}
