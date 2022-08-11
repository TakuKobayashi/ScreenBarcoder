package net.taptappun.taku.kobayashi.screenbarcoder

import android.Manifest
import android.annotation.SuppressLint
import android.app.Activity
import android.content.pm.PackageManager
import android.hardware.camera2.CameraAccessException
import android.hardware.camera2.CameraCharacteristics
import android.hardware.camera2.CameraManager
import android.os.Build
import android.os.Bundle
import android.util.SparseIntArray
import android.view.Surface
import android.view.SurfaceHolder
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.camera.core.CameraSelector
import androidx.camera.core.ImageAnalysis
import androidx.camera.core.Preview
import androidx.camera.lifecycle.ProcessCameraProvider
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import com.google.mlkit.vision.common.InputImage
import net.taptappun.taku.kobayashi.screenbarcoder.databinding.ActivityCameraScanBinding
import java.util.concurrent.ExecutorService
import java.util.concurrent.Executors

class CameraScanActivity : AppCompatActivity() {

    private val imageAnalysisExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private val surfaceViewExecutor: ExecutorService by lazy {
        Executors.newSingleThreadExecutor()
    }
    private val detectors = setOf(
        BarcodeImageDetector(),
    )
    private lateinit var binding: ActivityCameraScanBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityCameraScanBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val holder = binding.overlaySurfaceView.holder
        holder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
            }

            override fun surfaceChanged(holder: SurfaceHolder, format: Int, width: Int, height: Int) {
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
            }
        })

        if (allPermissionsGranted()) {
            // permissionは得られているので、カメラ始動
            startCamera()
        } else {
            // permission許可要求
            ActivityCompat.requestPermissions(
                this,
                REQUIRED_PERMISSIONS,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults:
            IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (allPermissionsGranted()) {
                // カメラ開始処理
                startCamera()
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
                finish()
            }
        }
    }

    override fun onDestroy() {
        super.onDestroy()
        for (detector in detectors) {
            detector.release()
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
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
                        val image = InputImage.fromMediaImage(mediaImage, imageProxy.imageInfo.rotationDegrees)
                        for (detector in detectors) {
                            detector.detect(image)
                        }
                        // Pass image to an ML Kit Vision API
                    }
                    // insert your code here.
                    // after done, release the ImageProxy object
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

    companion object {
        public const val TAG = "AndroidScreenBarcoder"
        private const val REQUEST_CODE_PERMISSIONS = 10
        // 必要なpermissionのリスト
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.CAMERA,
                Manifest.permission.RECORD_AUDIO
            ).apply {
                // WRITE_EXTERNAL_STORAGEはPie以下で必要
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
    }

    