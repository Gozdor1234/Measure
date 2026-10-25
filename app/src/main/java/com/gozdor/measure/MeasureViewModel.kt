package com.gozdor.measure

import android.content.Context
import android.graphics.Bitmap
import android.net.Uri
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateListOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.setValue
import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlin.math.min

enum class Mode(val label: String) { SCALE("Scale"), OUTLINE("Outline"), RULER("Measure") }

class MeasureViewModel : ViewModel() {
    var bitmap by mutableStateOf<Bitmap?>(null)
        private set
    var mode by mutableStateOf(Mode.SCALE)
    var unit by mutableStateOf(LengthUnit.MM)
    var message by mutableStateOf<String?>(null)
    var detecting by mutableStateOf(false)
        private set

    /** Two taps on the ruler plus the real distance between them. */
    val scalePts = mutableStateListOf<Pt>()
    var scaleMm by mutableStateOf<Double?>(null)
    var showScaleDialog by mutableStateOf(false)

    val outline = mutableStateListOf<Pt>()
    val lines = mutableStateListOf<Pair<Pt, Pt>>()
    var pending by mutableStateOf<Pt?>(null)
        private set

    val pxPerMm: Double?
        get() {
            val mm = scaleMm ?: return null
            if (scalePts.size < 2 || mm <= 0) return null
            return scalePts[0].dist(scalePts[1]) / mm
        }

    fun load(context: Context, uri: Uri) {
        viewModelScope.launch {
            val bmp = withContext(Dispatchers.IO) { runCatching { ImageLoader.load(context, uri) }.getOrNull() }
            if (bmp == null) { message = "Couldn't open that image"; return@launch }
            bitmap = bmp
            scalePts.clear(); scaleMm = null; outline.clear(); lines.clear(); pending = null
            mode = Mode.SCALE
        }
    }

    fun onTap(p: Pt) {
        when (mode) {
            Mode.SCALE -> if (scalePts.size < 2) {
                scalePts.add(p)
                if (scalePts.size == 2) showScaleDialog = true
            }
            Mode.OUTLINE -> detect(p)
            Mode.RULER -> {
                val start = pending
                if (start == null) pending = p else { lines.add(start to p); pending = null }
            }
        }
    }

    private fun detect(p: Pt) {
        val bmp = bitmap ?: return
        if (detecting) return
        detecting = true
        viewModelScope.launch {
            val found = if (OutlineDetector.ready) {
                withContext(Dispatchers.Default) { runCatching { OutlineDetector.detect(bmp, p) }.getOrNull() }
            } else null
            outline.clear()
            if (found != null) {
                outline.addAll(found)
            } else {
                // Fall back to a box the user drags onto the object.
                val h = min(bmp.width, bmp.height) * 0.1
                outline.addAll(listOf(Pt(p.x - h, p.y - h), Pt(p.x + h, p.y - h), Pt(p.x + h, p.y + h), Pt(p.x - h, p.y + h)))
                message = if (OutlineDetector.ready) "No clear outline found. Drag the box corners onto the object."
                else "Outline detection unavailable. Drag the box corners onto the object."
            }
            detecting = false
        }
    }

    /** Draggable points for the current mode, in a fixed order that [moveHandle] understands. */
    fun handles(): List<Pt> = when (mode) {
        Mode.SCALE -> scalePts.toList()
        Mode.OUTLINE -> outline.toList()
        Mode.RULER -> lines.flatMap { listOf(it.first, it.second) } + listOfNotNull(pending)
    }

    fun moveHandle(i: Int, p: Pt) {
        val bmp = bitmap ?: return
        val c = Pt(p.x.coerceIn(0.0, bmp.width.toDouble()), p.y.coerceIn(0.0, bmp.height.toDouble()))
        when (mode) {
            Mode.SCALE -> scalePts[i] = c
            Mode.OUTLINE -> outline[i] = c
            Mode.RULER -> if (i < lines.size * 2) {
                val l = lines[i / 2]
                lines[i / 2] = if (i % 2 == 0) l.copy(first = c) else l.copy(second = c)
            } else pending = c
        }
    }

    fun clearMode() {
        when (mode) {
            Mode.SCALE -> { scalePts.clear(); scaleMm = null }
            Mode.OUTLINE -> outline.clear()
            Mode.RULER -> { lines.clear(); pending = null }
        }
    }
}
