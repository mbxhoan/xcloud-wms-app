package vn.delfi.xcloudwms.feature.login

data class LoginUiState(
    val operatorCode: String = "",
    val password: String = "",
    val isLoading: Boolean = false,
    val isAuthenticated: Boolean = false,
    val errorMessage: String? = null,
    val helperMessage: String = "Màn này mới dựng lớp nền native, chưa gọi API xác thực thật.",
)
