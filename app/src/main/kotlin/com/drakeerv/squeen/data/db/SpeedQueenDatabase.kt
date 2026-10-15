package com.drakeerv.squeen.data.db

import androidx.room.Dao
import androidx.room.Database
import androidx.room.Entity
import androidx.room.PrimaryKey
import androidx.room.Query
import androidx.room.RoomDatabase
import androidx.room.Upsert
import kotlinx.coroutines.flow.Flow

/**
 * Tiny read-through cache of raw JSON:API payloads. Keeping the original
 * payload (instead of normalised tables) means one mapper is authoritative and
 * the cache round-trips perfectly.
 */
@Entity(tableName = "payload_cache")
data class PayloadCacheEntity(
    @PrimaryKey val key: String,
    val payload: String,
    val updatedAt: Long,
)

@Dao
interface PayloadCacheDao {

    @Query("SELECT payload FROM payload_cache WHERE `key` = :key")
    suspend fun get(key: String): String?

    @Query("SELECT * FROM payload_cache WHERE `key` = :key")
    suspend fun getEntity(key: String): PayloadCacheEntity?

    @Query("SELECT payload FROM payload_cache WHERE `key` = :key")
    fun observe(key: String): Flow<String?>

    @Upsert
    suspend fun put(entity: PayloadCacheEntity)

    @Query("DELETE FROM payload_cache WHERE `key` = :key")
    suspend fun delete(key: String)

    @Query("DELETE FROM payload_cache WHERE updatedAt < :cutoff")
    suspend fun deleteOlderThan(cutoff: Long)
}

@Database(entities = [PayloadCacheEntity::class], version = 1, exportSchema = false)
abstract class SpeedQueenDatabase : RoomDatabase() {
    abstract fun payloadCacheDao(): PayloadCacheDao
}
