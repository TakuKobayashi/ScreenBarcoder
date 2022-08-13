package net.taptappun.taku.kobayashi.screenbarcoder

import android.app.Activity
import android.content.Intent
import android.content.pm.PackageManager
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.activity.result.ActivityResult
import androidx.activity.result.contract.ActivityResultContracts
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat

abstract class ScreenScanCommonActivity : AppCompatActivity() {

    private val requestPermissionNamesStash = mutableSetOf<String>()
    private var canDrawOverlayCallback: (() -> Unit)? = null
    private var permissionGrantedCallback: ((permissionNames: Array<String>) -> Unit)? = null

    // 参考:
    // https://buildersbox.corp-sansan.com/entry/2020/05/27/110000
    // https://qiita.com/yass97/items/62cccfad5190cc4d4fa6
    // onResume寄りまで定義しないとこんな感じのエラーがでてしまう
    // LifecycleOwner is attempting to register while current state is RESUMED. LifecycleOwners must call register before they are STARTED.
    protected val mediaProjectionStartActivityForResult = registerForActivityResult(
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
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                canDrawOverlayCallback?.invoke()
            }
        }else{
            canDrawOverlayCallback?.invoke()
        }
    }

    protected fun checkAndRequestPermissions(permissionNames: Array<String>, permissionAllGrantedCallback: (permissionNames: Array<String>) -> Unit){
        requestPermissionNamesStash.clear()
        if (permissionsGranted(permissionNames)) {
            permissionAllGrantedCallback(permissionNames)
        } else {
            requestPermissionNamesStash.addAll(permissionNames)
            permissionGrantedCallback = permissionAllGrantedCallback
            // permission許可要求
            ActivityCompat.requestPermissions(
                this,
                permissionNames,
                REQUEST_CODE_PERMISSIONS
            )
        }
    }

    private fun permissionsGranted(permissionNames: Array<String>): Boolean {
        return permissionNames.all { ContextCompat.checkSelfPermission(baseContext, it) == PackageManager.PERMISSION_GRANTED }
    }

    override fun onRequestPermissionsResult(
        requestCode: Int,
        permissions: Array<String>,
        grantResults:
        IntArray
    ) {
        super.onRequestPermissionsResult(requestCode, permissions, grantResults)
        if (requestCode == REQUEST_CODE_PERMISSIONS) {
            if (permissionsGranted(requestPermissionNamesStash.toTypedArray())) {
                permissionGrantedCallback?.invoke(requestPermissionNamesStash.toTypedArray())
            } else {
                Toast.makeText(
                    this,
                    "Permissions not granted by the user.",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }

    protected fun checkCanDrawOverlay(gratedCallback: () -> Unit) {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            if (Settings.canDrawOverlays(this)) {
                gratedCallback.invoke()
            } else {
                canDrawOverlayCallback = gratedCallback
                Toast.makeText(
                    this,
                    "画面上のオーバーレイする設定を有効にしてください",
                    Toast.LENGTH_SHORT
                ).show()
                // 許可されていない
                val intent = Intent(
                    Settings.ACTION_MANAGE_OVERLAY_PERMISSION,
                    Uri.parse("package:${this.packageName}")
                );
                // 設定画面に移行
                settingsStartActivityForResult.launch(intent);
            }
        }
    }

    companion object {
        public const val TAG = "AndroidScreenBarcoder"
        private const val REQUEST_CODE_PERMISSIONS = 10
    }
}