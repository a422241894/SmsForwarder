package com.idormy.sms.forwarder.entity.action

import java.io.Serializable

data class CallSetting(
    var description: String = "", //描述
    var phoneNumber: String = "", //电话号码或模板
    var simSlot: Int = 1, //拨号卡槽（1=SIM1, 2=SIM2）
) : Serializable