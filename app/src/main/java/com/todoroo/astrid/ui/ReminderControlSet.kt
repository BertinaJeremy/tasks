/*
 * Copyright (c) 2012 Todoroo Inc
 *
 * See the file "LICENSE" for the full license governing this code.
 */
package com.todoroo.astrid.ui

import android.app.Activity
import android.content.DialogInterface
import android.content.Intent
import android.graphics.Paint
import android.os.Bundle
import android.view.View
import android.view.ViewGroup
import android.widget.LinearLayout
import android.widget.TextView
import androidx.annotation.StringRes
import androidx.compose.material.AlertDialog
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.saveable.rememberSaveable
import androidx.compose.ui.ExperimentalComposeUiApi
import com.google.android.material.composethemeadapter.MdcTheme
import com.todoroo.andlib.utility.AndroidUtilities
import dagger.hilt.android.AndroidEntryPoint
import org.tasks.R
import org.tasks.activities.DateAndTimePickerActivity
import org.tasks.compose.AddReminderDialog
import org.tasks.compose.Constants
import org.tasks.data.Alarm
import org.tasks.data.Alarm.Companion.TYPE_DATE_TIME
import org.tasks.data.Alarm.Companion.TYPE_RANDOM
import org.tasks.data.Alarm.Companion.TYPE_REL_END
import org.tasks.data.Alarm.Companion.TYPE_REL_START
import org.tasks.data.Alarm.Companion.whenDue
import org.tasks.data.Alarm.Companion.whenOverdue
import org.tasks.data.Alarm.Companion.whenStarted
import org.tasks.databinding.ControlSetRemindersBinding
import org.tasks.date.DateTimeUtils
import org.tasks.dialogs.DialogBuilder
import org.tasks.dialogs.MyTimePickerDialog
import org.tasks.reminders.AlarmToString
import org.tasks.ui.TaskEditControlFragment
import java.util.concurrent.TimeUnit
import javax.inject.Inject


/**
 * Control set dealing with reminder settings
 *
 * @author Tim Su <tim></tim>@todoroo.com>
 */
@AndroidEntryPoint
class ReminderControlSet : TaskEditControlFragment() {
    @Inject lateinit var activity: Activity
    @Inject lateinit var dialogBuilder: DialogBuilder
    @Inject lateinit var alarmToString: AlarmToString

    private lateinit var alertContainer: LinearLayout
    private lateinit var mode: TextView
    
    private var randomControlSet: RandomReminderControlSet? = null
    private val showDialog = mutableStateOf(false)

    override fun createView(savedInstanceState: Bundle?) {
        showDialog.value = savedInstanceState?.getBoolean(DIALOG_VISIBLE) ?: false
        mode.paintFlags = mode.paintFlags or Paint.UNDERLINE_TEXT_FLAG
        when {
            viewModel.ringNonstop!! -> setRingMode(2)
            viewModel.ringFiveTimes!! -> setRingMode(1)
            else -> setRingMode(0)
        }
        viewModel.selectedAlarms.value.forEach(this::addAlarmRow)
    }

    override fun onSaveInstanceState(outState: Bundle) {
        super.onSaveInstanceState(outState)

        outState.putBoolean(DIALOG_VISIBLE, showDialog.value)
    }

    private fun onClickRingType() {
        val modes = resources.getStringArray(R.array.reminder_ring_modes)
        val ringMode = when {
            viewModel.ringNonstop == true -> 2
            viewModel.ringFiveTimes == true -> 1
            else -> 0
        }
        dialogBuilder
                .newDialog()
                .setSingleChoiceItems(modes, ringMode) { dialog: DialogInterface, which: Int ->
                    setRingMode(which)
                    dialog.dismiss()
                }
                .show()
    }

    private fun setRingMode(ringMode: Int) {
        viewModel.ringNonstop = ringMode == 2
        viewModel.ringFiveTimes = ringMode == 1
        mode.setText(getRingModeString(ringMode))
    }

    @StringRes
    private fun getRingModeString(ringMode: Int): Int {
        return when (ringMode) {
            2 -> R.string.ring_nonstop
            1 -> R.string.ring_five_times
            else -> R.string.ring_once
        }
    }

    private fun addAlarm(selected: String) {
        val id = viewModel.task?.id ?: 0
        when (selected) {
            getString(R.string.when_started) ->
                addAlarmRow(whenStarted(id))
            getString(R.string.when_due) ->
                addAlarmRow(whenDue(id))
            getString(R.string.when_overdue) ->
                addAlarmRow(whenOverdue(id))
            getString(R.string.randomly) ->
                addAlarmRow(Alarm(id, TimeUnit.DAYS.toMillis(14), TYPE_RANDOM))
            getString(R.string.pick_a_date_and_time) ->
                addNewAlarm()
            getString(R.string.repeat_option_custom) ->
                addCustomAlarm()
        }
    }

    private fun addAlarm() {
        val options = options
        if (options.size == 1) {
            addNewAlarm()
        } else {
            dialogBuilder
                    .newDialog()
                    .setItems(options) { dialog: DialogInterface, which: Int ->
                        addAlarm(options[which])
                        dialog.dismiss()
                    }
                    .show()
        }
    }

    @OptIn(ExperimentalComposeUiApi::class)
    override fun bind(parent: ViewGroup?) =
        ControlSetRemindersBinding.inflate(layoutInflater, parent, true).let {
            alertContainer = it.alertContainer
            mode = it.reminderAlarm.apply {
                setOnClickListener { onClickRingType() }
            }
            it.alarmsAdd.setOnClickListener { addAlarm() }
            it.dialogView.setContent {
                MdcTheme {
                    val openDialog = remember { showDialog }
                    val selectedInterval = rememberSaveable { mutableStateOf(15L as Long?) }
                    val selectedMultiplier = rememberSaveable { mutableStateOf(0) }
                    if (openDialog.value) {
                        AlertDialog(
                            onDismissRequest = {
                                openDialog.value = false
                                AndroidUtilities.hideKeyboard(activity)
                            },
                            text = {
                                AddReminderDialog.AddReminderDialog(
                                    openDialog,
                                    selectedInterval,
                                    selectedMultiplier,
                                )
                            },
                            confirmButton = {
                                Constants.TextButton(text = R.string.ok, onClick = {
                                    val multiplier = -1 * when (selectedMultiplier.value) {
                                            1 -> TimeUnit.HOURS.toMillis(1)
                                            2 -> TimeUnit.DAYS.toMillis(1)
                                            3 -> TimeUnit.DAYS.toMillis(7)
                                            else -> TimeUnit.MINUTES.toMillis(1)
                                    }

                                    selectedInterval.value?.let { i ->
                                        addAlarmRow(
                                            Alarm(
                                                viewModel.task?.id ?: 0L,
                                                i * multiplier,
                                                TYPE_REL_END
                                            )
                                        )
                                        openDialog.value = false
                                        AndroidUtilities.hideKeyboard(activity)
                                    }
                                })
                            },
                            dismissButton = {
                                Constants.TextButton(
                                    text = R.string.cancel,
                                    onClick = {
                                        openDialog.value = false
                                        AndroidUtilities.hideKeyboard(activity)
                                    })
                            },
                        )
                    } else {
                        selectedInterval.value = 15
                        selectedMultiplier.value = 0
                    }
                }
            }
            it.root
        }

    override val icon = R.drawable.ic_outline_notifications_24px

    override fun controlId() = TAG

    override fun onActivityResult(requestCode: Int, resultCode: Int, data: Intent?) {
        if (requestCode == REQUEST_NEW_ALARM) {
            if (resultCode == Activity.RESULT_OK) {
                val timestamp = data!!.getLongExtra(MyTimePickerDialog.EXTRA_TIMESTAMP, 0L)
                if (viewModel.selectedAlarms.value.none { it.type == TYPE_DATE_TIME && timestamp == it.time }) {
                    val alarm = Alarm(viewModel.task?.id ?: 0, timestamp, TYPE_DATE_TIME)
                    viewModel.selectedAlarms.value = viewModel.selectedAlarms.value.plus(alarm)
                    addAlarmRow(alarm)
                }
            }
        } else {
            super.onActivityResult(requestCode, resultCode, data)
        }
    }

    private fun addAlarmRow(alarm: Alarm) {
        val alarmRow = addAlarmRow(alarm) {
            if (alarm.type == TYPE_RANDOM) {
                viewModel.selectedAlarms.value =
                    viewModel.selectedAlarms.value.filterNot { it.type == TYPE_RANDOM }
                randomControlSet = null
            } else {
                viewModel.selectedAlarms.value =
                    viewModel.selectedAlarms.value.filterNot { it.same(alarm) }
            }
        }
        if (alarm.type == TYPE_RANDOM) {
            randomControlSet = RandomReminderControlSet(activity, alarmRow, alarm.time, viewModel)
        }
    }

    private fun addNewAlarm() {
        val intent = Intent(activity, DateAndTimePickerActivity::class.java)
        intent.putExtra(
                DateAndTimePickerActivity.EXTRA_TIMESTAMP, DateTimeUtils.newDateTime().noon().millis)
        startActivityForResult(intent, REQUEST_NEW_ALARM)
    }

    private fun addCustomAlarm() {
        showDialog.value = true
    }

    private fun addAlarmRow(alarm: Alarm, onRemove: View.OnClickListener): View {
        val alertItem = requireActivity().layoutInflater.inflate(R.layout.alarm_edit_row, null)
        alertContainer.addView(alertItem)
        addAlarmRow(alertItem, alarm, onRemove)
        return alertItem
    }

    private fun addAlarmRow(alertItem: View, alarm: Alarm, onRemove: View.OnClickListener?) {
        val display = alertItem.findViewById<TextView>(R.id.alarm_string)
        viewModel.selectedAlarms.value = viewModel.selectedAlarms.value.plus(alarm)
        display.text = alarmToString.toString(alarm)
        alertItem
                .findViewById<View>(R.id.clear)
                .setOnClickListener { v: View? ->
                    alertContainer.removeView(alertItem)
                    onRemove?.onClick(v)
                }
    }

    private val options: List<String>
        get() {
            val options: MutableList<String> = ArrayList()
            if (viewModel.selectedAlarms.value.find { it.type == TYPE_REL_START && it.time == 0L } == null) {
                options.add(getString(R.string.when_started))
            }
            if (viewModel.selectedAlarms.value.find { it.type == TYPE_REL_END && it.time == 0L } == null) {
                options.add(getString(R.string.when_due))
            }
            if (viewModel.selectedAlarms.value.find { it.type == TYPE_REL_END && it.time == TimeUnit.HOURS.toMillis(24) } == null) {
                options.add(getString(R.string.when_overdue))
            }
            if (randomControlSet == null) {
                options.add(getString(R.string.randomly))
            }
            options.add(getString(R.string.pick_a_date_and_time))
            options.add(getString(R.string.repeat_option_custom))
            return options
        }

    companion object {
        const val TAG = R.string.TEA_ctrl_reminders_pref
        private const val REQUEST_NEW_ALARM = 12152
        private const val DIALOG_VISIBLE = "dialog_visible"
    }
}

