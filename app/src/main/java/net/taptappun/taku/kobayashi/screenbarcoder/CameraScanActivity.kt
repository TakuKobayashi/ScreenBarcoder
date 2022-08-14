package net.taptappun.taku.kobayashi.screenbarcoder

import android.annotation.SuppressLint
import android.graphics.Paint
import android.graphics.PixelFormat
import android.os.Bundle
import android.util.Log
import android.util.Size
import android.view.OrientationEventListener
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
    private var imageAnalysis: ImageAnalysis? = null
    private var cameraPreview: Preview? = null
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
                Log.d(ScreenScanCommonActivity.TAG, "surfaceChangedWidth:$width surfaceChangedHeight:$height")
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

    override fun onWindowFocusChanged(hasFocus: Boolean) {
        super.onWindowFocusChanged(hasFocus)
        if (hasFocus) {
            // Util.requestFullScreenMode(this)
        }
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
        orientationEventListener.enable()
    }

    override fun onPause() {
        super.onPause()
        isSurfaceRendering = false
        surfaceViewThread = null
        unbindAllCamera()
        orientationEventListener.disable()
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
