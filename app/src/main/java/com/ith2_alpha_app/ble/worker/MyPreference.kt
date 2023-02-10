package com.ith2_alpha_app.ble.worker

import android.content.Context
import android.content.SharedPreferences
import androidx.appcompat.app.AppCompatActivity

class MyPreference(context: Context) {

    private var preference: SharedPreferences =
        context.getSharedPreferences("PREFERENCE_BLE", AppCompatActivity.MODE_PRIVATE)


    companion object {
        fun newInstance(context: Context) = MyPreference(context)
    }


    var address: String
        get() = preference.getString("address", "")!!
        set(address) = preference.edit().putString("address", address).apply()

    var checked: Boolean
        get() = preference.getBoolean("checked", true)
        set(checked) = preference.edit().putBoolean("checked", checked).apply()
}