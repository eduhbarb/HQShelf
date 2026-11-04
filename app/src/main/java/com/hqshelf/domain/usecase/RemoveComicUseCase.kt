package com.hqshelf.domain.usecase

import com.hqshelf.domain.repository.ShelfRepository

class RemoveComicUseCase(private val repository: ShelfRepository) {
    suspend operator fun invoke(position: Int) = repository.removeComic(position)
}
