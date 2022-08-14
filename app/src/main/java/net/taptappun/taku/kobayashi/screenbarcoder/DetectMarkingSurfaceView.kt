package net.taptappun.taku.kobayashi.screenbarcoder

import android.content.Context
import android.graphics.Paint
import android.graphics.PixelFormat
import android.util.AttributeSet
import android.util.Log
import android.util.Size
import android.view.SurfaceHolder
import android.view.SurfaceView

class DetectMarkingSurfaceView : SurfaceView {
    private var surfaceViewThread: Thread? = null
    private var isSurfaceRendering = false
    private val detectors = mutableSetOf<ImageDetector<out Any>>()

    constructor(context: Context): super(context) {
        init()
    }

    constructor(context: Context, attrs: AttributeSet): super(context, attrs) {
        init()
    }

    private fun init() {
        val surfaceHolder = holder
        // OverlayしているSurfaceViewの背景を透過させないとカメラ画面が表示されない
        // SurfaceHolder.setFormat(PixelFormat.TRANSLUCENT)は背景を透過する設定
        surfaceHolder.setFormat(PixelFormat.TRANSLUCENT)
        this.setZOrderMediaOverlay(true)
        surfaceHolder.addCallback(object : SurfaceHolder.Callback {
            override fun surfaceCreated(holder: SurfaceHolder) {
                startRenderThread()
            }

            override fun surfaceChanged(
                holder: SurfaceHolder,
                format: Int,
                width: Int,
                height: Int
            ) {
                startRenderThread()
                Log.d(ScreenScanCommonActivity.TAG, "surfaceChangedWidth:$width surfaceChangedHeight:$height")
                for (detector in detectors) {
                    detector.initRenderBitmap(Size(width, height))
                }
            }

            override fun surfaceDestroyed(holder: SurfaceHolder) {
                for (detector in detectors) {
                    detector.release()
                }
                stopRenderThread()
            }
        })
    }

    fun startRenderThread() {
        isSurfaceRendering = true
        if (surfaceViewThread != null) {
            return
        }
        surfaceViewThread = Thread {
            while (isSurfaceRendering) {
                val canvas = holder.lockCanvas()
                if (canvas != null) {
//                    val paint = Paint()
//                    paint.color = Color.RED
//                    canvas.drawRect(Rect(canvas.width / 2 - 100, canvas.height / 2 - 100, canvas.width / 2 + 100, canvas.height / 2 + 100), paint)
                    for (detector in detectors) {
                        val paint = Paint()
                        val renderMarkedBitmap = detector.renderMarkedBitmap()
                        if (renderMarkedBitmap != null) {
                            canvas.drawBitmap(renderMarkedBitmap, 0f, 0f, paint)
                        }
                    }
                    holder.unlockCanvasAndPost(canvas)
                }
            }
        }
        surfaceViewThread?.start()
    }

    fun setDetectors(vararg detector: ImageDetector<out Any>){
        detectors.addAll(detector)
    }

    fun stopRenderThread(){
        isSurfaceRendering = false
        surfaceViewThread = null
    }
}