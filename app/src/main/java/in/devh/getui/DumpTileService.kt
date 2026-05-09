package `in`.devh.getui

import android.content.Intent
import android.service.quicksettings.TileService

class DumpTileService : TileService() {
    override fun onClick() {
        super.onClick()
        // Broadcast to trigger the dump in XmlDumpService
        val intent = Intent("in.devh.getui.ACTION_DUMP_XML")
        intent.setPackage(packageName)
        sendBroadcast(intent)
        
        // Removed sendBroadcast(Intent(Intent.ACTION_CLOSE_SYSTEM_DIALOGS))
        // This fixes the SecurityException on Android 12+.
        // The XmlDumpService will now handle closing the shade.
    }
}