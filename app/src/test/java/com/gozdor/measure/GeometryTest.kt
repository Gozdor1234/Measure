package com.gozdor.measure

import org.junit.Assert.assertEquals
import org.junit.Assert.assertNotNull
import org.junit.Assert.assertNull
import org.junit.Test
import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.sin

class GeometryTest {
    private fun rotatedRect(w: Double, h: Double, deg: Double, cx: Double = 500.0, cy: Double = 400.0): List<Pt> {
        val t = deg * PI / 180
        return listOf(-w / 2 to -h / 2, w / 2 to -h / 2, w / 2 to h / 2, -w / 2 to h / 2).map { (x, y) ->
            Pt(cx + x * cos(t) - y * sin(t), cy + x * sin(t) + y * cos(t))
        }
    }

    private fun circlePoints(cx: Double, cy: Double, r: Double, n: Int) =
        (0 until n).map { Pt(cx + r * cos(2 * PI * it / n), cy + r * sin(2 * PI * it / n)) }

    @Test fun areaAndPerimeterOfRectangle() {
        val r = listOf(Pt(0.0, 0.0), Pt(4.0, 0.0), Pt(4.0, 3.0), Pt(0.0, 3.0))
        assertEquals(12.0, Geometry.area(r), 1e-9)
        assertEquals(14.0, Geometry.perimeter(r), 1e-9)
    }

    @Test fun hullDropsInteriorPoints() {
        val pts = listOf(Pt(0.0, 0.0), Pt(10.0, 0.0), Pt(10.0, 10.0), Pt(0.0, 10.0), Pt(5.0, 5.0), Pt(2.0, 7.0))
        assertEquals(4, Geometry.convexHull(pts).size)
    }

    @Test fun minAreaRectFindsRotatedRectangle() {
        val rect = Geometry.minAreaRect(rotatedRect(100.0, 40.0, 30.0))
        assertEquals(100.0, rect.length, 1e-6)
        assertEquals(40.0, rect.width, 1e-6)
    }

    @Test fun minAreaRectIgnoresBumpsInsideHull() {
        val poly = rotatedRect(100.0, 40.0, -12.0) + Pt(500.0, 400.0)
        val rect = Geometry.minAreaRect(poly)
        assertEquals(100.0, rect.length, 1e-6)
        assertEquals(40.0, rect.width, 1e-6)
    }

    @Test fun circleFitIsExactOnCircle() {
        val c = Geometry.fitCircle(circlePoints(50.0, 20.0, 30.0, 40))
        assertEquals(50.0, c.center.x, 1e-6)
        assertEquals(20.0, c.center.y, 1e-6)
        assertEquals(30.0, c.radius, 1e-6)
    }

    @Test fun circularityOfSquareAndCircle() {
        assertEquals(PI / 4, Geometry.circularity(rotatedRect(10.0, 10.0, 0.0)), 1e-9)
        assertEquals(1.0, Geometry.circularity(circlePoints(0.0, 0.0, 10.0, 200)), 1e-3)
    }

    @Test fun squareMeasuresInMillimetresWithoutDiameter() {
        val m = Geometry.measureOutline(rotatedRect(200.0, 200.0, 17.0), pxPerMm = 2.0)
        assertEquals(100.0, m.lengthMm, 1e-6)
        assertEquals(100.0, m.widthMm, 1e-6)
        assertEquals(10_000.0, m.areaMm2, 1e-6)
        assertEquals(400.0, m.perimeterMm, 1e-6)
        assertNull(m.diameterMm)
    }

    @Test fun roundOutlineGetsDiameter() {
        val m = Geometry.measureOutline(circlePoints(300.0, 300.0, 100.0, 120), pxPerMm = 2.0)
        assertNotNull(m.diameterMm)
        assertEquals(100.0, m.diameterMm!!, 1e-6)
    }

    @Test fun unitFormatting() {
        assertEquals("1.00 in", LengthUnit.IN.formatLength(25.4))
        assertEquals("2.54 cm", LengthUnit.CM.formatLength(25.4))
        assertEquals("25.4 mm", LengthUnit.MM.formatLength(25.4))
        assertEquals("1.00 in²", LengthUnit.IN.formatArea(645.16))
    }
}
