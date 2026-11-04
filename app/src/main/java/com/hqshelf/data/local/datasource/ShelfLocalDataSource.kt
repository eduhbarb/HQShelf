package com.hqshelf.data.local.datasource

import com.hqshelf.data.local.db.AppDatabase
import com.hqshelf.data.local.db.ShelfItem

class ShelfLocalDataSource(private val db: AppDatabase) {
    val dao = db.shelfItemDao()

    suspend fun getAll(): List<ShelfItem> = dao.getAll()
    suspend fun getByPosition(position: Int): ShelfItem? = dao.getByPosition(position)
    suspend fun save(item: ShelfItem) = dao.insert(item)
    suspend fun delete(item: ShelfItem) = dao.delete(item)
    suspend fun deleteByPosition(position: Int) = dao.deleteByPosition(position)
}
