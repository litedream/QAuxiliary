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

package top.dreamix.util.net

import android.content.Context
import android.os.Build
import androidx.annotation.RequiresApi
import io.github.qauxv.util.Log
import org.json.JSONArray
import org.json.JSONObject
import top.dreamix.Config
import top.dreamix.util.ui.LoadingDialog
import java.io.BufferedReader
import java.io.InputStreamReader
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL
import java.time.LocalDateTime
import java.util.Locale

object ScheduleParser {

    private const val BASE_URL = "https://dashscope.aliyuncs.com/compatible-mode/v1/chat/completions" // 替换为实际的AI模型API地址
    private const val MODEL_NAME = "qwen-turbo"
    private const val ACCESS_KEY = Config.AI_ACCESS_KEY
    private const val PROMPT_TEMPLATE = """你是一个日程解析器，输入一个文本，输出一个日程信息列表文本语言为json，格式如下。[ { "title": "日程标题", "start": "yyyy-MM-dd HH:mm", "end": "yyyy-MM-dd HH:mm", "notice": 30, "location": "日程地点", "description": "日程描述" }, { "title": "日程标题", "start": "yyyy-MM-dd HH:mm", "end": "yyyy-MM-dd HH:mm", "notice": 30, "location": "日程地点", "description": "日程描述" } ... ]如果你没有从文本得到日程的某个属性，填null。除了json文本外不要输出任何内容，也不要输出markdown。假定现在是为%s"""

    @RequiresApi(Build.VERSION_CODES.O)
    fun parseText(inputText: String, ctx: Context, callback: Callback) {
        Log.i("Dreamix: 创建网络请求线程")
        // 获取今天的日期和星期
        val today = LocalDateTime.now().toString().trim('\n')
        // 填充模板
        val prompt = String.format(Locale.CHINA, PROMPT_TEMPLATE, today)
        Log.d("Dreamix: prompt: $prompt")
        // 创建URL对象
        val url = URL(BASE_URL)
        val connection = url.openConnection() as HttpURLConnection
        val loadingDialog = LoadingDialog(ctx){
            connection.disconnect()
        }
        loadingDialog.show()
        Thread {
            try {

                // 设置请求方法和头部
                connection.requestMethod = "POST"
                connection.setRequestProperty("Content-Type", "application/json")
                connection.setRequestProperty("Authorization", "Bearer $ACCESS_KEY")
                connection.doOutput = true

                // 构建请求的 JSON 数据
                val aiRequest = JSONObject()
                    .put("model", MODEL_NAME)
                    .put("messages",
                        JSONArray()
                            .put(JSONObject()
                                .put("role", "system")
                                .put("content", prompt))
                            .put(JSONObject()
                                .put("role", "user")
                                .put("content", inputText)))
                val jsonInputString = aiRequest.toString()

                // 设置10s超时
                connection.connectTimeout = 10000
                // 发送请求
                Log.i("Dreamix: 发送请求")
                OutputStreamWriter(connection.outputStream).use { os ->
                    os.write(jsonInputString)
                    os.flush()
                    os.close()
                }

                // 获取响应
                Log.i("Dreamix: 等待响应")
                val responseCode = connection.responseCode
                Log.d("Dreamix: Response Code: $responseCode")
                if (responseCode == HttpURLConnection.HTTP_OK) {
                    val response = StringBuilder()
                    BufferedReader(InputStreamReader(connection.inputStream)).use { br ->
                        var responseLine: String?
                        while (br.readLine().also { responseLine = it } != null) {
                            response.append(responseLine!!.trim())
                        }
                    }
                    // 回调处理响应
                    Log.i("Dreamix: 成功回调处理响应")
                    Log.i("Dreamix: response: $response")
                    callback.onSuccess(response.toString())
                } else {
                    callback.onError("Response Code: $responseCode")
                }

            } catch (e: Exception) {
                callback.onError(e.message ?: "Unknown error")
            } finally {
                loadingDialog.dismiss()
            }
        }.start()
    }

    interface Callback {
        fun onSuccess(response: String)
        fun onError(error: String)
    }
}
