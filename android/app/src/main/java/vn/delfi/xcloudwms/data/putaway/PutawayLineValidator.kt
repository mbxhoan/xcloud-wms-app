package vn.delfi.xcloudwms.data.putaway

/**
 * Validation UX phía client trước khi gọi `rpc_pa_add_line`. Đây chỉ là chặn nhanh để báo lỗi
 * sớm; backend vẫn là nơi xác thực cuối cùng khi add line/submit (xem AGENTS.md).
 * Bám các điều kiện trong scanner PWA: from required, product required, qty > 0, from != to,
 * qty <= available (khi biết available).
 */
object PutawayLineValidator {
    sealed interface Result {
        data object Ok : Result
        data class Invalid(val field: Field, val message: String) : Result
    }

    enum class Field { FROM_LOCATION, TO_LOCATION, PRODUCT, QTY }

    data class Input(
        val fromLocationId: String?,
        val toLocationId: String?,
        /** Có sản phẩm đã chọn HOẶC có mã quét để phân giải. */
        val hasProductOrCode: Boolean,
        val quantity: Double?,
        /** Tồn khả dụng nếu API đã trả về; null nếu chưa biết → bỏ qua check này. */
        val availableQty: Double? = null,
    )

    private const val EPSILON = 1e-9

    fun validate(input: Input): Result {
        if (input.fromLocationId.isNullOrBlank()) {
            return Result.Invalid(Field.FROM_LOCATION, "Vui lòng chọn hoặc quét vị trí nguồn.")
        }
        if (!input.hasProductOrCode) {
            return Result.Invalid(Field.PRODUCT, "Vui lòng quét SKU/serial/lot hoặc chọn sản phẩm.")
        }
        val qty = input.quantity
        if (qty == null || qty <= 0.0) {
            return Result.Invalid(Field.QTY, "Số lượng phải lớn hơn 0.")
        }
        if (input.toLocationId.isNullOrBlank()) {
            return Result.Invalid(Field.TO_LOCATION, "Vui lòng chọn hoặc quét vị trí đích.")
        }
        if (input.fromLocationId == input.toLocationId) {
            return Result.Invalid(Field.TO_LOCATION, "Vị trí đích phải khác vị trí nguồn.")
        }
        val available = input.availableQty
        if (available != null && qty > available + EPSILON) {
            return Result.Invalid(
                Field.QTY,
                "Số lượng vượt tồn khả dụng (yêu cầu ${formatQty(qty)}, khả dụng ${formatQty(available)}).",
            )
        }
        return Result.Ok
    }

    fun formatQty(value: Double): String {
        return if (value % 1.0 == 0.0) {
            value.toLong().toString()
        } else {
            value.toString().trimEnd('0').trimEnd('.')
        }
    }
}
