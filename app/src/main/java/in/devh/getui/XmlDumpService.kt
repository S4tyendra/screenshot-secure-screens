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
import java.io.File
import java.io.FileWriter

class XmlDumpService : AccessibilityService() {

    private val receiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            if (intent?.action == "in.devh.getui.ACTION_DUMP_XML") {
                // Dismiss the notification shade to capture the app beneath
                // This replaces ACTION_CLOSE_SYSTEM_DIALOGS which is restricted on Android 12+
                performGlobalAction(GLOBAL_ACTION_DISMISS_NOTIFICATION_SHADE)
                
                // Delay slightly to let the shade animation finish before capturing
                Handler(Looper.getMainLooper()).postDelayed({
                    dumpHierarchy()
                }, 500)
            }
        }
    }

    override fun onServiceConnected() {
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
            Toast.makeText(this, "Root node is null (FLAG_SECURE blocks this or screen is transitioning)", Toast.LENGTH_LONG).show()
            return
        }

        val sb = StringBuilder()
        sb.append("<?xml version='1.0' encoding='UTF-8' standalone='yes' ?>\n")
        sb.append("<hierarchy>\n")
        buildXml(rootNode, sb, "")
        sb.append("</hierarchy>")

        saveToFile(sb.toString())
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

    private fun saveToFile(xml: String) {
        try {
            val dir = Environment.getExternalStoragePublicDirectory(Environment.DIRECTORY_DOWNLOADS)
            val file = File(dir, "ui_dump_${System.currentTimeMillis()}.xml")
            FileWriter(file).use { it.write(xml) }
            Toast.makeText(this, "Saved to ${file.absolutePath}", Toast.LENGTH_SHORT).show()
        } catch (e: Exception) {
            Log.e("XmlDumpService", "Error saving file", e)
            Toast.makeText(this, "Error saving file: ${e.message}", Toast.LENGTH_SHORT).show()
        }
    }
}