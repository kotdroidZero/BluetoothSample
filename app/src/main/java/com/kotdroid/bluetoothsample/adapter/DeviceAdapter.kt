package com.kotdroid.bluetoothsample.adapter

import android.annotation.SuppressLint
import android.view.LayoutInflater
import android.view.View
import android.view.ViewGroup
import androidx.recyclerview.widget.RecyclerView
import com.kotdroid.bluetoothsample.R
import com.kotdroid.bluetoothsample.data.model.Device
import kotlinx.android.synthetic.main.device_item.view.*

class DeviceAdapter(
    private var mDeviceList: List<Device>,
    private val callback: DeviceCallback
) :
    RecyclerView.Adapter<DeviceAdapter.DeviceHolder>() {


    var connectedPosition = -1
    var disConnectedPosition = -1

    class DeviceHolder(itemView: View) : RecyclerView.ViewHolder(itemView)

    override fun onCreateViewHolder(parent: ViewGroup, viewType: Int): DeviceHolder {
        val view = LayoutInflater.from(parent.context)
            .inflate(R.layout.device_item, parent, false)
        return DeviceHolder(view)
    }

    @SuppressLint("SetTextI18n")
    override fun onBindViewHolder(holder: DeviceHolder, position: Int) {
        val device = mDeviceList[position]
        holder.itemView.apply {
            tvDeviceName.text = mDeviceList[position].name
            tvRange.text = mDeviceList[position].address
            tvPower.text = "${mDeviceList[position].signal2}(rssi: ${mDeviceList[position].signal})"
            tvStatus.text = mDeviceList[position].status

            btnConnect.setOnClickListener {
                connectedPosition = position
                callback.onConncetionClick(mDeviceList[position])
            }

            btnDisConnect.setOnClickListener {
                disConnectedPosition = position
                callback.onDisConncetionClick(mDeviceList[position])
            }
        }


    }

    override fun getItemCount(): Int {
        return mDeviceList.size
    }

    fun updateConnectedDevice() {
        mDeviceList[connectedPosition].status = "Connected"

        if (disConnectedPosition > -1)
            mDeviceList[connectedPosition].status = "Dis Connected"

        notifyDataSetChanged()
    }


    fun updateDisConnectedDevice() {
        mDeviceList[disConnectedPosition].status = "DisConnected"
        notifyDataSetChanged()
    }


    interface DeviceCallback {
        fun onConncetionClick(device: Device)
        fun onDisConncetionClick(device: Device)
    }
}