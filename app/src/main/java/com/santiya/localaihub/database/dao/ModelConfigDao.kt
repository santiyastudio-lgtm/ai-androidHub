package com.santiya.localaihub.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.santiya.localaihub.models.table_schema.ModelConfig

@Dao
interface ModelConfigDao {
    @Query("SELECT * FROM model_config WHERE model_id = :modelId")
    suspend fun getByModelId(modelId: String): ModelConfig?
}
