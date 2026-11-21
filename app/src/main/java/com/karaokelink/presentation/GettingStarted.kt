package com.karaokelink.presentation

import com.karaokelink.R
import android.app.Activity
import android.app.Dialog
import android.graphics.*
import android.graphics.drawable.ColorDrawable
import android.view.Gravity
import android.view.View
import android.view.ViewGroup
import android.widget.*

/** Three short steps, with explicit Skip and an always-available replay entry. */
object GettingStarted {
    private data class Page(val eyebrow: String, val title: String, val detail: String, val steps: List<String>)
    private fun pages(activity: Activity) = listOf(
        Page(activity.getString(R.string.setup_prepare), activity.getString(R.string.setup_title), activity.getString(R.string.setup_detail), listOf(activity.getString(R.string.setup_wifi), activity.getString(R.string.setup_speaker), activity.getString(R.string.setup_latency))),
        Page(activity.getString(R.string.setup_player_step), activity.getString(R.string.setup_player_title), activity.getString(R.string.setup_player_detail), listOf(activity.getString(R.string.setup_search), activity.getString(R.string.setup_play), activity.getString(R.string.setup_keep_open))),
        Page(activity.getString(R.string.setup_mic_step), activity.getString(R.string.setup_mic_title), activity.getString(R.string.setup_mic_detail), listOf(activity.getString(R.string.setup_allow), activity.getString(R.string.setup_connect), activity.getString(R.string.setup_screen_off)))
    )
    fun show(activity: Activity, completed: () -> Unit): Dialog {
        val pages = pages(activity)
        val dialog = Dialog(activity)
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 20))
            background = AppUi.shape(activity, AppUi.background, 28)
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val step = AppUi.label(activity, "", 13f, AppUi.mint, true)
        header.addView(step, LinearLayout.LayoutParams(0, -2, 1f))
        val skip = AppUi.action(activity, activity.getString(R.string.skip), false).apply { minHeight = dp(activity, 48) }
        header.addView(skip)
        shell.addView(header)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scrolling = ScrollView(activity).apply { addView(content); isFillViewport = false }
        shell.addView(scrolling, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = AppUi.action(activity, activity.getString(R.string.back), false)
        val next = AppUi.action(activity, activity.getString(R.string.next))
        footer.addView(back, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(activity, 10) })
        footer.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        shell.addView(footer)
        var index = 0
        fun finish() { completed(); dialog.dismiss() }
        fun renderPage() {
            val page = pages[index]
            step.text = activity.getString(R.string.setup_progress, index + 1, pages.size)
            content.removeAllViews()
            content.addView(SetupDrawing(activity, index), LinearLayout.LayoutParams(-1, dp(activity, 150)))
            content.addView(AppUi.label(activity, page.eyebrow, 13f, if (index == 2) AppUi.violet else AppUi.mint, true))
            content.addView(AppUi.label(activity, page.title, 27f, bold = true).apply { setPadding(0, dp(activity, 8), 0, dp(activity, 8)) })
            content.addView(AppUi.label(activity, page.detail, 15f, AppUi.muted))
            page.steps.forEachIndexed { number, text ->
                val row = LinearLayout(activity).apply { gravity = Gravity.TOP; setPadding(0, dp(activity, 14), 0, 0) }
                row.addView(AppUi.label(activity, "${number + 1}", 14f, AppUi.mint, true), LinearLayout.LayoutParams(dp(activity, 28), -2))
                row.addView(AppUi.label(activity, text, 14f), LinearLayout.LayoutParams(0, -2, 1f))
                content.addView(row)
            }
            content.setPadding(0, 0, 0, dp(activity, 22))
            back.visibility = if (index == 0) View.GONE else View.VISIBLE
            next.text = if (index == pages.lastIndex) activity.getString(R.string.get_started) else activity.getString(R.string.next)
            scrolling.post { scrolling.scrollTo(0, 0) }
        }
        back.setOnClickListener { if (index > 0) { index--; renderPage() } }
        next.setOnClickListener { if (index == pages.lastIndex) finish() else { index++; renderPage() } }
        skip.setOnClickListener { finish() }
        dialog.setContentView(shell)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.setOnCancelListener { completed() }
        renderPage()
        dialog.show()
        resize(activity, dialog)
        return dialog
    }
    fun resize(activity: Activity, dialog: Dialog?) {
        if (dialog?.isShowing == true) dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setDimAmount(.65f)
            setLayout(-1, minOf(dp(activity, 680), (activity.resources.displayMetrics.heightPixels * .88f).toInt()))
        }
    }
    private fun dp(activity: Activity, value: Int) = AppUi.dp(activity, value)

    /** Code-native onboarding illustration: no bitmap downloads or image dependencies. */
    private class SetupDrawing(private val activity: Activity, private val page: Int) : View(activity) {
        private val paint = Paint(Paint.ANTI_ALIAS_FLAG)
        override fun onDraw(canvas: Canvas) {
            super.onDraw(canvas)
            val sx = width / 320f; val sy = height / 150f
            canvas.save(); canvas.scale(sx, sy)
            fun line(color: Int, stroke: Float = 2f) { paint.color = color; paint.style = Paint.Style.STROKE; paint.strokeWidth = stroke; paint.strokeCap = Paint.Cap.ROUND }
            fun phone(left: Float, tint: Int, player: Boolean) {
                paint.style = Paint.Style.FILL; paint.color = AppUi.surface
                canvas.drawRoundRect(left, 12f, left + 74f, 125f, 14f, 14f, paint)
                line(tint); canvas.drawRoundRect(left, 12f, left + 74f, 125f, 14f, 14f, paint)
                canvas.drawLine(left + 29f, 20f, left + 45f, 20f, paint)
                if (player) {
                    paint.style = Paint.Style.FILL; paint.color = tint
                    val path = Path().apply { moveTo(left + 29, 50f); lineTo(left + 49, 64f); lineTo(left + 29, 78f); close() }
                    canvas.drawPath(path, paint)
                    line(tint); canvas.drawLine(left + 15, 94f, left + 59, 94f, paint)
                    canvas.drawLine(left + 15, 102f, left + 43, 102f, paint)
                } else {
                    line(tint, 3f); canvas.drawRoundRect(left + 29, 43f, left + 45, 70f, 8f, 8f, paint)
                    canvas.drawArc(left + 22, 54f, left + 52, 82f, 0f, 180f, false, paint)
                    canvas.drawLine(left + 37, 82f, left + 37, 94f, paint)
                    canvas.drawLine(left + 28, 94f, left + 46, 94f, paint)
                }
            }
            phone(40f, if (page == 2) AppUi.border else AppUi.mint, true)
            phone(206f, if (page == 1) AppUi.border else AppUi.violet, false)
            line(AppUi.mint)
            canvas.drawLine(132f, 66f, 186f, 66f, paint)
            canvas.drawLine(132f, 66f, 140f, 59f, paint)
            canvas.drawLine(132f, 66f, 140f, 73f, paint)
            paint.style = Paint.Style.FILL; paint.color = AppUi.muted; paint.textSize = 11f; paint.textAlign = Paint.Align.CENTER
            canvas.drawText(activity.getString(R.string.player_short), 77f, 144f, paint)
            canvas.drawText(activity.getString(R.string.mic_short), 243f, 144f, paint)
            canvas.restore()
        }
    }
}