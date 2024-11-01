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

package top.dreamix.util.system

import android.Manifest
import android.app.Activity
import android.content.ContentValues
import android.content.Context
import android.content.pm.PackageManager
import android.icu.text.SimpleDateFormat
import android.os.Build
import android.provider.CalendarContract
import android.widget.Button
import android.widget.EditText
import androidx.appcompat.app.AlertDialog
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import io.github.qauxv.R
import io.github.qauxv.ui.CommonContextWrapper
import io.github.qauxv.util.Log
import io.github.qauxv.util.Toasts
import io.github.qauxv.util.xpcompat.XC_MethodHook.MethodHookParam
import java.util.TimeZone
import top.dreamix.util.data.ScheduleEvent
import java.time.LocalDateTime
import java.util.Date
import java.util.Locale

object CalendarHelper {

    private const val CALENDAR_PERMISSION_CODE = 100

    /**
     * 检查并请求日历权限
     */
    fun checkAndRequestCalendarPermission(activity: Activity): Boolean {
        val permissionStatus = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_CALENDAR)
        return if (permissionStatus != PackageManager.PERMISSION_GRANTED) {
            ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.WRITE_CALENDAR, Manifest.permission.READ_CALENDAR), CALENDAR_PERMISSION_CODE)
            false
        } else {
            true
        }
    }

    /**
     * 添加日程事件
     * @param context 上下文
     * @param event 日程信息
     */
    fun addEventToCalendar(context: Context, event: ScheduleEvent): Boolean {
        if (!checkAndRequestCalendarPermission(context as Activity)) return false

        // 设置endMillis和notice的默认值
        setDefaultValues(event)

        val calendarId = getPrimaryCalendarId(context) ?: return false


        // 插入事件
        val eventValues = ContentValues().apply {
            put(CalendarContract.Events.CALENDAR_ID, calendarId)
            put(CalendarContract.Events.TITLE, event.title)
            put(CalendarContract.Events.DESCRIPTION, event.description)
            if (event.location != null) put(CalendarContract.Events.EVENT_LOCATION, event.location)
            put(CalendarContract.Events.DTSTART, event.startMillis)
            put(CalendarContract.Events.DTEND, event.endMillis)
            put(CalendarContract.Events.EVENT_TIMEZONE, TimeZone.getDefault().id)
        }

        val eventUri = context.contentResolver.insert(CalendarContract.Events.CONTENT_URI, eventValues)
        val eventId = eventUri?.lastPathSegment?.toLong() ?: return false

        // 添加提醒
        if (event.notice != null) {
            val reminderValues = ContentValues().apply {
                put(CalendarContract.Reminders.EVENT_ID, eventId)
                put(CalendarContract.Reminders.METHOD, CalendarContract.Reminders.METHOD_ALERT)
                put(CalendarContract.Reminders.MINUTES, event.notice)
            }
            context.contentResolver.insert(CalendarContract.Reminders.CONTENT_URI, reminderValues)
        }
        return true
    }

    /**
     * 设置默认值
     * 如果endMillis为null且startMillis不为空，则将endMillis设置为startMillis的一小时后。
     * 如果notice为null，则设置为30分钟。
     */
    public fun setDefaultValues(event: ScheduleEvent) {
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        if (event.startMillis == null) {
            val now = parseDate(LocalDateTime.now().toString())!!
            event.startMillis = now + 3600 * 1000
        }
        if (event.endMillis == null) {
            event.endMillis = event.startMillis!! + 3600 * 1000 // 一小时后
        }
        if (event.notice == null) {
            event.notice = 30 // 提前30分钟提醒
        }
    }

    public fun showConfirmationDialog(event: ScheduleEvent, ctx: Context) {
        val dialogView = (ctx as Activity).layoutInflater.inflate(R.layout.dreamix_confirm_schedule_dialog, null)

        val titleEditText: EditText = dialogView.findViewById(R.id.titleEditText)
        val descriptionEditText: EditText = dialogView.findViewById(R.id.descriptionEditText)
        val locationEditText: EditText = dialogView.findViewById(R.id.locationEditText)
        val startTimeEditText: EditText = dialogView.findViewById(R.id.startTimeEditText)
        val endTimeEditText: EditText = dialogView.findViewById(R.id.endTimeEditText)
        val reminderEditText: EditText = dialogView.findViewById(R.id.reminderEditText)

        titleEditText.setText(event.title ?: "")
        descriptionEditText.setText(event.description ?: "")
        locationEditText.setText(event.location ?: "")
        startTimeEditText.setText(formatDate(event.startMillis))
        endTimeEditText.setText(formatDate(event.endMillis))
        reminderEditText.setText(event.notice?.toString() ?: "")

        val alertDialog = AlertDialog.Builder(CommonContextWrapper.createAppCompatContext(ctx))
            .setView(dialogView)
            .create()

        dialogView.findViewById<Button>(R.id.cancelButton).setOnClickListener {
            alertDialog.dismiss()
        }

        dialogView.findViewById<Button>(R.id.confirmButton).setOnClickListener {
            // 获取用户修改后的值
            event.title = titleEditText.text.toString().ifEmpty { null }
            event.description = descriptionEditText.text.toString().ifEmpty { null }
            event.location = locationEditText.text.toString().ifEmpty { null }
            startTimeEditText.text.toString().let { event.startMillis = parseDate(it) as Long}
            event.endMillis = parseDate(endTimeEditText.text.toString())
            event.notice = reminderEditText.text.toString().toIntOrNull()

            // 调用添加日程的方法
            if (addEventToCalendar(ctx, event)) Toasts.show("添加成功") else Toasts.show("添加失败")
            alertDialog.dismiss()
        }
        // 即将显示对话框
        Log.i("Get to show dialog")
        alertDialog.show()
    }

    private fun formatDate(millis: Long?): String {
        return if (millis != null) {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            dateFormat.format(Date(millis))
        } else {
            ""
        }
    }

    public fun parseDate(originString: String?): Long? {
        if (originString == null) return null
        val dateString: String = if (originString.contains('T')) originString.replace('T', ' ') else originString
        return try {
            val dateFormat = SimpleDateFormat("yyyy-MM-dd HH:mm", Locale.getDefault())
            dateFormat.parse(dateString)?.time
        } catch (e: Exception) {
            null
        }
    }
    /**
     * 获取主日历ID
     * @param context 上下文
     * @return 主日历ID或null
     */
    private fun getPrimaryCalendarId(context: Context): Long? {
        val projection = arrayOf(CalendarContract.Calendars._ID, CalendarContract.Calendars.IS_PRIMARY)
        val cursor = context.contentResolver.query(
            CalendarContract.Calendars.CONTENT_URI,
            projection,
            "${CalendarContract.Calendars.IS_PRIMARY} = 1",
            null,
            null
        )

        return cursor?.use {
            if (it.moveToFirst()) {
                it.getLong(it.getColumnIndexOrThrow(CalendarContract.Calendars._ID))
            } else {
                null
            }
        }
    }
}
