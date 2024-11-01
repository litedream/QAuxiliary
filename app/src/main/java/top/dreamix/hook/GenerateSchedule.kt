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

package top.dreamix.hook

import android.app.Activity
import android.app.AlertDialog
import android.content.Context
import android.os.Build
import android.view.View
import android.widget.TextView
import cc.hicore.QApp.QAppUtils
import cc.ioctl.util.Reflex
import cc.ioctl.util.afterHookIfEnabled
import com.github.kyuubiran.ezxhelper.utils.getIntOrNull
import com.github.kyuubiran.ezxhelper.utils.getJSONArrayOrNull
import com.github.kyuubiran.ezxhelper.utils.getLongOrNull
import com.github.kyuubiran.ezxhelper.utils.getObjectOrNull
import com.github.kyuubiran.ezxhelper.utils.getStringOrNull
import com.tencent.qqnt.kernel.nativeinterface.MsgRecord
import com.xiaoniu.dispatcher.OnMenuBuilder
import io.github.qauxv.util.xpcompat.XC_MethodHook
import io.github.qauxv.util.xpcompat.XposedBridge
import io.github.qauxv.util.xpcompat.XposedHelpers
import io.github.qauxv.R
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.base.annotation.UiItemAgentEntry
import io.github.qauxv.bridge.AppRuntimeHelper
import io.github.qauxv.dsl.FunctionEntryRouter
import io.github.qauxv.hook.CommonSwitchFunctionHook
import io.github.qauxv.ui.CommonContextWrapper
import io.github.qauxv.util.CustomMenu
import io.github.qauxv.util.Initiator
import io.github.qauxv.util.Log
import io.github.qauxv.util.Toasts
import kotlinx.serialization.json.Json
import org.json.JSONArray
import org.json.JSONObject
import top.dreamix.util.data.ScheduleEvent
import xyz.nextalone.util.clazz
import xyz.nextalone.util.invoke
import com.xiaoniu.util.ContextUtils
import io.github.qauxv.core.MainHook
import org.json.JSONException
import top.dreamix.util.net.ScheduleParser
import top.dreamix.util.system.CalendarHelper

@FunctionHookEntry
@UiItemAgentEntry
object GenerateSchedule : CommonSwitchFunctionHook(), OnMenuBuilder {
    override val name: String = "根据消息生成日程"

    override val description: String = "长按文本消息，选择 日程"

    override val uiItemLocation: Array<String> = FunctionEntryRouter.Locations.Auxiliary.MESSAGE_CATEGORY

    private var isHook: Boolean = false

    private const val TEXT_CONTEXT = "com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent"
    private const val MIX_CONTEXT = "com.tencent.mobileqq.aio.msglist.holder.component.mix.AIOMixContentComponent"

    override fun initOnce(): Boolean {
        return QAppUtils.isQQnt()
    }

    private val getMenuItemCallBack = afterHookIfEnabled(60) { param ->
        try {
            val original: Array<Any> = param.result as Array<Any>
            val originLength = original.size
            val itemClass = original.javaClass.componentType!!
            val ret: Array<Any?> = java.lang.reflect.Array.newInstance(itemClass, originLength + 1) as Array<Any?>
            System.arraycopy(original, 0, ret, 0, originLength)
            ret[originLength] = CustomMenu.createItem(itemClass, R.id.item_generate_schedule, "添加日程")
            CustomMenu.checkArrayElementNonNull(ret)
            param.result = ret
        } catch (e: Throwable) {
            traceError(e)
            throw e
        }
    }

    private val menuItemClickCallback = afterHookIfEnabled(60) { param ->
        val id = param.args[0] as Int
        val ctx = param.args[1] as Activity
        val chatMessage = param.args[2]
        val wc = CommonContextWrapper.createAppCompatContext(ctx)
        when (id) {
            R.id.item_generate_schedule -> {
                generateScheduleByMessage(Reflex.getInstanceObjectOrNull(chatMessage, "msg")?.toString() ?: "", param)
            }
        }
        /* 作用不明
        if (!isHook) {
            isHook = true
            val _TranslateResult = "com.tencent.mobileqq.ocr.data.TranslateResult".clazz!!
            val _C88380b = "com.tencent.mobileqq.ocr.b".clazz!!
            XposedHelpers.findAndHookMethod(_C88380b, "c", Boolean::class.java, Int::class.java, _TranslateResult,
                afterHookIfEnabled {
                    val translateResult = it.args[2]
                    val dstContent = XposedHelpers.findMethodExactIfExists(_TranslateResult, "f")
                        .invoke(translateResult)?.toString() ?: "翻译失败"
                    AlertDialog.Builder(wc)
                        .setMessage(dstContent)
                        .show()
                        .findViewById<TextView>(android.R.id.message)
                        .setTextIsSelectable(true)
                })
        }*/
    }


    private fun generateScheduleByMessage(content: String, param: XC_MethodHook.MethodHookParam) {
        val runtime = AppRuntimeHelper.getAppRuntime()
        val activity = ContextUtils.getCurrentActivity()
        // 判断第三阶段是否初始化完成
        /*
        if (!MainHook.getInstance().third_stage_inited) {
            Toasts.show("请等待模块第三阶段初始化")
            return
        }*/
        if (Build.VERSION.SDK_INT < Build.VERSION_CODES.O) return
        ScheduleParser.parseText(content, activity as Context,object : ScheduleParser.Callback {
            override fun onSuccess(response: String) {
                // 处理成功的响应
                // response string 转json
                val res = JSONObject(response)
                var items = JSONArray()
                try {
                    items = JSONArray((res.getJSONArray("choices").get(0) as JSONObject).getJSONObject("message").getString("content"))
                } catch (e: JSONException) {
                    Log.e("Dreamix: 返回json解析失败", e)
                    Toasts.show("返回json解析失败")
                    return
                }
                for (i in 0..<items.length()){
                    (activity).runOnUiThread {
                        Log.i("Dreamix: 已进入UI线程")
                        val item = items.get(i) as JSONObject
                        Log.i(item.toString())
                        // JSONObject -> ScheduleEvent
                        val event: ScheduleEvent
                        try {
                            event = ScheduleEvent(
                                title = item.getStringOrNull("title"),
                                description = item.getStringOrNull("description"),
                                location = item.getStringOrNull("location"),
                                startMillis = CalendarHelper.parseDate(item.getStringOrNull("start")),
                                endMillis = CalendarHelper.parseDate(item.getStringOrNull("end")),
                                notice = item.getIntOrNull("notice")
                            )
                            // "null" -> null
                            if (event.title == "null") event.title = null
                            if (event.description == "null") event.description = null
                            if (event.location == "null") event.location = null
                        } catch (e: JSONException) {
                            Toasts.show("日程结果解析错误")
                            return@runOnUiThread
                        }
                        // 先设置初始值
                        Log.i("Dreamix: $event")
                        CalendarHelper.setDefaultValues(event)
                        Log.i("Dreamix: $event")
                        // 弹出确认框
                        try {
                            val ctx = ContextUtils.getCurrentActivity() as Context
                            CalendarHelper.showConfirmationDialog(event, ctx)
                        } catch (e: Exception) {
                            Log.e(e)
                            Toasts.show("创建日程失败")
                            return@runOnUiThread
                        }
                    }
                }
            }

            override fun onError(error: String) {
                Toasts.show( "网络请求失败: $error")
                Log.e(error)
            }
        })

    }
    /*
    private fun m245294i3(str: String): Boolean {
        for (element in str) {
            if (element.code in 19968..40868) {
                return true
            }
        }
        return false
    }*/

    override val targetComponentTypes: Array<String>
        get() = arrayOf("com.tencent.mobileqq.aio.msglist.holder.component.text.AIOTextContentComponent")

    override fun onGetMenuNt(msg: Any, componentType: String, param: XC_MethodHook.MethodHookParam) {
        if (!isEnabled) return
        val item = CustomMenu.createItemIconNt(msg, "日程", R.drawable.ic_item_generate_schedule_72dp, R.id.item_generate_schedule) {
            val msgRecord = XposedHelpers.callMethod(msg, "getMsgRecord") as MsgRecord
            when (componentType) {
                TEXT_CONTEXT -> {
                    val stringBuilder = StringBuilder()
                    msgRecord.elements.forEach { element ->
                        element.textElement?.let { textElement ->
                            stringBuilder.append(textElement.content)
                        }
                    }
                    generateScheduleByMessage(stringBuilder.toString(), param)
                }

                MIX_CONTEXT -> {
                    // TODO: 待开发
                    Toasts.show("快了快了, 已经新建文件夹了!")
                }
            }
        }
        param.result = (param.result as List<*>) + item
    }
}