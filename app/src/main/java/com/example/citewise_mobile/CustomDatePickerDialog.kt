package com.example.citewise_mobile

import android.app.Dialog
import android.content.Context
import android.os.Bundle
import android.view.LayoutInflater
import android.view.Window
import android.widget.GridView
import android.widget.ImageButton
import android.widget.TextView
import androidx.annotation.ColorInt
import androidx.core.content.ContextCompat
import java.text.SimpleDateFormat
import java.util.Calendar
import java.util.Date
import java.util.Locale

class CustomDatePickerDialog(
    context: Context,
    private val onPicked: (Date) -> Unit,
    private val minDate: Date = stripTime(Date()) // default: today
) : Dialog(context) {

    private lateinit var monthYearText: TextView
    private lateinit var prevBtn: ImageButton
    private lateinit var nextBtn: ImageButton
    private lateinit var grid: GridView

    private val cal: Calendar = Calendar.getInstance().apply {
        time = maxOf(stripTime(Date()), stripTime(minDate))
        set(Calendar.DAY_OF_MONTH, 1)
    }

    private val today = stripTime(Date())
    private val minMonthCal = Calendar.getInstance().apply {
        time = stripTime(minDate)
        set(Calendar.DAY_OF_MONTH, 1)
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        requestWindowFeature(Window.FEATURE_NO_TITLE)
        setContentView(LayoutInflater.from(context).inflate(R.layout.dialog_custom_datepicker, null))

        monthYearText = findViewById(R.id.monthYearText)
        prevBtn = findViewById(R.id.prevMonth)
        nextBtn = findViewById(R.id.nextMonth)
        grid = findViewById(R.id.calendarGrid)

        updateHeader()
        bindGrid()

        prevBtn.setOnClickListener {
            cal.add(Calendar.MONTH, -1)
            updateHeader()
            bindGrid()
        }
        nextBtn.setOnClickListener {
            cal.add(Calendar.MONTH, 1)
            updateHeader()
            bindGrid()
        }
    }

    private fun bindGrid() {
        val adapter = MonthGridAdapter(
            context = context,
            monthCal = cal,
            minDate = minDate,
            onClickDay = { date ->
                onPicked(date)
                dismiss()
            }
        )
        grid.adapter = adapter
        updatePrevEnabled()
    }

    private fun updateHeader() {
        val fmt = SimpleDateFormat("MMMM yyyy", Locale.getDefault())
        monthYearText.text = fmt.format(cal.time)
    }

    private fun updatePrevEnabled() {
        val cur = Calendar.getInstance().apply {
            time = cal.time
            set(Calendar.DAY_OF_MONTH, 1)
        }
        prevBtn.isEnabled = !isSameMonth(cur, minMonthCal)
        // style the disabled state tint (optional)
        if (!prevBtn.isEnabled) {
            @ColorInt val tint = ContextCompat.getColor(context, android.R.color.darker_gray)
            prevBtn.imageTintList = android.content.res.ColorStateList.valueOf(tint)
        } else {
            prevBtn.imageTintList = null
        }
    }

    private fun isSameMonth(a: Calendar, b: Calendar): Boolean =
        a.get(Calendar.YEAR) == b.get(Calendar.YEAR) &&
                a.get(Calendar.MONTH) == b.get(Calendar.MONTH)

    companion object {
        fun stripTime(date: Date): Date {
            val c = Calendar.getInstance().apply {
                time = date
                set(Calendar.HOUR_OF_DAY, 0)
                set(Calendar.MINUTE, 0)
                set(Calendar.SECOND, 0)
                set(Calendar.MILLISECOND, 0)
            }
            return c.time
        }
    }
}
