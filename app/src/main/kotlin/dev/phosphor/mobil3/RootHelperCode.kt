package dev.phosphor.mobil3

import android.content.Context
import android.os.Process
import android.system.ErrnoException
import android.system.Os
import android.system.OsConstants
import java.io.File
import java.io.FileInputStream
import java.io.FileOutputStream
import java.security.MessageDigest

/** Publishes only the variant-pinned read-only DEX. No provider access occurs here. */
internal object RootHelperCode {
    private fun sha(bytes: ByteArray) = MessageDigest.getInstance("SHA-256").digest(bytes).joinToString("") { "%02x".format(it.toInt() and 255) }
    private fun directory(file: File, uid: Int) {
        try { Os.mkdir(file.path, 448) } catch (e: ErrnoException) { if (e.errno != OsConstants.EEXIST) throw e }
        val stat = Os.lstat(file.path)
        check(OsConstants.S_ISDIR(stat.st_mode) && stat.st_uid == uid && stat.st_mode and 63 == 0) { "Private helper directory identity/mode failed" }
    }
    private fun verify(file: File, uid: Int, expected: String) {
        val fd = Os.open(file.path, OsConstants.O_RDONLY or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 0)
        FileInputStream(fd).use { input ->
            val stat = Os.fstat(fd)
            check(OsConstants.S_ISREG(stat.st_mode) && stat.st_uid == uid && stat.st_mode and 146 == 0 && stat.st_size in 1L..1048576L) { "Sealed helper inode identity/type/mode/size failed" }
            val bytes = input.readBytesBounded(1048576)
            check(sha(bytes) == expected) { "Sealed helper digest mismatch" }
        }
    }
    private fun java.io.InputStream.readBytesBounded(max: Int): ByteArray {
        val out = java.io.ByteArrayOutputStream()
        val buffer = ByteArray(4096)
        while (true) {
            val count = read(buffer)
            if (count < 0) break
            check(count > 0 && out.size() + count <= max) { "Asset size/progress limit" }
            out.write(buffer, 0, count)
        }
        return out.toByteArray()
    }
    @Synchronized fun stage(context: Context) {
        val uid = Process.myUid()
        val expected = context.assets.open("root-audio/helper.sha256").use { it.readBytesBounded(64).toString(Charsets.US_ASCII) }
        check(expected.matches(Regex("[a-f0-9]{64}"))) { "Packaged helper digest invalid" }
        val data = context.noBackupFilesDir
        val fixed = File("/data/user/${uid / 100000}/${BuildConfig.APPLICATION_ID}/no_backup")
        check(context.packageName == BuildConfig.APPLICATION_ID && data.canonicalPath == fixed.canonicalPath) { "Unsupported fixed private data placement" }
        val managed = Os.lstat(data.path)
        check(OsConstants.S_ISDIR(managed.st_mode) && managed.st_uid == uid && managed.st_mode and 2 == 0) { "Android-managed no_backup owner/type/world-write failed" }
        val base = File(data, "root-helper").also { directory(it, uid) }
        val version = File(base, BuildConfig.ROOT_AUDIO_BUILD).also { directory(it, uid) }
        val target = File(version, "helper.jar")
        if (target.exists()) { verify(target, uid, expected); return }
        val temp = File(version, "helper.jar.new")
        val fd = Os.open(temp.path, OsConstants.O_WRONLY or OsConstants.O_CREAT or OsConstants.O_EXCL or OsConstants.O_NOFOLLOW or OsConstants.O_CLOEXEC, 256)
        var published = false
        try {
            FileOutputStream(fd).use { out ->
                Os.fchmod(fd, 256) // Read-only before writing through the exclusive already-open handle.
                val bytes = context.assets.open("root-audio/helper.jar").use { it.readBytesBounded(1048576) }
                check(sha(bytes) == expected) { "Bundled DEX digest mismatch" }
                out.write(bytes)
                out.fd.sync()
            }
            verify(temp, uid, expected)
            check(!target.exists()) { "Unexpected concurrent helper publication" }
            Os.rename(temp.path, target.path)
            published = true
            verify(target, uid, expected)
        } finally {
            if (!published) runCatching { Os.remove(temp.path) }
        }
    }
}
