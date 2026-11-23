package com.example.iqmemo.ui.calendar

import android.view.LayoutInflater
import android.view.ViewGroup
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.DiffUtil
import androidx.recyclerview.widget.ListAdapter
import androidx.recyclerview.widget.RecyclerView
import com.example.iqmemo.R
import com.example.iqmemo.databinding.ItemCalendarDayBinding

data class CalendarDayItem(
    val epochDay: Long,
    val dayOfMonth: Int,
    val inDisplayedMonth: Boolean,
    val selected: Boolean,
    val today: Boolean,
    val taskCount: Int
)

class CalendarDayAdapter(
    private val onDayClick: (CalendarDayItem) -> Unit
) : ListAdapter<CalendarDayItem, CalendarDayAdapter.DayViewHolder>(Diff) {

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DayViewHolder {
        val binding = ItemCalendarDayBinding.inflate(
            LayoutInflater.from(parent.context), parent, false
        )
        return DayViewHolder(binding)
    }

    override fun onBindViewHolder(holder: DayViewHolder, position: Int) {
        holder.bind(getItem(position))
    }

    inner class DayViewHolder(
        private val binding: ItemCalendarDayBinding
    ) : RecyclerView.ViewHolder(binding.root) {

        fun bind(item: CalendarDayItem) {
            val context = binding.root.context
            binding.tvDay.text = item.dayOfMonth.toString()
            binding.tvDay.alpha = if (item.inDisplayedMonth) 1f else 0.35f
            binding.tvDay.setTextColor(
                ContextCompat.getColor(
                    context,
                    if (item.selected) R.color.white else R.color.color_text_primary
                )
            )
            binding.tvDay.background = when {
                item.selected -> ContextCompat.getDrawable(context, R.drawable.bg_calendar_selected)
                item.today -> ContextCompat.getDrawable(context, R.drawable.bg_calendar_today)
                else -> null
            }
            binding.tvTaskCount.text = when {
                item.taskCount > 9 -> "9+"
                item.taskCount > 0 -> item.taskCount.toString()
                else -> ""
            }
            binding.tvTaskCount.visibility =
                if (item.taskCount > 0) android.view.View.VISIBLE else android.view.View.INVISIBLE
            binding.root.contentDescription = buildString {
                append(item.dayOfMonth)
                append("日")
                if (item.taskCount > 0) append("，${item.taskCount}项待办")
                if (item.selected) append("，已选择")
            }
            binding.root.setOnClickListener { onDayClick(item) }
        }
    }

    private object Diff : DiffUtil.ItemCallback<CalendarDayItem>() {
        override fun areItemsTheSame(oldItem: CalendarDayItem, newItem: CalendarDayItem): Boolean =
            oldItem.epochDay == newItem.epochDay

        override fun areContentsTheSame(oldItem: CalendarDayItem, newItem: CalendarDayItem): Boolean =
            oldItem == newItem
    }
}
