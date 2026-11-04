package com.hqshelf.data.local.db

import androidx.room.Dao
import androidx.room.Delete
import androidx.room.Insert
import androidx.room.OnConflictStrategy
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface ShelfItemDao {
    @Query("SELECT * FROM shelf_items ORDER BY position ASC")
    suspend fun getAll(): List<ShelfItem>

    @Query("SELECT * FROM shelf_items ORDER BY position ASC")
    fun observeAll(): Flow<List<ShelfItem>>

    @Query("SELECT * FROM shelf_items WHERE position = :position")
    suspend fun getByPosition(position: Int): ShelfItem?

    @Insert(onConflict = OnConflictStrategy.REPLACE)
    suspend fun insert(item: ShelfItem)

    @Delete
    suspend fun delete(item: ShelfItem)

    @Query("DELETE FROM shelf_items WHERE position = :position")
    suspend fun deleteByPosition(position: Int)
}
