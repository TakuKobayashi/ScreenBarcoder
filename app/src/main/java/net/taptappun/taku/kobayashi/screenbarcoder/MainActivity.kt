package net.taptappun.taku.kobayashi.screenbarcoder

import android.Manifest
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.core.app.ActivityCompat
import net.taptappun.taku.kobayashi.screenbarcoder.databinding.ActivityMainBinding

class MainActivity : ScreenScanCommonActivity() {
    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordStartButton = binding.recordStartButton
        recordStartButton.setOnClickListener { v ->
            checkCanDrawOverlay {
                val permissions = mutableListOf<String>()
                // WRITE_EXTERNAL_STORAGEはPie以下で必要
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
                checkAndRequestPermissions(permissions.toTypedArray()) {
                    val mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                    mediaProjectionStartActivityForResult.launch(mediaProjectionManager.createScreenCaptureIntent())
                }
            }

        }

        val recordStopButton = binding.recordStopButton
        recordStopButton.setOnClickListener { v ->
            val intent = Intent(this, ScreenRecordService::class.java)
            stopService(intent)
        }

        val cameraScanStartButton = binding.cameraScanStartButton
        cameraScanStartButton.setOnClickListener { v ->
            val permissions = mutableListOf<String>(Manifest.permission.CAMERA)
            // WRITE_EXTERNAL_STORAGEはPie以下で必要
            if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                permissions.add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
            }
            checkAndRequestPermissions(permissions.toTypedArray()) {
                val intent = Intent(this, CameraScanActivity::class.java)
                startActivity(intent)
            }
        }
    }

    /**
     * A native method that is implemented by the 'screenbarcoder' native library,
     * which is packaged with this application.
     */
    external fun stringFromJNI(): String


    companion object {
        // Used to load the 'screenbarcoder' library on application startup.
        init {
            System.loadLibrary("screenbarcoder")
        }
    }
}