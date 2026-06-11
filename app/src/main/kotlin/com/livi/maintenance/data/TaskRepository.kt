package com.livi.maintenance.data

import kotlinx.coroutines.flow.Flow

class TaskRepository(private val dao: TaskDao) {
    fun observeAll(): Flow<List<TaskEntity>> = dao.observeAll()
    suspend fun getAll(): List<TaskEntity> = dao.getAll()
    suspend fun getEnabled(): List<TaskEntity> = dao.getEnabled()
    suspend fun getPending(): List<TaskEntity> = dao.getPending()
    suspend fun get(id: Long): TaskEntity? = dao.get(id)
    suspend fun upsert(task: TaskEntity): Long =
        if (task.id == 0L) dao.insert(task) else { dao.update(task); task.id }
    suspend fun delete(task: TaskEntity) = dao.delete(task)
    suspend fun deleteById(id: Long) = dao.deleteById(id)
}
