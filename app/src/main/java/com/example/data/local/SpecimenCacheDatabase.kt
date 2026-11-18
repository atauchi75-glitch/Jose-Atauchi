package com.example.data.local

import android.content.Context
import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.Room
import androidx.room.RoomDatabase
import com.example.model.ObservationRecord
import com.squareup.moshi.Moshi
import com.squareup.moshi.kotlin.reflect.KotlinJsonAdapterFactory
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.withContext
import java.io.File

@Entity(tableName = "specimen_cache")
data class CachedSpecimenEntity(
    @PrimaryKey val id: String,
    val createdAtEpochMs: Long,
    val payloadJson: String
)

@Dao
interface SpecimenCacheDao {
    @Query("SELECT * FROM specimen_cache ORDER BY createdAtEpochMs DESC LIMIT 20")
    fun observeRecent(): Flow<List<CachedSpecimenEntity>>

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun upsert(entity: CachedSpecimenEntity)

    @Query("DELETE FROM specimen_cache WHERE id = :id")
    suspend fun deleteById(id: String)
}

@Database(entities = [CachedSpecimenEntity::class], version = 1, exportSchema = false)
abstract class SpecimenCacheDatabase : RoomDatabase() {
    abstract fun specimenCacheDao(): SpecimenCacheDao

    companion object {
        @Volatile
        private var INSTANCE: SpecimenCacheDatabase? = null

        fun getInstance(context: Context): SpecimenCacheDatabase {
            return INSTANCE ?: synchronized(this) {
                INSTANCE ?: Room.databaseBuilder(
                    context.applicationContext,
                    SpecimenCacheDatabase::class.java,
                    "fauna_gbif_cache.db"
                ).build().also { INSTANCE = it }
            }
        }
    }
}

class SpecimenLocalStore(context: Context) {
    private val dao = SpecimenCacheDatabase.getInstance(context).specimenCacheDao()
    private val moshi = Moshi.Builder().addLast(KotlinJsonAdapterFactory()).build()
    private val adapter = moshi.adapter(ObservationRecord::class.java)

    val recentObservations: Flow<List<ObservationRecord>> = dao.observeRecent().map { entities ->
        entities.mapNotNull { entity ->
            runCatching { adapter.fromJson(entity.payloadJson) }.getOrNull()
        }
    }

    suspend fun save(record: ObservationRecord) = withContext(Dispatchers.IO) {
        val json = adapter.toJson(record)
        dao.upsert(
            CachedSpecimenEntity(
                id = record.id,
                createdAtEpochMs = record.createdAtEpochMs,
                payloadJson = json
            )
        )
    }

    suspend fun delete(record: ObservationRecord) = withContext(Dispatchers.IO) {
        dao.deleteById(record.id)
        if (record.imageLocalPath.isNotBlank()) {
            runCatching { File(record.imageLocalPath).delete() }
        }
    }
}
