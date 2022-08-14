package net.taptappun.taku.kobayashi.screenbarcoder

import android.annotation.SuppressLint
import android.app.Activity
import android.graphics.Paint
import android.graphics.PixelFormat
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.Size
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors
import net.taptappun.taku.kobayashi.screenbarcoder.databinding.ActivityCameraScanBinding

class CameraScanActivity : AppCompatActivity() {

    private val imageAnalysisExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private val detectors = setOf(
        BarcodeImageDetector(),
    )
    private var surfaceViewThread: Thread? = null
    private var isSurfaceRendering = false
    private var surfaceHolder: SurfaceHolder? = null
    private lateinit var binding: ActivityCameraScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Cameraの上にOverlayさせるためにはSurfaceView.setZOrderMediaOverlay(true)が必要
        // 参考: https://stackoverflow.com/questions/2933882/how-to-draw-an-overlay-on-a-surfaceview-used-by-camera-on-android
        binding.overlaySurfaceView.setZOrderMediaOverlay(true)
        val holder = binding.overlaySurfaceView.holder
        // OverlayしているSUrfaceViewの背景を透過させないとカメラ画面が表示されない
        // SurfaceHolder.setFormat(PixelFormat.TRANSLUCENT)は背景を透過する設定
        holder.setFormat(PixelFormat.TRANSLUCENT)
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                surfaceHolder = holder
                startRenderThread()
                startCamera()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                surfaceHolder = holder
                startRenderThread()
                startCamera()
                for (detector in detectors) {
                    detector.initRenderBitmap(Size(width, height))
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                unbindAllCamera()
                for (detector in detectors) {
                    detector.release()
                }
                isSurfaceRendering = false
                surfaceViewThread = null
            }
        })
    }

    private fun startRenderThread() {
        isSurfaceRendering = true
        if (surfaceViewThread != null) {
            return
        }
        surfaceViewThread = Thread {
            while (isSurfaceRendering) {
                val canvas = surfaceHolder?.lockCanvas()
                if (canvas != null) {
//                    val paint = Paint()
//                    paint.color = Color.RED
//                    canvas.drawRect(Rect(canvas.width / 2 - 100, canvas.height / 2 - 100, canvas.width / 2 + 100, canvas.height / 2 + 100), paint)
                    for (detector in detectors) {
                        val paint = Paint()
                        val renderMarkedBitmap = detector.renderMarkedBitmap()
                        if (renderMarkedBitmap != null) {
                            canvas.drawBitmap(renderMarkedBitmap, 0f, 0f, paint)
                        }
                    }
                    surfaceHolder?.unlockCanvasAndPost(canvas)
                }
            }
        }
        surfaceViewThread?.start()
    }

    override fun onResume() {
        super.onResume()
        isSurfaceRendering = true
        startRenderThread()
        startCamera()
    }

    override fun onPause() {
        super.onPause()
        isSurfaceRendering = false
        surfaceViewThread = null
        unbindAllCamera()
    }

    override fun onDestroy() {
        super.onDestroy()
        isSurfaceRendering = false
        surfaceViewThread = null
        for (detector in detectors) {
            detector.release()
        }
        releaseCamera()
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // ライフサイクルにバインドするために利用する
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // PreviewのUseCase
            val preview = Preview.Builder().build()
            preview.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

            val imageAnalysis = ImageAnalysis.Builder()
                // enable the following line if RGBA output is needed.
                // .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_RGBA_8888)
                .setBackpressureStrategy(ImageAnalysis.STRATEGY_KEEP_ONLY_LATEST)
                .build()
            imageAnalysis.setAnalyzer(
                imageAnalysisExecutor,
                ImageAnalysis.Analyzer { imageProxy ->
                    val mediaImage = imageProxy.image
                    if (mediaImage != null) {
                        val image = InputImage.fromMediaImage(
                            mediaImage,
                            imageProxy.imageInfo.rotationDegrees
                        )
                        for (detector in detectors) {
                            detector.detect(image)
                        }
                    }
                    imageProxy.close()
                }
            )

            // アウトカメラを設定
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // バインドされているカメラを解除
            cameraProvider.unbindAll()
            // カメラをライフサイクルにバインド
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                preview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
        }

        private fun unbindAllCamera() {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProviderFuture.cancel(true)
        }

        @SuppressLint("RestrictedApi")
        private fun releaseCamera() {
            val cameraProviderFuture = ProcessCameraProvider.getInstance(this)
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()
            cameraProvider.unbindAll()
            cameraProviderFuture.cancel(true)
            cameraProvider.shutdown()
        }

        /**
         * Get the angle by which an image must be rotated given the device's current
         * orientation.
         */
        @Throws(CameraAccessException::class)
        private fun getRotationCompensation(
            cameraId: String,
            activity: Activity,
            isFrontFacing: Boolean
        ): Int {
            val orientations = SparseIntArray()
            orientations.append(Surface.ROTATION_0, 0)
            orientations.append(Surface.ROTATION_90, 90)
            orientations.append(Surface.ROTATION_180, 180)
            orientations.append(Surface.ROTATION_270, 270)
            // Get the device's current rotation relative to its "native" orientation.
            // Then, from the ORIENTATIONS table, look up the angle the image must be
            // rotated to compensate for the device's rotation.
            val deviceRotation = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.R) {
                if (this.display == null) {
                    0
                } else {
                    this.display!!.rotation
                }
            } else {
                this.windowManager.defaultDisplay.rotation
            }
            var rotationCompensation = orientations.get(deviceRotation)

            // Get the device's sensor orientation.
            val cameraManager = activity.getSystemService(CAMERA_SERVICE) as CameraManager
            val sensorOrientation = cameraManager
                .getCameraCharacteristics(cameraId)
                .get(CameraCharacteristics.SENSOR_ORIENTATION)!!

            if (isFrontFacing) {
                rotationCompensation = (sensorOrientation + rotationCompensation) % 360
            } else { // back-facing
                rotationCompensation = (sensorOrientation - rotationCompensation + 360) % 360
            }
            return rotationCompensation
        }
    }
    