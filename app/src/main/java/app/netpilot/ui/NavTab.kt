package app.netpilot.ui

import androidx.annotation.DrawableRes
import androidx.annotation.StringRes
import app.netpilot.R

/** The four top-level sections. Rail on TV, bottom bar on touch devices. */
enum class NavTab(@StringRes val titleRes: Int, @DrawableRes val iconRes: Int) {
    DASHBOARD(R.string.tab_dashboard, R.drawable.ic_dashboard),
    PRIVATE_DNS(R.string.tab_dns, R.drawable.ic_dns),
    VPN(R.string.tab_vpn, R.drawable.ic_vpn),
    SETTINGS(R.string.tab_settings, R.drawable.ic_settings),
}
