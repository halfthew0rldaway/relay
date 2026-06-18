package dev.bleu.relay.data.db

import androidx.room.*
import kotlinx.coroutines.flow.Flow

@Dao
interface TransferDao {

    @Query("SELECT * FROM transfer_records ORDER BY timestampMs DESC")
    fun observeAll(): Flow<List<TransferRecord>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(record: TransferRecord)

    @Query("DELETE FROM transfer_records")
    suspend fun deleteAll()
}
