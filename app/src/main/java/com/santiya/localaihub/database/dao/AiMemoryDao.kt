package com.santiya.localaihub.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.santiya.localaihub.models.table_schema.AiMemory

@Dao
interface AiMemoryDao {
    @Query("SELECT * FROM ai_memories ORDER BY updated_at DESC")
    suspend fun getAllOnce(): List<AiMemory>
}
