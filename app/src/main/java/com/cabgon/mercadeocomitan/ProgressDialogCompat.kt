package com.cabgon.mercadeocomitan

import android.app.Activity
import android.content.Context
import android.content.ContextWrapper
import android.view.Gravity
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import androidx.appcompat.app.AlertDialog
import com.google.android.material.dialog.MaterialAlertDialogBuilder

/** Lightweight Material replacement for the deprecated platform ProgressDialog. */
class ProgressDialogCompat(private val context: Context) {
    private val messageView = TextView(context)
    private val dialog: AlertDialog = MaterialAlertDialogBuilder(context)
        .setTitle(R.string.progress_title)
        .setView(LinearLayout(context).apply {
            orientation = LinearLayout.HORIZONTAL
            gravity = Gravity.CENTER_VERTICAL
            setPadding(48, 16, 48, 16)
            addView(ProgressBar(context))
            addView(messageView, LinearLayout.LayoutParams(
                LinearLayout.LayoutParams.WRAP_CONTENT,
                LinearLayout.LayoutParams.WRAP_CONTENT
            ).apply { marginStart = 24 })
        })
        .create()

    private fun findActivity(ctx: Context?): Activity? {
        var current = ctx
        while (current is ContextWrapper) {
            if (current is Activity) return current
            current = current.baseContext
        }
        return null
    }

    private fun canOperateOnWindow(): Boolean {
        val activity = findActivity(context) ?: return true
        return !activity.isFinishing && !activity.isDestroyed
    }

    fun setTitle(title: CharSequence?) { dialog.setTitle(title ?: "") }
    fun setMessage(message: CharSequence?) { messageView.text = message ?: "" }
    fun setCanceledOnTouchOutside(cancel: Boolean) { dialog.setCanceledOnTouchOutside(cancel) }
    fun show() {
        if (!canOperateOnWindow()) return
        runCatching {
            if (!dialog.isShowing) dialog.show()
        }
    }
    fun dismiss() {
        runCatching {
            if (dialog.isShowing) dialog.dismiss()
        }
    }
}
