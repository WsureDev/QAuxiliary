/*
 * QAuxiliary - An Xposed module for QQ/TIM
 * Copyright (C) 2019-2023 QAuxiliary developers
 * https://github.com/cinit/QAuxiliary
 *
 * This software is non-free but opensource software: you can redistribute it
 * and/or modify it under the terms of the GNU Affero General Public License
 * as published by the Free Software Foundation; either
 * version 3 of the License, or any later version and our eula as published
 * by QAuxiliary contributors.
 *
 * This software is distributed in the hope that it will be useful,
 * but WITHOUT ANY WARRANTY; without even the implied warranty of
 * MERCHANTABILITY or FITNESS FOR A PARTICULAR PURPOSE.  See the GNU
 * Affero General Public License for more details.
 *
 * You should have received a copy of the GNU Affero General Public License
 * and eula along with this software.  If not, see
 * <https://www.gnu.org/licenses/>
 * <https://github.com/cinit/QAuxiliary/blob/master/LICENSE.md>.
 */

package me.singleneuron.hook.decorator

import android.app.Activity
import android.view.View
import android.widget.EditText
import cc.ioctl.hook.notification.MessageInterception
import cc.ioctl.util.msg.MessageReceiver
import com.google.android.material.dialog.MaterialAlertDialogBuilder
import io.github.qauxv.base.IUiItemAgent
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.base.annotation.UiItemAgentEntry
import io.github.qauxv.config.ConfigManager
import io.github.qauxv.dsl.FunctionEntryRouter
import io.github.qauxv.hook.CommonConfigFunctionHook
import io.github.qauxv.ui.CommonContextWrapper
import io.github.qauxv.util.Log
import io.github.qauxv.util.SyncUtils.async
import io.github.qauxv.util.encodeToJson
import kotlinx.coroutines.CoroutineScope
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.launch
import kotlinx.coroutines.withContext
import kotlinx.serialization.Serializable
import me.singleneuron.data.MsgRecordData
import java.io.OutputStreamWriter
import java.net.HttpURLConnection
import java.net.URL

@UiItemAgentEntry
@FunctionHookEntry
object MessageListenerHttpd : CommonConfigFunctionHook(), MessageReceiver {
    override val name = "OneBotHttpApi"
    override val description = "为OneBot协议适配监听器"
    override val valueState: MutableStateFlow<String?>? = null
    override val uiItemLocation = FunctionEntryRouter.Locations.Auxiliary.NOTIFICATION_CATEGORY
    override fun onReceive(data: MsgRecordData?): Boolean {
        if (data != null) {
            val url = ConfigManager.getExFriendCfg()?.getStringOrDefault(MessageListenerHttpd::class.simpleName!!, "")
            if (!url.isNullOrEmpty()) {
                CoroutineScope(Dispatchers.IO).launch {
                    sendMessageToServer(data, url)
                }
            }
        }
        return false
    }

    override fun initOnce(): Boolean {
        return MessageInterception.initialize()
    }

    override val onUiItemClickListener: (IUiItemAgent, Activity, View) -> Unit = { _, activity, _ ->
        val dialogContext = CommonContextWrapper.createMaterialDesignContext(activity)
        MaterialAlertDialogBuilder(dialogContext).apply {
            setTitle("设置监听服务器URL")
            setMessage("请输入监听服务器URL，用来接收消息")
            val editTextPreference: EditText = EditText(dialogContext).apply {
                setText(ConfigManager.getExFriendCfg()?.getStringOrDefault(MessageListenerHttpd::class.simpleName!!, ""))
                hint = "留空以禁用"
            }
            setView(editTextPreference)
            setPositiveButton("确定") { _, _ ->
                val regexString = editTextPreference.text.toString()
                ConfigManager.getExFriendCfg()?.putString(MessageListenerHttpd::class.simpleName!!, regexString)
            }
            setNegativeButton("取消") { _, _ -> }
        }.show()
    }

    private suspend fun sendMessageToServer(data: MsgRecordData, server: String) = withContext(Dispatchers.IO){
        val url = URL(server)
        async {
            val httpURLConnection = url.openConnection() as HttpURLConnection
            httpURLConnection.requestMethod = "POST"
            httpURLConnection.setRequestProperty("Content-Type", "application/json") // The format of the content we're sending to the server
            httpURLConnection.setRequestProperty("Accept", "application/json") // The format of response we want to get from the server
            httpURLConnection.doInput = true
            httpURLConnection.doOutput = true

            val outputStreamWriter = OutputStreamWriter(httpURLConnection.outputStream)
            val jsonObjectString = PostMessageReq(data.toString()).encodeToJson()
            outputStreamWriter.write(jsonObjectString)
            outputStreamWriter.flush()

            // Check if the connection is successful
            val responseCode = httpURLConnection.responseCode
            if (responseCode == HttpURLConnection.HTTP_OK) {
                val response = httpURLConnection.inputStream.bufferedReader()
                    .use { it.readText() }  // defaults to UTF-8

                Log.i("MessageListenerHttpd_POST_SUCCESS ! Message: $jsonObjectString ,response:$response")

            } else {
                Log.w("MessageListenerHttpd_POST_ERROR ! Message: $jsonObjectString")
            }
        }
    }

}

@Serializable
data class PostMessageReq(val msgRecordDataToString: String) {
    val message = msgRecordDataToString
}