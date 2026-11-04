package com.hqshelf.domain.usecase

import com.hqshelf.domain.model.Comic
import com.hqshelf.domain.repository.ShelfRepository

class GetShelfUseCase(private val repository: ShelfRepository) {
    suspend operator fun invoke(): List<Comic> = repository.getShelf()
}
