package com.jomo.clashbrowser

import android.app.Service
import android.content.Intent
import android.os.IBinder

/**
 * 生命周期哨兵：用户从最近任务划掉应用时回调 onTaskRemoved，
 * 立刻 destroyForcibly 销毁内核进程，绝不驻留后台耗电。
 */
class CoreKeeperService : Service() {

    override fun onBind(intent: Intent?): IBinder? = null

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int = START_NOT_STICKY

    override fun onTaskRemoved(rootIntent: Intent?) {
        // 用户主动划掉应用 = 正常退出，下次启动不弹恢复弹窗
        SessionStore.init(this)
        SessionStore.markCleanExit()
        ProxyManager.shutdown()
        stopSelf()
        super.onTaskRemoved(rootIntent)
    }

    override fun onDestroy() {
        ProxyManager.shutdown()
        super.onDestroy()
    }
}