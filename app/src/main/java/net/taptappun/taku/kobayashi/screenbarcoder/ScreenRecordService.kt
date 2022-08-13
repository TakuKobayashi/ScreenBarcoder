package net.taptappun.taku.kobayashi.screenbarcoder

import android.annotation.SuppressLint
import android.app.*
import android.content.ContentValues
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.hardware.display.DisplayManager
import android.hardware.display.VirtualDisplay
import android.media.ImageReader
import android.media.MediaCodecInfo
import android.media.MediaCodecList
import android.media.MediaRecorder
import android.media.projection.MediaProjection
import android.media.projection.MediaProjectionManager
import android.os.Build
import android.os.IBinder
import android.provider.MediaStore
import android.provider.Settings
import android.util.Log
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import java.io.FileDescriptor

// 参考: https://takusan23.github.io/Bibouroku/2020/04/06/MediaProjection/
class ScreenRecordService : Service() {
    // 画面録画で使う
    lateinit var mediaRecorder: MediaRecorder
    lateinit var projectionManager: MediaProjectionManager
    lateinit var projection: MediaProjection
    lateinit var virtualDisplay: VirtualDisplay
    lateinit var imageReader: ImageReader
    private lateinit var overlayView: View
    private lateinit var windowManager: WindowManager

    override fun onCreate() {
        super.onCreate()
        val layoutInflater = LayoutInflater.from(this)

        // レイアウトファイルからInfalteするViewを作成
        overlayView = layoutInflater.inflate(R.layout.overlay_view, null)
        windowManager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
    }

    override fun onBind(intent: Intent?): IBinder? {
        Log.d(ScreenScanCommonActivity.TAG, "onBind")
        return null
    }

    override fun onStartCommand(intent: Intent, flags: Int, startId: Int): Int {
        Log.d(ScreenScanCommonActivity.TAG, "onStartCommand")
        // 通知を出す。
        val notificationManager = getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        val notificationBuilder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            // 通知チャンネル
            val channelID = "rec_notify"
            // 通知チャンネルが存在しないときは登録する
            if (notificationManager.getNotificationChannel(channelID) == null) {
                val channel = NotificationChannel(
                    channelID,
                    "録画サービス起動中通知",
                    NotificationManager.IMPORTANCE_HIGH
                )
                notificationManager.createNotificationChannel(channel)
            }
            Notification.Builder(applicationContext, channelID)
        } else {
            Notification.Builder(applicationContext)
        }
        // 通知作成

        val notification = notificationBuilder
            .setContentText("録画中です。")
            .setContentTitle("画面録画")
            .build()

        startForeground(1, notification)
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            showWindowOverlay()
        }
        startRec(intent)
        return START_NOT_STICKY
    }

    private fun showWindowOverlay() {
        val windowType = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            // WindowManager.LayoutParams.TYPE_SYSTEM_ALERT
            WindowManager.LayoutParams.TYPE_SYSTEM_OVERLAY
        }
        val layoutParams = WindowManager.LayoutParams(
            windowType, // Overlay レイヤに表示
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE // フォーカスを奪わない
                or WindowManager.LayoutParams.FLAG_NOT_TOUCHABLE // 画面の操作を無効化(タップを受け付けない)
                or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL // これがないとedittextをおしてもキーボードが反応しないようだ
                or WindowManager.LayoutParams.FLAG_LAYOUT_NO_LIMITS, // 画面外への拡張を許可
            // viewを透明にする
            PixelFormat.TRANSLUCENT
        )
        windowManager.addView(overlayView, layoutParams)
    }

    // 録画開始
    @SuppressLint("WrongConstant")
    private fun startRec(intent: Intent) {
        val data: Intent? = intent.getParcelableExtra("data")
        val code = intent.getIntExtra("code", Activity.RESULT_OK)
        // 画面の大きさ
        val height = intent.getIntExtra("height", 1000)
        val width = intent.getIntExtra("width", 1000)
        val dpi = intent.getIntExtra("dpi", 1000)
        if (data != null) {
            projectionManager =
                getSystemService(Context.MEDIA_PROJECTION_SERVICE) as MediaProjectionManager
            // Service上でMediaProjectionを行う場合AndroidMannifest.xmlで以下の項目をいれないとエラーが発生しちゃう
            // android:foregroundServiceType="mediaProjection"
            // 参考: https://stackoverflow.com/questions/61276730/media-projections-require-a-foreground-service-of-type-serviceinfo-foreground-se

            // codeはActivity.RESULT_OKとかが入る。
            projection = projectionManager.getMediaProjection(code, data)
            imageReader = ImageReader.newInstance(width, height, PixelFormat.RGBA_8888, 2)
            imageReader.setOnImageAvailableListener(imageReaderListener, null)

            mediaRecorder = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
                MediaRecorder(this)
            } else {
                MediaRecorder()
            }
            mediaRecorder.setAudioSource(MediaRecorder.AudioSource.MIC)
            mediaRecorder.setVideoSource(MediaRecorder.VideoSource.SURFACE)
            mediaRecorder.setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            mediaRecorder.setVideoEncoder(MediaRecorder.VideoEncoder.H264)
            mediaRecorder.setAudioEncoder(MediaRecorder.AudioEncoder.AMR_NB)
            mediaRecorder.setVideoEncodingBitRate(1080 * 10000) // 1080は512ぐらいにしといたほうが小さくできる
            mediaRecorder.setVideoFrameRate(30)
            mediaRecorder.setVideoSize(width, height)
            mediaRecorder.setAudioSamplingRate(44100)
            val fileDescriptor = getWillSaveFileDescriptor()
            mediaRecorder.setOutputFile(fileDescriptor)
            // surfaceをいれるためにはMediaCodicで録画できるように試みる必要がある
            // https://developer.android.com/reference/android/media/MediaCodec
            // じゃないと java.lang.IllegalArgumentException: not a PersistentSurface というエラーが出る
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
//                mediaRecorder.setInputSurface(imageReader.surface)
            }
            mediaRecorder.prepare()

            /*
            val mediaCodecInfo = selectCodec(MIME_TYPE)
            val codec = MediaCodec.createByCodecName(mediaCodecInfo?.name.toString());
            codec.setCallback(object : MediaCodec.Callback() {
                override fun onInputBufferAvailable(codec: MediaCodec, index: Int) {
                    TODO("Not yet implemented")
                }

                override fun onOutputBufferAvailable(
                    codec: MediaCodec,
                    index: Int,
                    info: MediaCodec.BufferInfo
                ) {
                    TODO("Not yet implemented")
                }

                override fun onError(codec: MediaCodec, e: MediaCodec.CodecException) {
                    TODO("Not yet implemented")
                }

                override fun onOutputFormatChanged(codec: MediaCodec, format: MediaFormat) {
                    TODO("Not yet implemented")
                }
            })

            val format = MediaFormat.createVideoFormat(MIME_TYPE, width, height)
            format.setInteger(
                MediaFormat.KEY_COLOR_FORMAT,
                MediaCodecInfo.CodecCapabilities.COLOR_FormatYUV420Flexible
            )
            format.setInteger(MediaFormat.KEY_BIT_RATE, 1080 * 10000)
            format.setInteger(MediaFormat.KEY_FRAME_RATE, 30)
            format.setInteger(MediaFormat.KEY_I_FRAME_INTERVAL, 0)
            codec.configure(format, null, null, MediaCodec.CONFIGURE_FLAG_ENCODE)
            codec.start();
            val mediaMuxer = new MediaMuxer(outputFile, MediaMuxer.OutputFormat.MUXER_OUTPUT_MPEG_4);
            */

            // DISPLAYMANAGERの仮想ディスプレイ表示条件
            // VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR	コンテンツをミラーリング表示する
            // VIRTUAL_DISPLAY_FLAG_OWN_CONTENT_ONLY	独自のコンテンツを表示。ミラーリングしない
            // VIRTUAL_DISPLAY_FLAG_PRESENTATION	プレゼンテーションモード
            // VIRTUAL_DISPLAY_FLAG_PUBLIC	HDMIやWirelessディスプレイ
            // VIRTUAL_DISPLAY_FLAG_SECURE	暗号化対策が施されたセキュアなディスプレイ
            // https://techbooster.org/android/application/17026/
            virtualDisplay = projection.createVirtualDisplay(
                "recode",
                width,
                height,
                dpi,
                DisplayManager.VIRTUAL_DISPLAY_FLAG_AUTO_MIRROR,
                mediaRecorder.surface,
                // imageReader.surface,
                object : VirtualDisplay.Callback() {
                    override fun onPaused() {
                        Log.d(ScreenScanCommonActivity.TAG, "VirtualDisplay onPaused")
                    }

                    override fun onResumed() {
                        Log.d(ScreenScanCommonActivity.TAG, "VirtualDisplay onResumed")
                    }

                    override fun onStopped() {
                        Log.d(ScreenScanCommonActivity.TAG, "VirtualDisplay onStopped")
                    }
                },
                null
            )

            // 開始
            mediaRecorder.start()
        }
    }

    // 録画止める
    private fun stopRec() {
        // 何にも録画していないのにstartしているとstopの時に stop failed. というエラーが出ちゃう
        // mediaRecorder.stop()
        mediaRecorder.release()
        imageReader.close()
        virtualDisplay.release()
        projection.stop()
    }

    private val imageReaderListener = ImageReader.OnImageAvailableListener { reader: ImageReader ->
        val image = reader.acquireLatestImage()
        Log.d(ScreenScanCommonActivity.TAG, "width:${image.width} height:${image.height} planeSize:${image.planes.size}")
        for (imagePlane in image.planes) {
            Log.d(ScreenScanCommonActivity.TAG, "rowStride:${imagePlane.rowStride} pixelStride:${imagePlane.pixelStride}")
        }
        /*
        val planes = image.planes
        val yPlane = planes[0]
        val uPlane = planes[1]
        val vPlane = planes[2]
        val mBuffer: ByteArray = yuvToBuffer(
            yPlane.getBuffer(),
            uPlane.getBuffer(),
            vPlane.getBuffer(),
            yPlane.getPixelStride(),
            yPlane.getRowStride(),
            uPlane.getPixelStride(),
            uPlane.getRowStride(),
            vPlane.getPixelStride(),
            vPlane.getRowStride(),
            image.width,
            image.height
        )
        mQueue.add(MyData(mBuffer, image.timestamp, false))
        */
        image.close()
    }

    // 保存先取得。今回は対象範囲別ストレージに保存する
    // API Level 29からの新しい動画ファイルの保存方法を実装するとこうなった
    // https://star-zero.medium.com/android-q%E3%81%AEscoped-storage%E3%81%AB%E3%82%88%E3%82%8B%E5%A4%89%E6%9B%B4-afe41cde9f35
    private fun getWillSaveFileDescriptor(): FileDescriptor {
        val resolver = applicationContext.contentResolver
        // Find all audio files on the primary external storage device.
        val videoCollection = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
            MediaStore.Video.Media.getContentUri(
                MediaStore.VOLUME_EXTERNAL_PRIMARY
            )
        } else {
            MediaStore.Video.Media.EXTERNAL_CONTENT_URI
        }
        val videoDetails = ContentValues()
        videoDetails.put(MediaStore.Video.Media.DISPLAY_NAME, "${System.currentTimeMillis()}.mp4")
        val videoContentUri = resolver.insert(videoCollection, videoDetails)
        val parcelFileDescriptor = resolver.openFileDescriptor(videoContentUri!!, "rw")
        return parcelFileDescriptor!!.fileDescriptor
    }

    // Service終了と同時に録画終了
    override fun onDestroy() {
        super.onDestroy()
        Log.d(ScreenScanCommonActivity.TAG, "onDestroy")
        if (Build.VERSION.SDK_INT < 23 || Settings.canDrawOverlays(this)) {
            windowManager.removeView(overlayView)
        }
        stopRec()
    }

    private fun selectCodec(mimeType: String): MediaCodecInfo? {
        val codecInfos = MediaCodecList(MediaCodecList.ALL_CODECS).codecInfos
        for (codecInfo in codecInfos) {
            if (!codecInfo.isEncoder) {
                continue
            }
            val types = codecInfo.supportedTypes
            for (j in types.indices) {
                if (types[j].equals(mimeType, ignoreCase = true)) {
                    return codecInfo
                }
            }
        }
        return null
    }

    companion object {
        private const val MIME_TYPE = "video/avc" // H.264 Advanced Video Coding
    }
}
