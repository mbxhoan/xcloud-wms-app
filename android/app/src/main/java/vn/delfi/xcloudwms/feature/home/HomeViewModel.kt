package vn.delfi.xcloudwms.feature.home

import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.viewModelScope
import androidx.lifecycle.viewmodel.initializer
import androidx.lifecycle.viewmodel.viewModelFactory
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch
import vn.delfi.xcloudwms.data.session.SessionRepository
import vn.delfi.xcloudwms.domain.model.DeviceLicenseStatus

class HomeViewModel(
    private val sessionRepository: SessionRepository,
) : ViewModel() {
    val uiState: StateFlow<HomeUiState> = sessionRepository.session.map { session ->
        HomeUiState(
            isAuthenticated = session.isAuthenticated,
            operatorName = session.displayName ?: "Chưa đăng nhập",
            tenantLabel = session.tenant?.label ?: "Chưa xác định",
            warehouseLabel = session.currentWarehouse?.label ?: "Chưa chọn",
            buildEnvironment = session.buildEnvironment.uppercase(),
            connectionLabel = session.connectionLabel ?: "Chưa cấu hình",
            moduleShortcuts = buildMenuShortcuts(session.permissions),
            canSwitchWarehouse = session.allowedWarehouses.size > 1,
            deviceStatusLabel = when (session.deviceLicense?.status) {
                null -> "Chưa kiểm tra"
                DeviceLicenseStatus.PENDING -> "Chờ duyệt"
                DeviceLicenseStatus.BLOCKED -> "Bị chặn"
                DeviceLicenseStatus.REVOKED -> "Đã thu hồi"
                DeviceLicenseStatus.EXPIRED -> "Hết hạn"
                else -> "Đang hoạt động"
            },
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(5_000),
        initialValue = HomeUiState(),
    )

    fun logout() {
        viewModelScope.launch {
            sessionRepository.signOut()
        }
    }

    private fun buildMenuShortcuts(permissions: Set<String>): List<ModuleShortcut> {
        val moduleDefinitions = listOf(
            ModuleDefinition(
                title = "Nhận hàng",
                note = "Xem và thao tác phiếu nhập trong kho làm việc.",
                requiredPermissions = setOf("gr.scan"),
                actionKey = ACTION_GOODS_RECEIPT,
                actionLabel = "Mở danh sách phiếu nhập",
            ),
            ModuleDefinition(
                title = "Xuất hàng",
                note = "Xem và thao tác phiếu xuất được phân công.",
                requiredPermissions = setOf("gi.scan"),
                actionKey = ACTION_GOODS_ISSUE,
                actionLabel = "Mở danh sách phiếu xuất",
            ),
            ModuleDefinition(
                title = "Kiểm kê",
                note = "Quét và ghi nhận số lượng kiểm kê theo quyền hiện tại.",
                requiredPermissions = setOf("inventory.scan"),
                actionKey = ACTION_INVENTORY_COUNT,
                actionLabel = "Mở danh sách phiếu kiểm kê",
            ),
            ModuleDefinition(
                title = "Sắp xếp kho",
                note = "Chuyển vị trí hàng hóa trong cùng kho làm việc.",
                requiredPermissions = setOf("inventory.scan"),
                actionKey = ACTION_PUTAWAY,
                actionLabel = "Mở sắp xếp kho",
            ),
            ModuleDefinition(
                title = "Tra cứu tồn",
                note = "Tra cứu nhanh theo mã hàng, lô hoặc serial.",
                requiredPermissions = setOf("inventory.scan"),
                actionKey = ACTION_STOCK_LOOKUP,
                actionLabel = "Mở tra cứu tồn",
            ),
            ModuleDefinition(
                title = "Đơn vị chứa",
                note = "Xem thao tác liên quan tới đơn vị chứa khi tenant đã bật tính năng.",
                requiredPermissions = setOf("lpn.read"),
            ),
        )

        val shortcuts = moduleDefinitions.filter { definition ->
            definition.requiredPermissions.all(permissions::contains)
        }.map { definition ->
            ModuleShortcut(
                title = definition.title,
                note = definition.note,
                actionKey = definition.actionKey,
                actionLabel = definition.actionLabel,
            )
        }

        return if (shortcuts.isEmpty()) {
            listOf(
                ModuleShortcut(
                    title = "Chưa có danh mục thao tác",
                    note = "Tài khoản hiện chưa được cấp quyền cho module quét nào.",
                ),
            )
        } else {
            shortcuts
        }
    }

    companion object {
        const val ACTION_STOCK_LOOKUP = "stock_lookup"
        const val ACTION_PUTAWAY = "putaway"
        const val ACTION_GOODS_ISSUE = "goods_issue"
        const val ACTION_GOODS_RECEIPT = "goods_receipt"
        const val ACTION_INVENTORY_COUNT = "inventory_count"

        fun factory(
            sessionRepository: SessionRepository,
        ): ViewModelProvider.Factory = viewModelFactory {
            initializer {
                HomeViewModel(
                    sessionRepository = sessionRepository,
                )
            }
        }
    }

    private data class ModuleDefinition(
        val title: String,
        val note: String,
        val requiredPermissions: Set<String>,
        val actionKey: String? = null,
        val actionLabel: String? = null,
    )
}
