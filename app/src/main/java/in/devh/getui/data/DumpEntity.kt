package `in`.devh.getui.data

import androidx.room.Entity
import androidx.room.PrimaryKey

@Entity(tableName = "dumps")
data class DumpEntity(
    @PrimaryKey(autoGenerate = true) val id: Long = 0,
    val ts: Long,
    val packageName: String,
    val appName: String,
    val dump: String,
    val html: String = "",
    val imgPath: String = "",
    val error: String? = null
)
