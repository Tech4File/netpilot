package app.netpilot.ui.dns

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import app.netpilot.core.model.DnsProfile
import app.netpilot.databinding.ItemDnsProfileBinding

/** Profiles list. Row tap = activate/deactivate; trailing actions edit/delete. */
class DnsProfilesAdapter(
    private val onRowClick: (DnsProfile) -> Unit,
    private val onEdit: (DnsProfile) -> Unit,
    private val onDelete: (DnsProfile) -> Unit,
) : RecyclerView.Adapter<DnsProfilesAdapter.Holder>() {

    private val items = mutableListOf<DnsProfile>()
    private var activeId: String? = null

    fun submit(profiles: List<DnsProfile>, active: String?) {
        items.clear()
        items += profiles
        activeId = active
        notifyDataSetChanged()
    }

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
        Holder(ItemDnsProfileBinding.inflate(LayoutInflater.from(parent.context), parent, false))

    override fun getItemCount(): Int = items.size

    override fun onBindViewHolder(holder: Holder, position: Int) = holder.bind(items[position])

    inner class Holder(private val binding: ItemDnsProfileBinding) :
        RecyclerView.ViewHolder(binding.root) {

        fun bind(profile: DnsProfile) {
            binding.profileName.text = profile.name
            binding.profileHost.text = profile.hostname
            binding.profileRadio.isSelected = profile.id == activeId
            binding.profileRoot.setOnClickListener { onRowClick(profile) }
            binding.btnEdit.setOnClickListener { onEdit(profile) }
            binding.btnDelete.setOnClickListener { onDelete(profile) }
            // D-pad: left arrow exits to the nav rail, right arrow reaches the row actions.
            binding.btnEdit.nextFocusLeftId = app.netpilot.R.id.nav_rail
        }
    }
}
