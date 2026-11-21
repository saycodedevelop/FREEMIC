package com.karaokelink.presentation

import android.content.Context
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
    val surface = Color.rgb(21, 31, 50)
    val border = Color.rgb(43, 57, 79)
    val mint = Color.rgb(104, 233, 196)
    val violet = Color.rgb(185, 170, 255)
    val text = Color.rgb(242, 246, 253)
    val muted = Color.rgb(172, 187, 209)
    val danger = Color.rgb(255, 151, 159)
    val amber = Color.rgb(246, 205, 135)
    fun dp(context: Context, value: Int) = (value * context.resources.displayMetrics.density + .5f).toInt()
    fun shape(context: Context, fill: Int = surface, radius: Int = 20, stroke: Int? = border) = GradientDrawable().apply {
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
            shape(context, if (primary) tint else surface, 14, if (primary) null else border), null)
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
        background = RippleDrawable(ColorStateList.valueOf(Color.argb(45, 255, 255, 255)), shape(context, surface, 14), null)
        setPadding(dp(context, 12), dp(context, 12), dp(context, 12), dp(context, 12))
        minimumWidth = dp(context, 48); minimumHeight = dp(context, 48)
        setOnClickListener { action() }
    }
}