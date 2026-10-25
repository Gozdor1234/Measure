package com.gozdor.measure

import android.widget.Toast
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.result.PickVisualMediaRequest
import androidx.activity.result.contract.ActivityResultContracts
import androidx.compose.foundation.Canvas
import androidx.compose.foundation.gestures.awaitEachGesture
import androidx.compose.foundation.gestures.awaitFirstDown
import androidx.compose.foundation.gestures.calculateCentroid
import androidx.compose.foundation.gestures.calculatePan
import androidx.compose.foundation.gestures.calculateZoom
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.foundation.verticalScroll
import androidx.compose.material3.AlertDialog
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.Scaffold
import androidx.compose.material3.SegmentedButton
import androidx.compose.material3.SegmentedButtonDefaults
import androidx.compose.material3.SingleChoiceSegmentedButtonRow
import androidx.compose.material3.Text
import androidx.compose.material3.TextButton
import androidx.compose.material3.TopAppBar
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableFloatStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.CornerRadius
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.asImageBitmap
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.withTransform
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.input.pointer.positionChange
import androidx.compose.ui.layout.onSizeChanged
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalDensity
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.IntOffset
import androidx.compose.ui.unit.IntSize
import androidx.compose.ui.unit.dp
import androidx.compose.ui.unit.sp
import androidx.core.content.FileProvider
import androidx.lifecycle.viewmodel.compose.viewModel
import java.io.File
import java.util.Locale
import kotlin.math.min

private val ScaleColor = Color(0xFFFFC107)
private val OutlineColor = Color(0xFF00E5FF)
private val RulerColor = Color(0xFFFF4081)

@OptIn(ExperimentalMaterial3Api::class)
@Composable
fun MeasureApp(vm: MeasureViewModel = viewModel()) {
    val context = LocalContext.current
    val captureUri = remember {
        val dir = File(context.cacheDir, "images").apply { mkdirs() }
        FileProvider.getUriForFile(context, "${context.packageName}.files", File(dir, "capture.jpg"))
    }
    val takePicture = rememberLauncherForActivityResult(ActivityResultContracts.TakePicture()) { ok ->
        if (ok) vm.load(context, captureUri)
    }
    val pickPhoto = rememberLauncherForActivityResult(ActivityResultContracts.PickVisualMedia()) { uri ->
        if (uri != null) vm.load(context, uri)
    }

    LaunchedEffect(vm.message) {
        vm.message?.let { Toast.makeText(context, it, Toast.LENGTH_LONG).show(); vm.message = null }
    }

    Scaffold(
        topBar = {
            TopAppBar(
                title = { Text("Measure") },
                actions = {
                    TextButton(onClick = { takePicture.launch(captureUri) }) { Text("Camera") }
                    TextButton(onClick = {
                        pickPhoto.launch(PickVisualMediaRequest(ActivityResultContracts.PickVisualMedia.ImageOnly))
                    }) { Text("Gallery") }
                },
            )
        },
    ) { pad ->
        Column(Modifier.padding(pad).fillMaxSize()) {
            if (vm.bitmap == null) {
                EmptyState(Modifier.weight(1f).fillMaxWidth())
            } else {
                Box(Modifier.weight(1f).fillMaxWidth()) {
                    MeasureCanvas(vm)
                    if (vm.detecting) CircularProgressIndicator(Modifier.align(Alignment.Center))
                }
                ControlPanel(vm)
            }
        }
    }
    if (vm.showScaleDialog) ScaleDialog(vm)
}

@Composable
private fun EmptyState(modifier: Modifier) {
    Column(modifier.verticalScroll(rememberScrollState()).padding(24.dp), verticalArrangement = Arrangement.spacedBy(12.dp)) {
        Text("Take or pick a top-down photo with a ruler or tape measure lying next to the object.", style = MaterialTheme.typography.titleMedium)
        Text(
            "For accurate results:\n" +
                "• Shoot straight down, phone parallel to the table.\n" +
                "• Ruler flat and at the same height as the surface you are measuring. A ruler on the table under a tall object makes the object read too large.\n" +
                "• Plain background that contrasts with the object. Keep the object apart from the ruler.\n" +
                "• Even, diffuse light so shadows don't merge into the outline.\n" +
                "• Keep the object and ruler away from the extreme edges of the frame.",
        )
        Text(
            "Then:\n" +
                "1. Scale: tap two ruler marks far apart and enter the distance.\n" +
                "2. Outline: tap the object, then drag points to adjust.\n" +
                "3. Measure: tap any two points.\n" +
                "Pinch to zoom. While dragging a point, a magnifier shows exactly where it sits.",
        )
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun ControlPanel(vm: MeasureViewModel) {
    Column(Modifier.fillMaxWidth().padding(horizontal = 12.dp, vertical = 8.dp), verticalArrangement = Arrangement.spacedBy(6.dp)) {
        SingleChoiceSegmentedButtonRow(Modifier.fillMaxWidth()) {
            Mode.entries.forEachIndexed { i, m ->
                SegmentedButton(
                    selected = vm.mode == m,
                    onClick = { vm.mode = m },
                    shape = SegmentedButtonDefaults.itemShape(i, Mode.entries.size),
                ) { Text(m.label) }
            }
        }
        Text(hint(vm), style = MaterialTheme.typography.bodySmall)
        Results(vm)
        Row(verticalAlignment = Alignment.CenterVertically) {
            UnitPicker(vm.unit, { vm.unit = it }, Modifier.weight(1f))
            if (vm.mode == Mode.SCALE && vm.scalePts.size == 2) {
                TextButton(onClick = { vm.showScaleDialog = true }) { Text("Distance") }
            }
            TextButton(onClick = vm::clearMode) { Text("Clear") }
        }
    }
}

private fun hint(vm: MeasureViewModel): String {
    val px = vm.pxPerMm
    return when (vm.mode) {
        Mode.SCALE -> when {
            vm.scalePts.size < 2 -> "Tap two marks on the ruler, as far apart as possible."
            px == null -> "Tap Distance and enter the real distance between the marks."
            else -> "Scale set: 1 px = %.3f mm. Drag the marks to fine-tune.".format(Locale.US, 1 / px)
        }
        Mode.OUTLINE -> "Tap the object to outline it. Drag points to adjust." +
            if (px == null) " Set the scale first for real units." else ""
        Mode.RULER -> "Tap two points to measure between them." +
            if (px == null) " Set the scale first for real units." else ""
    }
}

@Composable
private fun Results(vm: MeasureViewModel) {
    val px = vm.pxPerMm ?: return
    val u = vm.unit
    val style = MaterialTheme.typography.bodyLarge
    if (vm.mode == Mode.OUTLINE && vm.outline.size >= 3) {
        val m = runCatching { Geometry.measureOutline(vm.outline, px) }.getOrNull() ?: return
        Text("Length ${u.formatLength(m.lengthMm)}    Width ${u.formatLength(m.widthMm)}", style = style)
        Text("Area ${u.formatArea(m.areaMm2)}    Perimeter ${u.formatLength(m.perimeterMm)}", style = style)
        m.diameterMm?.let { Text("Diameter ${u.formatLength(it)}", style = style) }
    }
    if (vm.mode == Mode.RULER && vm.lines.isNotEmpty()) {
        Text(vm.lines.mapIndexed { i, (a, b) -> "${i + 1}: ${u.formatLength(a.dist(b) / px)}" }.joinToString("    "), style = style)
    }
}

@OptIn(ExperimentalMaterial3Api::class)
@Composable
private fun UnitPicker(selected: LengthUnit, onPick: (LengthUnit) -> Unit, modifier: Modifier = Modifier) {
    SingleChoiceSegmentedButtonRow(modifier) {
        LengthUnit.entries.forEachIndexed { i, u ->
            SegmentedButton(
                selected = selected == u,
                onClick = { onPick(u) },
                shape = SegmentedButtonDefaults.itemShape(i, LengthUnit.entries.size),
            ) { Text(u.label) }
        }
    }
}

@Composable
private fun ScaleDialog(vm: MeasureViewModel) {
    var unit by remember { mutableStateOf(vm.unit) }
    var text by remember { mutableStateOf(vm.scaleMm?.let { "%.4g".format(Locale.US, it / unit.mmPer) } ?: "") }
    val value = text.replace(',', '.').toDoubleOrNull()?.takeIf { it > 0 }
    AlertDialog(
        onDismissRequest = { vm.showScaleDialog = false },
        title = { Text("Distance between the marks") },
        text = {
            Column(verticalArrangement = Arrangement.spacedBy(12.dp)) {
                OutlinedTextField(
                    value = text,
                    onValueChange = { text = it },
                    singleLine = true,
                    label = { Text("Distance") },
                    keyboardOptions = KeyboardOptions(keyboardType = KeyboardType.Decimal),
                )
                UnitPicker(unit, { unit = it })
            }
        },
        confirmButton = {
            TextButton(enabled = value != null, onClick = {
                vm.scaleMm = value!! * unit.mmPer
                vm.showScaleDialog = false
            }) { Text("Set") }
        },
        dismissButton = { TextButton(onClick = { vm.showScaleDialog = false }) { Text("Cancel") } },
    )
}

@Composable
private fun MeasureCanvas(vm: MeasureViewModel) {
    val bmp = vm.bitmap ?: return
    val image = remember(bmp) { bmp.asImageBitmap() }
    val tm = rememberTextMeasurer()
    val touchRadius = with(LocalDensity.current) { 28.dp.toPx() }
    var viewSize by remember { mutableStateOf(IntSize.Zero) }
    var s by remember(bmp) { mutableFloatStateOf(0f) } // screen px per image px
    var t by remember(bmp) { mutableStateOf(Offset.Zero) } // image origin on screen
    var dragging by remember { mutableStateOf<Int?>(null) }

    fun fitScale() = min(viewSize.width / image.width.toFloat(), viewSize.height / image.height.toFloat())
    fun toScreen(p: Pt) = Offset(p.x.toFloat() * s + t.x, p.y.toFloat() * s + t.y)
    fun toImg(o: Offset) = Pt(((o.x - t.x) / s).toDouble(), ((o.y - t.y) / s).toDouble())

    LaunchedEffect(bmp, viewSize) {
        if (viewSize != IntSize.Zero && s == 0f) {
            s = fitScale()
            t = Offset((viewSize.width - image.width * s) / 2f, (viewSize.height - image.height * s) / 2f)
        }
    }

    Canvas(
        Modifier
            .fillMaxSize()
            .clipToBounds()
            .onSizeChanged { viewSize = it }
            .pointerInput(bmp) {
                awaitEachGesture {
                    val down = awaitFirstDown()
                    val handles = vm.handles()
                    dragging = handles.indices
                        .map { it to (toScreen(handles[it]) - down.position).getDistance() }
                        .filter { it.second <= touchRadius }
                        .minByOrNull { it.second }?.first
                    var moved = false
                    var multi = false
                    while (true) {
                        val event = awaitPointerEvent()
                        val pressed = event.changes.filter { it.pressed }
                        if (pressed.isEmpty()) break
                        if (pressed.size > 1) {
                            multi = true
                            dragging = null
                            val c = event.calculateCentroid()
                            val fit = fitScale()
                            val ns = (s * event.calculateZoom()).coerceIn(fit, fit * 25f)
                            t = c - (c - t) * (ns / s) + event.calculatePan()
                            s = ns
                        } else if (!multi) {
                            val ch = pressed[0]
                            if ((ch.position - down.position).getDistance() > viewConfiguration.touchSlop) moved = true
                            val d = ch.positionChange()
                            val i = dragging
                            if (i != null) {
                                vm.handles().getOrNull(i)?.let { vm.moveHandle(i, it + Pt(d.x / s.toDouble(), d.y / s.toDouble())) }
                            } else if (moved) {
                                t += d
                            }
                        }
                        event.changes.forEach { it.consume() }
                    }
                    if (!moved && !multi && dragging == null) {
                        val p = toImg(down.position)
                        if (p.x in 0.0..image.width.toDouble() && p.y in 0.0..image.height.toDouble()) vm.onTap(p)
                    }
                    dragging = null
                }
            },
    ) {
        if (s == 0f) return@Canvas
        withTransform({ translate(t.x, t.y); scale(s, s, pivot = Offset.Zero) }) { drawImage(image) }

        val px = vm.pxPerMm
        val u = vm.unit
        val stroke = 2.dp.toPx()
        val dash = PathEffect.dashPathEffect(floatArrayOf(12.dp.toPx(), 6.dp.toPx()))

        // Scale reference
        if (vm.scalePts.size == 2) {
            val a = toScreen(vm.scalePts[0]); val b = toScreen(vm.scalePts[1])
            drawLine(ScaleColor, a, b, stroke)
            vm.scaleMm?.let { label(tm, "ref ${u.formatLength(it)}", (a + b) / 2f) }
        }
        vm.scalePts.forEach { handle(toScreen(it), ScaleColor, vm.mode == Mode.SCALE) }

        // Object outline, bounding rectangle, fitted circle
        if (vm.outline.size >= 3) {
            val pts = vm.outline.map(::toScreen)
            drawPath(polygon(pts), OutlineColor, style = Stroke(stroke))
            val m = runCatching { Geometry.measureOutline(vm.outline, px ?: 1.0) }.getOrNull()
            if (m != null) {
                val c = m.rect.corners.map(::toScreen)
                drawPath(polygon(c), Color.White.copy(alpha = 0.85f), style = Stroke(stroke * 0.75f, pathEffect = dash))
                m.circle?.let { drawCircle(Color.White.copy(alpha = 0.85f), it.radius.toFloat() * s, toScreen(it.center), style = Stroke(stroke * 0.75f, pathEffect = dash)) }
                if (px != null) {
                    val center = c.reduce { acc, o -> acc + o } / 4f
                    val firstSideLonger = m.rect.corners[0].dist(m.rect.corners[1]) >= m.rect.corners[1].dist(m.rect.corners[2])
                    val longSide = if (firstSideLonger) (c[0] + c[1]) / 2f else (c[1] + c[2]) / 2f
                    val shortSide = if (firstSideLonger) (c[1] + c[2]) / 2f else (c[0] + c[1]) / 2f
                    label(tm, "L ${u.formatLength(m.lengthMm)}", pushOut(longSide, center, 16.dp.toPx()))
                    label(tm, "W ${u.formatLength(m.widthMm)}", pushOut(shortSide, center, 16.dp.toPx()))
                    m.diameterMm?.let { label(tm, "Ø ${u.formatLength(it)}", center) }
                }
            }
            if (vm.mode == Mode.OUTLINE) pts.forEach { handle(it, OutlineColor, true) }
        }

        // Point-to-point measurements
        vm.lines.forEach { (pa, pb) ->
            val a = toScreen(pa); val b = toScreen(pb)
            drawLine(RulerColor, a, b, stroke)
            if (px != null) label(tm, u.formatLength(pa.dist(pb) / px), (a + b) / 2f)
        }
        vm.lines.forEach { (a, b) -> handle(toScreen(a), RulerColor, vm.mode == Mode.RULER); handle(toScreen(b), RulerColor, vm.mode == Mode.RULER) }
        vm.pending?.let { handle(toScreen(it), RulerColor, true) }

        dragging?.let { i -> vm.handles().getOrNull(i)?.let { loupe(image, it, s, toScreen(it)) } }
    }
}

private fun polygon(pts: List<Offset>) = Path().apply {
    moveTo(pts[0].x, pts[0].y)
    pts.drop(1).forEach { lineTo(it.x, it.y) }
    close()
}

private fun pushOut(p: Offset, center: Offset, by: Float): Offset {
    val d = p - center
    val len = d.getDistance()
    return if (len == 0f) p else p + d / len * by
}

private fun DrawScope.handle(at: Offset, color: Color, active: Boolean) {
    if (active) {
        drawCircle(color.copy(alpha = 0.35f), 9.dp.toPx(), at)
        drawCircle(Color.White, 9.dp.toPx(), at, style = Stroke(1.5.dp.toPx()))
    }
    drawCircle(color, 3.dp.toPx(), at)
}

private fun DrawScope.label(tm: TextMeasurer, text: String, at: Offset) {
    val layout = tm.measure(text, TextStyle(color = Color.White, fontSize = 13.sp))
    val pad = 4.dp.toPx()
    val tl = Offset(at.x - layout.size.width / 2f, at.y - layout.size.height / 2f)
    drawRoundRect(
        Color(0xCC000000),
        topLeft = tl - Offset(pad, pad),
        size = Size(layout.size.width + 2 * pad, layout.size.height + 2 * pad),
        cornerRadius = CornerRadius(pad),
    )
    drawText(layout, topLeft = tl)
}

/** Magnified, unsmoothed view around a point being dragged, so the finger doesn't hide what's under it. */
private fun DrawScope.loupe(image: ImageBitmap, at: Pt, viewScale: Float, onScreen: Offset) {
    val r = 64.dp.toPx()
    val margin = 12.dp.toPx()
    val center = if (onScreen.x < size.width / 2 && onScreen.y < 2.5f * r) {
        Offset(size.width - margin - r, margin + r)
    } else {
        Offset(margin + r, margin + r)
    }
    val srcSize = (2 * r / (viewScale * 3f)).toInt().coerceIn(1, min(image.width, image.height))
    val sx = (at.x - srcSize / 2.0).toInt().coerceIn(0, image.width - srcSize)
    val sy = (at.y - srcSize / 2.0).toInt().coerceIn(0, image.height - srcSize)
    val dst = Offset(center.x - r, center.y - r)
    val k = 2 * r / srcSize
    val cross = Offset(dst.x + ((at.x - sx) * k).toFloat(), dst.y + ((at.y - sy) * k).toFloat())
    val arm = 10.dp.toPx()

    clipPath(Path().apply { addOval(Rect(center, r)) }) {
        drawRect(Color.Black, dst, Size(2 * r, 2 * r))
        drawImage(
            image,
            srcOffset = IntOffset(sx, sy),
            srcSize = IntSize(srcSize, srcSize),
            dstOffset = IntOffset(dst.x.toInt(), dst.y.toInt()),
            dstSize = IntSize((2 * r).toInt(), (2 * r).toInt()),
            filterQuality = FilterQuality.None,
        )
        drawLine(Color.Red, cross - Offset(arm, 0f), cross + Offset(arm, 0f), 1.5.dp.toPx())
        drawLine(Color.Red, cross - Offset(0f, arm), cross + Offset(0f, arm), 1.5.dp.toPx())
    }
    drawCircle(Color.White, r, center, style = Stroke(2.dp.toPx()))
}
