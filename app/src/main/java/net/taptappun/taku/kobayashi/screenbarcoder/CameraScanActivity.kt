package net.taptappun.taku.kobayashi.screenbarcoder

import android.annotation.SuppressLint
import android.os.Bundle
import android.view.OrientationEventListener
import android.view.Surface
import android.view.WindowManager
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
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraPreview: Preview? = null
    private lateinit var binding: ActivityCameraScanBinding
    private lateinit var detectMarkingSurfaceView: DetectMarkingSurfaceView

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)
        // スリープにさせない設定
        window.addFlags(WindowManager.LayoutParams.FLAG_KEEP_SCREEN_ON)
        // アプリ起動中は画面は回転しない設定
        Util.screenOrientationToLock(this)
        detectMarkingSurfaceView = binding.overlaySurfaceView
        for(detector in detectors){
            detectMarkingSurfaceView.setDetectors(detector)
        }
    }

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Util.requestFullScreenMode(this)
        }
    }

    override fun onResume() {
        super.onResume()
        detectMarkingSurfaceView.startRenderThread()
        startCamera()
        orientationEventListener.enable()
    }

    override fun onPause() {
        super.onPause()
        detectMarkingSurfaceView.stopRenderThread()
        unbindAllCamera()
        orientationEventListener.disable()
    }

    override fun onDestroy() {
        super.onDestroy()
        detectMarkingSurfaceView.stopRenderThread()
        for (detector in detectors) {
            detector.release()
        }
        releaseCamera()
    }

    private val orientationEventListener by lazy {
        object : OrientationEventListener(this) {
            override fun onOrientationChanged(orientation: Int) {
                if (orientation < 0) {
                    return
                }

                val rotation = when (orientation % 360) {
                    in 45 until 135 -> Surface.ROTATION_270
                    in 135 until 225 -> Surface.ROTATION_180
                    in 225 until 315 -> Surface.ROTATION_90
                    else -> Surface.ROTATION_0
                }

                imageAnalysis?.targetRotation = rotation
                cameraPreview?.targetRotation = rotation
            }
        }
    }

    private fun startCamera() {
        val cameraProviderFuture = ProcessCameraProvider.getInstance(this)

        cameraProviderFuture.addListener({
            // ライフサイクルにバインドするために利用する
            val cameraProvider: ProcessCameraProvider = cameraProviderFuture.get()

            // PreviewのUseCase
            cameraPreview = Preview.Builder().build()
            cameraPreview?.setSurfaceProvider(binding.cameraPreview.surfaceProvider)

            imageAnalysis = buildImageAnalysis()

            // アウトカメラを設定
            val cameraSelector = CameraSelector.DEFAULT_BACK_CAMERA
            // バインドされているカメラを解除
            cameraProvider.unbindAll()
            // カメラをライフサイクルにバインド
            cameraProvider.bindToLifecycle(
                this,
                cameraSelector,
                cameraPreview,
                imageAnalysis
            )
        }, ContextCompat.getMainExecutor(this))
    }

    @SuppressLint("UnsafeOptInUsageError")
    private fun buildImageAnalysis(): ImageAnalysis {
        val imageAnalysis = ImageAnalysis.Builder()
            // enable the following line if RGBA output is needed.
            .setOutputImageFormat(ImageAnalysis.OUTPUT_IMAGE_FORMAT_YUV_420_888)
            // .setOutputImageRotationEnabled(true)
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
                        /*
                        // mediaImageの内容を画面に表示するようにした処理
                        val markingCanvas = detector.refreshRenderMarkedCanvas()
                        if(mediaImage.planes != null){
                            val bitmapImage = Util.convertYuvImageToJpegBitmap(mediaImage)
                            markingCanvas.drawBitmap(Util.rotateBitmap(bitmapImage, image.rotationDegrees.toFloat()), 0f, 0f, null)
                            bitmapImage.recycle()
                        }
                        */
                        detector.detect(image)
                    }
                }
                imageProxy.close()
            }
        )
        return imageAnalysis
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
}
