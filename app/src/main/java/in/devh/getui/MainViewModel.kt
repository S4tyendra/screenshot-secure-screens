package `in`.devh.getui

import android.app.Application
import androidx.lifecycle.AndroidViewModel
import androidx.lifecycle.viewModelScope
import `in`.devh.getui.data.AppDatabase
import `in`.devh.getui.data.DumpEntity
import `in`.devh.getui.data.DumpSummary
import kotlinx.coroutines.flow.Flow
import kotlinx.coroutines.launch

class MainViewModel(application: Application) : AndroidViewModel(application) {
    private val db = AppDatabase.getDatabase(application)
    private val dumpDao = db.dumpDao()

    val allDumps: Flow<List<DumpSummary>> = dumpDao.getAllDumpsSummary()

    suspend fun getDumpById(id: Long): DumpEntity? {
        return dumpDao.getDumpById(id)
    }

    fun deleteDump(id: Long) {
        viewModelScope.launch {
            dumpDao.deleteDump(id)
        }
    }
}
