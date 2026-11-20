package com.example.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import androidx.room.Update
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

@Dao
interface TransactionDao {
    @Query("SELECT * FROM transactions ORDER BY timestampEpochMs DESC")
    fun observeAll(): Flow<List<TransactionEntity>>

    @Query("SELECT * FROM transactions ORDER BY timestampEpochMs DESC")
    suspend fun getAllOnce(): List<TransactionEntity>

    @Query("SELECT * FROM transactions WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): TransactionEntity?

    @Query("SELECT * FROM transactions WHERE UPPER(transactionId) = UPPER(:txId) LIMIT 1")
    suspend fun getByTransactionId(txId: String): TransactionEntity?

    @Insert(onConflict = OnConflictStrategy.ABORT)
    suspend fun insert(entity: TransactionEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAllReplace(entities: List<TransactionEntity>)

    @Update
    suspend fun update(entity: TransactionEntity)

    @Query("DELETE FROM transactions")
    suspend fun deleteAll()
}

@Dao
interface SmsArchiveDao {
    @Query("SELECT * FROM sms_archive ORDER BY receivedAtEpochMs DESC")
    fun observeAll(): Flow<List<SmsArchiveEntity>>

    @Query("SELECT * FROM sms_archive ORDER BY receivedAtEpochMs DESC")
    suspend fun getAllOnce(): List<SmsArchiveEntity>

    @Query("SELECT * FROM sms_archive WHERE id = :id LIMIT 1")
    suspend fun getById(id: Long): SmsArchiveEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: SmsArchiveEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<SmsArchiveEntity>)

    @Query("UPDATE sms_archive SET relatedTransactionLocalId = :txLocalId WHERE id = :smsId")
    suspend fun linkTransaction(smsId: Long, txLocalId: Long)

    @Query("DELETE FROM sms_archive")
    suspend fun deleteAll(): Int
}

@Dao
interface WalletDao {
    @Query("SELECT * FROM wallets ORDER BY network ASC")
    fun observeAll(): Flow<List<WalletEntity>>

    @Query("SELECT * FROM wallets ORDER BY network ASC")
    suspend fun getAllOnce(): List<WalletEntity>

    @Query("SELECT * FROM wallets WHERE network = :network LIMIT 1")
    suspend fun getByNetwork(network: String): WalletEntity?

    @Upsert
    suspend fun upsert(entity: WalletEntity)

    @Upsert
    suspend fun upsertAll(entities: List<WalletEntity>)

    @Query("DELETE FROM wallets")
    suspend fun deleteAll()
}

@Dao
interface OpeningBalanceDao {
    @Query("SELECT * FROM opening_balances ORDER BY network ASC")
    fun observeAll(): Flow<List<OpeningBalanceEntity>>

    @Query("SELECT * FROM opening_balances ORDER BY network ASC")
    suspend fun getAllOnce(): List<OpeningBalanceEntity>

    @Query("SELECT * FROM opening_balances WHERE network = :network LIMIT 1")
    suspend fun getByNetwork(network: String): OpeningBalanceEntity?

    @Upsert
    suspend fun upsert(entity: OpeningBalanceEntity)

    @Upsert
    suspend fun upsertAll(entities: List<OpeningBalanceEntity>)

    @Query("DELETE FROM opening_balances")
    suspend fun deleteAll()
}

@Dao
interface NetworkFormatDao {
    @Query("SELECT * FROM network_formats ORDER BY network ASC")
    fun observeAll(): Flow<List<NetworkFormatEntity>>

    @Query("SELECT * FROM network_formats ORDER BY network ASC")
    suspend fun getAllOnce(): List<NetworkFormatEntity>

    @Upsert
    suspend fun upsert(entity: NetworkFormatEntity)

    @Upsert
    suspend fun upsertAll(entities: List<NetworkFormatEntity>)

    @Query("UPDATE network_formats SET isEnabled = :enabled, updatedAtEpochMs = :updatedAt WHERE network = :network")
    suspend fun setEnabled(network: String, enabled: Boolean, updatedAt: Long)

    @Query("UPDATE network_formats SET senderIdentifiersCsv = :senderIdsCsv, updatedAtEpochMs = :updatedAt WHERE network = :network")
    suspend fun updateSenderIdentifiers(network: String, senderIdsCsv: String, updatedAt: Long)

    @Query("DELETE FROM network_formats")
    suspend fun deleteAll()
}

@Dao
interface BusinessSettingsDao {
    @Query("SELECT * FROM business_settings WHERE id = 1 LIMIT 1")
    fun observe(): Flow<BusinessSettingsEntity?>

    @Query("SELECT * FROM business_settings WHERE id = 1 LIMIT 1")
    suspend fun getOnce(): BusinessSettingsEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: BusinessSettingsEntity)
}

@Dao
interface AuditLogDao {
    @Query("SELECT * FROM audit_logs ORDER BY timestampEpochMs DESC")
    fun observeAll(): Flow<List<AuditLogEntity>>

    @Query("SELECT * FROM audit_logs ORDER BY timestampEpochMs DESC")
    suspend fun getAllOnce(): List<AuditLogEntity>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(entity: AuditLogEntity): Long

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertAll(entities: List<AuditLogEntity>)

    @Query("DELETE FROM audit_logs")
    suspend fun deleteAll()
}
