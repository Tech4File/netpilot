package app.netpilot.ui.licenses

import android.os.Bundle
import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.appcompat.app.AppCompatActivity
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import app.netpilot.databinding.ActivityLicensesBinding
import app.netpilot.databinding.ItemLicenseBinding

/** Settings → Open-source licenses. */
class LicensesActivity : AppCompatActivity() {

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        val binding = ActivityLicensesBinding.inflate(layoutInflater)
        setContentView(binding.root)
        binding.licensesList.layoutManager = LinearLayoutManager(this)
        binding.licensesList.adapter = LicensesAdapter(LicenseCatalog.entries)
    }

    private class LicensesAdapter(private val items: List<LicenseEntry>) :
        RecyclerView.Adapter<LicensesAdapter.Holder>() {

        override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): Holder =
            Holder(ItemLicenseBinding.inflate(LayoutInflater.from(parent.context), parent, false))

        override fun getItemCount(): Int = items.size

        override fun onBindViewHolder(holder: Holder, position: Int) {
            val item = items[position]
            holder.binding.licenseName.text = item.name
            holder.binding.licenseDesc.text = item.description
            holder.binding.licenseLicense.text =
                item.license + if (item.shipped) "" else "  ·  not shipped in the APK"
        }

        class Holder(val binding: ItemLicenseBinding) : RecyclerView.ViewHolder(binding.root)
    }
}
