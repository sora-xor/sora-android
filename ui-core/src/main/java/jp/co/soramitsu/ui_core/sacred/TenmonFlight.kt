package jp.co.soramitsu.ui_core.sacred

import android.app.Activity
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.os.SystemClock
import android.view.View
import android.view.ViewGroup
import android.view.Window
import androidx.annotation.RequiresApi
import androidx.core.view.WindowCompat
import androidx.core.view.WindowInsetsControllerCompat
import jp.co.soramitsu.ui_core.lit.LitLightRig

/**
 * The launch scene across the two screens every start of the app goes through.
 *
 * The startup screen [begin]s it: on the light page the mark comes down and the rings of mother of pearl spread
 * under it, and a pulse runs through them while the wallet is checked. The first screen (the PIN screen, or the
 * welcome) [carryOn]s it over itself exactly where it stood, and once that screen has said where it shows the mark
 * ([firstScreenShown]) the mark flies there, the rings sink into the page and the screen is uncovered. A tap on the
 * scene uncovers the screen at once.
 *
 * A phone that does not play the traced scene (Android 12 or older, power saving, animations removed, a hot phone,
 * or a GPU that will not draw it) shows the plain scene at rest on the startup screen, and its first screen opens
 * as it is. Call everything here on the main thread.
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

    /** Whether a scene is on that the next screen carries on: that screen then opens with no transition of its own. */
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
     * seen. The first screen then opens as it is.
     */
    fun rest(host: ViewGroup) {
        (host.getChildAt(0) as? TenmonStage)?.rest()
        if (carry == null) playing = false
    }

    /** Takes the scene out of [host]: the startup screen shows something else. The first screen opens as it is. */
    fun end(host: ViewGroup) {
        (host.getChildAt(0) as? TenmonStage)?.dropScene()
        host.removeAllViews()
        if (carry == null) playing = false
    }

    /** Frames the traced scene in [host] has really drawn; 0 where the plain scene shows. */
    fun framesDrawn(host: ViewGroup): Int = (host.getChildAt(0) as? TenmonStage)?.scene?.framesCompleted ?: 0

    /**
     * Carries the scene the startup screen began on over [activity]'s whole window, until its first screen is ready
     * ([firstScreenShown]) or a moment has passed with no word from it. Returns whether there was a scene to carry.
     */
    fun carryOn(activity: Activity): Boolean {
        if (!isPlaying || Build.VERSION.SDK_INT < Build.VERSION_CODES.TIRAMISU) return false
        val window = activity.window ?: return false
        val decor = window.decorView as? ViewGroup ?: return false
        carry = cover(activity, window, decor)
        return true
    }

    @RequiresApi(Build.VERSION_CODES.TIRAMISU)
    private fun cover(activity: Activity, window: Window, decor: ViewGroup): Carry {
        val stage = TenmonStage(activity)
        // Over everything the window draws, the bars' own bands included: the scene stands where the startup screen
        // showed it.
        stage.translationZ = OVER_THE_WINDOW
        // Nothing below is touched while it is covered.
        stage.isClickable = true
        val now = SystemClock.elapsedRealtimeNanos()
        stage.play(timeline, if (timeline.isStarted) timeline.at(now) else null)
        decor.addView(stage, ViewGroup.LayoutParams(ViewGroup.LayoutParams.MATCH_PARENT, ViewGroup.LayoutParams.MATCH_PARENT))
        // Dark icons in the bars over the light page, whatever the theme under it.
        val bars = WindowCompat.getInsetsController(window, decor)
        val made = Carry(stage, bars, bars.isAppearanceLightStatusBars, bars.isAppearanceLightNavigationBars)
        bars.isAppearanceLightStatusBars = true
        bars.isAppearanceLightNavigationBars = true
        stage.onGaveUp = { finish(FADE_MILLIS) }
        // A tap does not wait for the scene: what it shows fades at once, and the screen takes every touch from then.
        stage.setOnClickListener { skip() }
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
        val scene = carry.stage.scene ?: return
        if (carry.left) {
            // Laid out again before the mark has left: it flies to where the screen shows it now.
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
        if (scene.framesCompleted == 0) {
            // The scene's surface over this screen has no frame yet. The mark flies on when it has, so the way out
            // is seen from its start; a surface that never draws gives way to the plain scene, or the longest wait
            // ends it.
            main.postDelayed(carry.leaves, FRAME_MILLIS)
            return
        }
        carry.left = true
        carry.stage.isClickable = false
        aim(carry, scene, carry.landing)
        scene.onOver = { finish(FADE_MILLIS) }
        timeline.leave(SystemClock.elapsedRealtimeNanos())
    }

    /** A tap on the scene: the first screen is wanted now. What the scene shows fades over it at once. */
    private fun skip() {
        val carry = carry ?: return
        if (carry.left) return
        carry.stage.cover.showFrame(timeline.at(SystemClock.elapsedRealtimeNanos()), carry.landing)
        finish(FADE_MILLIS)
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
        // From here every touch reaches the screen. (Clearing the click listener would make the stage clickable again.)
        stage.isClickable = false
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
        val leaves = Runnable { leave() }
        val ends = Runnable { finish(FADE_MILLIS) }
    }

    /** The end of the way out: the rings sunk into the page, the mark set down. */
    private val ARRIVED = TenmonTimeline.Moment(TenmonScene.Phase.OUTRO, 1f, 0f, 0f, true)

    /** 110 ms stepped twice by the golden ratio, as the iOS app fades what its scene ended on. */
    private val FADE_MILLIS = SacredGeometry.beatMillis(2).toLong()

    /** How long the first screen has to say where it shows the mark before the scene leaves without its word. */
    private const val FIRST_SCREEN_GRACE_MILLIS = 1000L

    /** A screen's layout comes to rest within a few frames of its first. */
    private const val SETTLE_MILLIS = 48L
    private const val FRAME_MILLIS = 16L

    /** However slow the phone, the first screen is uncovered by then. */
    private const val LONGEST_MILLIS = 5000L

    /** A startup screen made again this soon after its scene left the window joins that scene. */
    private const val REJOIN_NANOS = 1_500_000_000L
    private const val OVER_THE_WINDOW = 10_000f
}
