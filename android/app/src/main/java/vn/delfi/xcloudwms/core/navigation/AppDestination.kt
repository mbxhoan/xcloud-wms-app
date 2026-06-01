package vn.delfi.xcloudwms.core.navigation

enum class AppDestination(val route: String) {
    Splash("splash"),
    Login("login"),
    WarehouseSwitch("warehouse_switch"),
    NoWarehouse("no_warehouse"),
    Home("home"),
    ScannerTest("scanner_test"),
    StockLookup("stock_lookup"),
    Putaway("putaway"),
}
