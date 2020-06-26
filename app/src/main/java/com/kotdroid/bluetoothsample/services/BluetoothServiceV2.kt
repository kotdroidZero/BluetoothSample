package com.kotdroid.bluetoothsample.services

/**
 * @author Kotdroid
 * 17/2/20
 */

import android.app.*
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Binder
import android.os.Build
import android.os.Handler
import android.os.IBinder
import android.util.Log
import androidx.core.app.NotificationCompat
import com.kotdroid.bluetoothsample.R
import com.kotdroid.bluetoothsample.activity.MainActivity
import kotlin.math.max
import kotlin.math.min


class BluetoothServiceV2 : Service() {

    //region Variables
    val CHANNEL_ID = "Bluetooth Notification"
    private val serviceBinder = BToothServiceBinder()
    private var bluetoothReceiverRegistered: Boolean = false
    private var mBluetoothAdapter: BluetoothAdapter? = null
    val NOTIF_ID = 4

    private val handler = Handler()
    private var scanTask: Runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 500)
            scanBluetooth()
        }
    }

    //endregion

    //region Service Lifecycle Methods
    override fun onBind(intent: Intent?): IBinder? {
        return serviceBinder
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("onCreateService", "Service created")

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("onStartCommand", "Service started")

        createNotificationChannel()

        handler.post(scanTask)

        startForeground(NOTIF_ID, getMyActivityNotification(""))
        //do heavy work on a background thread
        //stopSelf();


        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.e("onDestroy", "Service Destroyed")
        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
        }
        stopForeground(true)
        stopSelf()
        super.onDestroy()

    }
    //endregion

    //region Local Binder Class
    internal inner class BToothServiceBinder : Binder() {
        val service: BluetoothServiceV2
            get() = this@BluetoothServiceV2
    }
    //endregion


    //region Notification
    private fun createNotificationChannel() {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            val serviceChannel = NotificationChannel(
                CHANNEL_ID,
                "Foreground Service Channel",
                NotificationManager.IMPORTANCE_DEFAULT
            )
            val manager = getSystemService(NotificationManager::class.java)
            manager!!.createNotificationChannel(serviceChannel)
        }
    }
    //endregion

    private fun updateNotification(text: String) {

        val notification: Notification = getMyActivityNotification(text)

        val mNotificationManager =
            getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager
        mNotificationManager.notify(NOTIF_ID, notification)
    }


    private fun getMyActivityNotification(text: String): Notification {
        val notificationIntent = Intent(this, MainActivity::class.java)
//        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TASK)
//        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_CLEAR_TOP)
//        notificationIntent.addFlags(Intent.FLAG_ACTIVITY_NEW_TASK)

        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)

        return NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Bluetooth Sample")
            .setContentText(text)
            .setOnlyAlertOnce(true)
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
    }


    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d("tag", "onReceive: Execute")
            val action = intent.action

            if (BluetoothDevice.ACTION_FOUND == action || BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                val deviceName = device!!.name
                val paired = device.bondState == BluetoothDevice.BOND_BONDED
                val deviceAddress = device.address
                val deviceRSSI = intent.extras!!.getShort(BluetoothDevice.EXTRA_RSSI, 0.toShort())
//                val mDevice = Device(deviceName, paired, deviceAddress, deviceRSSI, device)


                val strength = min(max(2 * (deviceRSSI + 100), 0), 100)

                if (strength < 95) {
                    updateNotification("You moved away from the network.\n Signal Strength : $strength")
                } else {
                    updateNotification("You are within 1 meter range.\n Signal Strength : $strength")
                }
            }
        }

    }


    private fun scanBluetooth() {
        bluetoothReceiverRegistered = true
        val filter = IntentFilter()
        filter.addAction(BluetoothDevice.ACTION_FOUND)
        filter.addAction(BluetoothDevice.ACTION_ACL_CONNECTED)
        filter.addAction(BluetoothDevice.ACTION_CLASS_CHANGED)
        registerReceiver(bluetoothReceiver, filter)
        if (mBluetoothAdapter!!.isDiscovering) {
            mBluetoothAdapter!!.cancelDiscovery()
        }
        mBluetoothAdapter!!.startDiscovery()
    }
}