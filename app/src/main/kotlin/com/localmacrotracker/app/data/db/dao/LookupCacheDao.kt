package com.localmacrotracker.app.data.db.dao

import androidx.room.*
import com.localmacrotracker.app.data.db.entities.LookupCacheEntity

@Dao
interface LookupCacheDao {

    @Query("""
        SELECT * FROM lookup_cache
        WHERE normalizedQuery = :query AND providerName = :provider AND expiresAt > :now
        LIMIT 1
    """)
    suspend fun getCached(
        query: String,
        provider: String,
        now: Long = System.currentTimeMillis()
    ): LookupCacheEntity?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insertOrUpdate(cache: LookupCacheEntity)

    @Query("DELETE FROM lookup_cache WHERE expiresAt <= :now")
    suspend fun deleteExpired(now: Long = System.currentTimeMillis())

    @Query("DELETE FROM lookup_cache")
    suspend fun clearAll()
}
