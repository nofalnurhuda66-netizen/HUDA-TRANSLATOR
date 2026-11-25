package com.huda.translator

import android.app.*
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.Image
import android.media.ImageReader
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.*
import android.view.Gravity
import android.view.WindowManager
import android.widget.Button
import android.widget.LinearLayout
import android.widget.TextView
import android.graphics.Color
import com.google.mlkit.vision.common.InputImage
import com.google.mlkit.vision.text.TextRecognition
import com.google.mlkit.vision.text.latin.TextRecognizerOptions
import com.google.mlkit.nl.translate.*
import java.util.concurrent.atomic.AtomicBoolean

class ScreenCaptureService : Service() {
    private var projection: MediaProjection? = null
    private var reader: ImageReader? = null
    private var display: VirtualDisplay? = null
    private var overlay: LinearLayout? = null
    private var resultText: TextView? = null
    private var windowManager: WindowManager? = null
    private val busy = AtomicBoolean(false)
    private val handler = Handler(Looper.getMainLooper())
    private val recognizer by lazy { TextRecognition.getClient(TextRecognizerOptions.DEFAULT_OPTIONS) }
    private val translator by lazy {
        Translation.getClient(TranslatorOptions.Builder()
            .setSourceLanguage(TranslateLanguage.ENGLISH)
            .setTargetLanguage(TranslateLanguage.INDONESIAN)
            .build())
    }
    private var lastRun = 0L

    override fun onBind(intent: Intent?) = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        createNotification()
        val code = intent?.getIntExtra("resultCode", Activity.RESULT_CANCELED) ?: Activity.RESULT_CANCELED
        val data = intent?.getParcelableExtra<Intent>("resultData")
        if (code != Activity.RESULT_OK || data == null) {
            stopSelf()
            return START_NOT_STICKY
        }
        try {
            val manager = getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            projection = manager.getMediaProjection(code, data)
            projection?.registerCallback(object : MediaProjection.Callback() {
                override fun onStop() { stopSelf() }
            }, handler)
            showOverlay()
            startCapture()
        } catch (_: Exception) { stopSelf() }
        return START_NOT_STICKY
    }

    private fun createNotification() {
        val channelId = "huda_capture"
        if (Build.VERSION.SDK_INT >= 26) {
            val channel = NotificationChannel(channelId, "HUDA screen translation",
                NotificationManager.IMPORTANCE_LOW)
            getSystemService(NotificationManager::class.java).createNotificationChannel(channel)
        }
        val notification = Notification.Builder(this, channelId)
            .setContentTitle("HUDA TRANSLATOR aktif")
            .setContentText("Sesi penerjemahan layar sedang berjalan")
            .setSmallIcon(android.R.drawable.ic_menu_search)
            .build()
        startForeground(7, notification)
    }

    private fun showOverlay() {
        windowManager = getSystemService(WINDOW_SERVICE) as WindowManager
        val root = LinearLayout(this).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(18, 14, 18, 14)
            setBackgroundColor(Color.rgb(31, 30, 62))
        }
        val header = TextView(this).apply {
            text = "HUDA TRANSLATOR  •  EN → ID"
            textSize = 14f
            setTextColor(Color.WHITE)
        }
        resultText = TextView(this).apply {
            text = "Menunggu teks pada layar..."
            textSize = 14f
            setTextColor(Color.WHITE)
            setPadding(0, 12, 0, 12)
        }
        val close = Button(this).apply {
            text = "Tutup penerjemah"
            setOnClickListener { stopSelf() }
        }
        root.addView(header); root.addView(resultText); root.addView(close)
        overlay = root
        val type = if (Build.VERSION.SDK_INT >= 26)
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        else WindowManager.LayoutParams.TYPE_PHONE
        val params = WindowManager.LayoutParams(
            (resources.displayMetrics.widthPixels * 0.88).toInt(),
            WindowManager.LayoutParams.WRAP_CONTENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or
                WindowManager.LayoutParams.FLAG_LAYOUT_IN_SCREEN,
            PixelFormat.TRANSLUCENT
        ).apply {
            gravity = Gravity.TOP or Gravity.CENTER_HORIZONTAL
            y = 120
        }
        windowManager?.addView(root, params)
    }

    private fun startCapture() {
        val metrics = resources.displayMetrics
        val width = metrics.widthPixels
        val height = metrics.heightPixels
        val density = metrics.densityDpi
        reader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
        reader?.setOnImageAvailableListener({ source ->
            val now = System.currentTimeMillis()
            if (now - lastRun < 1600 || !busy.compareAndSet(false, true)) {
                source.acquireLatestImage()?.close()
                return@setOnImageAvailableListener
            }
            lastRun = now
            val image = source.acquireLatestImage()
            if (image == null) { busy.set(false); return@setOnImageAvailableListener }
            processImage(image)
        }, handler)
        display = projection?.createVirtualDisplay(
            "HUDA_SCREEN_CAPTURE", width, height, density,
            DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
            reader?.surface, null, handler
        )
    }

    private fun processImage(image: Image) {
        try {
            val bitmap = image.toBitmap()
            image.close()
            val input = InputImage.fromBitmap(bitmap, 0)
            recognizer.process(input)
                .addOnSuccessListener { result ->
                    val text = result.text.trim()
                    if (text.isBlank()) {
                        resultText?.post { resultText?.text = "Tidak ada teks yang terdeteksi." }
                        busy.set(false)
                    } else {
                        translator.downloadModelIfNeeded()
                            .addOnSuccessListener {
                                translator.translate(text)
                                    .addOnSuccessListener { translated ->
                                        resultText?.post { resultText?.text = translated }
                                        busy.set(false)
                                    }
                                    .addOnFailureListener { showError("Terjemahan gagal"); busy.set(false) }
                            }
                            .addOnFailureListener { showError("Model bahasa gagal diunduh"); busy.set(false) }
                    }
                }
                .addOnFailureListener { showError("OCR gagal membaca layar"); busy.set(false) }
        } catch (_: Exception) { image.close(); busy.set(false) }
    }

    private fun showError(message: String) {
        resultText?.post { resultText?.text = message }
    }

    private fun Image.toBitmap(): android.graphics.Bitmap {
        val plane = planes[0]
        val buffer = plane.buffer
        val pixelStride = plane.pixelStride
        val rowStride = plane.rowStride
        val rowPadding = rowStride - pixelStride * width
        val padded = android.graphics.Bitmap.createBitmap(
            width + rowPadding / pixelStride, height, android.graphics.Bitmap.Config.ARGB_8888)
        padded.copyPixelsFromBuffer(buffer)
        val cropped = android.graphics.Bitmap.createBitmap(padded, 0, 0, width, height)
        padded.recycle()
        return cropped
    }

    override fun onDestroy() {
        try { display?.release(); reader?.close(); projection?.stop() } catch (_: Exception) {}
        try { overlay?.let { windowManager?.removeView(it) } } catch (_: Exception) {}
        try { recognizer.close(); translator.close() } catch (_: Exception) {}
        handler.removeCallbacksAndMessages(null)
        super.onDestroy()
    }
}
