package com.hqshelf.domain.usecase

import android.net.Uri
import com.hqshelf.domain.model.Comic
import com.hqshelf.domain.repository.ShelfRepository

class AddComicUseCase(private val repository: ShelfRepository) {
    suspend operator fun invoke(uri: Uri, position: Int): Comic = repository.addComicFromUri(uri, position)
}
