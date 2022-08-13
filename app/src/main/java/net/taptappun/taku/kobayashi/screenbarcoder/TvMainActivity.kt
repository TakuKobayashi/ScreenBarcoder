package net.taptappun.taku.kobayashi.screenbarcoder

import android.Manifest
import android.app.Service
import android.content.Intent
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.Bundle
import net.taptappun.taku.kobayashi.screenbarcoder.databinding.TvactivityMainBinding

class TvMainActivity : ScreenScanCommonActivity() {
    private lateinit var binding: TvactivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = TvactivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordStartButton = binding.recordStartButtonTV
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

        val recordStopButton = binding.recordStopButtonTV
        recordStopButton.setOnClickListener { v ->
            val intent = Intent(this, ScreenRecordService::class.java)
            stopService(intent)
        }
    }
}
