package com.omarea.vtools.activities

import android.annotation.SuppressLint
import android.app.TimePickerDialog
import android.content.*
import android.os.BatteryManager
import android.os.Bundle
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.*
import com.omarea.Scene
import com.omarea.common.shared.FileWrite
import com.omarea.common.shell.KeepShellPublic
import com.omarea.common.ui.DialogHelper
import com.omarea.data.EventBus
import com.omarea.data.EventType
import com.omarea.data.GlobalStatus
import com.omarea.library.device.BatteryCapacity
import com.omarea.library.shell.BatteryUtils
import com.omarea.store.SpfConfig
import com.omarea.vtools.R
import com.omarea.vtools.dialogs.DialogNumberInput
import kotlinx.android.synthetic.main.activity_battery.*
import java.util.*

class ActivityBattery : ActivityBase() {
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_battery)
        setBackArrow()
        onViewCreated()
    }

    private fun onViewCreated() {
        battery_exec_options.onItemSelectedListener = object : AdapterView.OnItemSelectedListener {
            override fun onNothingSelected(parent: AdapterView<*>?) {}
            override fun onItemSelected(parent: AdapterView<*>?, view: View?, position: Int, id: Long) {
                val defaultValue = spf.getInt(SpfConfig.CHARGE_SPF_EXEC_MODE, SpfConfig.CHARGE_SPF_EXEC_MODE_DEFAULT)
                val currentValue = when (position) {
                    0 -> SpfConfig.CHARGE_SPF_EXEC_MODE_SPEED_DOWN
                    1 -> SpfConfig.CHARGE_SPF_EXEC_MODE_SPEED_UP
                    2 -> SpfConfig.CHARGE_SPF_EXEC_MODE_SPEED_FORCE
                    else -> SpfConfig.CHARGE_SPF_EXEC_MODE_SPEED_UP
                }
                if (currentValue != defaultValue) {
                    spf.edit().putInt(SpfConfig.CHARGE_SPF_EXEC_MODE, currentValue).apply()
                }
            }
        }

        val ResumeCharge = "sh " + FileWrite.writePrivateShellFile("addin/resume_charge.sh", "addin/resume_charge.sh", this)
        val spf = getSharedPreferences(SpfConfig.CHARGE_SPF, Context.MODE_PRIVATE)
        val batteryUtils = BatteryUtils(this)

        val qcSettingSuupport = batteryUtils.qcSettingSupport()
        val pdSettingSupport = batteryUtils.pdSupported()

        settings_qc.setOnClickListener {
            val checked = (it as CompoundButton).isChecked
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, checked).apply()
            if (checked) {
                notifyConfigChanged()
                Scene.toast(R.string.battery_auto_boot_desc, Toast.LENGTH_LONG)
            } else {
                Scene.toast(R.string.battery_qc_rehoot_desc, Toast.LENGTH_LONG)
            }
        }
        settings_qc.setOnCheckedChangeListener { _, isChecked ->
            battery_charge_speed_ext.visibility = if (isChecked) View.VISIBLE else View.GONE
            if (!isChecked) {
                battery_night_mode.isChecked = false
                spf.edit().putBoolean(SpfConfig.CHARGE_SPF_NIGHT_MODE, false).apply()
            }
        }
        settings_bp.setOnClickListener {
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_BP, settings_bp.isChecked).apply()
            if (!settings_bp.isChecked) {
                KeepShellPublic.doCmdSync(ResumeCharge)
            } else {
                notifyConfigChanged()
                Scene.toast(R.string.battery_auto_boot_desc, Toast.LENGTH_LONG)
            }
        }

        settings_bp_level.setOnSeekBarChangeListener(OnSeekBarChangeListener(Runnable {
            notifyConfigChanged()
        }, spf, battery_bp_level_desc))
        settings_qc_limit.setOnSeekBarChangeListener(OnSeekBarChangeListener2(Runnable {
            val level = spf.getInt(SpfConfig.CHARGE_SPF_QC_LIMIT, SpfConfig.CHARGE_SPF_QC_LIMIT_DEFAULT)
            if (spf.getBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false)) {
                batteryUtils.setChargeInputLimit(level, this)
            }
            notifyConfigChanged()
        }, spf, settings_qc_limit_desc))

        if (!qcSettingSuupport) {
            settings_qc.isEnabled = false
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_QC_BOOSTER, false).putBoolean(SpfConfig.CHARGE_SPF_NIGHT_MODE, false).apply()
            settings_qc_limit.isEnabled = false
            settings_qc_limit_current.visibility = View.GONE
        }

        if (!batteryUtils.bpSettingSupport()) {
            settings_bp.isEnabled = false
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_BP, false).apply()
            bp_cardview.visibility = View.GONE
        } else {
            bp_cardview.visibility = View.VISIBLE
        }

        if (pdSettingSupport) {
            settings_pd_support.visibility = View.VISIBLE
            settings_pd.setOnClickListener {
                val isChecked = (it as CompoundButton).isChecked
                batteryUtils.setAllowed(isChecked)
            }
            settings_pd.isChecked = batteryUtils.pdAllowed()
            settings_pd_state.text = if (batteryUtils.pdActive()) getString(R.string.battery_pd_active_1) else getString(R.string.battery_pd_active_0)
        } else {
            settings_pd_support.visibility = View.GONE
        }

        if (batteryUtils.stepChargeSupport()) {
            settings_step_charge.visibility = View.VISIBLE
            settings_step_charge_enabled.setOnClickListener {
                batteryUtils.setStepCharge((it as Checkable).isChecked)
            }
            settings_step_charge_enabled.isChecked = batteryUtils.getStepCharge()
        } else {
            settings_step_charge.visibility = View.GONE
        }

        btn_battery_history.setOnClickListener {
            try {
                val powerUsageIntent = Intent(Intent.ACTION_POWER_USAGE_SUMMARY)
                val resolveInfo = packageManager.resolveActivity(powerUsageIntent, 0)
                if (resolveInfo != null) {
                    startActivity(powerUsageIntent)
                }
            } catch (ex: Exception) {
            }
        }
        btn_battery_history_del.setOnClickListener {
            DialogHelper.confirm(this,
                    "需要重启",
                    "删除电池使用记录需要立即重启手机，是否继续？",
                    {
                        KeepShellPublic.doCmdSync(
                                "rm -f /data/system/batterystats-checkin.bin;" +
                                        "rm -f /data/system/batterystats-daily.xml;" +
                                        "rm -f /data/system/batterystats.bin;" +
                                        "rm -rf /data/system/battery-history;" +
                                        "rm -rf /data/charge_logger;" +
                                        "rm -rf /data/vendor/charge_logger;" +
                                        "sync;" +
                                        "sleep 2;" +
                                        "reboot;")
                    })
        }

        bp_disable_charge.setOnClickListener {
            KeepShellPublic.doCmdSync("sh " + FileWrite.writePrivateShellFile("addin/disable_charge.sh", "addin/disable_charge.sh", this.context))
            Scene.toast(R.string.battery_charge_disabled, Toast.LENGTH_LONG)
        }
        bp_enable_charge.setOnClickListener {
            KeepShellPublic.doCmdSync(ResumeCharge)
            Scene.toast(R.string.battery_charge_resumed, Toast.LENGTH_LONG)
        }

        battery_get_up.setText(minutes2Str(spf.getInt(SpfConfig.CHARGE_SPF_TIME_GET_UP, SpfConfig.CHARGE_SPF_TIME_GET_UP_DEFAULT)))
        battery_get_up.setOnClickListener {
            val nightModeGetUp = spf.getInt(SpfConfig.CHARGE_SPF_TIME_GET_UP, SpfConfig.CHARGE_SPF_TIME_GET_UP_DEFAULT)
            TimePickerDialog(this.context, TimePickerDialog.OnTimeSetListener { view, hourOfDay, minute ->
                spf.edit().putInt(SpfConfig.CHARGE_SPF_TIME_GET_UP, hourOfDay * 60 + minute).apply()
                battery_get_up.setText(String.format(getString(R.string.battery_night_mode_time), hourOfDay, minute))
                notifyConfigChanged()
            }, nightModeGetUp / 60, nightModeGetUp % 60, true).show()
        }

        battery_sleep.setText(minutes2Str(spf.getInt(SpfConfig.CHARGE_SPF_TIME_SLEEP, SpfConfig.CHARGE_SPF_TIME_SLEEP_DEFAULT)))
        battery_sleep.setOnClickListener {
            val nightModeSleep = spf.getInt(SpfConfig.CHARGE_SPF_TIME_SLEEP, SpfConfig.CHARGE_SPF_TIME_SLEEP_DEFAULT)
            TimePickerDialog(this.context, TimePickerDialog.OnTimeSetListener { _, hourOfDay, minute ->
                spf.edit().putInt(SpfConfig.CHARGE_SPF_TIME_SLEEP, hourOfDay * 60 + minute).apply()
                battery_sleep.setText(String.format(getString(R.string.battery_night_mode_time), hourOfDay, minute))
                notifyConfigChanged()
            }, nightModeSleep / 60, nightModeSleep % 60, true).show()
        }
        battery_night_mode.isChecked = spf.getBoolean(SpfConfig.CHARGE_SPF_NIGHT_MODE, SpfConfig.CHARGE_SPF_NIGHT_MODE_DEFAULT)
        battery_night_mode.setOnCheckedChangeListener { _, isChecked ->
            spf.edit().putBoolean(SpfConfig.CHARGE_SPF_NIGHT_MODE, isChecked).apply()
            notifyConfigChanged()
        }

        val bm = getSystemService(BATTERY_SERVICE) as BatteryManager
        battery_level.text = String.format(Locale.getDefault(), "%d%%", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CAPACITY))
        battery_voltage.text = String.format(Locale.getDefault(), "%.1fmV", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_VOLTAGE) / 1000f)
        battery_charge.text = String.format(Locale.getDefault(), "%.2fA", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000000f)

        val handler = Handler(Looper.getMainLooper())
        val runnable = object : Runnable {
            override fun run() {
                battery_charge.text = String.format(Locale.getDefault(), "%.2fA", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_CURRENT_NOW) / 1000000f)
                handler.postDelayed(this, 2000)
            }
        }
        handler.postDelayed(runnable, 2000)

        battery_temp.text = String.format(Locale.getDefault(), "%.1f°C", bm.getIntProperty(BatteryManager.BATTERY_PROPERTY_TEMPERATURE) / 10f)
        val batteryInfo = batteryUtils.getBatteryInfo()
        battery_technology.text = batteryInfo.technology
        battery_health.text = batteryUtils.getBatteryHealth(batteryInfo.health)
        battery_charge_full.text = String.format(Locale.getDefault(), "%d", batteryUtils.getChargeFull())

        battery_forgery_ratio.setOnClickListener { batteryForgeryRatio() }
        battery_forgery_charge_full.setOnClickListener { batteryForgeryChargeFull() }
    }

    private fun batteryForgeryRatio() {
        DialogNumberInput(this).showDialog(object : DialogNumberInput.DialogNumberInputRequest {
            override var min: Int = -1
            override var max: Int = 100
            override var default: Int = batteryUtils.getCapacity().toInt() // Ensure this value is Int

            override fun onApply(value: Int) {
                batteryUtils.setCapacity(value)
                updateBatteryForgery()
            }
        })
    }

    private fun batteryForgeryChargeFull() {
        DialogNumberInput(this).showDialog(object : DialogNumberInput.DialogNumberInputRequest {
            override var min: Int = 1000
            override var max: Int = 20000
            override var default: Int = batteryUtils.getChargeFull().toInt() // Ensure this value is Int

            override fun onApply(value: Int) {
                batteryUtils.setChargeFull(value)
                updateBatteryForgery()
            }
        })
    }

    private fun updateBatteryForgery() {
        battery_level.text = String.format(Locale.getDefault(), "%d%%", batteryUtils.getCapacity())
        battery_charge_full.text = String.format(Locale.getDefault(), "%d", batteryUtils.getChargeFull())
    }

    override fun onResume() {
        super.onResume()
        battery_charge_full.text = String.format(Locale.getDefault(), "%d", batteryUtils.getChargeFull())
        battery_level.text = String.format(Locale.getDefault(), "%d%%", batteryUtils.getCapacity())
    }

    private fun notifyConfigChanged() {
        EventBus.publish(EventType.CONFIG_CHANGED)
    }

    private fun minutes2Str(minutes: Int): String {
        val hours = minutes / 60
        val mins = minutes % 60
        return String.format(Locale.getDefault(), "%02d:%02d", hours, mins)
    }
}
