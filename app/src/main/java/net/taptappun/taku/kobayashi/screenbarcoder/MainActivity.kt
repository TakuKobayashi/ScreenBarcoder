package net.taptappun.taku.kobayashi.screenbarcoder

import android.os.Bundle

class MainActivity : ScreenScanCommonActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
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