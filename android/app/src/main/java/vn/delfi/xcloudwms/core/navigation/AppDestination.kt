package vn.delfi.xcloudwms.core.navigation

enum class AppDestination(val route: String) {
    Splash("splash"),
    Login("login"),
    WarehouseSwitch("warehouse_switch"),
    NoWarehouse("no_warehouse"),
    Home("home"),
    DeviceHardwareInfo("device_hardware_info"),
    ScannerTest("scanner_test"),
    StockLookup("stock_lookup"),
    Putaway("putaway"),
    GoodsIssueList("goods_issue"),
    GoodsIssuePick("goods_issue/{headerId}"),
    GoodsReceiptList("goods_receipt"),
    GoodsReceiptReceive("goods_receipt/{headerId}"),
    ;

    companion object {
        fun goodsIssuePickRoute(headerId: String): String = "goods_issue/$headerId"

        fun goodsReceiptReceiveRoute(headerId: String): String = "goods_receipt/$headerId"
    }
}
