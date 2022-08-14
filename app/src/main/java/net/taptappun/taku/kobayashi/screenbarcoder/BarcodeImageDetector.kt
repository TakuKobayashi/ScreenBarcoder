package net.taptappun.taku.kobayashi.screenbarcoder

import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.RectF
import android.util.Log
import com.google.mlkit.vision.barcode.BarcodeScanning
import com.google.mlkit.vision.barcode.common.Barcode
import com.google.mlkit.vision.common.InputImage

class BarcodeImageDetector : ImageDetector<Barcode>() {
    public override fun detect(image: InputImage) {
        // [START set_detector_options]
        // Format: https://zenn.dev/mochico/articles/0c1f1104852659
        // https://developers.google.com/ml-kit/vision/barcode-scanning/android
        /*
        val options = BarcodeScannerOptions.Builder()
            .setBarcodeFormats(
                Barcode.FORMAT_QR_CODE,
                Barcode.FORMAT_AZTEC)
            .build()
        */
        // [END set_detector_options]

        // [START get_detector]
        val scanner = BarcodeScanning.getClient()
        // Or, to specify the formats to recognize:
//        val scanner = BarcodeScanning.getClient(options)
        // [END get_detector]
        // [START run_detector]
        scanner
            .process(image)
            .addOnSuccessListener { barcodes -> renderDetectMarks(barcodes, image) }
            .addOnFailureListener { e ->
                // Task failed with an exception
            }
    }

    override fun renderDetectMarks(detects: MutableList<Barcode>, inputImage: InputImage) {
        Log.d(ScreenScanCommonActivity.TAG, "scan!!")
        val markingCanvas = refreshRenderMarkedCanvas()

        for (barcode in detects) {
            val bounds = barcode.boundingBox
            val corners = barcode.cornerPoints
            val rawValue = barcode.rawValue
            val valueType = barcode.valueType
//            Log.d(ScreenScanCommonActivity.TAG, "barCodeBounds:$bounds barCodeRawValue:$rawValue barcodeValueType:$valueType barcodeCornersCount:$corners")
            when (valueType) {
                Barcode.TYPE_WIFI -> {
                    val ssid = barcode.wifi!!.ssid
                    val password = barcode.wifi!!.password
                    val type = barcode.wifi!!.encryptionType
                }
                Barcode.TYPE_URL -> {
                    val title = barcode.url!!.title
                    val url = barcode.url!!.url
                    Log.d(ScreenScanCommonActivity.TAG, "barCodeTitle:$title barCodeUrl:$url")
                }
            }
            renderMarkings(markingCanvas, barcode, inputImage)
        }
        // [END get_barcodes]
        // [END_EXCLUDE]
    }

    private fun renderMarkings(markingRenderCanvas: Canvas, barcode: Barcode, inputImage: InputImage) {
        val bounds = barcode.boundingBox
        val rawValue = barcode.rawValue
        val rotateInputImageRect = Util.rotateRect(
            RectF(0f, 0f, inputImage.width.toFloat(), inputImage.height.toFloat()),
            inputImage.rotationDegrees.toFloat()
        )
        val rotateBounds = Util.rotateRect(RectF(bounds), inputImage.rotationDegrees.toFloat())
        val scaleFactorX = calcScaleFactorX(rotateInputImageRect.width())
        val scaleFactorY = calcScaleFactorY(rotateInputImageRect.height())
        val drawBound = RectF(rotateBounds)
        drawBound.left *= scaleFactorX
        drawBound.top *= scaleFactorY
        drawBound.right *= scaleFactorX
        drawBound.bottom *= scaleFactorY
        // Log.d(ScreenScanCommonActivity.TAG, "bounds:$bounds rotateBounds:$rotateBounds drawBound:$drawBound")
        if (bounds != null) {
            val barcodeRectPaint = Paint()
            // #66cdaa: mediumaquamarine
            barcodeRectPaint.color = Color.rgb(102, 205, 170)
            markingRenderCanvas.drawRect(drawBound, barcodeRectPaint)
        }

        if (bounds != null && rawValue != null) {
            val textPaint = Paint()
            textPaint.color = Color.BLACK
            markingRenderCanvas.drawText(rawValue, drawBound.centerX().toFloat(), drawBound.bottom.toFloat(), textPaint)
        }
    }
}
