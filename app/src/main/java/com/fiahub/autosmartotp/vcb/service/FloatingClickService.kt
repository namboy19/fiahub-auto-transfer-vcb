package com.fiahub.autosmartotp.vcb.service

import android.app.Service
import android.content.Context
import android.content.Intent
import android.content.res.Configuration
import android.graphics.PixelFormat
import android.os.Build
import android.os.IBinder
import android.view.LayoutInflater
import android.view.WindowManager
import android.widget.RelativeLayout
import android.widget.TextView
import android.widget.Toast
import com.fiahub.autosmartotp.vcb.R
import com.fiahub.autosmartotp.vcb.TouchAndDragListener
import com.fiahub.autosmartotp.vcb.dp2px
import com.fiahub.autosmartotp.vcb.logd
import com.fiahub.autosmartotp.vcb.model.AuthenticateAccount
import com.fiahub.autosmartotp.vcb.model.TransferFail
import com.fiahub.autosmartotp.vcb.model.TransferInfo
import com.fiahub.autosmartotp.vcb.service.api.ApiService
import kotlinx.coroutines.*
import kotlinx.coroutines.flow.catch
import kotlinx.coroutines.flow.collect
import kotlinx.coroutines.flow.flow
import kotlinx.coroutines.flow.flowOn
import java.util.*
import kotlin.coroutines.CoroutineContext


class FloatingClickService : Service(), CoroutineScope {

    private lateinit var manager: WindowManager
    private lateinit var view: RelativeLayout
    private lateinit var params: WindowManager.LayoutParams
    private var xForRecord = 0
    private var yForRecord = 0
    private var startDragDistance: Int = 0

    ///-------------------------------------------------------------
    private var isOn = false
    private var transferInfo: TransferInfo? = null
    private var authenticateAccount: AuthenticateAccount? = null

    companion object {
        val POOLING_INTERVAL = 20000L
        val RESTART_INTERVAL = 25000L
        val RESTART_FAIL_MAX = 3

        const val PARAM_UNLOCK_OTP_PIN = "PARAM_UNLOCK_OTP_PIN"
        const val PARAM_PASSWORD = "PARAM_PASSWORD"

        fun start(context: Context, pin: String, pass: String) {
            context.startService(Intent(context, FloatingClickService::class.java).apply {
                putExtra(PARAM_UNLOCK_OTP_PIN, pin)
                putExtra(PARAM_PASSWORD, pass)
            })
        }
    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {

        authenticateAccount = AuthenticateAccount(intent?.getStringExtra(PARAM_PASSWORD) ?: "",
            intent?.getStringExtra(PARAM_UNLOCK_OTP_PIN) ?: "")

        return super.onStartCommand(intent, flags, startId)
    }

    override fun onBind(intent: Intent): IBinder? {
        return null
    }

    override fun onCreate() {
        super.onCreate()

        startDragDistance = dp2px(10f)
        view = LayoutInflater.from(this).inflate(R.layout.widget, null) as RelativeLayout

        //setting the layout parameters
        val overlayParam =
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
                WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
            } else {
                WindowManager.LayoutParams.TYPE_PHONE
            }

        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.WRAP_CONTENT,
            WindowManager.LayoutParams.WRAP_CONTENT,
            overlayParam,
            WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE,
            PixelFormat.TRANSLUCENT)

        //adding an touchlistener to make drag movement of the floating widget
        view.setOnTouchListener(TouchAndDragListener(params, startDragDistance,
            {
                isOn = !isOn

                if (isOn) {
                    openBankApp()
                    processTransaction()
                } else {
                    stopAuto()
                }
                view.findViewById<TextView>(R.id.button).text = if (isOn) "ON" else "OFF"
            },
            { manager.updateViewLayout(view, params) }))

        //getting windows services and adding the floating view to it
        manager = getSystemService(Context.WINDOW_SERVICE) as WindowManager
        manager.addView(view, params)
    }


    private var restartCountFail = 0
    private var restartAutoJob: Job? = null

    private fun startAutoRestartJob() {

        restartAutoJob?.cancel()

        restartAutoJob = launch {

            delay(RESTART_INTERVAL)

            autoClickServiceVcb?.stopGetOtp()

            if (isCompletedTransfer || restartCountFail == RESTART_FAIL_MAX) {

                if (isCompletedTransfer) {
                    sendLog("[ERROR] \n" +
                            "Cannot check status of this transaction. >>> PLEASE CHECK MANUAL <<<")
                }

                if (restartCountFail == RESTART_FAIL_MAX) {
                    sendLog("[ERROR] \n" +
                            "Failed retry transfer this transaction. >>> PLEASE CHECK MANUAL <<<")
                }

                //clear current transaction to process next transaction after n failed or transfer was marked completed
                transferInfo = null
            } else {
                restartCountFail++
            }

            processTransaction()
        }
    }

    private var isCompletedTransfer: Boolean = false

    private fun stopAuto() {

        autoClickServiceVcb?.stopGetOtp()
        restartCountFail = 0

        getTransactionJob?.cancel()
        restartAutoJob?.cancel()
    }

    private fun openBankApp() {
        val packageName = getString(R.string.bank_package_id)

        try {
            val intent = packageManager.getLaunchIntentForPackage(packageName)
            intent?.flags = Intent.FLAG_ACTIVITY_CLEAR_TASK or Intent.FLAG_ACTIVITY_NEW_TASK

            startActivity(intent)
        } catch (e: Exception) {
            Toast.makeText(this, "App not installed", Toast.LENGTH_SHORT).show()
        }
    }

    private val testTransaction: Queue<TransferInfo> by lazy {

        val queue: Queue<TransferInfo> = LinkedList()

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huynh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh chấn nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        /* queue.add(TransferInfo("0331000472949",
             "LÂM THỊ BÉ",
             "vcb",
             "1000",
             "AAAAAAAAAAAAAAA",
             false))

         queue.add(TransferInfo("0331000472949",
             "LÂM THỊ BÉ HIỀN",
             "vcb",
             "1000",
             "AAAAAAAAAAAAAAA",
             false))*/

        queue.add(TransferInfo("19033272714017",
            "huynh nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue.add(TransferInfo("19033272714017",
            "huỳnh chấn nam",
            "techcombank",
            "1000",
            "WERTRSDFDASFWEFW",
            true))

        queue
    }

    private var getTransactionJob: Job? = null

    private fun startGetTransactionJob() {

        getTransactionJob?.cancel()

        getTransactionJob = launch {

            /*flow {
                    emit(ApiService.apiService.getPendingTransaction().await())
                }.flowOn(Dispatchers.IO).catch {
                    //emit(null)
                    onGetTransactionFailed()
                }.collect {
                    it.transaction_code.takeIf { !it.isNullOrEmpty() }?.also {
                        onGetTransSuccess(it)
                    } ?: kotlin.run {
                        onGetTransactionFailed()
                    }
                }*/

            delay(1000)

            val trans = testTransaction.poll()

            if (trans != null) {
                startTransfer(trans)
            } else {
                delay(POOLING_INTERVAL)
                startGetTransactionJob()
            }
        }
    }

    private fun startTransfer(trans: TransferInfo) {
        transferInfo = trans
        transferInfo?.let { transferInfo ->
            authenticateAccount?.let { account ->

                sendLog("=====================================================\n" +
                        "[START]\n" +
                        "${transferInfo}")

                autoClickServiceVcb?.startGetOtp(transferInfo,
                    account,
                    ::onTransferCompleted,
                    ::onTransferSuccess,
                    ::onTransferFailed)

                startAutoRestartJob()
            }
        }
    }

    private fun processTransaction() {

        //////////// clear current job
        restartAutoJob?.cancel()
        getTransactionJob?.cancel()

        if (transferInfo == null) {

            isCompletedTransfer = false
            restartCountFail = 0

            startGetTransactionJob()
        } else {
            openBankApp()
            startTransfer(transferInfo!!)
        }
    }

    private fun onTransferCompleted() {
        // call api update order with state transferred
        isCompletedTransfer = true
        sendLog("TRANSFER COMPLETED")
    }

    private fun onTransferSuccess() {

        /* ApiService.apiService.sendOtp(otp, transactionID).observeOn(AndroidSchedulers.mainThread())
             .subscribeOn(Schedulers.io()).subscribe({

             }, {
                 it.printStackTrace()
             })*/

        // call api update order with state success
        sendLog("TRANSFER SUCCEED")

        transferInfo = null
        processTransaction()
    }

    private fun onTransferFailed(reason: TransferFail) {
        when (reason) {
            is TransferFail.WrongAccountName -> {

                val text = "[ERROR] \n" +
                        "Bank account name not match"
                sendLog(text)

                transferInfo = null
                processTransaction()
            }
        }
    }

    private fun sendLog(log: String) = launch {
        flow {
            emit(ApiService.apiService.sendTelegramLog(log).await())
        }.flowOn(Dispatchers.IO).catch {
            //emit(null)
        }.collect {

        }
    }

    override fun onDestroy() {
        super.onDestroy()
        manager.removeView(view)
        stopAuto()
    }

    override fun onConfigurationChanged(newConfig: Configuration) {
        super.onConfigurationChanged(newConfig)
        "FloatingClickService onConfigurationChanged".logd()
        val x = params.x
        val y = params.y
        params.x = xForRecord
        params.y = yForRecord
        xForRecord = x
        yForRecord = y
        manager.updateViewLayout(view, params)
    }

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main

}