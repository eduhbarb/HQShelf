package com.hqshelf.data.local.db

import androidx.room.Entity
import androidx.room.PrimaryKey
import com.hqshelf.domain.model.Comic

@Entity(tableName = "shelf_items")
data class ShelfItem(
    @PrimaryKey val position: Int, // 1..5 (slot fixo da estante)
    val uriString: String,          // URI original do SAF (content://)
    val localFilePath: String?,     // caminho da cópia privada (para FileProvider)
    val fileName: String,
    val mimeType: String,
    val thumbnailPath: String?,     // miniatura em filesDir (persistente)
    val addedAt: Long = System.currentTimeMillis()
) {
    fun toComic(): Comic = Comic(
        position = position,
        uriString = uriString,
        localFilePath = localFilePath,
        fileName = fileName,
        mimeType = mimeType,
        thumbnailPath = thumbnailPath,
        addedAt = addedAt
    )
}
