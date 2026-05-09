package `in`.devh.getui

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Canvas
import android.os.Handler
import android.os.Looper
import android.util.Log
import android.webkit.WebView
import android.webkit.WebViewClient
import com.google.genai.Client
import com.google.genai.types.Content
import com.google.genai.types.GenerateContentConfig
import com.google.genai.types.Part
import com.google.genai.types.ThinkingConfig
import com.google.genai.types.ThinkingLevel
import com.google.common.collect.ImmutableList
import `in`.devh.getui.data.AppDatabase
import `in`.devh.getui.data.DumpEntity
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import java.io.File
import java.io.FileOutputStream

class GeminiProcessor(private val context: Context) {
    private val db = AppDatabase.getDatabase(context)
    private val scope = CoroutineScope(Dispatchers.IO)
    private val prefs = context.getSharedPreferences("getui_prefs", Context.MODE_PRIVATE)

    fun processDump(dumpId: Long) {
        scope.launch {
            try {
                val entity = db.dumpDao().getDumpById(dumpId) ?: return@launch
                val apiKey = prefs.getString("gemini_api_key", "") ?: ""
                val systemPrompt = prefs.getString("system_prompt", "") ?: ""

                if (apiKey.isEmpty()) {
                    db.dumpDao().updateDump(entity.copy(error = "API Key is missing"))
                    return@launch
                }

                val client = Client.builder().apiKey(apiKey).build()
                
                val contents = ImmutableList.of(
                    Content.builder()
                        .role("user")
                        .parts(ImmutableList.of(
                            Part.fromText(entity.dump)
                        ))
                        .build()
                )

                val config = GenerateContentConfig.builder()
                    .thinkingConfig(
                        ThinkingConfig.builder()
                            .thinkingLevel(ThinkingLevel("low"))
                            .build()
                    )
                    .systemInstruction(
                        Content.fromParts(
                            Part.fromText(systemPrompt + "\n\nIMPORTANT: YOUR HTML CODE MUST START WITH '<!DOCTYPE html>' and end with '</html>' IRRESPECTIVE OF ABOVE INSTRUCTIONS.")
                        )
                    )
                    .build()

                val response = client.models.generateContent("gemini-3-flash-preview", contents, config)
                val aiOutput = response.text() ?: ""
                
                // Parse last </html> and first <!DOCTYPE html>
                val lastHtmlIndex = aiOutput.lastIndexOf("</html>")
                val firstHtmlIndex = aiOutput.indexOf("<!DOCTYPE html>")
                
                var cleanHtml = if (firstHtmlIndex != -1 && lastHtmlIndex != -1) {
                    aiOutput.substring(firstHtmlIndex, lastHtmlIndex + 7)
                } else {
                    aiOutput
                }

                // If it's a full HTML doc, try to extract body content to avoid nesting in our template
                val bodyStart = cleanHtml.indexOf("<body")
                val bodyEnd = cleanHtml.lastIndexOf("</body>")
                if (bodyStart != -1 && bodyEnd != -1) {
                    val actualStart = cleanHtml.indexOf(">", bodyStart) + 1
                    cleanHtml = cleanHtml.substring(actualStart, bodyEnd)
                } else {
                    // Try stripping html tags if body wasn't found
                    cleanHtml = cleanHtml.replace(Regex("<!DOCTYPE html>", RegexOption.IGNORE_CASE), "")
                    cleanHtml = cleanHtml.replace(Regex("<html>", RegexOption.IGNORE_CASE), "")
                    cleanHtml = cleanHtml.replace(Regex("</html>", RegexOption.IGNORE_CASE), "")
                    cleanHtml = cleanHtml.replace(Regex("<head>.*?</head>", RegexOption.DOT_MATCHES_ALL), "")
                }

                db.dumpDao().updateDump(entity.copy(html = cleanHtml.trim()))
                
                // Now render to image
                renderHtmlToImage(dumpId, cleanHtml)

            } catch (e: Exception) {
                Log.e("GeminiProcessor", "Error processing dump", e)
                val entity = db.dumpDao().getDumpById(dumpId)
                if (entity != null) {
                    db.dumpDao().updateDump(entity.copy(error = e.message))
                }
            }
        }
    }

    private fun renderHtmlToImage(dumpId: Long, bodyContent: String) {
        val htmlData = """
            <!DOCTYPE html>
            <html>
            <head>
                <meta name="viewport" content="width=device-width, initial-scale=1.0, maximum-scale=1.0, user-scalable=no" />
                <script src="file:///android_asset/tailwindcss.js"></script>
                <script src="file:///android_asset/lucide.min.js"></script>
            </head>
            <body class="bg-gray-50">
                $bodyContent
                <script>
                    lucide.createIcons();
                </script>
            </body>
            </html>
        """.trimIndent()

        Handler(Looper.getMainLooper()).post {
            val webView = WebView(context)
            webView.settings.javaScriptEnabled = true
            webView.settings.allowFileAccess = true
            webView.settings.allowContentAccess = true
            
            // Set a fixed width for mobile representation
            webView.layout(0, 0, 1080, 1920) 

            webView.webViewClient = object : WebViewClient() {
                override fun onPageFinished(view: WebView?, url: String?) {
                    // Wait 5 seconds as requested
                    Handler(Looper.getMainLooper()).postDelayed({
                        captureWebView(webView, dumpId)
                    }, 5000)
                }
            }
            webView.loadDataWithBaseURL("file:///android_asset/", htmlData, "text/html", "UTF-8", null)
        }
    }

    private fun captureWebView(webView: WebView, dumpId: Long) {
        scope.launch {
            try {
                // Ensure WebView content height is measured
                val width = 1080
                val height = withContext(Dispatchers.Main) {
                    webView.measure(
                        android.view.View.MeasureSpec.makeMeasureSpec(width, android.view.View.MeasureSpec.EXACTLY),
                        android.view.View.MeasureSpec.makeMeasureSpec(0, android.view.View.MeasureSpec.UNSPECIFIED)
                    )
                    webView.contentHeight.coerceAtLeast(1920)
                }

                val bitmap = Bitmap.createBitmap(width, height, Bitmap.Config.ARGB_8888)
                val canvas = Canvas(bitmap)
                
                withContext(Dispatchers.Main) {
                    webView.layout(0, 0, width, height)
                    webView.draw(canvas)
                }

                val timestamp = System.currentTimeMillis()
                val imageFile = File(context.filesDir, "images/dump_$dumpId.png")
                imageFile.parentFile?.mkdirs()
                
                FileOutputStream(imageFile).use { out ->
                    bitmap.compress(Bitmap.CompressFormat.PNG, 100, out)
                }

                val entity = db.dumpDao().getDumpById(dumpId)
                if (entity != null) {
                    db.dumpDao().updateDump(entity.copy(imgPath = imageFile.absolutePath))
                }
                
                Log.i("GeminiProcessor", "Image saved: ${imageFile.absolutePath}")
            } catch (e: Exception) {
                Log.e("GeminiProcessor", "Error capturing WebView", e)
                val entity = db.dumpDao().getDumpById(dumpId)
                if (entity != null) {
                    db.dumpDao().updateDump(entity.copy(error = "Image generation failed: ${e.message}"))
                }
            }
        }
    }
}
