package com.floatplay.overlay

import android.accessibilityservice.AccessibilityService
import android.accessibilityservice.GestureDescription
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.ApplicationInfo
import android.graphics.Path
import android.graphics.Rect
import android.os.Handler
import android.util.Log
import android.view.ViewConfiguration
import android.view.accessibility.AccessibilityNodeInfo

/**
 * Debug-build measuring hook for the crop pad.
 *
 * `adb shell am broadcast -a com.floatplay.overlay.PROBE --es op <op> ...` runs a raw gesture or a pad button
 * and logs the straighten bar's exact value under the tag FTProbe, so step sizes can be measured on a phone
 * without looking at the screen. Only the shell (which holds DUMP) can send it, and only debuggable builds register it.
 */
internal class PhotosProbe(
    private val service: PhotosCropService,
    private val handler: Handler,
) {
    private var receiver: BroadcastReceiver? = null

    fun register() {
        if (service.applicationInfo.flags and ApplicationInfo.FLAG_DEBUGGABLE == 0) return
        val r = object : BroadcastReceiver() {
            override fun onReceive(context: Context, intent: Intent) = handle(intent)
        }
        service.registerReceiver(
            r,
            IntentFilter(ACTION),
            android.Manifest.permission.DUMP,
            handler,
            Context.RECEIVER_EXPORTED,
        )
        receiver = r
        Log.i(TAG, "ready slop=${ViewConfiguration.get(service).scaledTouchSlop} density=${service.resources.displayMetrics.density}")
    }

    fun unregister() {
        receiver?.let { service.unregisterReceiver(it) }
        receiver = null
    }

    private fun handle(intent: Intent) {
        val id = intent.getStringExtra("id") ?: "-"
        val settle = intent.getIntExtra("settle", 700).toLong()
        when (intent.getStringExtra("op")) {
            "state" -> log(id, "state")
            "button" -> {
                val name = intent.getStringExtra("name").orEmpty()
                service.probeButton(name) { result ->
                    handler.postDelayed({ log(id, "button $name $result") }, settle)
                }
            }
            "steps" -> {
                // --es set "pan_x=22,pan_y=22,rot_drag=26,rot_rad=0.01,pinch=22" stores overrides; --ez clear true drops them
                val stored = service.probeStepPrefs() ?: return
                val edit = stored.edit()
                if (intent.getBooleanExtra("clear", false)) {
                    listOf(
                        PhotosCropService.KEY_ROTATION_RAD, PhotosCropService.KEY_ROTATION_DRAG,
                        PhotosCropService.KEY_PAN_X, PhotosCropService.KEY_PAN_Y, PhotosCropService.KEY_PINCH,
                    ).forEach { edit.remove(it) }
                }
                intent.getStringExtra("set")?.split(',')?.forEach { pair ->
                    val (key, value) = pair.split('=').map { it.trim() }.let { it[0] to it.getOrElse(1) { "" } }
                    when (key) {
                        "rot_rad" -> edit.putFloat(PhotosCropService.KEY_ROTATION_RAD, value.toFloat())
                        "rot_drag" -> edit.putInt(PhotosCropService.KEY_ROTATION_DRAG, value.toInt())
                        "pan_x" -> edit.putInt(PhotosCropService.KEY_PAN_X, value.toInt())
                        "pan_y" -> edit.putInt(PhotosCropService.KEY_PAN_Y, value.toInt())
                        "pinch" -> edit.putInt(PhotosCropService.KEY_PINCH, value.toInt())
                    }
                }
                edit.commit()
                Log.i(TAG, "$id steps device=${android.os.Build.DEVICE} ${service.probeSteps()}")
            }
            "resetzoom" -> {
                service.probeResetZoom()
                Log.i(TAG, "$id resetzoom")
            }
            "set" -> {
                val node = sliderNode() ?: return log(id, "set no-slider")
                node.refresh()
                val now = node.rangeInfo?.current ?: 0f
                val target = if (intent.hasExtra("to")) intent.getFloatExtra("to", 0f) else now + intent.getFloatExtra("by", 0f)
                val args = android.os.Bundle().apply {
                    putFloat(AccessibilityNodeInfo.ACTION_ARGUMENT_PROGRESS_VALUE, target)
                }
                val ok = node.performAction(android.R.id.accessibilityActionSetProgress, args)
                val actions = node.actionList.joinToString(",") { it.id.toString() + ":" + (it.label ?: "") }
                handler.postDelayed({ log(id, "set $target ok=$ok before=$now actions=[$actions]") }, settle)
            }
            "scroll" -> {
                val node = sliderNode() ?: return log(id, "scroll no-slider")
                node.refresh()
                val now = node.rangeInfo?.current
                val forward = intent.getBooleanExtra("forward", true)
                val ok = node.performAction(
                    if (forward) AccessibilityNodeInfo.ACTION_SCROLL_FORWARD else AccessibilityNodeInfo.ACTION_SCROLL_BACKWARD,
                )
                handler.postDelayed({ log(id, "scroll forward=$forward ok=$ok before=$now") }, settle)
            }
            "rot" -> {
                val bounds = sliderBounds() ?: return log(id, "rot no-slider")
                val y = bounds.exactCenterY()
                val x = bounds.exactCenterX() + intent.getFloatExtra("start", 0f)
                gesture(id, intent, listOf(floatArrayOf(x, y, x + intent.getFloatExtra("dx", 0f), y)), settle)
            }
            "drag" -> {
                val x = intent.getFloatExtra("x", 0f)
                val y = intent.getFloatExtra("y", 0f)
                gesture(id, intent, listOf(floatArrayOf(x, y, x + intent.getFloatExtra("dx", 0f), y + intent.getFloatExtra("dy", 0f))), settle)
            }
            "pinch" -> {
                val x = intent.getFloatExtra("x", 540f)
                val y = intent.getFloatExtra("y", 1200f)
                val gap = intent.getFloatExtra("gap", 63f)
                val half = intent.getFloatExtra("half", 0f)
                gesture(
                    id,
                    intent,
                    listOf(floatArrayOf(x - gap, y, x - gap - half, y), floatArrayOf(x + gap, y, x + gap + half, y)),
                    settle,
                )
            }
            else -> Log.w(TAG, "$id unknown op")
        }
    }

    /** Each stroke: optional still press, the move, optional still hold before lifting. */
    private fun gesture(id: String, intent: Intent, strokes: List<FloatArray>, settle: Long) {
        val pre = intent.getIntExtra("pre", 0).toLong()
        val dur = intent.getIntExtra("dur", 220).toLong().coerceAtLeast(1)
        val hold = intent.getIntExtra("hold", 0).toLong()
        val phases = ArrayList<List<Triple<FloatArray, FloatArray, Long>>>()
        if (pre > 0) phases.add(strokes.map { Triple(floatArrayOf(it[0], it[1]), floatArrayOf(it[0], it[1]), pre) })
        // via (px along the drag) splits the move: reach it first, optionally rest, then finish the move
        val via = intent.getFloatExtra("via", 0f)
        if (via > 0f) {
            val viaDur = intent.getIntExtra("viadur", 60).toLong().coerceAtLeast(1)
            val rest = intent.getIntExtra("rest", 0).toLong()
            val mid = strokes.map {
                val len = Math.hypot((it[2] - it[0]).toDouble(), (it[3] - it[1]).toDouble()).toFloat()
                val f = if (len > 0f) via / len else 0f
                floatArrayOf(it[0] + (it[2] - it[0]) * f, it[1] + (it[3] - it[1]) * f)
            }
            phases.add(strokes.mapIndexed { i, it -> Triple(floatArrayOf(it[0], it[1]), mid[i], viaDur) })
            if (rest > 0) phases.add(mid.map { Triple(it, it, rest) })
            phases.add(strokes.mapIndexed { i, it -> Triple(mid[i], floatArrayOf(it[2], it[3]), dur) })
        } else {
            phases.add(strokes.map { Triple(floatArrayOf(it[0], it[1]), floatArrayOf(it[2], it[3]), dur) })
        }
        if (hold > 0) phases.add(strokes.map { Triple(floatArrayOf(it[2], it[3]), floatArrayOf(it[2], it[3]), hold) })
        val before = sliderValue()
        // repeat runs the same gesture again, gap ms after each one ends
        val repeat = intent.getIntExtra("repeat", 1).coerceAtLeast(1)
        val gap = intent.getIntExtra("gap", 0).toLong()
        val results = ArrayList<String>()
        fun run() {
            service.runPhases(phases) { result ->
                results.add(result)
                if (results.size < repeat) {
                    handler.postDelayed({ run() }, gap)
                } else {
                    handler.postDelayed({ log(id, "gesture ${results.joinToString(",")} before=$before") }, settle)
                }
            }
        }
        run()
    }

    private fun sliderNode(): AccessibilityNodeInfo? {
        val root = service.probeRoot() ?: return null
        return service.probeSlider(root)
    }

    private fun sliderBounds(): Rect? = sliderNode()?.let { node -> Rect().also { node.getBoundsInScreen(it) } }

    private fun sliderValue(): String {
        val node = sliderNode() ?: return "none"
        node.refresh()
        val range = node.rangeInfo ?: return "norange"
        return range.current.toString()
    }

    private fun log(id: String, what: String) {
        val node = sliderNode()
        val detail = if (node == null) {
            "slider=none"
        } else {
            node.refresh()
            val range = node.rangeInfo
            val bounds = Rect().also { node.getBoundsInScreen(it) }
            "value=${range?.current} min=${range?.min} max=${range?.max} type=${range?.type} " +
                "state=${node.stateDescription} bounds=$bounds"
        }
        Log.i(TAG, "$id $what $detail")
    }

    companion object {
        const val TAG = "FTProbe"
        const val ACTION = "com.floatplay.overlay.PROBE"
    }
}

/** A finished gesture reports how it ended; "cancelled" means Android or a real touch cut it short. */
internal fun AccessibilityService.dispatchPhases(
    phases: List<List<Triple<FloatArray, FloatArray, Long>>>,
    handler: Handler,
    done: (String) -> Unit,
) {
    var previous: List<GestureDescription.StrokeDescription>? = null
    fun step(index: Int) {
        val phase = phases[index]
        val last = index == phases.lastIndex
        val builder = GestureDescription.Builder()
        val strokes = phase.mapIndexed { i, (from, to, ms) ->
            val path = Path().apply {
                moveTo(from[0], from[1])
                lineTo(to[0], to[1])
            }
            val stroke = previous?.get(i)?.continueStroke(path, 0, ms, !last)
                ?: GestureDescription.StrokeDescription(path, 0, ms, !last)
            builder.addStroke(stroke)
            stroke
        }
        previous = strokes
        val accepted = dispatchGesture(
            builder.build(),
            object : AccessibilityService.GestureResultCallback() {
                override fun onCompleted(gestureDescription: GestureDescription?) {
                    if (last) done("completed") else step(index + 1)
                }

                override fun onCancelled(gestureDescription: GestureDescription?) = done("cancelled")
            },
            handler,
        )
        if (!accepted) done("rejected")
    }
    step(0)
}
