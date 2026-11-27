package jp.co.soramitsu.ui_core.sacred

import kotlin.math.PI
import kotlin.math.cos
import kotlin.math.exp
import kotlin.math.max
import kotlin.math.min
import kotlin.math.pow
import kotlin.math.sin

/**
 * The launch scene, as the numbers its shader is given each frame.
 *
 * The mark (the red disc with 天) comes down out of the lens onto the page. Where it lands, seven rings of mother of
 * pearl spread out from under it like the ripples of a drop and stay: a stack of polished discs with the mark on
 * top, lit from the upper left. While the wallet gets ready a pulse runs out through them. Then the mark lifts off,
 * the rings sink back into the page, and the mark sets down where the next screen shows it. Turning the phone leans
 * the stack, moves the lamp and runs the shell's colours. The iOS app computes the same numbers
 * (SacredTenmonScene.swift).
 */
class TenmonScene(
    /** The surface's size in pixels, and how many of them make a dp. */
    val width: Float,
    val height: Float,
    val pixelsPerDp: Float,
    /** Where the mark lands and its radius there, in pixels. */
    val landingX: Float,
    val landingY: Float,
    landingRadius: Float,
    /** The page the rings sink into, as sRGB 0..1. */
    val pageRed: Float,
    val pageGreen: Float,
    val pageBlue: Float,
    /** Whether the mark stays when everything else has given way to the page. */
    val keepsMark: Boolean,
) {
    enum class Phase { INTRO, HOLD, OUTRO }

    /** What the shader reads each frame; the five are named as its uniforms are. */
    class Uniforms(
        /** x, y: size in pixels; z: seconds; w: pixels per dp. */
        val view: FloatArray,
        /** x, y: how far the stack of rings leans, per unit of height; z, w: how far the lamp has moved. */
        val flight: FloatArray,
        /** x, y: the mark's centre in pixels; z: its radius; w: its radius at rest, the rings' measure. */
        val lens: FloatArray,
        /** x: how far the rings have spread; y: seconds since they came to rest; z: the mark's lift; w: reveal. */
        val state: FloatArray,
        /** r, g, b: the page; w: 1 if the mark gives way with the rest. */
        val page: FloatArray,
    )

    /** The mark on its way down: its size over its resting size, how far above the page, how much of it is seen. */
    class Arriving(val scale: Float, val lift: Float, val seen: Float)

    /** The mark on its way to where it lands: how far along, how far lifted, how far the rings have given way. */
    class Leaving(val move: Float, val lift: Float, val reveal: Float)

    val landingRadius: Float = max(1f, landingRadius)

    /** The mark's radius at rest on this surface: the unit the rings are drawn in. */
    val rest: Float get() = restRadius(width, height)

    /**
     * @param progress 0..1 through the phase (unused while holding)
     * @param clock seconds since the scene first drew; the grain runs on it
     * @param waited seconds since the rings came to rest; the pulse and the lamp's drift run on it
     * @param tiltX how the phone is held, each axis -1..1
     */
    fun uniforms(phase: Phase, progress: Float, clock: Float, waited: Float, tiltX: Float = 0f, tiltY: Float = 0f): Uniforms {
        val t = unit(progress)
        val middleX = width / 2f
        val middleY = height / 2f
        val leanX = tiltX * TILT_LEAN
        val leanY = tiltY * TILT_LEAN
        val lampX = tiltX * TILT_LAMP + driftX(waited)
        val lampY = tiltY * TILT_LAMP + driftY(waited)
        // The mark swells a little each time the pulse leaves it.
        val swell = 0.035f * pulse(0, waited)
        val rest = rest
        val view = floatArrayOf(width, height, clock, pixelsPerDp)
        val page = floatArrayOf(pageRed, pageGreen, pageBlue, if (keepsMark) 0f else 1f)
        return when (phase) {
            Phase.INTRO -> {
                val mark = markArriving(t)
                // The camera starts off to one side of the stack and comes over it as the rings spread.
                val swing = 1f - easeOut(unit((t - 0.1f) / 0.9f))
                Uniforms(
                    view,
                    floatArrayOf(0.22f * swing + leanX, -1.15f * swing + leanY, lampX, lampY),
                    floatArrayOf(middleX, middleY, rest * mark.scale, rest),
                    floatArrayOf(bloom(t), 0f, mark.lift, 0f),
                    page,
                )
            }
            Phase.HOLD -> Uniforms(
                view,
                floatArrayOf(leanX, leanY, lampX, lampY),
                floatArrayOf(middleX, middleY, rest * (1f + swell), rest),
                floatArrayOf(1f, waited, 0f, 0f),
                page,
            )
            Phase.OUTRO -> {
                // Up off the stack, across to where it lands, and down.
                val way = markLeaving(t)
                val standing = rest * (1f + swell * (1f - way.move))
                val radius = (standing + (landingRadius - standing) * way.move) * (1f + 0.9f * way.lift)
                Uniforms(
                    view,
                    floatArrayOf(leanX, leanY, lampX, lampY),
                    floatArrayOf(
                        middleX + (landingX - middleX) * way.move,
                        middleY + (landingY - middleY) * way.move,
                        radius,
                        rest,
                    ),
                    floatArrayOf(1f, waited, way.lift, way.reveal),
                    page,
                )
            }
        }
    }

    companion object {
        const val INTRO_SECONDS = 1.0f
        const val OUTRO_SECONDS = 0.6f

        /** The pulse leaves the mark once in this many seconds. Matches PULSE_PERIOD in the shader. */
        const val PULSE_PERIOD = 1.9f

        /**
         * The rings, counted from the innermost. One stands this far above the next, in units of the mark's radius
         * at rest. Matches STEP in the shader.
         */
        const val RING_COUNT = 7
        const val RING_STEP = 0.10f

        /** The share of the intro the mark spends coming down, and the share its landing gives under it. */
        private const val FALL = 0.26f
        private const val GIVE = 0.24f

        /** How many times its resting size larger the mark is when it first shows, out of focus at the lens. */
        private const val NEARNESS = 2.6f

        /**
         * The mark's radius at rest, as a share of the width. A window that is wide for its height (an unfolded
         * screen, a folded cover) measures by 0.6 of its height instead, so the rings fill it as they fill a phone.
         */
        private const val MARK_SHARE = 0.155f
        private const val WIDEST_FRAME = 0.6f

        /** How far a full tilt of the phone leans the stack, and moves the lamp. */
        private const val TILT_LEAN = 0.9f
        private const val TILT_LAMP = 0.5f

        /** The mark's radius at rest on a surface of this size, in the size's own measure. */
        fun restRadius(width: Float, height: Float): Float = MARK_SHARE * min(width, height * WIDEST_FRAME)

        fun unit(value: Float): Float = min(1f, max(0f, value))

        fun easeInOut(t: Float): Float = if (t < 0.5f) 4f * t * t * t else 1f - (-2f * t + 2f).pow(3) / 2f

        fun easeOut(t: Float): Float = 1f - (1f - t).pow(3)

        fun smooth(from: Float, to: Float, value: Float): Float {
            val t = unit((value - from) / (to - from))
            return t * t * (3f - 2f * t)
        }

        // The rings. These match the shader, which draws them from the same clocks.

        /** Ring [index]'s radius at rest, in units of the mark's. */
        fun ringRadius(index: Int): Float = 1.52f + 0.60f * index + 0.07f * index * index

        /** How far the rings' clock has run at [progress] through the intro: they start as the mark lands. */
        fun bloom(progress: Float): Float = unit((unit(progress) - FALL + 0.02f) / (1f - FALL + 0.02f))

        /**
         * How far ring [index] has spread, 0..1 and a twentieth over on the way. The outermost leaves first and
         * arrives last, as the first ripple of a drop does.
         */
        fun ringSpread(index: Int, bloom: Float): Float {
            val start = 0.045f * (6 - index)
            val end = 0.74f + 0.04f * index
            val back = unit((bloom - start) / (end - start)) - 1f
            return 1f + 2.2f * back * back * back + 1.2f * back * back
        }

        /**
         * The pulse at the mark ([place] 0) or at ring `place - 1`, after waiting [seconds]: 0..1. It leaves the
         * mark once each period and passes the rings one after another.
         */
        fun pulse(place: Int, seconds: Float): Float {
            val waited = max(0f, seconds)
            val head = (waited / PULSE_PERIOD) % 1f * 13f - 2.5f
            val apart = head - place
            return exp(-apart * apart * 0.9f) * smooth(0f, 0.2f, waited)
        }

        // The mark.

        /** The mark on its way down, at [progress] through the intro. */
        fun markArriving(progress: Float): Arriving {
            val t = unit(progress)
            // Faster as it comes; the page gives a little under it and springs back.
            val down = unit(t / FALL)
            val lift = 1f - down * down
            val hit = unit((t - FALL) / GIVE)
            val squash = sin(PI.toFloat() * hit) * 0.07f * (1f - hit)
            return Arriving((1f + NEARNESS * lift) * (1f - squash), lift, 1f - smooth(0.5f, 1f, lift))
        }

        /** The mark on its way to where it lands, at [progress] through the outro: nearest the lens halfway. */
        fun markLeaving(progress: Float): Leaving {
            val t = unit(progress)
            return Leaving(easeInOut(t), max(0f, sin(PI.toFloat() * t)) * 0.10f, smooth(0f, 0.85f, t))
        }

        /** How far the lamp has wandered after waiting [seconds], so the shell is never quite still. */
        fun driftX(seconds: Float): Float = 0.10f * sin(0.9f * max(0f, seconds))

        fun driftY(seconds: Float): Float = 0.08f * (1f - cos(0.7f * max(0f, seconds)))
    }
}

/**
 * Where the launch scene stands at a moment. There is one for the process: the startup screen and the first screen
 * each show the scene in a window of their own, and both read this clock, so the second carries on exactly where the
 * first stood. The rings spread for [TenmonScene.INTRO_SECONDS] from the start, wait, and once [leave] is called the
 * mark flies on for [TenmonScene.OUTRO_SECONDS].
 */
class TenmonTimeline {
    /** [over]: the way out has run its whole time. */
    class Moment(val phase: TenmonScene.Phase, val progress: Float, val clock: Float, val waited: Float, val over: Boolean)

    private var startNanos = 0L
    private var leaveNanos = 0L
    private var started = false
    private var leaving = false
    private var leavesOnStart = false

    val isStarted: Boolean @Synchronized get() = started

    /** Whether the mark has been told to fly on (it does so once the rings lie spread). */
    val isLeaving: Boolean @Synchronized get() = leaving || leavesOnStart

    /** The mark starts coming down at [nowNanos]. Only the first call counts; it says whether it was this one. */
    @Synchronized
    fun start(nowNanos: Long): Boolean {
        if (started) return false
        started = true
        startNanos = nowNanos
        if (leavesOnStart) leave(nowNanos)
        return true
    }

    /** The first screen is ready: the mark flies on at [nowNanos], or as soon as the rings lie spread. */
    @Synchronized
    fun leave(nowNanos: Long) {
        if (leaving) return
        if (!started) {
            leavesOnStart = true
            return
        }
        leaving = true
        leaveNanos = max(nowNanos, startNanos + INTRO_NANOS)
    }

    @Synchronized
    fun at(nowNanos: Long): Moment {
        if (!started) return Moment(TenmonScene.Phase.INTRO, 0f, 0f, 0f, false)
        val seconds = max(0L, nowNanos - startNanos) / NANOS
        val waited = max(0f, seconds - TenmonScene.INTRO_SECONDS)
        if (leaving && nowNanos >= leaveNanos) {
            // The pulse runs on while the mark flies off, so nothing jumps when the wait ends.
            val progress = (nowNanos - leaveNanos) / NANOS / TenmonScene.OUTRO_SECONDS
            return Moment(TenmonScene.Phase.OUTRO, min(1f, progress), seconds, waited, progress >= 1f)
        }
        if (seconds < TenmonScene.INTRO_SECONDS) {
            return Moment(TenmonScene.Phase.INTRO, seconds / TenmonScene.INTRO_SECONDS, seconds, 0f, false)
        }
        return Moment(TenmonScene.Phase.HOLD, 0f, seconds, waited, false)
    }

    private companion object {
        const val NANOS = 1_000_000_000f
        const val INTRO_NANOS = 1_000_000_000L
    }
}
