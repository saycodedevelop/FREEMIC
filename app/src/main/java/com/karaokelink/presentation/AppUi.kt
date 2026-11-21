package com.karaokelink.presentation

import android.app.Activity
import android.app.Dialog
import android.content.Context
import android.graphics.drawable.ColorDrawable
import android.view.ViewGroup
import com.karaokelink.R
import android.content.res.ColorStateList
import android.graphics.Color
import android.graphics.Typeface
import android.graphics.drawable.GradientDrawable
import android.graphics.drawable.RippleDrawable
import android.view.Gravity
import android.view.View
import android.widget.*

/** Shared visual tokens and accessible native controls; no web-based UI layer. */
object AppUi {
    val background = Color.rgb(10, 16, 30)
    val surface = Color.rgb(20, 29, 46)
    val raised = Color.rgb(28, 39, 58)
    val border = Color.rgb(38, 50, 69)
    val mint = Color.rgb(95, 220, 189)
    val violet = Color.rgb(172, 164, 238)
    val text = Color.rgb(242, 246, 253)
    val muted = Color.rgb(172, 187, 209)
    val danger = Color.rgb(255, 151, 159)
    val amber = Color.rgb(246, 205, 135)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun shape(context: Context, fill: Int = surface, radius: Int = 18, stroke: Int? = border) = GradientDrawable().apply {
        setColor(fill); cornerRadius = dp(context, radius).toFloat()
        stroke?.let { setStroke(dp(context, 1), it) }
    }
    fun label(context: Context, value: String, size: Float = 15f, color: Int = text, bold: Boolean = false) = TextView(context).apply {
        text = value; textSize = size; setTextColor(color)
        includeFontPadding = false
        isAccessibilityHeading = bold && size >= 18f
        setLineSpacing(dp(context, 3).toFloat(), 1f)
        if (bold) typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
    }
    fun action(context: Context, label: String, primary: Boolean = true, tint: Int = mint) = Button(context).apply {
        text = label; textSize = 14f; isAllCaps = false
        typeface = Typeface.create("sans-serif-medium", Typeface.NORMAL)
        includeFontPadding = false
        minWidth = 0; minimumWidth = 0; minHeight = dp(context, 50)
        setPadding(dp(context, 14), dp(context, 10), dp(context, 14), dp(context, 10))
        setTextColor(if (primary) AppUi.background else tint)
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)),
            shape(context, if (primary) tint else raised, 12, null), null)
        stateListAnimator = null
    }
    fun icon(context: Context, drawable: Int, tint: Int = mint, size: Int = 24) = ImageView(context).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(tint)
        importantForAccessibility = View.IMPORTANT_FOR_ACCESSIBILITY_NO
        layoutParams = LinearLayout.LayoutParams(dp(context, size), dp(context, size))
        scaleType = ImageView.ScaleType.CENTER_INSIDE
    }
    fun iconButton(context: Context, drawable: Int, description: String, action: () -> Unit) = ImageButton(context).apply {
        setImageResource(drawable); imageTintList = ColorStateList.valueOf(muted)
        contentDescription = description
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), shape(context, raised, 12, null), null)
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        minimumWidth = dp(context, 48); minimumHeight = dp(context, 48)
        setOnClickListener { action() }
    }

    fun textAction(context: Context, title: String, tint: Int = muted) = action(context, title, false, tint).apply {
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(35, 255, 255, 255)), shape(context, Color.TRANSPARENT, 12, null), null)
    }

    /** One sheet pattern for sound controls, devices and connection information. */
    fun sheet(activity: Activity, title: String, subtitle: String, content: View): Dialog {
        val dialog = Dialog(activity)
        val shell = LinearLayout(activity).apply {
            orientation = LinearLayout.VERTICAL
            background = shape(activity, AppUi.background, 24, null)
            setPadding(dp(activity, 20), dp(activity, 10), dp(activity, 20), dp(activity, 20))
            tag = "settings-sheet"
        }
        val header = LinearLayout(activity).apply { gravity = Gravity.CENTER_VERTICAL }
        header.addView(label(activity, title, 21f, bold = true), LinearLayout.LayoutParams(0, -2, 1f))
        header.addView(iconButton(activity, R.drawable.ic_dismiss, "ปิดหน้าต่าง") { dialog.dismiss() }, LinearLayout.LayoutParams(dp(activity, 48), dp(activity, 48)))
        shell.addView(header)
        shell.addView(label(activity, subtitle, 13f, muted).apply { setPadding(0, 0, 0, dp(activity, 16)) })
        (content.parent as? ViewGroup)?.removeView(content)
        shell.addView(ScrollView(activity).apply { addView(content); isFillViewport = false }, LinearLayout.LayoutParams(-1, 0, 1f))
        shell.addView(action(activity, "เสร็จแล้ว").apply { setOnClickListener { dialog.dismiss() } },
            LinearLayout.LayoutParams(-1, -2).apply { topMargin = dp(activity, 12) })
        dialog.setContentView(shell)
        dialog.window?.setBackgroundDrawable(ColorDrawable(Color.TRANSPARENT))
        dialog.show()
        resizeSheet(activity, dialog)
        return dialog
    }
    fun resizeSheet(activity: Activity, dialog: Dialog?) {
        if (dialog?.isShowing != true) return
        dialog.window?.apply {
            setGravity(Gravity.BOTTOM)
            setDimAmount(.65f)
            setLayout(-1, minOf(dp(activity, 720), (activity.resources.displayMetrics.heightPixels * .86f).toInt()))
        }
    }}