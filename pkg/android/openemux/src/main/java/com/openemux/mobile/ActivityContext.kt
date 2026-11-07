package com.openemux.mobile

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper

/**
 * The activity behind [this] context, unwrapping the wrappers on the way.
 *
 * Compose's LocalContext is the activity only on the activity's own window: inside a sheet or a
 * dialog it is the dialog's ContextThemeWrapper, so `context as Activity` there throws
 * ClassCastException. That is how picking a language from the phone's sheet crashed the app in
 * 1.21.0. Never cast a context to an activity; call this (`ActivityContextGuardTest` keeps it so).
 */
fun Context.findActivity(): Activity {
    var context: Context? = this
    while (context != null) {
        if (context is Activity) return context
        context = (context as? ContextWrapper)?.baseContext
    }
    error("no activity behind $this")
}
