package com.santiya.localaihub.database.dao

import androidx.room.Dao
import androidx.room.Query
import com.santiya.localaihub.models.table_schema.Model

@Dao
interface ModelDao {
    @Query("SELECT * FROM models")
    suspend fun getAllOnce(): List<Model>
}
