package net.taptappun.taku.kobayashi.screenbarcoder

import android.Manifest
import android.app.Activity
import android.app.Service
import android.content.Intent
import android.content.pm.PackageManager
import android.media.projection.MediaProjectionManager
import android.net.Uri
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.widget.Button
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import net.taptappun.taku.kobayashi.screenbarcoder.databinding.ActivityMainBinding

abstract class BasicCommonActivity : AppCompatActivity() {
    // 参考:
    // https://buildersbox.corp-sansan.com/entry/2020/05/27/110000
    // https://qiita.com/yass97/items/62cccfad5190cc4d4fa6
    // onResume寄りまで定義しないとこんな感じのエラーがでてしまう
    // LifecycleOwner is attempting to register while current state is RESUMED. LifecycleOwners must call register before they are STARTED.
    private val mediaProjectionStartActivityForResult = registerForActivityResult(
        ActivityResultContracts.StartActivityForResult()) {
            result: ActivityResult ->
        if (result.resultCode == Activity.RESULT_OK && result.data != null) {
            val intent = Intent(this, ScreenRecordService::class.java)
            intent.putExtra("code", result.resultCode) //必要なのは結果。startActivityForResultのrequestCodeではない。
            intent.putExtra("data", result.data)
            //画面の大きさも一緒に入れる
            val metrics = resources.displayMetrics;
            intent.putExtra("height", metrics.heightPixels)
            intent.putExtra("width", metrics.widthPixels)
            intent.putExtra("dpi", metrics.densityDpi)

            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                startForegroundService(intent)
            } else {
                startService(intent);
            }
        }
    }

    private val settingsStartActivityForResult = registerForActivityResult(ActivityResultContracts.StartActivityForResult()) { result: ActivityResult ->
    }

    private lateinit var binding: ActivityMainBinding

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        binding = ActivityMainBinding.inflate(layoutInflater)
        setContentView(binding.root)

        val recordStartButton = findViewById<Button>(R.id.recordStartButton)
        recordStartButton.setOnClickListener { v ->
            if (allPermissionsGranted()) {
                val mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionStartActivityForResult.launch(mediaProjectionManager.createScreenCaptureIntent())
            } else {
                // permission許可要求
                ActivityCompat.requestPermissions(
                    this,
                    REQUIRED_PERMISSIONS,
                    REQUEST_CODE_PERMISSIONS
                )
            }

        }

        val recordStopButton = findViewById<Button>(R.id.recordStopButton)
        recordStopButton.setOnClickListener { v ->
            val intent = Intent(this, ScreenRecordService::class.java)
            stopService(intent)
        }

        // 参考: https://stackoverflow.com/questions/39911377/settings-candrawoverlays-for-api-23
        if (Build.VERSION.SDK_INT >= 23){
            if(Settings.canDrawOverlays(this)){
                //...
            } else {
                Toast.makeText(
                    this,
                    "画面上のオーバーレイする設定を有効にしてください",
                    Toast.LENGTH_SHORT
                ).show()
                // 許可されていない
                val intent = Intent(Settings.ACTION_MANAGE_OVERLAY_PERMISSION, Uri.parse("package:$packageName"));
                // 設定画面に移行
                settingsStartActivityForResult.launch(intent);
            }
        }
    }

    private fun allPermissionsGranted() = REQUIRED_PERMISSIONS.all {
        ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED
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
                val mediaProjectionManager = getSystemService(Service.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
                mediaProjectionStartActivityForResult.launch(mediaProjectionManager.createScreenCaptureIntent())
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

    companion object {
        public const val TAG = "AndroidScreenBarcoder"
        private const val REQUEST_CODE_PERMISSIONS = 10
        private val REQUIRED_PERMISSIONS =
            mutableListOf(
                Manifest.permission.RECORD_AUDIO
            ).apply {
                // WRITE_EXTERNAL_STORAGEはPie以下で必要
                if (Build.VERSION.SDK_INT <= Build.VERSION_CODES.P) {
                    add(Manifest.permission.WRITE_EXTERNAL_STORAGE)
                }
            }.toTypedArray()
    }
}