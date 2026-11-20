package com.example.data

import android.content.Context
import android.net.Uri
import com.example.domain.MauzoRepository
import com.example.security.BackupCrypto
import com.example.security.KeystoreManager
import java.io.File
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class BackupRestoreManager(
    private val context: Context,
    private val repository: MauzoRepository,
    private val keystoreManager: KeystoreManager
) {

    suspend fun exportEncryptedBackupToUri(uri: Uri, passphrase: String): Result<Unit> = runCatching {
        val json = repository.exportBackupJson()
        val encrypted = BackupCrypto.encryptBackup(json, passphrase)
        context.contentResolver.openOutputStream(uri)?.use { out ->
            out.write(encrypted)
            out.flush()
        } ?: error("Imeshindwa kufungua faili la kuhifadhi chelezo")
        repository.updateBusinessSettings { it.copy(lastBackupEpochMs = System.currentTimeMillis()) }
    }

    suspend fun restoreEncryptedBackupFromUri(uri: Uri, passphrase: String): Result<Unit> = runCatching {
        val bytes = context.contentResolver.openInputStream(uri)?.use { it.readBytes() }
            ?: error("Imeshindwa kusoma faili la chelezo")
        val json = BackupCrypto.decryptBackup(bytes, passphrase)
        repository.restoreBackupJson(json).getOrThrow()
    }

    /**
     * Writes an Android Keystore-wrapped automatic backup locally in internal app storage (last 5 kept).
     */
    suspend fun writeLocalAutoBackup(): Result<File> = runCatching {
        val dir = File(context.filesDir, "backups").apply { mkdirs() }
        val json = repository.exportBackupJson()
        val wrapped = keystoreManager.encryptWithKeystore(json.toByteArray(Charsets.UTF_8))
        val stamp = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.US).format(Date())
        val outFile = File(dir, "mauzo_auto_$stamp.enc")
        outFile.writeBytes(wrapped)

        val existing = dir.listFiles { f -> f.name.startsWith("mauzo_auto_") && f.name.endsWith(".enc") }
            ?.sortedByDescending { it.lastModified() }
            ?: emptyList()
        if (existing.size > 5) {
            existing.drop(5).forEach { it.delete() }
        }
        repository.updateBusinessSettings { it.copy(lastBackupEpochMs = System.currentTimeMillis()) }
        outFile
    }
}
