package com.gozdor.measure

import android.graphics.Bitmap
import org.opencv.android.Utils
import org.opencv.core.CvType
import org.opencv.core.Mat
import org.opencv.core.MatOfPoint
import org.opencv.core.MatOfPoint2f
import org.opencv.core.Point
import org.opencv.core.Scalar
import org.opencv.core.Size
import org.opencv.imgproc.Imgproc
import kotlin.math.max

object OutlineDetector {
    /** Set by MainActivity after OpenCV's native library loads. */
    var ready = false

    /** Detection runs on a downscaled copy; the user refines vertices on the full image. */
    private const val WORK_SIZE = 1600.0
    private const val GROW = 3

    /** Returns the outline (image pixel coordinates) of the object under [tap], or null if none is found. */
    fun detect(bitmap: Bitmap, tap: Pt): List<Pt>? {
        val s = minOf(1.0, WORK_SIZE / max(bitmap.width, bitmap.height))
        val rgba = Mat()
        val gray = Mat()
        val edges = Mat()
        try {
            Utils.bitmapToMat(bitmap, rgba)
            Imgproc.cvtColor(rgba, gray, Imgproc.COLOR_RGBA2GRAY)
            if (s < 1.0) Imgproc.resize(gray, gray, Size(), s, s, Imgproc.INTER_AREA)
            Imgproc.GaussianBlur(gray, gray, Size(5.0, 5.0), 0.0)

            // Canny thresholds from Otsu, so they adapt to the photo's contrast.
            val otsu = Imgproc.threshold(gray, Mat(), 0.0, 255.0, Imgproc.THRESH_BINARY or Imgproc.THRESH_OTSU)
            Imgproc.Canny(gray, edges, otsu * 0.5, otsu)

            // Grow edges to close small gaps, fill the blob under the tap, then shrink it back
            // by the same amount so the outline isn't biased outward.
            val kernel = Imgproc.getStructuringElement(Imgproc.MORPH_RECT, Size(3.0, 3.0))
            Imgproc.dilate(edges, edges, kernel, Point(-1.0, -1.0), GROW)

            val tapS = Point(tap.x * s, tap.y * s)
            val imageArea = gray.rows().toDouble() * gray.cols()
            val blob = externalContours(edges).firstOrNull {
                Imgproc.pointPolygonTest(MatOfPoint2f(*it.toArray()), tapS, false) >= 0
            } ?: return null
            if (Imgproc.contourArea(blob) > 0.95 * imageArea) return null

            val mask = Mat.zeros(gray.size(), CvType.CV_8UC1)
            Imgproc.drawContours(mask, listOf(blob), -1, Scalar(255.0), Imgproc.FILLED)
            Imgproc.erode(mask, mask, kernel, Point(-1.0, -1.0), GROW)
            val outline = externalContours(mask).maxByOrNull { Imgproc.contourArea(it) } ?: return null
            mask.release()
            if (Imgproc.contourArea(outline) < 0.0005 * imageArea) return null

            val curve = MatOfPoint2f(*outline.toArray())
            val simplified = MatOfPoint2f()
            Imgproc.approxPolyDP(curve, simplified, 0.003 * Imgproc.arcLength(curve, true), true)
            return simplified.toArray().map { Pt(it.x / s, it.y / s) }.takeIf { it.size >= 3 }
        } finally {
            rgba.release(); gray.release(); edges.release()
        }
    }

    private fun externalContours(img: Mat): List<MatOfPoint> {
        val out = ArrayList<MatOfPoint>()
        Imgproc.findContours(img, out, Mat(), Imgproc.RETR_EXTERNAL, Imgproc.CHAIN_APPROX_SIMPLE)
        return out
    }
}
