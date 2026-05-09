package `in`.devh.getui

import android.accessibilityservice.AccessibilityService
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Environment
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import android.widget.Toast
import android.content.pm.PackageManager
import `in`.devh.getui.data.AppDatabase
import `in`.devh.getui.data.DumpEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import java.io.File
import java.io.FileWriter
import java.text.SimpleDateFormat
import java.util.Date
import java.util.Locale

class XmlDumpService : AccessibilityService() {

    private val serviceScope = CoroutineScope(Dispatchers.IO)

    companion object {
        @Volatile
        var isRunning = false
            private set
    }

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "in.devh.getui.ACTION_DUMP_XML") {
                // Dismiss the notification shade to capture the app beneath
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                
                // Delay slightly to let the shade animation finish before capturing
                Handler(Looper.getMainLooper()).postDelayed({
                    dumpHierarchy()
                }, 500)
            }
        }
    }

    override fun onServiceConnected() {
        isRunning = true
        Log.i("XmlDumpService", "Service connected")
        val filter = IntentFilter("in.devh.getui.ACTION_DUMP_XML")
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            registerReceiver(receiver, filter, RECEIVER_EXPORTED)
        } else {
            @Suppress("UnspecifiedRegisterReceiverFlag")
            registerReceiver(receiver, filter)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent?) {}
    override fun onInterrupt() {}

    override fun onDestroy() {
        isRunning = false
        Log.i("XmlDumpService", "Service destroyed")
        try {
            unregisterReceiver(receiver)
        } catch (e: Exception) {
            Log.e("XmlDumpService", "Receiver not registered", e)
        }
        super.onDestroy()
    }

    private fun dumpHierarchy() {
        val rootNode = rootInActiveWindow
        if (rootNode == null) {
            Log.w("XmlDumpService", "Root node is null (FLAG_SECURE or transitioning)")
            Toast.makeText(this, "Root node is null", Toast.LENGTH_LONG).show()
            return
        }

        val packageName = rootNode.packageName?.toString() ?: "unknown"
        val appName = getAppName(packageName)

        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        sb.append("<hierarchy>\n")
        buildXml(rootNode, sb, "")
        sb.append("</hierarchy>")

        val xmlContent = sb.toString()
        saveToStorageAndDb(xmlContent, packageName, appName)
        rootNode.recycle()
    }

    private fun buildXml(node: AccessibilityNodeInfo, sb: StringBuilder, indent: String) {
        sb.append("$indent<node class=\"${node.className}\" package=\"${node.packageName}\" ")
        sb.append("text=\"${escapeXml(node.text)}\" content-desc=\"${escapeXml(node.contentDescription)}\" ")
        
        val bounds = android.graphics.Rect()
        node.getBoundsInScreen(bounds)
        sb.append("bounds=\"[${bounds.left},${bounds.top}][${bounds.right},${bounds.bottom}]\">\n")

        for (i in 0 until node.childCount) {
            val child = node.getChild(i)
            if (child != null) {
                buildXml(child, sb, "$indent  ")
                child.recycle()
            }
        }
        sb.append("$indent</node>\n")
    }

    private fun escapeXml(charSequence: CharSequence?): String {
        if (charSequence == null) return ""
        return charSequence.toString()
            .replace("&", "&amp;")
            .replace("<", "&lt;")
            .replace(">", "&gt;")
            .replace("\"", "&quot;")
            .replace("'", "&apos;")
    }

    private fun getAppName(packageName: String): String {
        val pm = packageManager
        return try {
            val appInfo = pm.getApplicationInfo(packageName, 0)
            pm.getApplicationLabel(appInfo).toString()
        } catch (e: Exception) {
            packageName
        }
    }

    private fun saveToStorageAndDb(xml: String, packageName: String, appName: String) {
        val ts = System.currentTimeMillis()
        val timestampStr = SimpleDateFormat("yyyyMMdd_HHmmss", Locale.getDefault()).format(Date(ts))
        
        try {
            // Save to app's data directory
            val dumpDir = File(filesDir, "dumps/$timestampStr")
            if (!dumpDir.exists()) {
                dumpDir.mkdirs()
            }

            val xmlFile = File(dumpDir, "dump.xml")
            FileWriter(xmlFile).use { it.write(xml) }

            val packageFile = File(dumpDir, "package.txt")
            FileWriter(packageFile).use { it.write("App Name: $appName\nPackage Name: $packageName\n") }

            // Save to Database
            serviceScope.launch {
                val db = AppDatabase.getDatabase(applicationContext)
                val id = db.dumpDao().insertDump(
                    DumpEntity(
                        ts = ts,
                        packageName = packageName,
                        appName = appName,
                        dump = xml
                    )
                )
                GeminiProcessor(applicationContext).processDump(id)
            }

            val msg = "Dump saved for $appName"
            Log.i("XmlDumpService", msg)
            Handler(Looper.getMainLooper()).post {
                Toast.makeText(this, msg, Toast.LENGTH_SHORT).show()
            }
        } catch (e: Exception) {
            Log.e("XmlDumpService", "Error saving dump", e)
        }
    }
}
