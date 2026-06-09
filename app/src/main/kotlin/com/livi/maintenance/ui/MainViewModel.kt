package com.livi.maintenance.ui

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import com.livi.maintenance.LiviApp
import com.livi.maintenance.data.TaskEntity
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

class MainViewModel(private val app: LiviApp) : ViewModel() {

    val tasks: StateFlow<List<TaskEntity>> = app.repository.observeAll()
        .stateIn(viewModelScope, SharingStarted.WhileSubscribed(5000), emptyList())

    fun upsert(task: TaskEntity) = viewModelScope.launch {
        val id = app.repository.upsert(task)
        val saved = app.repository.get(if (task.id == 0L) id else task.id) ?: return@launch
        app.scheduler.schedule(saved)
    }

    fun delete(task: TaskEntity) = viewModelScope.launch {
        app.scheduler.cancel(task.id)
        app.repository.delete(task)
    }

    fun runNow(task: TaskEntity) {
        app.scheduler.runNow(task)
    }
}

class MainViewModelFactory(private val app: LiviApp) : ViewModelProvider.Factory {
    @Suppress("UNCHECKED_CAST")
    override fun <T : ViewModel> create(modelClass: Class<T>): T =
        MainViewModel(app) as T
}
