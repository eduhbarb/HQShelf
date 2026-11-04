package com.hqshelf.domain.model

data class Comic(
    val position: Int,          // slot 1..5
    val uriString: String,      // URI original do SAF
    val localFilePath: String?, // cópia privada (para compartilhar via FileProvider)
    val fileName: String,
    val mimeType: String,
    val thumbnailPath: String?, // miniatura em cache
    val addedAt: Long
)
