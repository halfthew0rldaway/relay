package dev.bleu.relay.data.db

import androidx.room.*
import dev.bleu.relay.data.model.DeviceType
import kotlinx.coroutines.flow.Flow

@Entity(tableName = "pinned_devices")
data class PinnedDevice(
    @PrimaryKey val id: String,
    val name: String,
    val ipAddress: String,
    val port: Int,
    val type: DeviceType = DeviceType.PHONE,
    val pinnedAt: Long = System.currentTimeMillis()
)

@Dao
interface PinnedDeviceDao {
    @Query("SELECT * FROM pinned_devices ORDER BY pinnedAt DESC")
    fun observeAll(): Flow<List<PinnedDevice>>

    @Query("SELECT * FROM pinned_devices WHERE id = :id LIMIT 1")
    suspend fun getById(id: String): PinnedDevice?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(device: PinnedDevice)

    @Query("DELETE FROM pinned_devices WHERE id = :id")
    suspend fun deleteById(id: String)

    @Query("SELECT COUNT(*) FROM pinned_devices WHERE id = :id")
    suspend fun isPinned(id: String): Int
}
