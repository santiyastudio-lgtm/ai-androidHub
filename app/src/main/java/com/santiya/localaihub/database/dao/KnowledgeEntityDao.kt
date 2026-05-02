package com.santiya.localaihub.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.santiya.localaihub.models.table_schema.KnowledgeEntity

@Dao
interface KnowledgeEntityDao {
    @Query("SELECT * FROM knowledge_entities ORDER BY last_seen DESC")
    suspend fun getAll(): List<KnowledgeEntity>
}
