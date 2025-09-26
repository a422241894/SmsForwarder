package com.idormy.sms.forwarder.database.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import androidx.paging.Pager
import androidx.paging.PagingConfig
import androidx.paging.PagingData
import androidx.paging.cachedIn
import com.google.gson.Gson
import com.idormy.sms.forwarder.database.dao.TaskDao
import com.idormy.sms.forwarder.database.entity.Task
import com.idormy.sms.forwarder.database.ext.ioThread
import com.idormy.sms.forwarder.entity.TaskSetting
import com.idormy.sms.forwarder.entity.condition.CronSetting
import com.idormy.sms.forwarder.utils.STATUS_OFF
import com.idormy.sms.forwarder.utils.STATUS_ON
import com.idormy.sms.forwarder.utils.TASK_CONDITION_CRON
import com.idormy.sms.forwarder.utils.task.CronJobScheduler
import kotlinx.coroutines.flow.Flow
import gatewayapps.crondroid.CronExpression
import java.util.Date

class TaskViewModel(private val dao: TaskDao) : ViewModel() {
    private var type: String = "mine"

    fun setType(type: String): TaskViewModel {
        this.type = type
        return this
    }

    val allTasks: Flow<PagingData<Task>> = Pager(
        config = PagingConfig(
            pageSize = 10,
            enablePlaceholders = false,
            initialLoadSize = 10
        )
    ) {
        //TODO:根据条件查询，暂不使用
        //dao.pagingSource(type)
        if (type == "mine") dao.pagingSourceMine() else dao.pagingSourceFixed()
    }.flow.cachedIn(viewModelScope)

    fun insertOrUpdate(task: Task) = ioThread {
        if (task.id > 0) dao.update(task) else dao.insert(task)
    }

    fun delete(id: Long) = ioThread {
        dao.delete(id)
    }

    fun updateStatus(id: Long, status: Int) = ioThread {
        val task = dao.getSync(id) ?: return@ioThread

        if (task.type != TASK_CONDITION_CRON) {
            dao.updateStatus(id, status)
            if (status == STATUS_OFF) {
                CronJobScheduler.cancelTask(id)
            }
            return@ioThread
        }

        if (status == STATUS_ON) {
            runCatching {
                val gson = Gson()
                val conditions = gson.fromJson(task.conditions, Array<TaskSetting>::class.java)
                val cronSetting = conditions.firstOrNull()?.let {
                    gson.fromJson(it.setting, CronSetting::class.java)
                }
                if (cronSetting?.expression.isNullOrBlank()) {
                    throw IllegalStateException("Invalid cron expression")
                }

                val now = Date().apply { time = time / 1000 * 1000 }
                val nextExecTime = CronExpression(cronSetting!!.expression).getNextValidTimeAfter(now)
                    ?.apply { time = time / 1000 * 1000 }
                    ?: throw IllegalStateException("Failed to compute next execution time")

                task.status = status
                task.lastExecTime = now
                task.nextExecTime = nextExecTime

                dao.updateExecTime(id, now, nextExecTime, status)

                CronJobScheduler.cancelTask(id)
                CronJobScheduler.scheduleTask(task)
            }.onFailure {
                dao.updateStatus(id, STATUS_OFF)
                CronJobScheduler.cancelTask(id)
            }
        } else {
            dao.updateStatus(id, status)
            CronJobScheduler.cancelTask(id)
        }
    }
}