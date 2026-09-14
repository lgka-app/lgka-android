package com.lgka

import android.content.Context
import android.graphics.Bitmap
import android.graphics.Matrix
import android.hardware.Sensor
import android.hardware.SensorEvent
import android.hardware.SensorEventListener
import android.hardware.SensorManager
import android.os.SystemClock
import androidx.camera.core.Camera
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.ImageCapture
import androidx.camera.core.ImageCaptureException
import androidx.camera.core.ImageProxy
import androidx.camera.core.Preview
import androidx.camera.core.resolutionselector.AspectRatioStrategy
import androidx.camera.core.resolutionselector.ResolutionSelector
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.camera.view.PreviewView
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.core.content.ContextCompat
import androidx.lifecycle.LifecycleOwner
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.suspendCancellableCoroutine
import kotlinx.coroutines.withContext
import lgka.plan.ScanFrame
import lgka.plan.ScanGuidance
import lgka.plan.ScanHint
import lgka.plan.ScanQuad
import lgka.plan.TorchPolicy
import java.util.concurrent.Executors
import kotlin.coroutines.resume
import kotlin.math.acos
import kotlin.math.roundToInt
import kotlin.math.sqrt

/**
 * The Kurswahlprotokoll camera: CameraX preview, ~9 analysed frames a second (sheet outline, brightness,
 * glare, motion), the automatic torch and a burst of photos. State is Compose state on the main thread.
 */
class KurswahlCamera(private val context: Context) : SensorEventListener {
    var quad by mutableStateOf<ScanQuad?>(null); private set
    var state by mutableStateOf(ScanGuidance.State(ScanHint.NO_DOCUMENT, 0.0, false)); private set
    /** Gravity across the screen (x right, y up, in g), for the spirit level. */
    var level by mutableStateOf(0f to 0f); private set
    var torchOn by mutableStateOf(false); private set
    var isCapturing by mutableStateOf(false); private set
    /** Width / height of the analysed upright frames. */
    var frameAspect by mutableDoubleStateOf(3.0 / 4.0); private set

    /** Fires when "ready" held long enough. */
    var onAutoCapture: (() -> Unit)? = null

    private val executor = Executors.newSingleThreadExecutor()
    private val guidance = ScanGuidance()
    private var torch = TorchPolicy()
    private var camera: Camera? = null
    private var provider: ProcessCameraProvider? = null
    private var capture: ImageCapture? = null
    private var stableQuad: ScanQuad? = null
    private var previousQuad: ScanQuad? = null
    private var lastAnalysis = 0L
    private var running = false

    private val sensors = context.getSystemService(SensorManager::class.java)
    @Volatile private var tilt = 0.0
    @Volatile private var shake = 0.0
    @Volatile private var gravity = floatArrayOf(0f, 0f, 9.81f)
    private var rotation = 0.0
    private var acceleration = 0.0

    fun start(owner: LifecycleOwner, previewView: PreviewView) {
        if (running) return
        running = true
        sensors?.getDefaultSensor(Sensor.TYPE_GRAVITY)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors?.getDefaultSensor(Sensor.TYPE_GYROSCOPE)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        sensors?.getDefaultSensor(Sensor.TYPE_LINEAR_ACCELERATION)?.let { sensors.registerListener(this, it, SensorManager.SENSOR_DELAY_GAME) }
        val future = ProcessCameraProvider.getInstance(context)
        future.addListener({
            if (!running) return@addListener
            val cameraProvider = future.get()
            provider = cameraProvider
            val fourThree = ResolutionSelector.Builder().setAspectRatioStrategy(AspectRatioStrategy.RATIO_4_3_FALLBACK_AUTO_STRATEGY).build()
            val preview = Preview.Builder().setResolutionSelector(fourThree).build().also { it.surfaceProvider = previewView.surfaceProvider }
            val analysis = ImageAnalysis.Builder()
                .setResolutionSelector(fourThree)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
                .also { it.setAnalyzer(executor, ::analyse) }
            val imageCapture = ImageCapture.Builder()
                .setResolutionSelector(fourThree)
                .setCaptureMode(ImageCapture.CAPTURE_MODE_MAXIMIZE_QUALITY)
                .build()
            capture = imageCapture
            cameraProvider.unbindAll()
            camera = cameraProvider.bindToLifecycle(owner, CameraSelector.DEFAULT_BACK_CAMERA, preview, analysis, imageCapture)
        }, ContextCompat.getMainExecutor(context))
    }

    fun stop() {
        running = false
        sensors?.unregisterListener(this)
        camera?.cameraControl?.enableTorch(false)
        torchOn = false
        torch = TorchPolicy()
        provider?.unbindAll()
    }

    fun shutdown() {
        stop()
        executor.shutdown()
    }

    /**
     * Takes a few photos in a row and straightens each with the last stable detection; the scanner
     * merges them cell by cell. [onPhoto] runs right after each photo was taken (0-based), for the shutter effect.
     */
    suspend fun capture(count: Int, onPhoto: (Int) -> Unit): List<Bitmap> {
        val imageCapture = capture ?: return emptyList()
        if (isCapturing) return emptyList()
        isCapturing = true
        val framing = stableQuad ?: quad
        val images = mutableListOf<Bitmap>()
        try {
            repeat(count) { index ->
                // the shutter effect fires when the photo is exposed, not after it was processed
                val photo = takePhoto(imageCapture) { onPhoto(index) } ?: return@repeat
                images += withContext(Dispatchers.Default) {
                    // 4032 px keeps three photos in memory; the table crops are enlarged again for reading
                    SheetDetection.corrected(SheetDetection.limited(photo, 4032), framing)
                }
            }
        } finally {
            isCapturing = false
            guidance.reset()
        }
        return images
    }

    private suspend fun takePhoto(imageCapture: ImageCapture, onExposed: () -> Unit): Bitmap? = suspendCancellableCoroutine { continuation ->
        var exposed = false
        val main = ContextCompat.getMainExecutor(context)
        imageCapture.takePicture(executor, object : ImageCapture.OnImageCapturedCallback() {
            override fun onCaptureStarted() {
                exposed = true
                main.execute(onExposed)
            }

            override fun onCaptureSuccess(image: ImageProxy) {
                // devices that do not report the exposure get the effect with the photo
                if (!exposed) main.execute(onExposed)
                val bitmap = try {
                    val raw = image.toBitmap()
                    val degrees = image.imageInfo.rotationDegrees
                    if (degrees == 0) raw else Bitmap.createBitmap(raw, 0, 0, raw.width, raw.height, Matrix().apply { postRotate(degrees.toFloat()) }, true)
                } catch (e: Exception) {
                    null
                } finally {
                    image.close()
                }
                continuation.resume(bitmap)
            }

            override fun onError(exception: ImageCaptureException) {
                continuation.resume(null)
            }
        })
    }

    private fun analyse(image: ImageProxy) {
        val now = SystemClock.elapsedRealtime()
        if (now - lastAnalysis < 110) { image.close(); return }
        lastAnalysis = now
        val grid = try {
            SheetDetection.grid(image, image.imageInfo.rotationDegrees)
        } finally {
            image.close()
        }
        val detected = SheetDetection.detect(grid)
        val (luma, glare) = SheetDetection.brightness(grid, detected)
        val jitter = when {
            detected != null && previousQuad != null -> detected.jitterFrom(previousQuad!!)
            detected != null -> 1.0 // a sheet that just appeared is not steady yet
            else -> 0.0
        }
        previousQuad = detected
        val frame = ScanFrame(detected, luma, glare, tilt, shake, jitter, now / 1000.0)
        val aspect = grid.width.toDouble() / grid.height
        val g = gravity
        ContextCompat.getMainExecutor(context).execute { handle(frame, aspect, g) }
    }

    private fun handle(analysed: ScanFrame, aspect: Double, g: FloatArray) {
        if (!running || isCapturing) return
        frameAspect = aspect
        // gravity as iOS reports it (pointing to the ground, in g): Android's sensor gives the opposite
        level = (-g[0] / 9.81f) to (-g[1] / 9.81f)
        quad = analysed.quad

        // the torch decides itself: on when it stays dark, stronger or weaker as the frames show
        var frame = analysed
        if (camera?.cameraInfo?.hasFlashUnit() == true) {
            val previous = torch.level
            val next = torch.update(frame.luma, frame.glare, frame.time)
            if (next != previous) setTorch(next)
            torchOn = torch.isOn
            frame = frame.copy(torch = next)
        }

        val next = guidance.update(frame)
        if (next.hint == ScanHint.READY && analysed.quad != null) stableQuad = analysed.quad
        state = next
        if (next.capture) onAutoCapture?.invoke()
    }

    private fun setTorch(level: Double) {
        val control = camera?.cameraControl ?: return
        val info = camera?.cameraInfo ?: return
        if (level <= 0) {
            control.enableTorch(false)
            return
        }
        control.enableTorch(true)
        if (android.os.Build.VERSION.SDK_INT >= 33 && info.maxTorchStrengthLevel > 1) {
            control.setTorchStrengthLevel((level * info.maxTorchStrengthLevel).roundToInt().coerceIn(1, info.maxTorchStrengthLevel))
        }
    }

    override fun onSensorChanged(event: SensorEvent) {
        val v = event.values
        when (event.sensor.type) {
            Sensor.TYPE_GRAVITY -> {
                gravity = floatArrayOf(v[0], v[1], v[2])
                // lying flat over a table the phone's back points straight down: gravity along +z
                tilt = Math.toDegrees(acos((v[2] / 9.81f).toDouble().coerceIn(-1.0, 1.0)))
            }
            Sensor.TYPE_GYROSCOPE -> rotation = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble())
            Sensor.TYPE_LINEAR_ACCELERATION -> acceleration = sqrt((v[0] * v[0] + v[1] * v[1] + v[2] * v[2]).toDouble()) / 9.81
        }
        shake = rotation + 2 * acceleration
    }

    override fun onAccuracyChanged(sensor: Sensor?, accuracy: Int) = Unit
}
