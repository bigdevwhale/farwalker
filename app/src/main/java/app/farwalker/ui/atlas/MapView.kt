package app.farwalker.ui.atlas

import androidx.compose.foundation.Canvas
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.snapshotFlow
import androidx.compose.ui.graphics.Canvas
import androidx.compose.ui.graphics.FilterQuality
import androidx.compose.ui.graphics.ImageBitmap
import androidx.compose.ui.graphics.ImageBitmapConfig
import androidx.compose.ui.graphics.drawscope.CanvasDrawScope
import androidx.compose.ui.graphics.drawscope.withTransform
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.Job
import kotlinx.coroutines.delay
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import androidx.compose.runtime.mutableIntStateOf
import androidx.compose.runtime.rememberCoroutineScope
import kotlinx.coroutines.flow.collectLatest
import kotlin.math.abs
import androidx.compose.foundation.gestures.detectTapGestures
import androidx.compose.foundation.gestures.detectTransformGestures
import androidx.compose.runtime.Composable
import androidx.compose.runtime.Stable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableDoubleStateOf
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.draw.clipToBounds
import androidx.compose.ui.geometry.Offset
import androidx.compose.ui.geometry.Rect
import androidx.compose.ui.geometry.Size
import androidx.compose.ui.graphics.Brush
import androidx.compose.ui.graphics.Color
import androidx.compose.ui.graphics.Path
import androidx.compose.ui.graphics.PathEffect
import androidx.compose.ui.graphics.StrokeCap
import androidx.compose.ui.graphics.StrokeJoin
import androidx.compose.ui.graphics.drawscope.DrawScope
import androidx.compose.ui.graphics.drawscope.Stroke
import androidx.compose.ui.graphics.drawscope.clipPath
import androidx.compose.ui.graphics.drawscope.rotate
import androidx.compose.ui.input.pointer.pointerInput
import androidx.compose.ui.text.TextMeasurer
import androidx.compose.ui.text.TextStyle
import androidx.compose.ui.text.font.FontWeight
import androidx.compose.ui.text.drawText
import androidx.compose.ui.text.rememberTextMeasurer
import androidx.compose.ui.unit.sp
import app.farwalker.domain.Geo
import app.farwalker.domain.Journey
import app.farwalker.domain.Mode
import app.farwalker.domain.Travel
import app.farwalker.domain.World
import app.farwalker.ui.theme.C
import app.farwalker.ui.theme.T
import kotlin.math.cos
import kotlin.math.floor
import kotlin.math.ln
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sqrt

/** Where the map looks: the centre in degrees, [k] dp to a degree of latitude. */
@Stable
class Camera(lon: Double = 0.0, lat: Double = 30.0, k: Double = 40.0) {
    var lon by mutableDoubleStateOf(lon)
    var lat by mutableDoubleStateOf(lat)
    var k by mutableDoubleStateOf(k)
    /** Keeps the traveller in the middle until the map is dragged. */
    var follow by mutableStateOf(true)

    fun look(lat: Double, lon: Double) {
        this.lat = lat.coerceIn(-80.0, 82.0)
        this.lon = Geo.wrap(lon)
    }

    companion object {
        const val MIN_K = 0.9
        const val MAX_K = 250.0
    }
}

/** A frame's projection: plate carrée squeezed by the cosine of the centre's latitude. */
private class Proj(val cam: Camera, val w: Float, val h: Float, val focusY: Float, density: Float) {
    val cos = cos(Math.toRadians(cam.lat)).coerceAtLeast(0.25)
    /** Pixels to a degree: the camera counts in dp, so the map looks the same on every screen. */
    val ky = cam.k * density
    val kx = ky * cos
    val cx = w / 2
    val cy = h * focusY
    val lonSpan = cx / kx + 2
    val latTop = cam.lat + cy / ky + 1
    val latBottom = cam.lat - (h - cy) / ky - 1

    /** [lon] taken as the copy nearest the centre. */
    fun x(lon: Double): Float = (cx + (Geo.near(cam.lon, lon) - cam.lon) * kx).toFloat()
    fun xRaw(lon: Double): Float = (cx + (lon - cam.lon) * kx).toFloat()
    fun y(lat: Double): Float = (cy + (cam.lat - lat) * ky).toFloat()
    fun lonAt(x: Float) = cam.lon + (x - cx) / kx
    fun latAt(y: Float) = cam.lat - (y - cy) / ky
    fun visible(lat: Double, lon: Double, pad: Float = 0f): Boolean {
        val x = x(lon)
        val y = y(lat)
        return x > -pad && x < w + pad && y > -pad && y < h + pad
    }
}

/**
 * The engraved map: hatched seas with ripples along the coasts, hand-coloured borders round the
 * countries already stamped, the red line walked and the dotted line ahead, and the traveller.
 */
@Composable
fun AtlasMap(
    world: World,
    j: Journey,
    travel: Travel,
    camera: Camera,
    ru: Boolean,
    modifier: Modifier = Modifier,
    /** Where the traveller sits, as a fraction of the height (a panel covers the bottom). */
    focusY: Float = 0.42f,
    /** Keeps the scale bar above whatever covers the bottom, in px. */
    bottomInset: Float = 0f,
    interactive: Boolean = true,
) {
    val text = rememberTextMeasurer(cacheSize = 256)
    val gestures = if (interactive) Modifier
        .pointerInput(Unit) {
            detectTransformGestures { centroid, pan, zoom, _ ->
                camera.follow = false
                val p = Proj(camera, size.width.toFloat(), size.height.toFloat(), focusY, density)
                val lon = p.lonAt(centroid.x)
                val lat = p.latAt(centroid.y)
                camera.k = (camera.k * zoom).coerceIn(Camera.MIN_K, Camera.MAX_K)
                // Keep the spot under the fingers where it was, then follow the drag.
                val q = Proj(camera, size.width.toFloat(), size.height.toFloat(), focusY, density)
                camera.look(
                    camera.lat + (lat - q.latAt(centroid.y)) + pan.y / q.ky,
                    camera.lon + (lon - q.lonAt(centroid.x)) - pan.x / q.kx,
                )
            }
        }
        .pointerInput(Unit) {
            detectTapGestures(onDoubleTap = { o ->
                camera.follow = false
                val p = Proj(camera, size.width.toFloat(), size.height.toFloat(), focusY, density)
                camera.k = (camera.k * 2).coerceAtMost(Camera.MAX_K)
                camera.look(p.latAt(o.y), p.lonAt(o.x))
            })
        }
    else Modifier
    // Its own measurer: the sheet may be drawn on a background thread while the screen measures town names.
    val sheetText = rememberTextMeasurer(cacheSize = 64)
    val scope = rememberCoroutineScope()
    val base = remember { BaseLayer(scope) }
    // The zoom once the fingers have rested: the cached sheet is drawn again, sharp, at that scale.
    var rested by remember { mutableDoubleStateOf(camera.k) }
    LaunchedEffect(camera) {
        snapshotFlow { camera.k }.collectLatest { delay(220); rested = it }
    }
    val key = Triple(j.iso, j.stamps.size, ru)
    // The cached sheet is larger than the screen: clip it to the map's own bounds.
    Canvas(modifier.clipToBounds().then(gestures)) {
        val p = Proj(camera, size.width, size.height, focusY, density)
        base.draw(this, p, key, rested) { bp -> drawBase(world, j, bp, sheetText, ru) }
        drawLive(world, j, travel, p, text, ru, bottomInset)
    }
}

/**
 * The still part of the map (sea, land, coasts, colouring, country names) is costly to draw: it is
 * drawn once into a sheet larger than the screen, and the sheet is moved and stretched under the
 * fingers. It's drawn afresh when the view leaves it, the colouring changes, or a zoom comes to rest.
 */
private class BaseLayer(private val scope: CoroutineScope) {
    /** One drawn sheet: where its centre was, at what scale. */
    private class Sheet(val img: ImageBitmap, val key: Any, val lon: Double, val lat: Double, val k: Double, val kx: Double, val ky: Double, val cy: Float)

    private var sheet: Sheet? = null
    private var job: Job? = null
    /** Bumped when a sheet drawn in the background is ready, to draw the frame again. */
    private var ready by mutableIntStateOf(0)
    private val margin = 0.3f

    fun draw(scope: DrawScope, p: Proj, key: Any, rested: Double, render: DrawScope.(Proj) -> Unit) = with(scope) {
        ready
        val w = (size.width * (1 + 2 * margin)).toInt()
        val h = (size.height * (1 + 2 * margin)).toInt()
        val cur = sheet
        if (cur == null) {
            // Nothing to show meanwhile: draw it now.
            sheet = paint(p, key, w, h, density, layoutDirection, render)
        } else {
            // A resize (the keyboard opening) or new colouring: the old sheet stays up until the new one is ready.
            val cw = cur.img.width
            val ch = cur.img.height
            val sx = (p.kx / cur.kx).toFloat()
            val sy = (p.ky / cur.ky).toFloat()
            val left = p.x(cur.lon) - cw / 2f * sx
            val top = p.y(cur.lat) - cur.cy * sy
            val covers = left <= 0 && top <= 0 && left + cw * sx >= size.width && top + ch * sy >= size.height
            val stale = !covers || cur.key != key || cw != w || ch != h || sy !in 0.7f..1.45f ||
                (abs(cur.k - p.cam.k) > 1e-9 && abs(rested - p.cam.k) < 1e-9)
            if (stale && job == null) {
                // The old sheet stays on screen, moved and stretched, while the new one is drawn aside.
                val snap = Camera(p.cam.lon, p.cam.lat, p.cam.k)
                val q = Proj(snap, p.w, p.h, p.cy / p.h, density)
                val d = density
                val dir = layoutDirection
                job = this@BaseLayer.scope.launch {
                    val fresh = withContext(Dispatchers.Default) { paint(q, key, w, h, d, dir, render) }
                    sheet = fresh
                    job = null
                    ready++
                }
            }
            if (!covers) drawRect(C.paper)
            withTransform({ translate(left, top); scale(sx, sy, Offset.Zero) }) { drawImage(cur.img, filterQuality = FilterQuality.Low) }
            return@with
        }
        drawImage(sheet!!.img, topLeft = Offset(-size.width * margin, -size.height * margin))
    }

    private fun paint(p: Proj, key: Any, w: Int, h: Int, density: Float, dir: androidx.compose.ui.unit.LayoutDirection, render: DrawScope.(Proj) -> Unit): Sheet {
        val img = ImageBitmap(w, h, ImageBitmapConfig.Rgb565)
        val fy = (p.cy + p.h * margin) / h
        val bp = Proj(Camera(p.cam.lon, p.cam.lat, p.cam.k), w.toFloat(), h.toFloat(), fy, density)
        CanvasDrawScope().draw(androidx.compose.ui.unit.Density(density), dir, Canvas(img), Size(w.toFloat(), h.toFloat())) { render(bp) }
        return Sheet(img, key, bp.cam.lon, bp.cam.lat, bp.cam.k, bp.kx, bp.ky, bp.cy)
    }
}

private fun DrawScope.drawBase(world: World, j: Journey, p: Proj, text: TextMeasurer, ru: Boolean) {
    val dp = density
    drawRect(C.paper)

    // The sea: fine horizontal hatching, as an engraver would cut it.
    val hatch = 3.6f * dp
    var y = (p.cy % hatch)
    while (y < size.height) {
        drawLine(C.sea.copy(alpha = 0.12f), Offset(0f, y), Offset(size.width, y), 0.7f * dp)
        y += hatch
    }
    graticule(p)

    // The land, as one path for the coasts and one per country for its colouring.
    val land = Path()
    val stamped = mutableListOf<Path>()
    var current: Path? = null
    for ((iso, rings) in world.shapes) {
        val one = Path()
        var any = false
        for (r in rings) {
            if (r.maxLat < p.latBottom || r.minLat > p.latTop) continue
            for (shift in intArrayOf(-360, 0, 360)) {
                if (r.maxLon + shift < p.cam.lon - p.lonSpan || r.minLon + shift > p.cam.lon + p.lonSpan) continue
                ring(one, r.points, shift, p)
                any = true
            }
        }
        if (!any) continue
        land.addPath(one)
        if (iso == j.iso) current = one else if (iso in j.stamps) stamped += one
    }
    // Ripples along every coast: three lines, each a stroke of ink with a stroke of paper inside it.
    // Far out, the ripples shrink, or the oceans would be all ripple.
    val near = (p.cam.k / 12).coerceIn(0.3, 1.0).toFloat()
    val ripples = floatArrayOf(13f * near, 8.5f * near, 4.5f * near)
    for ((i, r) in ripples.withIndex()) {
        drawPath(land, C.sea.copy(alpha = 0.18f + i * 0.08f), style = Stroke(width = (2 * r + 1.1f) * dp, join = StrokeJoin.Round))
        drawPath(land, C.paper, style = Stroke(width = (2 * r - 0.3f) * dp, join = StrokeJoin.Round))
    }
    drawPath(land, C.land)
    // Hand colouring: a wash inside the borders of every country in the passport, red for the one underfoot.
    for (c in stamped) clipPath(c) { drawPath(c, C.wash.copy(alpha = 0.55f), style = Stroke(width = 8f * dp, join = StrokeJoin.Round)) }
    current?.let { c -> clipPath(c) { drawPath(c, C.redSoft.copy(alpha = 0.55f), style = Stroke(width = 9f * dp, join = StrokeJoin.Round)) } }
    drawPath(land, C.ink.copy(alpha = 0.78f), style = Stroke(width = 0.9f * dp, join = StrokeJoin.Round))

    countryNames(world, p, text, ru)
}

private fun DrawScope.drawLive(world: World, j: Journey, travel: Travel, p: Proj, text: TextMeasurer, ru: Boolean, bottomInset: Float) {
    trail(world, j, travel, p)
    ahead(world, j, travel, p)
    towns(world, j, travel, p, text, ru)
    traveller(world, j, travel, p)
    scaleBar(p, text, ru, bottomInset)

    // The edges darken like an old sheet's.
    drawRect(
        Brush.radialGradient(
            0.55f to Color.Transparent, 1f to C.paperDeep.copy(alpha = 0.55f),
            center = Offset(size.width / 2, size.height / 2), radius = max(size.width, size.height) * 0.75f,
        ),
    )
}

private fun ring(path: Path, pts: FloatArray, shift: Int, p: Proj) {
    val n = pts.size / 2
    if (n < 3) return
    path.moveTo(p.xRaw((pts[0] + shift).toDouble()), p.y(pts[1].toDouble()))
    for (i in 1 until n) path.lineTo(p.xRaw((pts[2 * i] + shift).toDouble()), p.y(pts[2 * i + 1].toDouble()))
    path.close()
}

private fun DrawScope.graticule(p: Proj) {
    val step = when {
        p.cam.k < 2 -> 30
        p.cam.k < 8 -> 10
        p.cam.k < 40 -> 5
        else -> 1
    }
    val color = C.sea.copy(alpha = 0.18f)
    val w = 0.6f * density
    var lat = floor(p.latBottom / step) * step
    while (lat <= p.latTop) {
        if (lat > -90 && lat < 90) drawLine(color, Offset(0f, p.y(lat)), Offset(size.width, p.y(lat)), if (lat == 0.0) w * 2 else w)
        lat += step
    }
    var lon = floor((p.cam.lon - p.lonSpan) / step) * step
    while (lon <= p.cam.lon + p.lonSpan) {
        val x = p.xRaw(lon)
        drawLine(color, Offset(x, p.y(min(90.0, p.latTop))), Offset(x, p.y(max(-90.0, p.latBottom))), w)
        lon += step
    }
}

private fun DrawScope.countryNames(world: World, p: Proj, text: TextMeasurer, ru: Boolean) {
    val placed = mutableListOf<Rect>()
    for (c in world.countries.values.sortedByDescending { it.area }) {
        if (!p.visible(c.labelLat, c.labelLon, 40f)) continue
        val across = sqrt(c.area) * p.kx
        if (across < 60 * density) continue
        val sp = when {
            across > 900 * density -> 21
            across > 400 * density -> 18
            across > 160 * density -> 15
            else -> 12
        }
        val name = c.name(ru).uppercase()
        val style = T.Map.copy(fontSize = sp.sp, color = C.muted)
        val m = text.measure(name, style)
        if (m.size.width > across * 1.25) continue
        val at = Offset(p.x(c.labelLon) - m.size.width / 2f, p.y(c.labelLat) - m.size.height / 2f)
        val box = Rect(at, Size(m.size.width.toFloat(), m.size.height.toFloat()))
        if (placed.any { it.overlaps(box) }) continue
        placed += box
        label(text, name, style, at, halo = 2.6f)
    }
}

/** The way already walked: red ink on foot, a railway by train, a dashed wake at sea. */
private fun DrawScope.trail(world: World, j: Journey, travel: Travel, p: Proj) {
    val t = j.trail
    for (i in 1 until t.size) {
        val a = world.town(t[i - 1].town)
        val b = world.town(t[i].town)
        segment(a.lat, a.lon, b.lat, b.lon, t[i].mode, p, done = true)
    }
    val (lat, lon) = travel.position(j)
    val here = world.town(j.town)
    if (j.at > 0) segment(here.lat, here.lon, lat, lon, j.ride?.mode ?: Mode.WALK, p, done = true)
}

/** The way ahead: a dotted line over land, long dashes over the sea. */
private fun DrawScope.ahead(world: World, j: Journey, travel: Travel, p: Proj) {
    var (lat, lon) = travel.position(j)
    for (leg in j.legs) {
        val e = world.edge(leg.edge)
        val b = world.town(travel.router.to(leg))
        segment(lat, lon, b.lat, b.lon, if (e.sea) Mode.SHIP else Mode.WALK, p, done = false)
        lat = b.lat; lon = b.lon
    }
    if (j.dest >= 0) {
        val d = world.town(j.dest)
        val x = p.x(d.lon)
        val y = p.y(d.lat)
        val s = density
        drawLine(C.ink, Offset(x, y), Offset(x, y - 22 * s), 1.4f * s)
        val flag = Path().apply { moveTo(x, y - 22 * s); lineTo(x + 13 * s, y - 18 * s); lineTo(x, y - 13 * s); close() }
        drawPath(flag, C.red)
        drawPath(flag, C.ink, style = Stroke(0.8f * s))
    }
}

private fun DrawScope.segment(lat1: Double, lon1: Double, lat2: Double, lon2: Double, mode: Mode, p: Proj, done: Boolean) {
    val x1 = p.x(lon1)
    val y1 = p.y(lat1)
    // The far end is unwrapped from the near one, so a road over the date line stays short.
    val x2 = x1 + ((Geo.near(lon1, lon2) - lon1) * p.kx).toFloat()
    val y2 = p.y(lat2)
    if (max(x1, x2) < -20 || min(x1, x2) > size.width + 20 || max(y1, y2) < -20 || min(y1, y2) > size.height + 20) return
    val a = Offset(x1, y1)
    val b = Offset(x2, y2)
    val s = density
    when {
        !done && mode == Mode.SHIP ->
            drawLine(C.sea, a, b, 1.3f * s, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(7 * s, 5 * s)))
        !done ->
            drawLine(C.red, a, b, 2f * s, StrokeCap.Round, PathEffect.dashPathEffect(floatArrayOf(0.1f, 5 * s)))
        mode == Mode.SHIP -> {
            drawLine(C.sea, a, b, 2f * s, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(9 * s, 4 * s)))
        }
        mode == Mode.TRAIN -> {
            // The railway of old maps: a black band with white sleepers.
            drawLine(C.ink, a, b, 3.4f * s, StrokeCap.Butt)
            drawLine(C.paper, a, b, 1.7f * s, StrokeCap.Butt, PathEffect.dashPathEffect(floatArrayOf(5 * s, 5 * s)))
        }
        else -> drawLine(C.red, a, b, 2.4f * s, StrokeCap.Round)
    }
}

/** A name on the map with a halo of paper round its letters, so no line or hatching runs through it. */
private fun DrawScope.label(text: TextMeasurer, name: String, style: TextStyle, at: Offset, halo: Float = 3.2f) {
    // The halo is the name in paper, laid eight times round where the ink goes.
    val ring = text.measure(name, style.copy(color = C.paper))
    val r = halo / 2 * density
    for (i in 0 until 8) {
        val a = i * Math.PI / 4
        drawText(ring, topLeft = at + Offset((cos(a) * r).toFloat(), (kotlin.math.sin(a) * r).toFloat()))
    }
    drawText(text.measure(name, style), topLeft = at)
}

/**
 * The towns, in three ranks so the eye has somewhere to land: where the traveller is, the next town
 * and the goal in bold upright letters; the towns on the way and the capitals in plain ones; the rest
 * small, pale and only when there's room.
 */
private fun DrawScope.towns(world: World, j: Journey, travel: Travel, p: Proj, text: TextMeasurer, ru: Boolean) {
    val placed = mutableListOf<Rect>()
    val here = travel.sleepTown(j)
    val next = travel.next(j)
    val key = listOfNotNull(here, next, j.town, j.dest.takeIf { it >= 0 }).toSet()
    val way = LinkedHashSet<Int>()
    for (leg in j.legs) way += travel.router.to(leg)
    val walked = LinkedHashSet<Int>()
    for (v in j.trail.asReversed().take(300)) walked += v.town
    val k = p.cam.k
    val s = density
    // The traveller's own spot is kept clear for the marker; the key towns may still sit beside it.
    val (lat, lon) = travel.position(j)
    val marker = Rect(Offset(p.x(lon) - 12 * s, p.y(lat) - 12 * s), Size(24 * s, 24 * s))

    // Small marks for every town walked through or ahead: the beads of the road.
    for (id in walked + way) {
        val t = world.town(id)
        if (id in key || !p.visible(t.lat, t.lon, 6f)) continue
        val o = Offset(p.x(t.lon), p.y(t.lat))
        drawCircle(C.land, 2.2f * s, o)
        drawCircle(C.ink, 2.2f * s, o, style = Stroke(0.9f * s))
    }

    fun put(t: app.farwalker.domain.Town, style: TextStyle, dot: Float, color: Color = C.ink, must: Boolean = false): Boolean {
        if (!p.visible(t.lat, t.lon, 10f)) return false
        val o = Offset(p.x(t.lon), p.y(t.lat))
        val m = text.measure(t.name(ru), style.copy(color = color))
        val size = Size(m.size.width.toFloat(), m.size.height.toFloat())
        val gap = dot + 4 * s
        // Right of the dot, left, above, below; a key town takes the first spot even if crowded.
        val spots = listOf(
            Rect(Offset(o.x + gap, o.y - size.height / 2), size),
            Rect(Offset(o.x - gap - size.width, o.y - size.height / 2), size),
            Rect(Offset(o.x - size.width / 2, o.y - gap - size.height), size),
            Rect(Offset(o.x - size.width / 2, o.y + gap), size),
        )
        val box = spots.firstOrNull { b -> !b.overlaps(marker) && placed.none { it.overlaps(b) } }
            ?: spots.firstOrNull { b -> !b.overlaps(marker) }.takeIf { must } ?: return false
        placed += box
        if (dot > 0) {
            drawCircle(C.land, dot, o)
            drawCircle(color, dot, o, style = Stroke(1.3f * s))
            drawCircle(color, dot * 0.38f, o)
        }
        label(text, t.name(ru), style.copy(color = color), box.topLeft)
        return true
    }

    val bold = TextStyle(fontFamily = app.farwalker.ui.theme.Cormorant, fontWeight = FontWeight.Bold, fontSize = 17.sp)
    val plain = TextStyle(fontFamily = app.farwalker.ui.theme.Cormorant, fontWeight = FontWeight.SemiBold, fontSize = 14.sp)
    val small = T.MapTown.copy(fontSize = 12.sp)
    if (j.dest >= 0) put(world.town(j.dest), bold, 4.5f * s, C.red, must = true)
    next?.let { put(world.town(it), bold, 4.5f * s, must = true) }
    if (here != next) put(world.town(here), bold, 4.5f * s, must = true)
    if (j.town != here && j.town != next) put(world.town(j.town), plain, 3.4f * s, must = true)
    placed += marker
    // The towns ahead on the way, nearest first, while they have room.
    for (id in way) if (id !in key) put(world.town(id), plain, 0f)
    for (t in world.ranked) {
        if (t.id in key || t.id in way) continue
        val show = when {
            t.cape -> k > 2
            t.capital -> k > 3.5
            t.pop >= 3_000_000 -> k > 8
            t.pop >= 800_000 -> k > 18
            t.pop >= 200_000 -> k > 45
            else -> k > 110
        }
        if (!show) continue
        if (t.capital || t.cape) put(t, plain, 3.2f * s) else put(t, small, 2.4f * s, C.ink.copy(alpha = 0.7f))
    }
}

private fun DrawScope.traveller(world: World, j: Journey, travel: Travel, p: Proj) {
    val (lat, lon) = travel.position(j)
    val o = Offset(p.x(lon), p.y(lat))
    val s = density
    when (j.ride?.mode) {
        Mode.SHIP -> ship(o, s)
        Mode.TRAIN -> {
            drawCircle(C.paper, 8f * s, o)
            drawRect(C.ink, Offset(o.x - 5 * s, o.y - 3 * s), Size(10 * s, 6 * s))
            drawRect(C.ink, Offset(o.x - 2 * s, o.y - 6 * s), Size(3 * s, 3 * s))
            drawCircle(C.red, 1.6f * s, Offset(o.x - 3 * s, o.y + 4 * s))
            drawCircle(C.red, 1.6f * s, Offset(o.x + 3 * s, o.y + 4 * s))
        }
        else -> {
            drawCircle(C.red.copy(alpha = 0.18f), 14f * s, o)
            drawCircle(C.red, 6.5f * s, o)
            drawCircle(C.ink, 6.5f * s, o, style = Stroke(1.4f * s))
            drawCircle(C.paper, 2f * s, o)
        }
    }
    // A needle towards the next town.
    val next = travel.next(j) ?: return
    if (j.ride != null) return
    val t = world.town(next)
    val dx = p.x(t.lon) - o.x
    val dy = p.y(t.lat) - o.y
    val len = sqrt(dx * dx + dy * dy)
    if (len < 1) return
    val angle = Math.toDegrees(kotlin.math.atan2(dy, dx).toDouble()).toFloat()
    rotate(angle, o) {
        val tip = Path().apply { moveTo(o.x + 17 * s, o.y); lineTo(o.x + 10 * s, o.y - 3.2f * s); lineTo(o.x + 10 * s, o.y + 3.2f * s); close() }
        drawPath(tip, C.ink)
    }
}

private fun DrawScope.ship(o: Offset, s: Float) {
    drawCircle(C.paper.copy(alpha = 0.9f), 13f * s, o)
    val hull = Path().apply {
        moveTo(o.x - 10 * s, o.y + 2 * s); lineTo(o.x + 10 * s, o.y + 2 * s); lineTo(o.x + 6 * s, o.y + 7 * s); lineTo(o.x - 7 * s, o.y + 7 * s); close()
    }
    drawPath(hull, C.ink)
    drawLine(C.ink, Offset(o.x, o.y + 2 * s), Offset(o.x, o.y - 11 * s), 1.2f * s)
    val sail = Path().apply { moveTo(o.x + 1 * s, o.y - 10 * s); lineTo(o.x + 8 * s, o.y); lineTo(o.x + 1 * s, o.y); close() }
    drawPath(sail, C.paper)
    drawPath(sail, C.ink, style = Stroke(1f * s))
    val jib = Path().apply { moveTo(o.x - 1 * s, o.y - 8 * s); lineTo(o.x - 1 * s, o.y); lineTo(o.x - 7 * s, o.y); close() }
    drawPath(jib, C.red)
}

private fun DrawScope.scaleBar(p: Proj, text: TextMeasurer, ru: Boolean, bottomInset: Float) {
    val kmPerPx = 111.2 / p.ky
    val want = 90 * density * kmPerPx
    val pow = 10.0.pow(floor(ln(want) / ln(10.0)))
    val nice = listOf(1.0, 2.0, 5.0, 10.0).map { it * pow }.last { it <= want }
    val w = (nice / kmPerPx).toFloat()
    val s = density
    val x0 = 16 * s
    val y0 = size.height - bottomInset - 18 * s
    val label = (if (nice >= 1) "%,d".format(nice.toLong()).replace(',', ' ') else "%.1f".format(nice)) + if (ru) " км" else " km"
    val m = text.measure(label, TextStyle(fontFamily = T.MapTown.fontFamily, fontSize = 12.sp, color = C.ink))
    // A patch of paper under the bar, so town names don't run through it.
    drawRect(C.paper.copy(alpha = 0.85f), Offset(x0 - 6 * s, y0 - m.size.height / 2f - 2 * s), Size(w + m.size.width + 18 * s, m.size.height + 4 * s))
    drawRect(C.ink, Offset(x0, y0), Size(w, 3 * s), style = Stroke(0.8f * s))
    drawRect(C.ink, Offset(x0, y0), Size(w / 2, 3 * s))
    drawText(m, topLeft = Offset(x0 + w + 6 * s, y0 - m.size.height / 2f + 1.5f * s))
}
