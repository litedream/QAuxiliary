/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2024 QAuxiliary developers
 * https://github.com/cinit/QAuxiliary
 *
 * This software is an opensource software: you can redistribute it
 * and/or modify it under the terms of the General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.
 * See the General Public License for more details.
 *
 * You should have received a copy of the General Public License
 * along with this software.
 * If not, see
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */

package top.dreamix.util.ui

import android.app.AlertDialog
import android.content.Context
import android.view.LayoutInflater
import android.widget.ProgressBar
import android.widget.TextView
import io.github.qauxv.R
import io.github.qauxv.ui.CommonContextWrapper

class LoadingDialog(
    private val context: Context,
    private val onCancel: (() -> Unit)? = null // 取消回调函数
) {
    private var dialog: AlertDialog? = null

    fun show() {
        val builder = AlertDialog.Builder(context, R.style.dreamix_loading_dialog)
        val inflater = LayoutInflater.from(context)
        val dialogView = inflater.inflate(R.layout.dreamix_loading_dialog, null)
        val progressBar = dialogView.findViewById<ProgressBar>(R.id.progress_bar)

        builder.setView(dialogView)
            .setCancelable(true) // 允许外部点击取消

        dialog = builder.create()

        // 设置取消监听器以触发回调函数
        dialog?.setOnCancelListener {
            onCancel?.invoke()
        }

        dialog?.show()
    }

    fun dismiss() {
        dialog?.dismiss()
    }
}
