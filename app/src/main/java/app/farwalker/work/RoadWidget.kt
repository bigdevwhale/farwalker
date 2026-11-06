package app.farwalker.work

import android.app.AlarmManager
import android.app.PendingIntent
import android.appwidget.AppWidgetManager
import android.appwidget.AppWidgetProvider
import android.content.ComponentName
import android.content.Context
import android.content.Intent
import android.graphics.Bitmap
import android.graphics.Canvas
import android.graphics.DashPathEffect
import android.graphics.Paint
import android.graphics.Path
import android.graphics.RectF
import android.graphics.Typeface
import android.widget.RemoteViews
import androidx.core.content.res.ResourcesCompat
import app.farwalker.MainActivity
import app.farwalker.R
import app.farwalker.container
import app.farwalker.domain.Geo
import app.farwalker.domain.Journey
import app.farwalker.domain.Mode
import app.farwalker.domain.Quests
import app.farwalker.domain.Travel
import app.farwalker.domain.World
import app.farwalker.localized
import app.farwalker.ru
import app.farwalker.ui.Words
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch
import kotlinx.coroutines.withTimeout
import java.time.LocalDate
import java.time.ZoneId
import kotlin.math.cos
import kotlin.math.max

/**
 * The road on the home screen: the day and the land underfoot, where the traveller is, today's steps,
 * the road between two towns and the day's task, beside a little engraved map of the country round them.
 * Drawn as one bitmap, so the fonts and the map look the same as in the app.
 */
class RoadWidget : AppWidgetProvider() {
    override fun onUpdate(context: Context, manager: AppWidgetManager, ids: IntArray) {
        val pending = goAsync()
        CoroutineScope(Dispatchers.Default).launch {
            try {
                // The widget's own periodic update wakes the app even after it was swiped away, which on some
                // phones force-stops it and drops the 15-minute work: so take the steps in here too, and put
                // the work back.
                runCatching { Alarms.schedule(context) }
                runCatching { withTimeout(SYNC_MS) { context.container.sync() } }
                updateAll(context)
            } finally {
                pending.finish()
            }
        }
    }

    companion object {
        /** How long the widget waits for the steps before drawing with what it has (a broadcast gets about 10 s). */
        private const val SYNC_MS = 7_000L

        suspend fun updateAll(context: Context) {
            val manager = AppWidgetManager.getInstance(context)
            val ids = manager.getAppWidgetIds(ComponentName(context, RoadWidget::class.java))
            if (ids.isEmpty()) return
            val c = context.container
            if (c.trip.state.value == null) c.trip.refresh()
            val j = c.trip.state.value ?: return
            if (!j.started) return
            val r = context.localized()
            val bmp = render(r, Words(r, r.ru, c.world), c.world, c.travel, j)
            val open = PendingIntent.getActivity(
                context, 0, Intent(context, MainActivity::class.java),
                PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT,
            )
            for (id in ids) {
                val views = RemoteViews(context.packageName, R.layout.widget_road)
                views.setImageViewBitmap(R.id.widget_image, bmp)
                views.setOnClickPendingIntent(R.id.widget_image, open)
                manager.updateAppWidget(id, views)
            }
            atMidnight(context, ids)
        }

        /** The steps are today's, so the widget redraws itself just after midnight even if nothing else wakes it. */
        private fun atMidnight(context: Context, ids: IntArray) {
            val intent = Intent(context, RoadWidget::class.java)
                .setAction(AppWidgetManager.ACTION_APPWIDGET_UPDATE)
                .putExtra(AppWidgetManager.EXTRA_APPWIDGET_IDS, ids)
            val pending = PendingIntent.getBroadcast(context, 1, intent, PendingIntent.FLAG_IMMUTABLE or PendingIntent.FLAG_UPDATE_CURRENT)
            val at = LocalDate.now().plusDays(1).atStartOfDay(ZoneId.systemDefault()).toInstant().toEpochMilli() + 60_000
            runCatching { context.getSystemService(AlarmManager::class.java).setAndAllowWhileIdle(AlarmManager.RTC, at, pending) }
        }

        // The atlas's colours (ui.theme.C), as plain ints for android.graphics.
        private const val PAPER = 0xFFEFE3C8.toInt()
        private const val PAPER2 = 0xFFE5D5B0.toInt()
        private const val LAND = 0xFFF3E9D2.toInt()
        private const val INK = 0xFF2B2118.toInt()
        private const val MUTED = 0xFF6B5A45.toInt()
        private const val FAINT = 0xFFA08E72.toInt()
        private const val RED = 0xFF9E2B20.toInt()
        private const val SEA = 0xFF4E6577.toInt()
        private const val WASH = 0xFFD9A85F.toInt()

        private const val W = 1080
        private const val H = 400

        /** Degrees of latitude from the traveller to the map's top and bottom edges. */
        private const val SPAN = 3.2

        fun render(r: Context, w: Words, world: World, travel: Travel, j: Journey): Bitmap {
            val out = Bitmap.createBitmap(W, H, Bitmap.Config.ARGB_8888)
            val cv = Canvas(out)
            val bold = ResourcesCompat.getFont(r, R.font.cormorant_bold) ?: Typeface.SERIF
            val semi = ResourcesCompat.getFont(r, R.font.cormorant_semibold) ?: Typeface.SERIF
            val serif = ResourcesCompat.getFont(r, R.font.pt_serif_regular) ?: Typeface.SERIF
            val italic = ResourcesCompat.getFont(r, R.font.pt_serif_italic) ?: Typeface.SERIF
            fun paint(color: Int, stroke: Float = 0f) = Paint(Paint.ANTI_ALIAS_FLAG).apply {
                this.color = color
                if (stroke > 0) { style = Paint.Style.STROKE; strokeWidth = stroke }
            }

            // The card: paper in a heavy rule and a hairline inside it, as on the app's framed sheets.
            val card = RectF(4f, 4f, W - 4f, H - 4f)
            cv.drawRoundRect(card, 18f, 18f, paint(PAPER))
            cv.drawRoundRect(card, 18f, 18f, paint(INK, 4f))
            cv.drawRoundRect(RectF(16f, 16f, W - 16f, H - 16f), 10f, 10f, paint(INK and 0x99FFFFFF.toInt(), 1.6f))

            // The map on the right, in its own frame.
            val m = RectF(W - 32f - 330f, 32f, W - 32f, H - 32f)
            map(cv, m, world, travel, j)
            cv.drawRect(m, paint(INK, 3f))

            // The words on the left, fitted to the room beside the map.
            val x = 44f
            val room = m.left - 28f - x
            fun text(t: String, y: Float, face: Typeface, size: Float, color: Int, spacing: Float = 0f) {
                val p = paint(color).apply { typeface = face; textSize = size; letterSpacing = spacing }
                val wd = p.measureText(t)
                if (wd > room) p.textSize = size * room / wd
                cv.drawText(t, x, y, p)
            }
            val today = LocalDate.now().toEpochDay()
            val day = (today - j.start + 1).toInt().coerceAtLeast(1)
            text((r.getString(R.string.day_n, day) + " · " + w.country(j.iso)).uppercase(), 70f, bold, 30f, MUTED, 0.14f)
            val ride = j.ride
            val title = when {
                ride?.mode == Mode.TRAIN -> r.getString(R.string.on_train, w.town(lastStop(world, travel, j)))
                ride?.mode == Mode.SHIP -> r.getString(R.string.at_sea, w.town(lastStop(world, travel, j)))
                j.at > 0 -> r.getString(R.string.on_road)
                else -> w.town(travel.sleepTown(j))
            }
            text(title, 132f, bold, 60f, INK)
            val steps = j.days[today] ?: 0
            val km = j.dayKm[today] ?: 0.0
            text(r.getString(R.string.km_n, w.km(km)) + " · " + r.getString(R.string.steps_n, w.group(steps.toLong())), 186f, serif, 34f, INK)

            // The road underfoot: the town behind, the red line walked, the town ahead.
            val next = travel.next(j)
            if (ride == null && next != null && j.legs.isNotEmpty()) {
                val len = world.edge(j.legs[0].edge).km.toDouble()
                val f = (j.at / len).coerceIn(0.0, 1.0).toFloat()
                val y = 226f
                val x0 = x + 10
                val x1 = x + room - 10
                val xf = x0 + (x1 - x0) * f
                cv.drawLine(xf, y, x1, y, paint(RED, 4f).apply { pathEffect = DashPathEffect(floatArrayOf(4f, 10f), 0f); strokeCap = Paint.Cap.ROUND })
                cv.drawLine(x0, y, xf, y, paint(RED, 5f))
                cv.drawCircle(x0, y, 8f, paint(INK))
                cv.drawCircle(x1, y, 8f, paint(PAPER))
                cv.drawCircle(x1, y, 8f, paint(INK, 2.5f))
                cv.drawCircle(xf, y, 12f, paint(RED))
                cv.drawCircle(xf, y, 12f, paint(INK, 2.5f))
                cv.drawCircle(xf, y, 4f, paint(PAPER))
                val from = w.town(j.town)
                val to = w.town(next)
                val sp = paint(MUTED).apply { typeface = serif; textSize = 26f }
                val tp = paint(INK).apply { typeface = serif; textSize = 26f; isFakeBoldText = true }
                val half = room / 2 - 8
                fun clip(t: String, p: Paint): String {
                    if (p.measureText(t) <= half) return t
                    var s = t
                    while (s.length > 1 && p.measureText("$s…") > half) s = s.dropLast(1)
                    return "$s…"
                }
                cv.drawText(clip(from, sp), x, y + 44, sp)
                val toText = clip(to, tp)
                cv.drawText(toText, x + room - tp.measureText(toText), y + 44, tp)
            } else {
                val line = if (j.dest >= 0) r.getString(R.string.goal, w.town(j.dest), w.km(travel.left(j))) else r.getString(R.string.no_goal)
                text(line, 240f, italic, 28f, MUTED)
            }

            // The day's task: how far along, in a thin bar, red when done.
            val q = Quests.of(j, today)[0]
            val ty = 318f
            val done = q.done
            text(
                if (done) r.getString(R.string.widget_task_done) + " · " + r.getString(R.string.task_reward, w.coins(q.reward))
                else r.getString(R.string.widget_task, w.group(q.progress.toLong()), w.group(q.target.toLong())),
                ty, italic, 28f, if (done) RED else MUTED,
            )
            val by = ty + 20
            cv.drawRect(x, by, x + room, by + 12, paint(PAPER2))
            cv.drawRect(x, by, x + room * (q.progress / q.target).toFloat().coerceIn(0f, 1f), by + 12, paint(if (done) RED else SEA))
            cv.drawRect(x, by, x + room, by + 12, paint(INK, 1.6f))
            return out
        }

        /** Where the current ride ends. */
        private fun lastStop(world: World, travel: Travel, j: Journey): Int {
            val ride = j.ride ?: return j.town
            var left = ride.km - ride.done + j.at
            for (leg in j.legs) {
                left -= world.edge(leg.edge).km
                if (left <= 1e-6) return travel.router.to(leg)
            }
            return j.legs.lastOrNull()?.let { travel.router.to(it) } ?: j.town
        }

        /**
         * The country round the traveller, as the atlas draws it: hatched sea, land in outline with the
         * stamped countries washed, the road walked in red, and the traveller.
         */
        private fun map(cv: Canvas, m: RectF, world: World, travel: Travel, j: Journey) {
            val (lat, lon) = travel.position(j)
            val ky = m.height() / (2 * SPAN)
            val kx = ky * max(0.2, cos(Math.toRadians(lat)))
            val cx = m.centerX()
            val cy = m.centerY()
            fun px(lo: Double) = (cx + (Geo.near(lon, lo) - lon) * kx).toFloat()
            fun py(la: Double) = (cy + (lat - la) * ky).toFloat()
            val halfLon = m.width() / 2 / kx + 1
            cv.save()
            cv.clipRect(m)
            cv.drawRect(m, Paint().apply { color = PAPER })
            val hatch = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = SEA; alpha = 40; strokeWidth = 1.4f }
            var y = m.top + 6
            while (y < m.bottom) { cv.drawLine(m.left, y, m.right, y, hatch); y += 9f }

            val fill = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = LAND }
            val wash = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = WASH; alpha = 120 }
            val here = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; alpha = 38 }
            val line = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; alpha = 200; style = Paint.Style.STROKE; strokeWidth = 1.8f; strokeJoin = Paint.Join.ROUND }
            for ((iso, rings) in world.shapes) {
                val path = Path()
                for (ring in rings) {
                    if (ring.maxLat < lat - SPAN - 1 || ring.minLat > lat + SPAN + 1) continue
                    val pts = ring.points
                    // A ring across the date line is drawn where it lies nearest the traveller.
                    var near = false
                    var k = 0
                    while (k < pts.size) {
                        if (kotlin.math.abs(Geo.near(lon, pts[k].toDouble()) - lon) < halfLon) { near = true; break }
                        k += 16
                    }
                    if (!near && !(ring.minLon <= lon && ring.maxLon >= lon)) continue
                    var prev = Double.NaN
                    for (i in 0 until pts.size / 2) {
                        val lo = if (prev.isNaN()) Geo.near(lon, pts[2 * i].toDouble()) else Geo.near(prev, pts[2 * i].toDouble())
                        prev = lo
                        val xx = (cx + (lo - lon) * kx).toFloat()
                        val yy = py(pts[2 * i + 1].toDouble())
                        if (i == 0) path.moveTo(xx, yy) else path.lineTo(xx, yy)
                    }
                    path.close()
                }
                if (path.isEmpty) continue
                cv.drawPath(path, fill)
                when {
                    iso == j.iso -> cv.drawPath(path, here)
                    iso in j.stamps -> cv.drawPath(path, wash)
                }
                cv.drawPath(path, line)
            }

            // The road walked, as far back as it shows.
            val trail = Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED; style = Paint.Style.STROKE; strokeWidth = 4f; strokeCap = Paint.Cap.ROUND; strokeJoin = Paint.Join.ROUND }
            val walked = Path()
            val recent = j.trail.takeLast(60)
            for ((i, v) in recent.withIndex()) {
                val t = world.town(v.town)
                if (i == 0) walked.moveTo(px(t.lon), py(t.lat)) else walked.lineTo(px(t.lon), py(t.lat))
            }
            walked.lineTo(px(lon), py(lat))
            cv.drawPath(walked, trail)
            // The way ahead, dotted, to the goal.
            val ahead = Path().apply { moveTo(px(lon), py(lat)) }
            for (leg in j.legs.take(40)) {
                val t = world.town(travel.router.to(leg))
                ahead.lineTo(px(t.lon), py(t.lat))
            }
            cv.drawPath(ahead, Paint(trail).apply { strokeWidth = 3f; pathEffect = DashPathEffect(floatArrayOf(1f, 9f), 0f) })
            if (j.dest >= 0) {
                val g = world.town(j.dest)
                cv.drawCircle(px(g.lon), py(g.lat), 7f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = RED })
            }
            val o = Paint(Paint.ANTI_ALIAS_FLAG)
            o.color = RED; o.alpha = 50; cv.drawCircle(cx, cy, 24f, o)
            o.alpha = 255; cv.drawCircle(cx, cy, 11f, o)
            cv.drawCircle(cx, cy, 11f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = INK; style = Paint.Style.STROKE; strokeWidth = 2.5f })
            cv.drawCircle(cx, cy, 3.5f, Paint(Paint.ANTI_ALIAS_FLAG).apply { color = PAPER })
            cv.restore()
            // A hairline inside the frame, as round the app's framed sheets.
            cv.drawRect(RectF(m.left + 6, m.top + 6, m.right - 6, m.bottom - 6), Paint(Paint.ANTI_ALIAS_FLAG).apply { color = FAINT; style = Paint.Style.STROKE; strokeWidth = 1f })
        }
    }
}
