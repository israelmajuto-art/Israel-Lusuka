package com.example.data

import android.content.Context
import android.os.Build
import androidx.room.Database
import androidx.room.Room
import androidx.room.RoomDatabase
import androidx.room.migration.Migration
import androidx.sqlite.db.SupportSQLiteDatabase
import net.zetetic.database.sqlcipher.SupportOpenHelperFactory

@Database(
    entities = [
        TransactionEntity::class,
        SmsArchiveEntity::class,
        WalletEntity::class,
        OpeningBalanceEntity::class,
        NetworkFormatEntity::class,
        BusinessSettingsEntity::class,
        AuditLogEntity::class
    ],
    version = 3,
    exportSchema = true
)
abstract class MauzoDatabase : RoomDatabase() {
    abstract fun transactionDao(): TransactionDao
    abstract fun smsArchiveDao(): SmsArchiveDao
    abstract fun walletDao(): WalletDao
    abstract fun openingBalanceDao(): OpeningBalanceDao
    abstract fun networkFormatDao(): NetworkFormatDao
    abstract fun businessSettingsDao(): BusinessSettingsDao
    abstract fun auditLogDao(): AuditLogDao

    companion object {
        const val DB_NAME = "mauzo_secure_ledger.db"

        val MIGRATION_1_2: Migration = object : Migration(1, 2) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE transactions ADD COLUMN reconciliationNote TEXT")
                db.execSQL("ALTER TABLE business_settings ADD COLUMN lastAutoBackupStatus TEXT")
            }
        }

        val MIGRATION_2_3: Migration = object : Migration(2, 3) {
            override fun migrate(db: SupportSQLiteDatabase) {
                db.execSQL("ALTER TABLE business_settings ADD COLUMN smsNotificationsEnabled INTEGER NOT NULL DEFAULT 1")
                db.execSQL("ALTER TABLE business_settings ADD COLUMN simSlotFilter INTEGER NOT NULL DEFAULT 0")
            }
        }

        fun createEncrypted(context: Context, passphraseBytes: ByteArray): MauzoDatabase {
            val builder = Room.databaseBuilder(
                context.applicationContext,
                MauzoDatabase::class.java,
                DB_NAME
            ).addMigrations(MIGRATION_1_2, MIGRATION_2_3)

            val isRobolectric = Build.FINGERPRINT.equals("robolectric", ignoreCase = true)
            if (!isRobolectric) {
                System.loadLibrary("sqlcipher")
                val factory = SupportOpenHelperFactory(passphraseBytes)
                builder.openHelperFactory(factory)
            }
            return builder.build()
        }

        fun createInMemoryForTest(context: Context): MauzoDatabase =
            Room.inMemoryDatabaseBuilder(context, MauzoDatabase::class.java)
                .allowMainThreadQueries()
                .addMigrations(MIGRATION_1_2, MIGRATION_2_3)
                .build()
    }
}
