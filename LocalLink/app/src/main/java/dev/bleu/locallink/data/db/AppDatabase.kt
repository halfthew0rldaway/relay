package dev.bleu.locallink.data.db

import android.content.Context
import androidx.room.*
import dev.bleu.locallink.data.model.DeviceType

@TypeConverters(Converters::class)
@Database(
    entities = [TransferRecord::class, PinnedDevice::class],
    version = 2,
    exportSchema = false
)
abstract class AppDatabase : RoomDatabase() {
    abstract fun transferDao(): TransferDao
    abstract fun pinnedDeviceDao(): PinnedDeviceDao

    companion object {
        @Volatile private var INSTANCE: AppDatabase? = null

        fun getInstance(context: Context): AppDatabase =
            INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    AppDatabase::class.java,
                    "locallink.db"
                )
                    .fallbackToDestructiveMigration()
                    .build().also { INSTANCE = it }
            }
    }
}

class Converters {
    @TypeConverter fun directionToString(v: TransferDirection): String = v.name
    @TypeConverter fun stringToDirection(v: String): TransferDirection = TransferDirection.valueOf(v)
    @TypeConverter fun statusToString(v: TransferStatus): String = v.name
    @TypeConverter fun stringToStatus(v: String): TransferStatus = TransferStatus.valueOf(v)
    @TypeConverter fun deviceTypeToString(v: DeviceType): String = v.name
    @TypeConverter fun stringToDeviceType(v: String): DeviceType = DeviceType.valueOf(v)
}
