package com.kotdroid.bluetoothsample.services

/**
 * @author Kotdroid
 * 17/2/20
 */
import android.app.NotificationChannel
import android.app.NotificationManager
import android.app.PendingIntent
import android.app.Service
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.Context
import android.content.Intent
import android.graphics.PixelFormat
import android.os.Binder
import android.os.Build
import android.os.IBinder
import android.util.Log
import android.view.Gravity
import android.view.LayoutInflater
import android.view.View
import android.view.WindowManager
import androidx.appcompat.app.AlertDialog
import androidx.core.app.NotificationCompat
import com.kotdroid.bluetoothsample.R
import com.kotdroid.bluetoothsample.activity.MainActivity
import kotlin.math.max
import kotlin.math.min
import kotlin.system.exitProcess


class BluetoothService : Service() {

    //region Variables
    val CHANNEL_ID = "Bluetooth Notification"
    private var myView: View? = null
    private val serviceBinder = BToothServiceBinder()
    private var wm: WindowManager? = null
    private var params: WindowManager.LayoutParams? = null

    //endregion

    //region Service Lifecycle Methods
    override fun onBind(intent: Intent?): IBinder? {
        return serviceBinder
    }

    override fun onCreate() {
        super.onCreate()
        Log.e("onCreateService", "Service created")

        val LAYOUT_FLAG: Int = if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.O) {
            WindowManager.LayoutParams.TYPE_APPLICATION_OVERLAY
        } else {
            WindowManager.LayoutParams.TYPE_PHONE
        }


        params = WindowManager.LayoutParams(
            WindowManager.LayoutParams.MATCH_PARENT,
            WindowManager.LayoutParams.MATCH_PARENT,
            LAYOUT_FLAG, WindowManager.LayoutParams.FLAG_NOT_TOUCH_MODAL
                    or WindowManager.LayoutParams.FLAG_WATCH_OUTSIDE_TOUCH
                    or WindowManager.LayoutParams.FLAG_NOT_FOCUSABLE, PixelFormat.TRANSLUCENT
        )
        params?.gravity = Gravity.TOP or Gravity.LEFT
        wm = getSystemService(Context.WINDOW_SERVICE) as WindowManager

    }

    override fun onStartCommand(intent: Intent?, flags: Int, startId: Int): Int {
        Log.e("onStartCommand", "Service started")

        createNotificationChannel()


        val notificationIntent = Intent(this, MainActivity::class.java)
        val pendingIntent = PendingIntent.getActivity(this, 0, notificationIntent, 0)
        val notification = NotificationCompat.Builder(this, CHANNEL_ID)
            .setContentTitle("Blur Activity Running")
            .setContentText("Hey Dude! Keep watching the videos by keeping the device near you.")
            .setSmallIcon(R.mipmap.ic_launcher)
            .setContentIntent(pendingIntent)
            .build()
        startForeground(1, notification)

        //do heavy work on a background thread
        //stopSelf();


        return START_NOT_STICKY
    }

    override fun onDestroy() {
        Log.e("onDestroy", "Service Destroyed")
        stopForeground(true)
        stopSelf()
        super.onDestroy()

    }
    //endregion

    //region Local Binder Class
    internal inner class BToothServiceBinder : Binder() {
        val service: BluetoothService
            get() = this@BluetoothService
    }
    //endregion

    //region BlurryViews Methods
    private fun inflateView() {
        if (myView != null) {
            return
        }
        val inflater = getSystemService(Context.LAYOUT_INFLATER_SERVICE) as LayoutInflater
        myView = inflater.inflate(R.layout.layout_blurred_view, null)
        // Add layout to window manager

        wm?.addView(myView, params)
        // manageBlurView(view!!, flLayout!!)
    }

    private fun removeBlurryView() {
        (getSystemService(Context.NOTIFICATION_SERVICE) as NotificationManager).cancel(0)
        if (myView != null) {
            (getSystemService(Context.WINDOW_SERVICE) as WindowManager).removeView(myView)
            myView = null
        }
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

    private fun getSignalStrength(bluetoothServiceIntent: Intent) {
        val action = bluetoothServiceIntent.action
        if (BluetoothDevice.ACTION_FOUND == action ||
            BluetoothDevice.ACTION_ACL_CONNECTED == action ||
            BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action ||
            BluetoothAdapter.ACTION_STATE_CHANGED == action
        ) {
            val rssi = bluetoothServiceIntent.getShortExtra(
                BluetoothDevice.EXTRA_RSSI, Short.MIN_VALUE
            ).toInt()

            val mSignalStrength = min(max(2 * (rssi + 100), 0), 100).toDouble()


            Log.i("strength", mSignalStrength.toString())

            if (mSignalStrength < 75.0) {
                inflateView()
            } else {
                removeBlurryView()
            }
        }

    }

    private fun showAlertAndExit() {
        AlertDialog.Builder(this)
            .setTitle(getString(R.string.not_compatible))
            .setMessage(getString(R.string.no_support))
            .setPositiveButton("Exit") { _, _ -> exitProcess(0) }
            .show()
    }

    fun toggleBlurryView(isBlurry: Boolean) {
        if (!isBlurry) {
            inflateView()
        } else {
            removeBlurryView()
        }
    }


}