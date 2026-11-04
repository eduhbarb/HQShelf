package com.hqshelf.ui

import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.setContent
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import com.hqshelf.data.local.db.AppDatabase
import com.hqshelf.data.local.datasource.ShelfLocalDataSource
import com.hqshelf.data.repository.ShelfRepositoryImpl
import com.hqshelf.domain.usecase.AddComicUseCase
import com.hqshelf.domain.usecase.GetShelfUseCase
import com.hqshelf.domain.usecase.ReorderComicUseCase
import com.hqshelf.domain.usecase.RemoveComicUseCase
import com.hqshelf.ui.viewmodel.ShelfViewModel
import com.hqshelf.widget.ShelfWidgetUpdater

class MainActivity : ComponentActivity() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val db = AppDatabase.getDatabase(applicationContext)
        val dataSource = ShelfLocalDataSource(db)
        val repo = ShelfRepositoryImpl(applicationContext, dataSource)
        val factory = object : ViewModelProvider.Factory {
            @Suppress("UNCHECKED_CAST")
            override fun <T : ViewModel> create(modelClass: Class<T>): T {
                return ShelfViewModel(
                    GetShelfUseCase(repo),
                    AddComicUseCase(repo),
                    RemoveComicUseCase(repo),
                    ReorderComicUseCase(repo),
                    applicationContext,
                    ShelfWidgetUpdater::update
                ) as T
            }
        }
        val viewModel: ShelfViewModel = ViewModelProvider(this, factory)[ShelfViewModel::class.java]
        setContent {
            ShelfScreen(viewModel)
        }
    }
}
