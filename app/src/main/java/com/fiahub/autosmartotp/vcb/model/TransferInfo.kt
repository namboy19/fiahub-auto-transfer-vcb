package com.fiahub.autosmartotp.vcb.model

data class TransferInfo(val transferBankAccountNum: String,
                        val transferBankAccountName: String,
                        val transferBankBranch: String,
                        val transferMoney: String,
                        val transferNote: String,
                        val isTransferOutSideVcb: Boolean)