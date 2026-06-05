package vn.delfi.xcloudwms.feature.login

data class LoginUiState(
    val operatorCode: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val loginErrorMessage: String? = null,
    val isPasswordVisible: Boolean = false,
    val showConnectionSection: Boolean = true,
    val connectionConfigured: Boolean = false,
    val connectionLabel: String? = null,
    val connectionUrl: String = "",
    val anonKey: String = "",
    val connectionQrInput: String = "",
    val isTestingConnection: Boolean = false,
    val isSavingConnection: Boolean = false,
    val isApplyingConnectionQr: Boolean = false,
    val connectionErrorMessage: String? = null,
    val connectionSuccessMessage: String? = null,
) {
    val isConnectionBusy: Boolean
        get() = isTestingConnection || isSavingConnection || isApplyingConnectionQr
}
