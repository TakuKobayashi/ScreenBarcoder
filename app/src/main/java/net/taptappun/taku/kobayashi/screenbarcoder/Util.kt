package net.taptappun.taku.kobayashi.screenbarcoder

import android.app.Activity
import android.os.Build
import android.view.View
import android.view.WindowInsets
import android.view.WindowInsetsController

class Util {
    companion object {
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
