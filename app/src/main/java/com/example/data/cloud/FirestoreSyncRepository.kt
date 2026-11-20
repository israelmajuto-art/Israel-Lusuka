package com.example.data.cloud

import android.content.Context
import com.example.R
import com.example.domain.BusinessSettings
import com.example.domain.MobileMoneyNetwork
import com.example.domain.OpeningBalanceRecord
import com.example.domain.TransactionRecord
import com.google.firebase.Firebase
import com.google.firebase.auth.FirebaseAuth
import com.google.firebase.auth.auth
import com.google.firebase.firestore.DocumentSnapshot
import com.google.firebase.firestore.FirebaseFirestore
import com.google.firebase.firestore.FirebaseFirestoreException
import com.google.firebase.firestore.Query
import com.google.firebase.firestore.snapshots
import kotlinx.coroutines.CancellationException
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.emitAll
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.tasks.await

data class CloudRestorePayload(
    val profile: UserBusinessProfile?,
    val transactions: List<TransactionRecord>,
    val openingBalances: List<OpeningBalanceRecord>
)

class FirestoreSyncRepository(
    private val db: FirebaseFirestore,
    private val auth: FirebaseAuth = Firebase.auth
) {
    constructor(context: Context) : this(
        FirebaseFirestore.getInstance(
            context.applicationContext.getString(R.string.firestore_database_id)
        )
    )

    private fun requireUserId(): String {
        val uid = auth.currentUser?.uid
        check(!uid.isNullOrBlank()) { "User must be signed in with Google before accessing Firestore." }
        return uid
    }

    fun observeCloudTransactions(userId: String): Flow<List<CloudTransactionRecord>> = flow {
        require(userId.isNotBlank()) { "User ID cannot be blank when observing Firestore." }
        val uid = requireUserId()
        check(uid == userId) { "Signed-in user changed; refusing cross-user stream." }
        val path = "users/$userId/transactions"
        emitAll(
            db.collection("users").document(userId).collection("transactions")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .snapshots()
                .map { snapshot ->
                    snapshot.toObjects(
                        CloudTransactionRecord::class.java,
                        DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                    )
                }
                .catch { error ->
                    val firestoreError = generateSequence(error) { it.cause }
                        .filterIsInstance<FirebaseFirestoreException>()
                        .firstOrNull()
                    if (firestoreError == null && error is CancellationException) throw error
                    val exceptionToLog = firestoreError ?: (error as? Exception)
                    if (exceptionToLog != null && auth.currentUser?.uid == userId) {
                        handleFirestoreError(exceptionToLog, OperationType.LIST, path)
                    }
                    throw error
                }
        )
    }

    suspend fun upsertUserProfile(settings: BusinessSettings, email: String?): Result<Unit> {
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
            docPath = docRef.path
            val profile = UserBusinessProfile(
                userId = uid,
                businessName = settings.businessName,
                primaryLanguage = settings.primaryLanguage.code,
                currencyCode = settings.currencyCode,
                email = email
            )
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(profile.toUpdateMap()).await()
            } else {
                docRef.set(profile.toCreateMap(uid)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncTransaction(tx: TransactionRecord): Result<Unit> {
        val docId = CloudTransactionRecord.buildDocId(tx)
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/transactions/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("transactions").document(docId)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(CloudTransactionRecord.toUpdateMap(tx)).await()
            } else {
                docRef.set(CloudTransactionRecord.toCreateMap(uid, tx)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncOpeningBalance(opening: OpeningBalanceRecord): Result<Unit> {
        if (opening.network !in MobileMoneyNetwork.supportedNetworks) {
            return Result.failure(IllegalArgumentException("Unsupported network for opening balance"))
        }
        val docId = opening.network.code
        var docPath = "users/${auth.currentUser?.uid ?: "unauthenticated"}/opening_balances/$docId"
        return try {
            val uid = requireUserId()
            val docRef = db.collection("users").document(uid)
                .collection("opening_balances").document(docId)
            docPath = docRef.path
            val existing = docRef.get().await()
            if (existing.exists()) {
                docRef.update(CloudOpeningBalance.toUpdateMap(opening)).await()
            } else {
                docRef.set(CloudOpeningBalance.toCreateMap(uid, opening)).await()
            }
            Result.success(Unit)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, docPath)
            Result.failure(e)
        }
    }

    suspend fun syncAllFromLocal(
        userId: String,
        settings: BusinessSettings,
        email: String?,
        transactions: List<TransactionRecord>,
        openingBalances: List<OpeningBalanceRecord>
    ): Result<Int> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user sync." }
            upsertUserProfile(settings, email).getOrThrow()
            openingBalances.forEach { ob ->
                if (ob.network in MobileMoneyNetwork.supportedNetworks) {
                    syncOpeningBalance(ob).getOrThrow()
                }
            }
            var syncedCount = 0
            transactions.forEach { tx ->
                syncTransaction(tx).getOrThrow()
                syncedCount++
            }
            Result.success(syncedCount)
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.WRITE, path)
            Result.failure(e)
        }
    }

    suspend fun restoreFromCloud(userId: String): Result<CloudRestorePayload> {
        val path = "users/$userId"
        return try {
            val uid = requireUserId()
            check(uid == userId) { "Signed-in user changed; refusing cross-user restore." }

            val userDoc = db.collection("users").document(uid).get().await()
            val profile = if (userDoc.exists()) {
                userDoc.toObject(
                    UserBusinessProfile::class.java,
                    DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
                )
            } else {
                null
            }

            val txSnap = db.collection("users").document(uid)
                .collection("transactions")
                .orderBy("timestampEpochMs", Query.Direction.DESCENDING)
                .get()
                .await()
            val txs = txSnap.toObjects(
                CloudTransactionRecord::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toDomain() }

            val obSnap = db.collection("users").document(uid)
                .collection("opening_balances")
                .get()
                .await()
            val openings = obSnap.toObjects(
                CloudOpeningBalance::class.java,
                DocumentSnapshot.ServerTimestampBehavior.ESTIMATE
            ).map { it.toDomain() }

            Result.success(CloudRestorePayload(profile, txs, openings))
        } catch (e: CancellationException) {
            throw e
        } catch (e: Exception) {
            handleFirestoreError(e, OperationType.LIST, path)
            Result.failure(e)
        }
    }
}
