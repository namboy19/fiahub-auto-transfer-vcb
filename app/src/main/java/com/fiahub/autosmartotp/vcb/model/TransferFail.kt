package com.fiahub.autosmartotp.vcb.model

sealed class TransferFail {
    class WrongAccountName(val name: String) : TransferFail()
}
