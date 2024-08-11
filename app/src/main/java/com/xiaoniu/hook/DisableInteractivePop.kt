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

package com.xiaoniu.hook

import com.github.kyuubiran.ezxhelper.utils.hookReturnConstant
import io.github.qauxv.base.annotation.FunctionHookEntry
import io.github.qauxv.base.annotation.UiItemAgentEntry
import io.github.qauxv.dsl.FunctionEntryRouter.Locations.Simplify
import io.github.qauxv.hook.CommonSwitchFunctionHook
import xyz.nextalone.util.clazz
import xyz.nextalone.util.method

@FunctionHookEntry
@UiItemAgentEntry
object DisableInteractivePop : CommonSwitchFunctionHook() {
    override val name = "禁用特定消息触发的交互式弹窗"

    override fun initOnce(): Boolean {
        // keyword string: 跳过, 关闭
        "Lcom/tencent/mobileqq/springhb/interactive/ui/InteractivePopManager;".clazz!!.method {
            it.parameterCount > 0 && it.parameterTypes[0].name == "androidx.fragment.app.Fragment"
        }!!.hookReturnConstant(null)
        return true
    }

    override val uiItemLocation = Simplify.UI_CHAT_MSG
}