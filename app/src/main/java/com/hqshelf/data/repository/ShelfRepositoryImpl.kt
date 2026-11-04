package com.hqshelf.data.repository

import android.content.Context
import android.net.Uri
import android.provider.OpenableColumns
import com.hqshelf.data.local.db.ShelfItem
import com.hqshelf.data.local.datasource.ShelfLocalDataSource
import com.hqshelf.data.source.ComicExtractor
import com.hqshelf.domain.model.Comic
import com.hqshelf.domain.repository.ShelfRepository
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.withContext
import java.io.File
import kotlin.io.copyTo

class ShelfRepositoryImpl(
    private val context: Context,
    private val dataSource: ShelfLocalDataSource,
    private val extractor: ComicExtractor = ComicExtractor
) : ShelfRepository {

    // MIMEs dos formatos aceitos no seletor: quadrinhos + livros.
    companion object {
        val OPEN_MIME_TYPES = arrayOf(
            "application/x-cbz",
            "application/x-cbr",
            "application/epub+zip",
            "application/pdf",
            "application/x-mobipocket-ebook"
        )
    }

    override suspend fun getShelf(): List<Comic> =
        dataSource.getAll().map { it.toComic() }

    override suspend fun getComic(position: Int): Comic? =
        dataSource.getByPosition(position)?.toComic()

    /**
     * Recebe um URI do SAF, copia o arquivo para o armazenamento privado do app,
     * extrai a capa, gera a miniatura (300x450) e persiste no slot [position].
     */
    override suspend fun addComicFromUri(uri: Uri, position: Int): Comic =
        withContext(Dispatchers.IO) {
            val fileName = getDisplayName(uri) ?: "quadrinho"
            val mime = extractor.mimeTypeFor(fileName, context.contentResolver.getType(uri))

            // Cópia do arquivo selecionado para storage privado do app (persistente, não é limpo pelo sistema)
            val localFile = File(context.filesDir, "${System.currentTimeMillis()}_$fileName")
            context.contentResolver.openInputStream(uri)?.use { input ->
                localFile.outputStream().use { output -> input.copyTo(output) }
            }

            if (!extractor.isSupported(localFile.name)) {
                localFile.delete()
                throw IllegalArgumentException(context.getString(com.hqshelf.R.string.error_unsupported))
            }

            val cover = extractor.extractCover(context, localFile)
            val thumb = extractor.resizeBitmap(cover.bitmap, 300, 450)
            val thumbPath = extractor.saveThumbnail(context, thumb, "cover_$position")

            val item = ShelfItem(
                position = position,
                uriString = uri.toString(),
                localFilePath = localFile.absolutePath,
                fileName = fileName,
                mimeType = mime,
                thumbnailPath = thumbPath
            )
            dataSource.save(item)
            item.toComic()
        }

    override suspend fun removeComic(position: Int) {
        val item = dataSource.getByPosition(position) ?: return
        // Remove arquivos físicos (miniatura + cópia do quadrinho)
        item.thumbnailPath?.let { File(it).delete() }
        item.localFilePath?.let { File(it).delete() }
        dataSource.deleteByPosition(position)
    }

    /**
     * Persiste uma nova ordem de posições. Cada item recebe a posição [index + 1]
     * e é gravado com [OnConflictStrategy.REPLACE], de modo que todas as posições
     * 1..n ficam atualizadas de forma atômica o suficiente para a tela e o widget.
     */
    override suspend fun reorder(items: List<Comic>) =
        withContext(Dispatchers.IO) {
            items.withIndex().forEach { (index, comic) ->
                val item = ShelfItem(
                    position = index + 1,
                    uriString = comic.uriString,
                    localFilePath = comic.localFilePath,
                    fileName = comic.fileName,
                    mimeType = comic.mimeType,
                    thumbnailPath = comic.thumbnailPath,
                    addedAt = comic.addedAt
                )
                dataSource.save(item)
            }
        }

    private fun getDisplayName(uri: Uri): String? {
        return context.contentResolver.query(uri, null, null, null, null).use { cursor ->
            if (cursor != null && cursor.moveToFirst()) {
                val index = cursor.getColumnIndex(OpenableColumns.DISPLAY_NAME)
                if (index >= 0) cursor.getString(index) else null
            } else null
        } ?: uri.lastPathSegment
    }
}
