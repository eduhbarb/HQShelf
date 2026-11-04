package com.hqshelf.domain.repository

import com.hqshelf.domain.model.Comic

interface ShelfRepository {
    suspend fun getShelf(): List<Comic>
    suspend fun getComic(position: Int): Comic?
    suspend fun addComicFromUri(uri: android.net.Uri, position: Int): Comic
    suspend fun removeComic(position: Int)
    suspend fun reorder(items: List<Comic>)
}
