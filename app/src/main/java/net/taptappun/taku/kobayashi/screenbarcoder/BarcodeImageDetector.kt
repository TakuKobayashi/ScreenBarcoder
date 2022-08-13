package net.taptappun.taku.kobayashi.screenbarcoder

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
        scanner.process(image).addOnSuccessListener { barcodes -> renderDetectMarks(barcodes) }.addOnFailureListener { e ->
            // Task failed with an exception
        }
    }

    override fun renderDetectMarks(detects: MutableList<Barcode>) {
        // Task completed successfully
        // [START_EXCLUDE]
        // [START get_barcodes]
        for (barcode in detects) {
            val bounds = barcode.boundingBox
            val corners = barcode.cornerPoints
            val rawValue = barcode.rawValue
            val valueType = barcode.valueType
            Log.d(ScreenScanCommonActivity.TAG, "barCodeBounds:$bounds barCodeRawValue:$rawValue barcodeValueType:$valueType barcodeCornersCount:$corners")
            // See API reference for complete list of supported types
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
        }
        // [END get_barcodes]
        // [END_EXCLUDE]
    }
}
