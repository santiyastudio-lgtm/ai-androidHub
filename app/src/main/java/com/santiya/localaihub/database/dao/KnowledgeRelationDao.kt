package com.santiya.localaihub.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.santiya.localaihub.models.table_schema.KnowledgeRelation

@Dao
interface KnowledgeRelationDao {
    @Query("SELECT * FROM knowledge_relations ORDER BY created_at DESC")
    suspend fun getAll(): List<KnowledgeRelation>
}
