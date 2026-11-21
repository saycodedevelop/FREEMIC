package com.karaokelink.presentation

import android.app.Activity
import android.app.AlertDialog
import android.app.LocaleManager
import android.content.Context
import android.content.res.Configuration
import android.os.Build
import android.os.LocaleList
import com.karaokelink.R
import java.util.Locale

/** Native app locales on Android 13+, persisted context override on Android 9–12. */
object AppLanguage {
    val tags = listOf("", "th", "en", "zh-CN", "vi", "id")
    private const val PREFERENCES = "karaokelink-language"
    fun selected(context: Context): String = if (Build.VERSION.SDK_INT >= 33)
        context.getSystemService(LocaleManager::class.java).applicationLocales.toLanguageTags()
    else context.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).getString("tag", "") ?: ""

    fun wrap(context: Context): Context {
        if (Build.VERSION.SDK_INT >= 33) return context
        val tag = selected(context)
        if (tag.isEmpty()) return context
        val configuration = Configuration(context.resources.configuration)
        configuration.setLocales(LocaleList(Locale.forLanguageTag(tag)))
        return context.createConfigurationContext(configuration)
    }

    fun select(activity: Activity, tag: String) {
        require(tag in tags)
        if (selected(activity) == tag) return
        if (Build.VERSION.SDK_INT >= 33) {
            activity.getSystemService(LocaleManager::class.java).applicationLocales = LocaleList.forLanguageTags(tag)
        } else {
            activity.getSharedPreferences(PREFERENCES, Context.MODE_PRIVATE).edit().putString("tag", tag).commit()
            activity.recreate()
        }
    }

    fun show(activity: Activity) {
        val names = arrayOf(activity.getString(R.string.system_language), "ไทย", "English", "简体中文", "Tiếng Việt", "Bahasa Indonesia")
        AlertDialog.Builder(activity).setTitle(R.string.language)
            .setSingleChoiceItems(names, tags.indexOf(selected(activity))) { dialog, index ->
                dialog.dismiss()
                select(activity, tags[index])
            }.setNegativeButton(R.string.cancel, null).show()
    }
}
