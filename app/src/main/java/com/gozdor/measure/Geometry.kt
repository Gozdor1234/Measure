package com.gozdor.measure

import java.util.Locale
import kotlin.math.PI
import kotlin.math.hypot
import kotlin.math.max
import kotlin.math.min
import kotlin.math.sqrt

/** A point in image pixel coordinates. */
data class Pt(val x: Double, val y: Double) {
    operator fun plus(o: Pt) = Pt(x + o.x, y + o.y)
    operator fun minus(o: Pt) = Pt(x - o.x, y - o.y)
    operator fun times(k: Double) = Pt(x * k, y * k)
    fun dist(o: Pt) = hypot(x - o.x, y - o.y)
}

/** Smallest-area bounding rectangle. Lengths in pixels, length >= width. */
data class RotatedRect(val length: Double, val width: Double, val corners: List<Pt>)

data class Circle(val center: Pt, val radius: Double)

enum class LengthUnit(val label: String, val mmPer: Double, private val decimals: Int, private val areaDecimals: Int) {
    MM("mm", 1.0, 1, 0),
    CM("cm", 10.0, 2, 2),
    IN("in", 25.4, 2, 2);

    fun formatLength(mm: Double) = "%.${decimals}f %s".format(Locale.US, mm / mmPer, label)
    fun formatArea(mm2: Double) = "%.${areaDecimals}f %s²".format(Locale.US, mm2 / (mmPer * mmPer), label)
}

data class OutlineMeasurements(
    val lengthMm: Double,
    val widthMm: Double,
    val areaMm2: Double,
    val perimeterMm: Double,
    /** Set only when the outline is close to a circle. */
    val diameterMm: Double?,
    val rect: RotatedRect,
    val circle: Circle?,
)

object Geometry {
    /** 4*pi*A/P^2 is 1.0 for a circle, 0.785 for a square, 0.907 for a hexagon. */
    const val ROUND_CIRCULARITY = 0.88

    /** Andrew's monotone chain. Returns hull vertices counter-clockwise. */
    fun convexHull(points: List<Pt>): List<Pt> {
        val p = points.distinct().sortedWith(compareBy({ it.x }, { it.y }))
        if (p.size < 3) return p
        fun cross(o: Pt, a: Pt, b: Pt) = (a.x - o.x) * (b.y - o.y) - (a.y - o.y) * (b.x - o.x)
        val hull = ArrayList<Pt>()
        for (pass in 0..1) {
            val start = hull.size
            for (pt in if (pass == 0) p else p.asReversed()) {
                while (hull.size >= start + 2 && cross(hull[hull.size - 2], hull[hull.size - 1], pt) <= 0) {
                    hull.removeAt(hull.size - 1)
                }
                hull.add(pt)
            }
            hull.removeAt(hull.size - 1)
        }
        return hull
    }

    /** The minimum-area rectangle has one side on a hull edge, so try each edge. */
    fun minAreaRect(points: List<Pt>): RotatedRect {
        val hull = convexHull(points)
        var best: RotatedRect? = null
        var bestArea = Double.MAX_VALUE
        for (i in hull.indices) {
            val a = hull[i]
            val b = hull[(i + 1) % hull.size]
            val len = a.dist(b)
            if (len == 0.0) continue
            val u = Pt((b.x - a.x) / len, (b.y - a.y) / len)
            val v = Pt(-u.y, u.x)
            var minU = Double.MAX_VALUE; var maxU = -Double.MAX_VALUE
            var minV = Double.MAX_VALUE; var maxV = -Double.MAX_VALUE
            for (q in hull) {
                val pu = q.x * u.x + q.y * u.y
                val pv = q.x * v.x + q.y * v.y
                minU = min(minU, pu); maxU = max(maxU, pu)
                minV = min(minV, pv); maxV = max(maxV, pv)
            }
            val w = maxU - minU
            val h = maxV - minV
            if (w * h < bestArea) {
                bestArea = w * h
                best = RotatedRect(
                    length = max(w, h),
                    width = min(w, h),
                    corners = listOf(
                        u * minU + v * minV, u * maxU + v * minV,
                        u * maxU + v * maxV, u * minU + v * maxV,
                    ),
                )
            }
        }
        return requireNotNull(best) { "Need at least 3 non-collinear points" }
    }

    /** Shoelace formula. */
    fun area(poly: List<Pt>): Double {
        var s = 0.0
        for (i in poly.indices) {
            val a = poly[i]
            val b = poly[(i + 1) % poly.size]
            s += a.x * b.y - b.x * a.y
        }
        return kotlin.math.abs(s) / 2
    }

    fun perimeter(poly: List<Pt>): Double = poly.indices.sumOf { poly[it].dist(poly[(it + 1) % poly.size]) }

    fun circularity(poly: List<Pt>): Double {
        val p = perimeter(poly)
        return if (p == 0.0) 0.0 else 4 * PI * area(poly) / (p * p)
    }

    /** Algebraic (Kasa) least-squares circle fit. Points are centered first for numerical stability. */
    fun fitCircle(points: List<Pt>): Circle {
        val mx = points.sumOf { it.x } / points.size
        val my = points.sumOf { it.y } / points.size
        var suu = 0.0; var svv = 0.0; var suv = 0.0
        var suuu = 0.0; var svvv = 0.0; var suvv = 0.0; var svuu = 0.0
        for (p in points) {
            val u = p.x - mx
            val v = p.y - my
            suu += u * u; svv += v * v; suv += u * v
            suuu += u * u * u; svvv += v * v * v; suvv += u * v * v; svuu += v * u * u
        }
        // Circle: u^2 + v^2 + D*u + E*v + F = 0. With centered data, F = -(Suu + Svv)/n.
        val r1 = -(suuu + suvv)
        val r2 = -(svvv + svuu)
        val det = suu * svv - suv * suv
        val d = (r1 * svv - r2 * suv) / det
        val e = (suu * r2 - suv * r1) / det
        val f = -(suu + svv) / points.size
        return Circle(Pt(mx - d / 2, my - e / 2), sqrt((d * d + e * e) / 4 - f))
    }

    fun measureOutline(poly: List<Pt>, pxPerMm: Double): OutlineMeasurements {
        val rect = minAreaRect(poly)
        val circle = if (circularity(poly) >= ROUND_CIRCULARITY) fitCircle(poly) else null
        return OutlineMeasurements(
            lengthMm = rect.length / pxPerMm,
            widthMm = rect.width / pxPerMm,
            areaMm2 = area(poly) / (pxPerMm * pxPerMm),
            perimeterMm = perimeter(poly) / pxPerMm,
            diameterMm = circle?.let { 2 * it.radius / pxPerMm },
            rect = rect,
            circle = circle,
        )
    }
}
