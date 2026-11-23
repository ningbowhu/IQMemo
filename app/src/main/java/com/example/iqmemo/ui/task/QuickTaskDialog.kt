package com.example.iqmemo.ui.task

import android.app.DatePickerDialog
import android.content.Context
import android.content.res.ColorStateList
import android.view.LayoutInflater
import android.view.View
import androidx.core.content.ContextCompat
import com.example.iqmemo.R
import com.example.iqmemo.databinding.DialogQuickTaskBinding
import com.example.iqmemo.utils.QuickDatePreset
import com.example.iqmemo.utils.TaskDateUtils
import com.google.android.material.bottomsheet.BottomSheetDialog
import com.google.android.material.button.MaterialButton
import java.time.LocalDate

object QuickTaskDialog {

    enum class CreationType { TEXT, VOICE }

    fun showCreate(
        context: Context,
        initialEpochDay: Long?,
        onCreate: (CreationType, Long?) -> Unit
    ) {
        show(
            context = context,
            initialEpochDay = initialEpochDay,
            createMode = true,
            onCreate = onCreate,
            onConfirm = {}
        )
    }

    fun showConvert(
        context: Context,
        initialEpochDay: Long? = LocalDate.now().toEpochDay(),
        onConfirm: (Long?) -> Unit
    ) {
        show(
            context = context,
            initialEpochDay = initialEpochDay,
            createMode = false,
            onCreate = { _, _ -> },
            onConfirm = onConfirm
        )
    }

    private fun show(
        context: Context,
        initialEpochDay: Long?,
        createMode: Boolean,
        onCreate: (CreationType, Long?) -> Unit,
        onConfirm: (Long?) -> Unit
    ) {
        val dialog = BottomSheetDialog(context)
        val binding = DialogQuickTaskBinding.inflate(LayoutInflater.from(context))
        val today = LocalDate.now()
        var selectedEpochDay = initialEpochDay

        fun render() {
            binding.tvQuickTaskSelection.text = context.getString(
                R.string.quick_task_current_target,
                TaskDateUtils.formatQuickTarget(selectedEpochDay)
            )
            val selectedPreset = TaskDateUtils.classifyQuickDate(selectedEpochDay, today)
            listOf(
                binding.btnQuickUndated to QuickDatePreset.UNDATED,
                binding.btnQuickToday to QuickDatePreset.TODAY,
                binding.btnQuickTomorrow to QuickDatePreset.TOMORROW,
                binding.btnQuickChooseDate to QuickDatePreset.CUSTOM
            ).forEach { (button, preset) ->
                styleDateButton(context, button, preset == selectedPreset)
            }
            binding.btnQuickChooseDate.text = if (selectedPreset == QuickDatePreset.CUSTOM) {
                TaskDateUtils.formatFilterDate(requireNotNull(selectedEpochDay))
            } else {
                context.getString(R.string.quick_choose_date)
            }
        }

        fun selectPreset(preset: QuickDatePreset) {
            selectedEpochDay = TaskDateUtils.resolveQuickDate(preset, today, selectedEpochDay)
            render()
        }

        binding.tvQuickTaskTitle.setText(
            if (createMode) R.string.quick_task_create_title else R.string.quick_task_convert_title
        )
        binding.tvQuickTaskMethod.visibility = if (createMode) View.VISIBLE else View.GONE
        binding.layoutQuickCreateActions.visibility = if (createMode) View.VISIBLE else View.GONE
        binding.btnQuickConfirm.visibility = if (createMode) View.GONE else View.VISIBLE

        binding.btnQuickUndated.setOnClickListener { selectPreset(QuickDatePreset.UNDATED) }
        binding.btnQuickToday.setOnClickListener { selectPreset(QuickDatePreset.TODAY) }
        binding.btnQuickTomorrow.setOnClickListener { selectPreset(QuickDatePreset.TOMORROW) }
        binding.btnQuickChooseDate.setOnClickListener {
            val initial = selectedEpochDay?.let(LocalDate::ofEpochDay) ?: today
            DatePickerDialog(
                context,
                { _, year, month, dayOfMonth ->
                    selectedEpochDay = LocalDate.of(year, month + 1, dayOfMonth).toEpochDay()
                    render()
                },
                initial.year,
                initial.monthValue - 1,
                initial.dayOfMonth
            ).show()
        }
        binding.btnQuickTextTask.setOnClickListener {
            dialog.dismiss()
            onCreate(CreationType.TEXT, selectedEpochDay)
        }
        binding.btnQuickVoiceTask.setOnClickListener {
            dialog.dismiss()
            onCreate(CreationType.VOICE, selectedEpochDay)
        }
        binding.btnQuickConfirm.setOnClickListener {
            dialog.dismiss()
            onConfirm(selectedEpochDay)
        }
        binding.btnQuickCancel.setOnClickListener { dialog.dismiss() }

        render()
        dialog.setContentView(binding.root)
        dialog.show()
    }

    private fun styleDateButton(context: Context, button: MaterialButton, selected: Boolean) {
        val accent = ContextCompat.getColor(context, R.color.color_accent)
        val background = ContextCompat.getColor(
            context,
            if (selected) R.color.color_accent else R.color.color_card
        )
        val text = ContextCompat.getColor(
            context,
            if (selected) R.color.white else R.color.color_accent
        )
        button.backgroundTintList = ColorStateList.valueOf(background)
        button.strokeColor = ColorStateList.valueOf(accent)
        button.setTextColor(text)
    }
}
