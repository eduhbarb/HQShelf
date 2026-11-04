package com.hqshelf.domain.usecase

import com.hqshelf.domain.model.Comic
import com.hqshelf.domain.repository.ShelfRepository

class ReorderComicUseCase(private val repository: ShelfRepository) {
    suspend operator fun invoke(items: List<Comic>) = repository.reorder(items)
}
