package app.netpilot.ui.vpn

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.RecyclerView
import app.netpilot.R
import app.netpilot.core.model.VpnProfile
import app.netpilot.databinding.ItemVpnProfileBinding

/** VPN profiles list. Row tap = connect/disconnect; trailing actions edit/delete. */
class VpnProfilesAdapter(
    private val onRowClick: (VpnProfile) -> Unit,
    private val onEdit: (VpnProfile) -> Unit,
    private val onDelete: (VpnProfile) -> Unit,
) : RecyclerView.Adapter<VpnProfilesAdapter.Holder>() {

    private val items = mutableListOf<VpnProfile>()
    private var connectedId: String? = null
    private var connectingId: String? = null

    fun submit(profiles: List<VpnProfile>, connected: String?, connecting: String? = null) {
        items.clear()
        items += profiles
        connectedId = connected
        connectingId = connecting
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemVpnProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemVpnProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: VpnProfile) {
            val context = binding.root.context
            binding.profileName.text = profile.name
            binding.typeBadge.setText(profile.type.labelRes)
            binding.profileHost.text = "${profile.serverHost}:${profile.serverPort}"
            val connected = profile.id == connectedId
            val connecting = !connected && profile.id == connectingId
            binding.profileState.setText(
                when {
                    connected -> R.string.vpn_status_connected
                    connecting -> R.string.vpn_status_connecting
                    else -> R.string.vpn_status_disconnected
                },
            )
            binding.profileState.setTextColor(
                ContextCompat.getColor(
                    context,
                    when {
                        connected -> R.color.status_success
                        connecting -> R.color.status_info
                        else -> R.color.on_surface_variant
                    },
                ),
            )
            binding.profileRoot.setOnClickListener { onRowClick(profile) }
            binding.btnEdit.setOnClickListener { onEdit(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
            binding.btnEdit.nextFocusLeftId = R.id.nav_rail
        }
    }
}
