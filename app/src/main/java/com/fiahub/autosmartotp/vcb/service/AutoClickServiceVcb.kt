package com.fiahub.autosmartotp.vcb.service

import android.accessibilityservice.AccessibilityService
import android.content.Intent
import android.os.Bundle
import android.support.v4.view.accessibility.AccessibilityNodeInfoCompat
import android.util.Log
import android.view.accessibility.AccessibilityEvent
import android.view.accessibility.AccessibilityNodeInfo
import com.fiahub.autosmartotp.vcb.MainActivity
import com.fiahub.autosmartotp.vcb.R
import com.fiahub.autosmartotp.vcb.Utils
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
import kotlin.coroutines.CoroutineContext


var autoClickServiceVcb: AutoClickServiceVcb? = null

class AutoClickServiceVcb : AccessibilityService(), CoroutineScope {

    override fun onInterrupt() {
        // NO-OP
    }

    private var onCompletedTransfer: (() -> Unit)? = null
    private var onSucceedTransfer: (() -> Unit)? = null
    private var onTransferFailed: ((TransferFail) -> Unit)? = null

    private var isStarted = false

    private var transferData: TransferInfo? = null
    private var authenticateAccount: AuthenticateAccount? = null

    private var isLoadedLoginScreen = false
    private var isLoadedHomeScreen = false
    private var isLoadedTransferScreen = false
    private var isInputedTransferInfo = false
    private var isLoadedPickBankBranchScreen = false
    private var isLoadedConfirmTransferScreen = false
    private var isLoadedUnlockOtpScreen = false
    private var isLoadedSuccessTransferScreen = false


    companion object {
        private const val DELAY_TIME_FOR_RENDER_SCREEN = 300L
    }

    fun isBankAppOpening(): Boolean? {
        if ((rootInActiveWindow?.packageName == null)) {
            return null
        } else if (rootInActiveWindow?.packageName == "android") {
            /**
             * try to dissmiss system dialog "App not response"
             */
            rootInActiveWindow?.findAccessibilityNodeInfosByViewId("android:id/aerr_close")
                ?.firstOrNull()?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            return false
        } else {
            return rootInActiveWindow?.packageName == getString(R.string.bank_package_id)
        }
    }

    override fun onAccessibilityEvent(event: AccessibilityEvent) {
        Log.d("nam", event.toString())
        handleScreenChanged()
    }

    private fun isLoginScreen(): Boolean {
        //--login screen
        return !rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/lblForgetPassword")
            .isNullOrEmpty()
    }

    private fun isHomeScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/ivLogout")
            .isNullOrEmpty() &&
                !rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/rvQuickMenu")
                    .isNullOrEmpty()
    }

    private fun isTransferScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByText("Thông tin người chuyển")
            .isNullOrEmpty() &&
                !rootInActiveWindow?.findAccessibilityNodeInfosByText("Thông tin người hưởng")
                    .isNullOrEmpty()
    }

    private fun isPickBankBranchScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByText("ngân hàng thụ hưởng")
            .isNullOrEmpty() &&
                !rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtSearch")
                    .isNullOrEmpty()
    }

    private fun isConfirmTransferScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByText("xác nhận thông tin")
            .isNullOrEmpty()
    }

    private fun isSuccessTransferScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByText("chuyển khoản thành công")
            .isNullOrEmpty()
    }

    private fun isUnlockOtpScreen(): Boolean {
        return !rootInActiveWindow?.findAccessibilityNodeInfosByText("xác thực giao dịch")
            .isNullOrEmpty()
    }

    fun startGetOtp(transfer: TransferInfo,
                    account: AuthenticateAccount,
                    onCompleted: () -> Unit,
                    onSucceed: () -> Unit,
                    onFailed: (TransferFail) -> Unit) {

        isStarted = true
        transferData = transfer
        authenticateAccount = account

        onCompletedTransfer = onCompleted
        onSucceedTransfer = onSucceed
        onTransferFailed = onFailed

        handleScreenChanged()
    }

    fun stopGetOtp() {
        isStarted = false
        isLoadedLoginScreen = false
        isLoadedHomeScreen = false
        isLoadedTransferScreen = false
        isInputedTransferInfo = false
        isLoadedPickBankBranchScreen = false
        isLoadedConfirmTransferScreen = false
        isLoadedUnlockOtpScreen = false
        isLoadedSuccessTransferScreen = false
    }

    private fun handleScreenChanged() {

        GlobalScope.launch(Dispatchers.Main) {

            if (!isStarted) {
                return@launch
            }

            when {
                isLoginScreen() && !isLoadedLoginScreen -> {
                    isLoadedLoginScreen = true

                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtInput")
                        ?.firstOrNull()?.let {
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                authenticateAccount?.password)
                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)

                            rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/btnNext")
                                ?.firstOrNull()
                                ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                        }
                }

                isHomeScreen() && !isLoadedHomeScreen -> {
                    //sendLog("-> home screen")

                    isLoadedHomeScreen = true
                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    //using recycler view with item, so can not specific the viewID

                    if (transferData?.isTransferOutSideVcb == true) {
                        rootInActiveWindow?.findAccessibilityNodeInfosByText("Chuyển tiền nhanh 24/7 ngoài VCB")
                            ?.firstOrNull()?.let {
                                performClick(it)
                            }
                    } else {
                        rootInActiveWindow?.findAccessibilityNodeInfosByText("Chuyển tiền trong VCB")
                            ?.firstOrNull()?.let {
                                performClick(it)
                            }
                    }
                }

                isTransferScreen() && !isLoadedTransferScreen -> {

                    //sendLog("-> transfer screen")

                    isLoadedTransferScreen = true
                    isLoadedPickBankBranchScreen = false

                    //wait to load balance
                    delay(1000)

                    //input bank account num
                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtContent1")
                        ?.firstOrNull()?.let {
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                transferData?.transferBankAccountNum)
                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }

                    //input amount money
                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtContent1")
                        ?.getOrNull(1)?.let {
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                transferData?.transferMoney)
                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }

                    //input transfer note
                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtContent2")
                        ?.firstOrNull()?.let {
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                transferData?.transferNote)
                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }

                    //----

                    if (!isInputedTransferInfo && transferData?.isTransferOutSideVcb == true) {
                        //pick bank branch
                        rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/llContent3")
                            ?.firstOrNull()?.let {
                                performClick(it)
                            }
                    } else {
                        //create transfer
                        rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/btContinue")
                            ?.firstOrNull()?.let {
                                performClick(it)
                            }
                    }

                    isInputedTransferInfo = true
                }

                isPickBankBranchScreen() && !isLoadedPickBankBranchScreen -> {

                    //sendLog("-> pick bank screen")

                    isLoadedPickBankBranchScreen = true
                    isLoadedTransferScreen = false

                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/edtSearch")
                        ?.firstOrNull()
                        ?.let {
                            //input search bank name
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                transferData?.transferBankBranch)
                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)


                            //wait search bank result display
                            delay(DELAY_TIME_FOR_RENDER_SCREEN)

                            // click select bank
                            rootInActiveWindow?.findAccessibilityNodeInfosByText(transferData?.transferBankBranch)
                                ?.find { it.className == "android.widget.TextView" }?.let {
                                    performClick(it)
                                }
                        }
                }

                isConfirmTransferScreen() && !isLoadedConfirmTransferScreen -> {
                    //sendLog("-> confirm transfer screen")

                    isLoadedConfirmTransferScreen = true

                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    rootInActiveWindow.findAccessibilityNodeInfosByViewId("com.VCB:id/tvContent")
                        .find { checkCorrectAccountName(it.text.toString()) }?.let {
                            rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/llptxt")
                                ?.firstOrNull()?.let {

                                    performClick(it)

                                    //wait popup select otp type  show
                                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                                    rootInActiveWindow?.findAccessibilityNodeInfosByText("smart otp")
                                        ?.firstOrNull()?.let {
                                            performClick(it)
                                        }

                                    //wait popup dismiss
                                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/btContinue")
                                        ?.firstOrNull()?.let { performClick(it) }
                                }
                        } ?: kotlin.run {

                        rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/ivTitleRight")
                            ?.firstOrNull()?.let {
                                performClick(it)
                                stopGetOtp()
                                onTransferFailed?.invoke(TransferFail.WrongAccountName(""))
                            }
                    }
                }

                isUnlockOtpScreen() && !isLoadedUnlockOtpScreen -> {
                    //sendLog("-> unlock otp screen")

                    isLoadedUnlockOtpScreen = true

                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    // set unlock otp pass to edittext
                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/otp")
                        ?.firstOrNull()
                        ?.let {
                            val arguments = Bundle()
                            arguments.putCharSequence(AccessibilityNodeInfo.ACTION_ARGUMENT_SET_TEXT_CHARSEQUENCE,
                                authenticateAccount?.unlockOtpPassword)

                            it.performAction(AccessibilityNodeInfo.ACTION_SET_TEXT, arguments)
                        }

                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/submit")
                        ?.firstOrNull()
                        ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

                    //wait for smart otp shown
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/btContinue")
                        ?.firstOrNull()
                        ?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

                    onCompletedTransfer?.invoke()
                }

                isSuccessTransferScreen() && !isLoadedSuccessTransferScreen -> {
                    //sendLog("-> success transfer screen")
                    isLoadedSuccessTransferScreen = true

                    //wait screen fully display
                    delay(DELAY_TIME_FOR_RENDER_SCREEN)

                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/ivHome")
                        ?.firstOrNull()?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)

                    // notify completed transfer
                    stopGetOtp()
                    onSucceedTransfer?.invoke()
                }

                else -> {
                    //dissmiss dialog:
                    // 1/ case feedback the app after some transfer success
                    rootInActiveWindow?.findAccessibilityNodeInfosByViewId("com.VCB:id/btCancel")
                        ?.firstOrNull()?.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
                }
            }
        }
    }

    private fun checkCorrectAccountName(accountName: String): Boolean {
        return Utils.removeUnicode(accountName.uppercase()) == Utils.removeUnicode(transferData?.transferBankAccountName?.uppercase()
            ?: "")
    }

    private fun performClick(node: AccessibilityNodeInfo) {
        //-- due to the item is wrapper in another view, so only the parents receive click event
        // force click all parent of item
        node.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
        var currentNode = node
        while (currentNode.parent != null) {
            currentNode.parent.performAction(AccessibilityNodeInfoCompat.ACTION_CLICK)
            currentNode = currentNode.parent
        }
    }

    override fun onServiceConnected() {
        super.onServiceConnected()
        "onServiceConnected".logd()
        autoClickServiceVcb = this
        startActivity(Intent(this, MainActivity::class.java)
            .addFlags(Intent.FLAG_ACTIVITY_NEW_TASK))
    }

    override fun onUnbind(intent: Intent?): Boolean {
        "AutoClickServiceVcb onUnbind".logd()
        autoClickServiceVcb = null
        return super.onUnbind(intent)
    }

    override fun onDestroy() {
        "AutoClickServiceVcb onDestroy".logd()
        autoClickServiceVcb = null
        super.onDestroy()
    }

    private fun sendLog(log: String) = launch {
        flow {
            emit(ApiService.apiService.sendTelegramLog(log).await())
        }.flowOn(Dispatchers.IO).catch {
            //emit(null)
        }.collect {

        }
    }

    override val coroutineContext: CoroutineContext
        get() = SupervisorJob() + Dispatchers.Main
}