package com.idormy.sms.forwarder.fragment.action

import android.annotation.SuppressLint
import android.content.Intent
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.work.Data
import androidx.work.OneTimeWorkRequestBuilder
import androidx.work.WorkManager
import com.google.gson.Gson
import com.google.gson.reflect.TypeToken
import com.hjq.permissions.OnPermissionCallback
import com.hjq.permissions.Permission
import com.hjq.permissions.XXPermissions
import com.idormy.sms.forwarder.R
import com.idormy.sms.forwarder.core.BaseFragment
import com.idormy.sms.forwarder.databinding.FragmentTasksActionCallBinding
import com.idormy.sms.forwarder.entity.MsgInfo
import com.idormy.sms.forwarder.entity.TaskSetting
import com.idormy.sms.forwarder.entity.action.CallSetting
import com.idormy.sms.forwarder.server.model.ConfigData
import com.idormy.sms.forwarder.utils.HttpServerUtils
import com.idormy.sms.forwarder.utils.KEY_BACK_DATA_ACTION
import com.idormy.sms.forwarder.utils.KEY_BACK_DESCRIPTION_ACTION
import com.idormy.sms.forwarder.utils.KEY_EVENT_DATA_ACTION
import com.idormy.sms.forwarder.utils.Log
import com.idormy.sms.forwarder.utils.TASK_ACTION_CALL
import com.idormy.sms.forwarder.utils.PhoneUtils
import com.idormy.sms.forwarder.utils.TaskWorker
import com.idormy.sms.forwarder.utils.XToastUtils
import com.idormy.sms.forwarder.workers.ActionWorker
import com.xuexiang.xaop.annotation.SingleClick
import com.xuexiang.xpage.annotation.Page
import com.xuexiang.xrouter.annotation.AutoWired
import com.xuexiang.xrouter.launcher.XRouter
import com.xuexiang.xrouter.utils.TextUtils
import com.xuexiang.xui.utils.CountDownButtonHelper
import com.xuexiang.xui.widget.actionbar.TitleBar
import java.util.Date

@Page(name = "Call")
@Suppress("PrivatePropertyName")
class CallFragment : BaseFragment<FragmentTasksActionCallBinding?>(), View.OnClickListener {

    private val TAG: String = CallFragment::class.java.simpleName
    private var titleBar: TitleBar? = null
    private var mCountDownHelper: CountDownButtonHelper? = null

    @JvmField
    @AutoWired(name = KEY_EVENT_DATA_ACTION)
    var eventData: String? = null

    private var description = ""
    private var phoneNumber = ""
    private var simSlot = 1

    override fun initArgs() {
        XRouter.getInstance().inject(this)
    }

    override fun viewBindingInflate(
        inflater: LayoutInflater,
        container: ViewGroup,
    ): FragmentTasksActionCallBinding {
        return FragmentTasksActionCallBinding.inflate(inflater, container, false)
    }

    override fun initTitle(): TitleBar? {
        titleBar = super.initTitle()!!.setImmersive(false).setTitle(R.string.task_call_phone)
        return titleBar
    }

    /**
     * 初始化控件
     */
    @SuppressLint("SetTextI18n")
    override fun initViews() {
        mCountDownHelper = CountDownButtonHelper(binding!!.btnTest, 1)
        mCountDownHelper!!.setOnCountDownListener(object : CountDownButtonHelper.OnCountDownListener {
            override fun onCountDown(time: Int) {
                binding!!.btnTest.text = String.format(getString(R.string.seconds_n), time)
            }

            override fun onFinished() {
                binding!!.btnTest.text = getString(R.string.test)
            }
        })

        val serverConfigStr = HttpServerUtils.serverConfig
        if (!TextUtils.isEmpty(serverConfigStr)) {
            val serverConfig: ConfigData = Gson().fromJson(serverConfigStr, object : TypeToken<ConfigData>() {}.type)
            binding!!.rbSimSlot1.text = "SIM1：" + serverConfig.extraSim1
            binding!!.rbSimSlot2.text = "SIM2：" + serverConfig.extraSim2
        }

        val simSlotCount = PhoneUtils.getSimSlotCount()
        if (simSlotCount < 2) {
            binding!!.rbSimSlot2.visibility = View.GONE
        }

        Log.d(TAG, "initViews eventData:$eventData")
        if (!eventData.isNullOrEmpty()) {
            val settingVo = Gson().fromJson(eventData, CallSetting::class.java)
            Log.d(TAG, "initViews settingVo:$settingVo")
            phoneNumber = settingVo.phoneNumber
            simSlot = if (simSlotCount >= 2 && settingVo.simSlot in 1..2) settingVo.simSlot else 1
        }

        binding!!.etPhoneNumber.setText(phoneNumber)
        binding!!.rgSimSlot.check(if (simSlot == 2 && simSlotCount >= 2) R.id.rb_sim_slot_2 else R.id.rb_sim_slot_1)
    }

    override fun onDestroyView() {
        mCountDownHelper?.recycle()
        super.onDestroyView()
    }

    override fun initListeners() {
        binding!!.btnTest.setOnClickListener(this)
        binding!!.btnDel.setOnClickListener(this)
        binding!!.btnSave.setOnClickListener(this)
    }

    @SingleClick
    override fun onClick(v: View) {
        try {
            when (v.id) {
                R.id.btn_test -> {
                    mCountDownHelper?.start()
                    XXPermissions.with(this)
                        .permission(Permission.CALL_PHONE)
                        .request(object : OnPermissionCallback {
                            override fun onGranted(permissions: List<String>, all: Boolean) {
                                mCountDownHelper?.start()
                                try {
                                    val settingVo = checkSetting()
                                    Log.d(TAG, settingVo.toString())
                                    val taskAction = TaskSetting(TASK_ACTION_CALL, getString(R.string.task_call_phone), settingVo.description, Gson().toJson(settingVo), requestCode)
                                    val taskActionsJson = Gson().toJson(arrayListOf(taskAction))
                                    val msgInfo = MsgInfo("task", getString(R.string.task_call_phone), settingVo.description, Date(), getString(R.string.task_call_phone))
                                    val actionData = Data.Builder().putLong(TaskWorker.TASK_ID, 0).putString(TaskWorker.TASK_ACTIONS, taskActionsJson).putString(TaskWorker.MSG_INFO, Gson().toJson(msgInfo)).build()
                                    val actionRequest = OneTimeWorkRequestBuilder<ActionWorker>().setInputData(actionData).build()
                                    WorkManager.getInstance().enqueue(actionRequest)
                                } catch (e: Exception) {
                                    mCountDownHelper?.finish()
                                    e.printStackTrace()
                                    Log.e(TAG, "onClick error: ${e.message}")
                                    XToastUtils.error(e.message.toString(), 30000)
                                }
                            }

                            override fun onDenied(permissions: List<String>, never: Boolean) {
                                mCountDownHelper?.finish()
                                XToastUtils.error(getString(R.string.no_call_phone_permission), 30000)
                            }
                        })
                    return
                }

                R.id.btn_del -> {
                    popToBack()
                    return
                }

                R.id.btn_save -> {
                    val settingVo = checkSetting()
                    val intent = Intent()
                    intent.putExtra(KEY_BACK_DESCRIPTION_ACTION, description)
                    intent.putExtra(KEY_BACK_DATA_ACTION, Gson().toJson(settingVo))
                    setFragmentResult(TASK_ACTION_CALL, intent)
                    popToBack()
                    return
                }
            }
        } catch (e: Exception) {
            XToastUtils.error(e.message.toString(), 30000)
            e.printStackTrace()
            Log.e(TAG, "onClick error: ${e.message}")
        }
    }

    private fun checkSetting(): CallSetting {
        phoneNumber = binding!!.etPhoneNumber.text.toString().trim()
        if (TextUtils.isEmpty(phoneNumber)) {
            throw Exception(getString(R.string.call_number_error))
        }

        val regex = Regex(getString(R.string.call_number_regex))
        if (!regex.matches(phoneNumber)) {
            throw Exception(getString(R.string.call_number_error))
        }

        simSlot = if (binding!!.rgSimSlot.checkedRadioButtonId == R.id.rb_sim_slot_2) 2 else 1

        description = getString(R.string.call_phone_number_with_sim, simSlot, phoneNumber)
        return CallSetting(description, phoneNumber, simSlot)
    }
}