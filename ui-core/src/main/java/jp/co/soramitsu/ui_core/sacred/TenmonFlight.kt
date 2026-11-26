package jp.co.soramitsu.ui_core.sacred

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import jp.co.soramitsu.ui_core.lit.LitLightRig

/**
 * The launch scene across the two screens every start of the app goes through.
 *
 * The startup screen [begin]s it: on the light page the mark comes down and the rings of mother of pearl spread
 * under it, and a pulse runs through them while the wallet is checked. The first screen (the PIN screen, or the
 * welcome) [carryOn]s it over itself exactly where it stood, and once that screen has said where it shows the mark
 * ([firstScreenShown]) the mark flies there, the rings sink into the page and the screen is uncovered. Until that
 * word the scene keeps the screen covered and takes its touches: nothing of the screen behind the PIN shows before
 * the PIN does. A tap on the scene says the person will not wait for it: it goes the moment the screen is ready,
 * or at once if the mark is already on its way, and from then every touch is the screen's.
 *
 * A phone that does not play the traced scene (Android 12 or older, power saving, animations removed, a hot phone,
 * or a GPU that will not draw it) shows the plain scene at rest on the startup screen, and its first screen carries
 * that still picture until it is ready, then uncovers itself. Call everything here on the main thread.
 */
object TenmonFlight {
    /** The page the scene plays on: the light page whatever theme is stored, as the launch window before it. */
    const val PAGE = 0xFFECE9E3.toInt()

    enum class Mode {
        /** The traced scene, moving, answering the phone's tilt. */
        TRACED,

        /** The plain scene at rest: nothing moves. */
        STILL,
    }

    /** Where a screen shows the mark, in its window's pixels. */
    class Landing(val x: Float, val y: Float, val radius: Float)

    /**
     * What a phone gets. [motion] is the device's motion policy (the full effect tier, no power saving, animations
     * not removed); it is asked before anything touches the GPU.
     */
    fun mode(motion: Boolean, sdkInt: Int, hot: Boolean): Mode =
        if (motion && sdkInt >= Build.VERSION_CODES.TIRAMISU && !hot) Mode.TRACED else Mode.STILL

    private var timeline = TenmonTimeline()

    /** The deadlines of a scene over a first screen run here, whether or not that screen ever reached its window. */
    private val main by lazy { Handler(Looper.getMainLooper()) }

    /** A traced scene has begun and is still on: the first screen carries it on. */
    private var playing = false

    /** When the startup screen's scene left its window with no first screen carrying it on; 0 while it is up. */
    private var orphanedNanos = 0L
    private var carry: Carry? = null

    /** A startup screen began a launch, in any mode, that no first screen has carried on yet. */
    private var launchPending = false

    /** When that startup screen left its window; 0 while it is up. */
    private var startupGoneNanos = 0L

    /** Counts the launches begun: only the startup screen that began the newest one can end it by going. */
    private var launches = 0

    /**
     * Whether the next first screen carries a launch on, traced or still: that screen then opens with no transition
     * of its own, as the scene is over it from its first frame.
     */
    val carriesOn: Boolean
        get() {
            // A launch whose startup screen went away with no first screen after it is over.
            if (launchPending && carry == null && startupGoneNanos != 0L &&
                SystemClock.elapsedRealtimeNanos() - startupGoneNanos > REJOIN_NANOS
            ) {
                launchPending = false
            }
            return isPlaying || (launchPending && carry == null)
        }

    /** Whether the traced scene is on for the next screen to carry on. */
    val isPlaying: Boolean
        get() {
            // A scene whose startup screen went away with no first screen after it is over.
            if (playing && carry == null && orphanedNanos != 0L &&
                SystemClock.elapsedRealtimeNanos() - orphanedNanos > REJOIN_NANOS
            ) {
                playing = false
            }
            return playing && carry == null
        }

    /**
     * Starts the scene in [host], which it fills, and says how it plays. A startup screen made again while its scene
     * is on (the phone was turned, the theme applied) joins the scene where it stands.
     */
    fun begin(host: ViewGroup, motion: Boolean): Mode {
        val context = host.context
        host.removeAllViews()
        if (carry == null) {
            launchPending = true
            startupGoneNanos = 0L
            val launch = ++launches
            // Only the startup screen's window going ends the launch: the scene taken out of it ([end]) does not, and
            // neither does an older startup screen that goes after this one has begun.
            host.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
                override fun onViewAttachedToWindow(view: View) = Unit

                override fun onViewDetachedFromWindow(view: View) {
                    view.removeOnAttachStateChangeListener(this)
                    if (launch == launches && launchPending && carry == null) {
                        startupGoneNanos = SystemClock.elapsedRealtimeNanos()
                    }
                }
            })
        }
        val stage = TenmonStage(context)
        host.addView(stage, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        val mode = mode(motion, Build.VERSION.SDK_INT, LitLightRig.isHot(context.applicationContext))
        if (mode == Mode.STILL || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) {
            if (carry == null) playing = false
            stage.showRest()
            return Mode.STILL
        }
        val now = SystemClock.elapsedRealtimeNanos()
        if (!isPlaying) timeline = TenmonTimeline()
        playing = true
        orphanedNanos = 0L
        stage.onGaveUp = { if (carry == null) playing = false }
        stage.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                if (playing && carry == null) orphanedNanos = SystemClock.elapsedRealtimeNanos()
            }
        })
        stage.play(timeline, if (timeline.isStarted) timeline.at(now) else null)
        return Mode.TRACED
    }

    /**
     * Stops the scene in [host] where it waits, with nothing moving, so what the startup screen shows over it is
     * seen. The first screen then carries that still picture.
     */
    fun rest(host: ViewGroup) {
        (host.getChildAt(0) as? TenmonStage)?.rest()
        if (carry == null) playing = false
    }

    /**
     * Takes the scene out of [host]: the startup screen shows something else. The first screen still keeps itself
     * covered, by the still picture, until it is ready.
     */
    fun end(host: ViewGroup) {
        (host.getChildAt(0) as? TenmonStage)?.dropScene()
        host.removeAllViews()
        if (carry == null) playing = false
    }

    /** Frames the traced scene in [host] has really drawn; 0 where the plain scene shows. */
    fun framesDrawn(host: ViewGroup): Int = (host.getChildAt(0) as? TenmonStage)?.scene?.framesCompleted ?: 0

    /**
     * Carries the launch the startup screen began over [activity]'s whole window, until its first screen is ready
     * ([firstScreenShown]) or, with no word from it, the longest wait is over. The traced scene goes on where it
     * stood; a launch that showed the still picture, or none, keeps that picture over the screen. Returns whether
     * there was a launch to carry.
     */
    fun carryOn(activity: Activity): Boolean {
        if (!carriesOn) return false
        val window = activity.window ?: return false
        val decor = window.decorView as? ViewGroup ?: return false
        val traced = isPlaying && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU
        launchPending = false
        carry = cover(activity, window, decor, traced)
        return true
    }

    private fun cover(activity: Activity, window: Window, decor: ViewGroup, traced: Boolean): Carry {
        val stage = TenmonStage(activity)
        // Over everything the window draws, the bars' own bands included: the scene stands where the startup screen
        // showed it.
        stage.translationZ = OVER_THE_WINDOW
        // Nothing below is touched while it is covered.
        stage.isClickable = true
        stage.onTouched = { skip() }
        if (traced && Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
            stage.play(timeline, if (timeline.isStarted) timeline.at(SystemClock.elapsedRealtimeNanos()) else null)
        } else {
            // The rings at rest: traced where this phone has drawn the scene, plain where it has not.
            stage.cover.showFrame(TenmonStage.RESTING, null)
        }
        decor.addView(stage, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Dark icons in the bars over the light page, whatever the theme under it.
        val bars = WindowCompat.getInsetsController(window, decor)
        val made = Carry(stage, bars, bars.isAppearanceLightStatusBars, bars.isAppearanceLightNavigationBars)
        bars.isAppearanceLightStatusBars = true
        bars.isAppearanceLightNavigationBars = true
        // A scene that would not draw over this screen has left the still picture in its place, which keeps the screen
        // covered until it is ready.
        stage.onGaveUp = { }
        stage.addOnAttachStateChangeListener(object : View.OnAttachStateChangeListener {
            override fun onViewAttachedToWindow(view: View) = Unit

            override fun onViewDetachedFromWindow(view: View) {
                // The window is going (the screen finished or was made again): the window takes the stage with it.
                if (carry?.stage === view) finish(0, detaching = true)
            }
        })
        main.postDelayed(made.leaves, FIRST_SCREEN_GRACE_MILLIS)
        main.postDelayed(made.ends, LONGEST_MILLIS)
        return made
    }

    /**
     * The first screen is laid out. [landing] is where it shows the mark, which then flies there and stays as the
     * rings sink; without one the mark gives way with the rings. Does nothing when no scene covers the screen.
     */
    fun firstScreenShown(landing: Landing?) {
        val carry = carry ?: return
        if (carry.left) {
            // Laid out again before the mark has left: it flies to where the screen shows it now.
            val scene = carry.stage.scene ?: return
            if (timeline.at(SystemClock.elapsedRealtimeNanos()).phase != TenmonScene.Phase.OUTRO) aim(carry, scene, landing)
            return
        }
        carry.landing = landing
        // A screen is often laid out twice as its insets arrive: the mark flies to where it has come to rest.
        main.removeCallbacks(carry.leaves)
        main.postDelayed(carry.leaves, SETTLE_MILLIS)
    }

    private fun aim(carry: Carry, scene: TenmonView, landing: Landing?) {
        carry.landing = landing
        scene.landing = landing
        // Under the surface, the still takes the frame the scene ends on: what stays when the surface goes.
        carry.stage.cover.showFrame(ARRIVED, landing)
    }

    /** The first screen is uncovered: the mark flies on, and from here every touch reaches the screen below. */
    private fun leave() {
        val carry = carry ?: return
        if (carry.left) return
        main.removeCallbacks(carry.leaves)
        val scene = carry.stage.scene
        if (scene == null) {
            finish(FADE_MILLIS)
            return
        }
        if (carry.skipped) {
            // Asked not to wait: the scene does not play its way out. The mark stands where the screen shows its
            // own, and fades over it.
            carry.stage.cover.showFrame(ARRIVED, carry.landing)
            finish(FADE_MILLIS)
            return
        }
        if (scene.framesCompleted == 0) {
            // The scene's surface over this screen has no frame yet. The mark flies on when it has, so the way out
            // is seen from its start; a surface that has none soon after the screen is ready, the screen does not
            // wait for: what the scene stands at fades over it.
            if (carry.frameWaits++ < FRAME_WAITS) {
                main.postDelayed(carry.leaves, FRAME_MILLIS)
            } else {
                carry.stage.cover.showFrame(timeline.at(SystemClock.elapsedRealtimeNanos()), carry.landing)
                finish(FADE_MILLIS)
            }
            return
        }
        carry.left = true
        carry.stage.isClickable = false
        aim(carry, scene, carry.landing)
        scene.onOver = { finish(FADE_MILLIS) }
        timeline.leave(SystemClock.elapsedRealtimeNanos())
    }

    /**
     * A finger set down on the scene: the person will not wait for it. While the first screen is not ready the scene
     * keeps it covered, and this touch from what is behind it, and goes the moment the screen is ready. Once the
     * mark is on its way, what the scene ends on fades at once, and the touch is the screen's.
     */
    private fun skip() {
        val carry = carry ?: return
        carry.skipped = true
        if (carry.left) finish(FADE_MILLIS)
    }

    /**
     * Uncovers the first screen: the surface goes, and what the scene ended on fades in the window over the screen,
     * whose own mark stands where the scene's landed. While its window is [detaching] the stage is left to go with
     * it: a view is never taken out of a parent that is taking its children out of the window.
     */
    private fun finish(fadeMillis: Long, detaching: Boolean = false) {
        val carry = carry ?: return
        this.carry = null
        playing = false
        val stage = carry.stage
        main.removeCallbacks(carry.leaves)
        main.removeCallbacks(carry.ends)
        // From here every touch reaches the screen.
        stage.isClickable = false
        stage.onTouched = null
        stage.dropScene()
        carry.bars.isAppearanceLightStatusBars = carry.lightStatusBar
        carry.bars.isAppearanceLightNavigationBars = carry.lightNavigationBar
        val gone = Runnable { (stage.parent as? ViewGroup)?.removeView(stage) }
        when {
            detaching -> main.post(gone)
            fadeMillis <= 0 || !stage.isAttachedToWindow -> gone.run()
            else -> stage.animate().alpha(0f).setDuration(fadeMillis).withEndAction(gone).start()
        }
    }

    /** The scene over the first screen, and how that screen's bars looked before it. */
    private class Carry(
        val stage: TenmonStage,
        val bars: WindowInsetsControllerCompat,
        val lightStatusBar: Boolean,
        val lightNavigationBar: Boolean,
    ) {
        var landing: Landing? = null
        var left = false
        var skipped = false
        var frameWaits = 0
        val leaves = Runnable { leave() }
        val ends = Runnable { finish(FADE_MILLIS) }
    }

    /** The end of the way out: the rings sunk into the page, the mark set down. */
    private val ARRIVED = TenmonTimeline.Moment(TenmonScene.Phase.OUTRO, 1f, 0f, 0f, true)

    /** 110 ms stepped twice by the golden ratio, as the iOS app fades what its scene ended on. */
    private val FADE_MILLIS = SacredGeometry.beatMillis(2).toLong()

    /**
     * How long the first screen has to say it is ready before the scene leaves without its word. Every first screen
     * says so as soon as it is laid out (the PIN screen, the welcome); this is only for one that never does.
     */
    private const val FIRST_SCREEN_GRACE_MILLIS = 4500L

    /** A screen's layout comes to rest within a few frames of its first. */
    private const val SETTLE_MILLIS = 48L
    private const val FRAME_MILLIS = 16L

    /** How many frames' time a ready screen waits for the scene over it to draw before it is uncovered anyway. */
    private const val FRAME_WAITS = 15

    /** However slow the phone, the first screen is uncovered by then. */
    private const val LONGEST_MILLIS = 6000L

    /** A startup screen made again this soon after its scene left the window joins that scene. */
    private const val REJOIN_NANOS = 1_500_000_000L
    private const val OVER_THE_WINDOW = 10_000f
}
