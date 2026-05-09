package `in`.devh.getui.data

data class DumpSummary(
    val id: Long,
    val ts: Long,
    val packageName: String,
    val appName: String,
    val html: String,
    val imgPath: String
)
