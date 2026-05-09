package `in`.devh.getui.data

import androidx.room.Dao
import androidx.room.Insert
import androidx.room.Query
import kotlinx.coroutines.flow.Flow

@Dao
interface DumpDao {
    @Query("SELECT id, ts, packageName, appName, html, imgPath, error FROM dumps ORDER BY ts DESC")
    fun getAllDumpsSummary(): Flow<List<DumpSummary>>

    @Query("SELECT * FROM dumps WHERE id = :id")
    suspend fun getDumpById(id: Long): DumpEntity?

    @Insert
    suspend fun insertDump(dump: DumpEntity): Long

    @androidx.room.Update
    suspend fun updateDump(dump: DumpEntity)

    @Query("DELETE FROM dumps WHERE id = :id")
    suspend fun deleteDump(id: Long)
}
