package com.example.citewise_mobile.adapters

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.ArrayAdapter
import android.widget.ImageView
import android.widget.Spinner
import android.widget.TextView
import com.example.citewise_mobile.R   // use *your* app’s R, not android.R

class ServiceSpinnerAdapter(
    context: Context,
    private val spinner: Spinner,
    private val items: List<String>
) : ArrayAdapter<String>(context, 0, items) {

    private val inflater = LayoutInflater.from(context)

    override fun getView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_service_selected, parent, false)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        tv.text = items.getOrNull(position) ?: ""
        return view
    }

    override fun getDropDownView(position: Int, convertView: View?, parent: ViewGroup): View {
        val view = convertView ?: inflater.inflate(R.layout.item_service_dropdown, parent, false)
        val tv = view.findViewById<TextView>(android.R.id.text1)
        val check = view.findViewById<ImageView>(R.id.checkIcon)

        tv.text = items[position]
        check.visibility = if (position == spinner.selectedItemPosition) View.VISIBLE else View.GONE
        return view
    }

    override fun getCount(): Int = items.size
    override fun getItem(position: Int): String? = items[position]
}
