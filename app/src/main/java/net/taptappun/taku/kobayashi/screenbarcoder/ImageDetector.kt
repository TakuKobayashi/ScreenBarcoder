package net.taptappun.taku.kobayashi.screenbarcoder

import android.graphics.Bitmap
import android.graphics.Canvas
import android.util.Size
import com.google.mlkit.vision.common.InputImage

abstract class ImageDetector<T> {
    private var markingBitmap: Bitmap? = null

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
    protected abstract fun renderDetectMarks(detects: MutableList<T>, inputImage: InputImage)

    public fun refreshRenderMarkedCanvas(): Canvas {
        clear()
        val canvas = if (markingBitmap == null) {
            Canvas()
        } else {
            Canvas(markingBitmap!!)
        }
        return canvas
    }

    protected fun calcScaleFactorX(inputImageWidth: Int): Float{
        if (markingBitmap != null) {
            return markingBitmap!!.width.toFloat() / inputImageWidth.toFloat()
        }
        return 1f
    }

    protected fun calcScaleFactorY(inputImageHeight: Int): Float{
        if (markingBitmap != null) {
            return markingBitmap!!.height.toFloat() / inputImageHeight.toFloat()
        }
        return 1f
    }
}
