package net.taptappun.taku.kobayashi.screenbarcoder

import android.app.Activity
import android.graphics.*
import android.media.Image
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController
import androidx.core.graphics.toRect
import java.io.ByteArrayOutputStream
import java.nio.ByteBuffer

class Util {
    companion object {
        fun convertYuvImageToJpegBitmap(image: Image): Bitmap {
            val planes = image.planes
            val yBuffer: ByteBuffer = planes[0].buffer
            val uBuffer: ByteBuffer = planes[1].buffer
            val vBuffer: ByteBuffer = planes[2].buffer
            val ySize: Int = yBuffer.remaining()
            val uSize: Int = uBuffer.remaining()
            val vSize: Int = vBuffer.remaining()
            val nv21 = ByteArray(ySize + uSize + vSize)
            yBuffer.get(nv21, 0, ySize)
            vBuffer.get(nv21, ySize, vSize)
            uBuffer.get(nv21, ySize + vSize, uSize)
            val yuvImage = YuvImage(nv21, ImageFormat.NV21, image.width, image.height, null)
            val out = ByteArrayOutputStream()
            yuvImage.compressToJpeg(Rect(0, 0, yuvImage.width, yuvImage.height), 100, out)
            val imageBytes: ByteArray = out.toByteArray()
            return BitmapFactory.decodeByteArray(imageBytes, 0, imageBytes.size, null)
        }

        // https://gist.github.com/kwmt/60964abd7eecbf0dc384c441abab0912
        fun rotateRect(source: RectF, degree: Float): Rect {
            val matrix = Matrix()
            matrix.setRotate(degree, source.centerX(), source.centerY())
            matrix.mapRect(source)
            return source.toRect()
        }

        fun rotateBitmap(source: Bitmap, angle: Float): Bitmap {
            val matrix = Matrix()
            matrix.postRotate(angle)
            return Bitmap.createBitmap(source, 0, 0, source.width, source.height, matrix, true)
        }

        // ナビゲーションバーとステータスバーを隠したフルスクリーンモードにする処理
        fun requestFullScreenMode(activity: Activity) {
            if (Build.VERSION.SDK_INT < 30) {
                // View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY で外部からスワイプしないとナビゲーションバーが出てこなくなる
                // View.SYSTEM_UI_FLAG_FULLSCREEN でステータスバーを非表示にする
                // View.SYSTEM_UI_FLAG_HIDE_NAVIGATION でナビゲーションバーを非表示にする
                // View.SYSTEM_UI_FLAG_LAYOUT_STABLE でナビゲーションバーが非表示になった時にレイアウトが崩れないようにする。
                // View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN ステータスバーが非表示になった時にレイアウトが崩れないようにする。
                val decorView = activity.window.decorView
                val systemUiVisibilityFlags = if (Build.VERSION.SDK_INT < 19) {
                    (
                        View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
                } else {
                    (
                        View.SYSTEM_UI_FLAG_IMMERSIVE_STICKY
                            or View.SYSTEM_UI_FLAG_FULLSCREEN
                            or View.SYSTEM_UI_FLAG_HIDE_NAVIGATION
                            or View.SYSTEM_UI_FLAG_LAYOUT_STABLE
                            or View.SYSTEM_UI_FLAG_LAYOUT_FULLSCREEN
                        )
                }

                decorView.systemUiVisibility = systemUiVisibilityFlags
            } else {
                activity.window.insetsController?.hide(WindowInsets.Type.statusBars() or WindowInsets.Type.navigationBars())
                activity.window.insetsController?.systemBarsBehavior = WindowInsetsController.BEHAVIOR_SHOW_TRANSIENT_BARS_BY_SWIPE
            }
        }
    }
}
