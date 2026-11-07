package uk.co.traynor.privategallery.core.sanctuary

import android.content.Context
import android.content.res.ColorStateList
import android.graphics.Canvas
import android.graphics.Color
import android.graphics.Paint
import android.graphics.PixelFormat
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.hardware.display.DisplayManager
import android.view.Display
import android.view.Gravity
import android.view.View
import android.view.WindowInsets
import android.view.WindowManager
import android.widget.Button
import android.widget.FrameLayout
import android.widget.LinearLayout
import android.widget.ScrollView
import android.widget.TextView
import java.util.Locale
import kotlin.math.roundToInt

/** A touch-consuming app surface below Android's security and navigation windows. */
internal class SanctuaryBlockingOverlay(
    service: Context,
    session: SanctuarySession,
    onNova: () -> Unit,
    onPhone: () -> Unit,
    onSettings: () -> Unit,
    onApp: (String) -> Unit,
    onNovaApp: (String) -> Unit,
) {
    private val display = service.getSystemService(DisplayManager::class.java)?.getDisplay(Display.DEFAULT_DISPLAY)
        ?: throw IllegalStateException("Default display unavailable")
    private val type = WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
    private val context = service.createDisplayContext(display).createWindowContext(type, null)
    private val windows = context.getSystemService(WindowManager::class.java)
        ?: throw IllegalStateException("Window service unavailable")
    private val timer = TextView(context).apply {
        gravity = Gravity.CENTER
        textSize = 32f
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        setTextColor(overlayTextColor)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_YES
    }
    private val ring = CountdownRing(context)
    private val surface = buildSurface(service, session, onNova, onPhone, onSettings, onApp, onNovaApp)

    init {
        windows.addView(surface, WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            type,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE or WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL or
                WindowManager.LayoutParams.FLAG_SECURE,
            PixelFormat.OPAQUE,
        ).apply {
            gravity = Gravity.TOP or Gravity.START
            setTitle("Nova Sanctuary")
            setFitInsetsTypes(WindowInsets.Type.systemBars() or WindowInsets.Type.displayCutout())
            setFitInsetsIgnoringVisibility(true)
        })
    }

    fun update(session: SanctuarySession, remaining: Long) {
        val seconds = (remaining + 999L) / 1_000L
        val text = String.format(Locale.ROOT, "%02d:%02d:%02d", seconds / 3_600, seconds / 60 % 60, seconds % 60)
        if (timer.text.toString() != text) timer.text = text
        ring.fraction = (remaining.toDouble() / (session.expiresAt - session.startedAt).coerceAtLeast(1L)).toFloat()
    }

    fun close() {
        try {
            windows.removeViewImmediate(surface)
        } catch (_: IllegalArgumentException) {
            // Android may detach a denied or dismissed overlay before this cleanup runs.
        }
    }

    private fun buildSurface(
        service: Context,
        session: SanctuarySession,
        onNova: () -> Unit,
        onPhone: () -> Unit,
            onSettings: () -> Unit,
        onApp: (String) -> Unit,
        onNovaApp: (String) -> Unit,
    ): ScrollView = ScrollView(context).apply {
        isFillViewport = true
        isClickable = true
        background = GradientDrawable(
            GradientDrawable.Orientation.TL_BR,
            intArrayOf(Color.rgb(18, 46, 50), Color.rgb(15, 22, 40), Color.rgb(20, 27, 49)),
        )
        val frame = FrameLayout(context)
        val column = object : LinearLayout(context) {
            override fun onMeasure(widthMeasureSpec: Int, heightMeasureSpec: Int) {
                val width = minOf(View.MeasureSpec.getSize(widthMeasureSpec), dp(540))
                super.onMeasure(View.MeasureSpec.makeMeasureSpec(width, View.MeasureSpec.EXACTLY), heightMeasureSpec)
            }
        }.apply {
            orientation = LinearLayout.VERTICAL
            gravity = Gravity.CENTER_HORIZONTAL
            setPadding(dp(24), dp(36), dp(24), dp(36))
        }
        column.label("NOVA SANCTUARY  /  ${session.mode.label.uppercase(Locale.ROOT)}", 13f, accent, 8)
        column.label("Make room for what matters.", 29f, overlayTextColor, 16, heading = true)
        column.addView(FrameLayout(context).apply {
            addView(ring, FrameLayout.LayoutParams(dp(154), dp(154), Gravity.CENTER))
            addView(timer, FrameLayout.LayoutParams(dp(154), dp(154), Gravity.CENTER))
        }, LinearLayout.LayoutParams(dp(160), dp(160)).apply { bottomMargin = dp(14) })
        column.label("This app is outside your approved list.", 16f, muted, 4)
        column.label("Pause. Breathe out slowly. Choose where to spend your attention.", 14f, muted, 20)
        column.addAction("Return to NovaOS", prominent = true, onClick = onNova)
        val novaApps = SanctuaryGuardian.overlayNovaApps.filterKeys { it in session.novaApps }
        if (novaApps.isNotEmpty()) {
            column.label("Approved NovaOS apps", 16f, overlayTextColor, 8, heading = true, top = 12)
            novaApps.forEach { (route, label) -> column.addAction(label) { onNovaApp(route) } }
        }
        column.label("Approved Android apps", 16f, overlayTextColor, 8, heading = true, top = 12)
        SanctuaryGuardian.installedApps(service)
            .filterKeys { session.allowExternalApps && it in session.androidApps }
            .forEach { (pkg, label) ->
                column.addAction(label) { onApp(pkg) }
            }
        column.addAction("Phone and emergency access", onClick = onPhone)
        column.addAction("Android Settings", onClick = onSettings)
        column.label("Android’s system controls and emergency access stay available.", 13f, muted, 0, top = 8)
        frame.addView(column, FrameLayout.LayoutParams(
            FrameLayout.LayoutParams.MATCH_PARENT,
            FrameLayout.LayoutParams.WRAP_CONTENT,
            Gravity.TOP or Gravity.CENTER_HORIZONTAL,
        ))
        addView(frame, FrameLayout.LayoutParams(FrameLayout.LayoutParams.MATCH_PARENT, FrameLayout.LayoutParams.WRAP_CONTENT))
    }

    private fun LinearLayout.label(
        value: String,
        size: Float,
        color: Int,
        bottom: Int,
        heading: Boolean = false,
        top: Int = 0,
    ) {
        val text = TextView(context).apply {
            this.text = value
            textSize = size
            gravity = Gravity.CENTER
            if (heading) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
            setAccessibilityHeading(heading)
            setTextColor(color)
        }
        addView(text, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { topMargin = dp(top); bottomMargin = dp(bottom) })
    }

    private fun LinearLayout.addAction(title: String, prominent: Boolean = false, onClick: () -> Unit) {
        val button = Button(context).apply {
            text = title
            isAllCaps = false
            textSize = 15f
            minimumHeight = dp(48)
            setPadding(dp(12), dp(8), dp(12), dp(8))
            setTextColor(if (prominent) Color.rgb(11, 43, 40) else overlayTextColor)
            val base = GradientDrawable().apply {
                setColor(if (prominent) accent else Color.rgb(37, 54, 70))
                cornerRadius = dp(14).toFloat()
            }
            background = RippleDrawable(ColorStateList.valueOf(Color.argb(36, 255, 255, 255)), base, null)
            setOnClickListener { onClick() }
        }
        addView(button, LinearLayout.LayoutParams(LinearLayout.LayoutParams.MATCH_PARENT, LinearLayout.LayoutParams.WRAP_CONTENT)
            .apply { bottomMargin = dp(8) })
    }

    private fun dp(amount: Int): Int = (amount * context.resources.displayMetrics.density).roundToInt()

    private class CountdownRing(context: Context) : View(context) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG).apply {
            style = Paint.Style.STROKE
            strokeWidth = resources.displayMetrics.density * 5
            strokeCap = Paint.Cap.ROUND
        }
        var fraction = 1f
            set(value) {
                field = value.coerceIn(0f, 1f)
                invalidate()
            }

        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val inset = paint.strokeWidth * 1.5f
            paint.color = Color.rgb(49, 74, 83)
            canvas.drawOval(inset, inset, width - inset, height - inset, paint)
            paint.color = accent
            canvas.drawArc(inset, inset, width - inset, height - inset, -90f, fraction * 360f, false, paint)
        }
    }

    private companion object {
        val overlayTextColor = Color.rgb(242, 249, 248)
        val muted = Color.rgb(191, 213, 220)
        val accent = Color.rgb(154, 233, 221)
    }
}
