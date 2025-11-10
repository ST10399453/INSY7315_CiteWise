package com.example.citewise_mobile

import android.content.Context
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import android.widget.BaseAdapter
import android.widget.TextView
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

data class DayCell(val date: Date?, val enabled: Boolean)

class MonthGridAdapter(
    private val context: Context,
    monthCal: Calendar,
    private val minDate: Date,
    private val onClickDay: (Date) -> Unit
) : BaseAdapter() {

    private val inflater = LayoutInflater.from(context)
    private val cells: List<DayCell>
    private val today: Date = CustomDatePickerDialog.stripTime(Date())

    init {
        val cal = Calendar.getInstance().apply {
            time = monthCal.time
            set(Calendar.DAY_OF_MONTH, 1)
        }

        val firstDayOfWeek = Calendar.MONDAY // your header starts with Monday
        val firstDow = cal.get(Calendar.DAY_OF_WEEK)
        val shift = ((firstDow - firstDayOfWeek + 7) % 7) // leading blanks

        val daysInMonth = cal.getActualMaximum(Calendar.DAY_OF_MONTH)

        val list = ArrayList<DayCell>(42)
        repeat(shift) { list.add(DayCell(null, false)) }

        for (d in 1..daysInMonth) {
            cal.set(Calendar.DAY_OF_MONTH, d)
            val date = CustomDatePickerDialog.stripTime(cal.time)
            val enabled = !date.before(CustomDatePickerDialog.stripTime(minDate))
            list.add(DayCell(date, enabled))
        }

        // trailing blanks to complete the grid to a multiple of 7
        while (list.size % 7 != 0) list.add(DayCell(null, false))

        cells = list
    }

    override fun getCount(): Int = cells.size
    override fun getItem(position: Int): Any = cells[position]
    override fun getItemId(position: Int): Long = position.toLong()

    override fun getView(position: Int, convertView: View?, parent: ViewGroup?): View {
        val view = convertView ?: inflater.inflate(R.layout.item_day, parent, false)
        val tv = view.findViewById<TextView>(R.id.dayText)

        val cell = cells[position]
        if (cell.date == null) {
            tv.text = ""
            tv.isEnabled = false
            tv.isSelected = false
            tv.alpha = 0f
            return view
        }

        val cal = Calendar.getInstance().apply { time = cell.date }
        tv.text = cal.get(Calendar.DAY_OF_MONTH).toString()

        tv.isEnabled = cell.enabled
        tv.alpha = if (cell.enabled) 1f else 0.35f

        // Selected look for "today" (optional): remove if not needed
        tv.isSelected = sameDay(cell.date, today)
        tv.setTextColor(
            if (tv.isSelected) ContextCompat.getColor(context, android.R.color.white)
            else ContextCompat.getColor(context, R.color.text_dark)
        )

        tv.setOnClickListener {
            if (cell.enabled) onClickDay(cell.date)
        }
        return view
    }

    private fun sameDay(a: Date, b: Date): Boolean {
        val ca = Calendar.getInstance().apply { time = a }
        val cb = Calendar.getInstance().apply { time = b }
        return ca.get(Calendar.YEAR) == cb.get(Calendar.YEAR) &&
                ca.get(Calendar.DAY_OF_YEAR) == cb.get(Calendar.DAY_OF_YEAR)
    }
}
