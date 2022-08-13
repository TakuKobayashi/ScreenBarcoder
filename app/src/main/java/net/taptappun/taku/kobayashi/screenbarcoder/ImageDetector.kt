package net.taptappun.taku.kobayashi.screenbarcoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Size
import com.google.mlkit.vision.common.InputImage

abstract class ImageDetector<T> {
    protected var markingBitmap: Bitmap? = null

    fun release() {
        if (markingBitmap != null) {
            markingBitmap?.recycle()
            markingBitmap = null
        }
    }

    fun clear() {
        if (markingBitmap != null) {
            val size = Size(markingBitmap!!.width, markingBitmap!!.height)
            this.release()
            this.initRenderBitmap(size)
        }
    }

    fun initRenderBitmap(size: Size) {
        if (markingBitmap == null || markingBitmap!!.isRecycled) {
            markingBitmap = Bitmap.createBitmap(size.width, size.height, Bitmap.Config.ARGB_8888)
        }
    }

    fun renderMarkedBitmap(): Bitmap? {
        return markingBitmap
    }

    abstract fun detect(image: InputImage)
    protected abstract fun renderDetectMarks(detects: MutableList<T>)

    protected fun refreshRenderMarkedCanvas(): Canvas {
        clear()
        val canvas = if (markingBitmap == null) {
            Canvas()
        } else {
            Canvas(markingBitmap!!)
        }
        return canvas
    }
}
