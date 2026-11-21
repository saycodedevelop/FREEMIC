package com.karaokelink.presentation

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
    private val pages = listOf(
        Page("เตรียมก่อนเริ่ม", "สองเครื่อง เวทีเดียวกัน", "ใช้มือถือเครื่องหนึ่งเปิดเพลง\nและอีกเครื่องเป็นไมโครโฟน", listOf("ต่อมือถือทั้งสองเครื่องเข้ากับ Wi-Fi เดียวกัน", "ต่อลำโพงกับเครื่องที่ใช้เปิดเพลง", "แนะนำสายหรือ USB หากต้องการเสียงร้องหน่วงน้อย")),
        Page("เครื่องที่ 1 • เครื่องเล่น", "เปิดเพลงบนเครื่องนี้", "เลือกเครื่องที่ต่อลำโพง\nแล้วแตะ «เปิดเครื่องเล่นเพลง»", listOf("ค้นหาชื่อเพลง หรือวางลิงก์ HTTPS ในแอป", "กดเล่นวิดีโอบนหน้าเพลง", "เปิดหน้านี้ค้างไว้ แล้วเตรียมมือถือเครื่องไมค์")),
        Page("เครื่องที่ 2 • ไมโครโฟน", "เชื่อมต่อ แล้วเริ่มร้อง", "บนมือถืออีกเครื่อง\nแตะ «ใช้เครื่องนี้เป็นไมค์»", listOf("อนุญาตไมโครโฟน และการแจ้งเตือน", "เลือกเครื่องเล่นที่แอปค้นพบ รอคำว่า «ไมค์พร้อมร้อง»", "ปิดหน้าจอร้องต่อได้ หยุดจากปุ่มในการแจ้งเตือน"))
    )
    fun show(activity: Activity, completed: () -> Unit): Dialog {
        val dialog = Dialog(activity)
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            setPadding(dp(activity, 22), dp(activity, 18), dp(activity, 22), dp(activity, 20))
            background = AppUi.shape(activity, AppUi.background, 28)
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val step = AppUi.label(activity, "", 13f, AppUi.mint, true)
        header.addView(step, LinearLayout.LayoutParams(0, -2, 1f))
        val skip = AppUi.action(activity, "ข้าม", false).apply { minHeight = dp(activity, 48) }
        header.addView(skip)
        shell.addView(header)
        val content = LinearLayout(activity).apply { orientation = LinearLayout.VERTICAL }
        val scrolling = ScrollView(activity).apply { addView(content); isFillViewport = false }
        shell.addView(scrolling, LinearLayout.LayoutParams(-1, 0, 1f))
        val footer = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        val back = AppUi.action(activity, "ย้อนกลับ", false)
        val next = AppUi.action(activity, "ถัดไป")
        footer.addView(back, LinearLayout.LayoutParams(0, -2, 1f).apply { rightMargin = dp(activity, 10) })
        footer.addView(next, LinearLayout.LayoutParams(0, -2, 1f))
        shell.addView(footer)
        var index = 0
        fun finish() { completed(); dialog.dismiss() }
        fun renderPage() {
            val page = pages[index]
            step.text = "เริ่มใช้งาน  ${index + 1} / ${pages.size}"
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
            back.visibility = if (index == 0) View.INVISIBLE else View.VISIBLE
            next.text = if (index == pages.lastIndex) "เริ่มใช้งาน" else "ถัดไป"
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
        if (dialog?.isShowing == true) dialog.window?.setLayout(
            (activity.resources.displayMetrics.widthPixels * .94f).toInt(),
            (activity.resources.displayMetrics.heightPixels * .88f).toInt())
    }
    private fun dp(activity: Activity, value: Int) = AppUi.dp(activity, value)

    /** Code-native onboarding illustration: no bitmap downloads or image dependencies. */
    private class SetupDrawing(activity: Activity, private val page: Int) : View(activity) {
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
            canvas.drawText("เครื่องเล่น", 77f, 144f, paint)
            canvas.drawText("เครื่องไมค์", 243f, 144f, paint)
            canvas.restore()
        }
    }
}