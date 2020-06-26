package com.kotdroid.bluetoothsample.activity

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.bluetooth.BluetoothManager
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import android.os.Handler
import android.util.Log
import android.widget.Toast
import androidx.appcompat.app.AppCompatActivity
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import androidx.recyclerview.widget.LinearLayoutManager
import androidx.recyclerview.widget.RecyclerView
import androidx.swiperefreshlayout.widget.SwipeRefreshLayout
import com.kotdroid.bluetoothsample.R
import com.kotdroid.bluetoothsample.adapter.DeviceAdapter
import com.kotdroid.bluetoothsample.data.model.Device
import com.kotdroid.bluetoothsample.services.BluetoothServiceV2
import com.kotdroid.bluetoothsample.utils.GeneralFunctions
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*
import kotlin.math.max
import kotlin.math.min

class MainActivity : AppCompatActivity(), SwipeRefreshLayout.OnRefreshListener,
    DeviceAdapter.DeviceCallback {


    companion object {
        var TAG = "MainActivity"
    }

    //region Variables
    private val ACCESS_COARSE_LOCATION_CODE = 1
    private val REQUEST_ENABLE_BLUETOOTH = 2
    private val REQUEST_ENABLE_BT = 123
    private val SCAN_MODE_ERROR = 3

    private var bluetoothReceiverRegistered: Boolean = false
    private val scanModeReceiverRegistered: Boolean = false

    private var mBluetoothAdapter: BluetoothAdapter? = null
    private var mConnectedBluetoothDevice: BluetoothDevice? = null

    private var bluetoothManager: BluetoothManager? = null
    private var recyclerView: RecyclerView? = null
    private var swipeRefreshLayout: SwipeRefreshLayout? = null
    private var deviceAdapter: DeviceAdapter? = null
    private val devices = ArrayList<Device>()

    private val handler = Handler()
    private var scanTask: Runnable = object : Runnable {
        override fun run() {
            handler.postDelayed(this, 500)
            scanBluetooth()
        }
    }

    private var bluetoothServiceIntent: Intent? = null

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            Log.d(TAG, "onReceive: Execute")
            val action = intent.action

            if (BluetoothDevice.ACTION_FOUND == action || BluetoothDevice.ACTION_ACL_CONNECTED == action) {
                val device =
                    intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                //if (device?.name.isNullOrEmpty()) return

                val deviceName = device!!.name
                val paired = device.bondState == BluetoothDevice.BOND_BONDED
                val deviceAddress = device.address
                val deviceRSSI = intent.extras!!.getShort(BluetoothDevice.EXTRA_RSSI, 0.toShort())

                val strength = min(max(2 * (deviceRSSI + 100), 0), 100)

                val mDevice = Device(
                    deviceName,
                    paired,
                    deviceAddress,
                    deviceRSSI,
                    "",
                    "dis connected",
                    device
                )

                if (strength < 95) {
                    mDevice.range = "You moved away from the network."
                } else {
                    mDevice.range = "You are within 1 meter range."
                }

                var isNew = true
                var positionToUpdate = -1

                LoopOuter@
                for (i in 0 until devices.size) {
                    if (devices[i].address.contentEquals(mDevice.address)) {
                        positionToUpdate = i
                        isNew = false
                        break@LoopOuter
                    }
                }

                if (isNew) {
                    devices.add(mDevice)
                }


                if (positionToUpdate > -1) {
                    devices[positionToUpdate] = mDevice
                    deviceAdapter?.notifyItemChanged(positionToUpdate)
                    positionToUpdate = -1
                } else {
                    deviceAdapter!!.notifyDataSetChanged()
                }

            }

//            else if (BluetoothAdapter.ACTION_DISCOVERY_FINISHED == action) {
//                if (devices.size == 0) {
//                    Log.d(TAG, "onReceive: No device")
//                }
//            }


        }
    }

    private val scanModeReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val scanMode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, SCAN_MODE_ERROR)
            if (scanMode == BluetoothAdapter.SCAN_MODE_CONNECTABLE || scanMode == BluetoothAdapter.SCAN_MODE_NONE) {
                Toast.makeText(
                    context,
                    "The device is not visible to the outside\n",
                    Toast.LENGTH_SHORT
                ).show()
            }
        }
    }
    //endregion


    //region LifeCycleMethods
    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)


        bluetoothManager = getSystemService(Context.BLUETOOTH_SERVICE) as BluetoothManager
        val toolbar = toolbar
        setSupportActionBar(toolbar)
        initView()
        //Request Permission
        if (Build.VERSION.SDK_INT >= 23) {
            if (ContextCompat.checkSelfPermission(
                    this,
                    Manifest.permission.ACCESS_COARSE_LOCATION
                ) != PackageManager.PERMISSION_GRANTED
            ) {
                Toast.makeText(this, "Get permission", Toast.LENGTH_SHORT).show()
                ActivityCompat.requestPermissions(
                    this,
                    arrayOf(Manifest.permission.ACCESS_COARSE_LOCATION),
                    ACCESS_COARSE_LOCATION_CODE
                )
            }
        }

        initData()
        handler.post(scanTask)
        deviceAdapter!!.notifyDataSetChanged()


        // check whether bluetooth is on then turn on service
        val mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
        when {
            mBluetoothAdapter == null -> {
                // Device does not support Bluetooth
                Toast.makeText(this, "Bluetooth Support not found !", Toast.LENGTH_LONG).show()
            }
            !mBluetoothAdapter.isEnabled -> {
                // Bluetooth is not enabled :)
                val enableBtIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                startActivityForResult(enableBtIntent, REQUEST_ENABLE_BT)
            }
        }


    }

    override fun onResume() {
        super.onResume()
        handler.post(scanTask)
    }

    override fun onRestart() {
        super.onRestart()
        handler.post(scanTask)
    }

    override fun onDestroy() {
        super.onDestroy()

        if (bluetoothReceiverRegistered) {
            unregisterReceiver(bluetoothReceiver)
        }
        if (scanModeReceiverRegistered) {
            unregisterReceiver(scanModeReceiver)
        }
        if (mConnectedBluetoothDevice != null) {
            unpairDevice(mConnectedBluetoothDevice!!)
            GeneralFunctions.connectedDeviceAddress = ""
        }


    }

    override fun onStop() {
        super.onStop()
        stopService(bluetoothServiceIntent)

    }
    //endregion


    override fun onRefresh() {
        runOnUiThread {
            if (mBluetoothAdapter != null) {
                if (!mBluetoothAdapter!!.isEnabled) {
                    //mBluetoothAdapter.enable();
                    val enableIntent = Intent(BluetoothAdapter.ACTION_REQUEST_ENABLE)
                    startActivityForResult(enableIntent, REQUEST_ENABLE_BLUETOOTH)
                }
                handler.post(scanTask)
            }
            deviceAdapter!!.notifyDataSetChanged()
            swipeRefreshLayout!!.isRefreshing = false
        }
    }

    //region Service Related Methods
    private fun startBluetoothNotificationService() {
        ContextCompat.startForegroundService(this, bluetoothServiceIntent!!)
    }

    //endregion

    //region Custom Methods

    //For Pairing
    private fun pairDevice(device: BluetoothDevice) {

        try {
            mBluetoothAdapter?.cancelDiscovery()
            Log.d("pairDevice()", "Start Pairing...")
            if (device.createBond()) {
                GeneralFunctions.connectedDeviceAddress = device.address
                deviceAdapter?.notifyDataSetChanged()
                ContextCompat.startForegroundService(this, bluetoothServiceIntent!!)
            }
//            val m: Method = device.javaClass.getMethod(
//                "createBond",
//                null as Class<*>?
//            )
//            m.invoke(device, null as Array<Any?>?)
            Log.d("pairDevice()", "Pairing finished.")
        } catch (e: Exception) {
            Log.e("pairDevice()", e.message)
        } finally {
            mBluetoothAdapter?.startDiscovery()

        }


    }

    //For UnPairing
    private fun unpairDevice(device: BluetoothDevice) {
        try {
            val method = device.javaClass.getMethod(
                "removeBond", null as Class<*>?
            )
            method.invoke(device, null as Array<Any>?)
            stopService(bluetoothServiceIntent)
        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    private fun scannedDevice(d: Device): Device? {
        for (device in devices) {
            if (d.address == device.address) {
                return device
            }
        }
        return null
    }

    private fun initView() {
        swipeRefreshLayout = swipe_refresh
        swipeRefreshLayout!!.setColorSchemeResources(R.color.colorPrimary)
        swipeRefreshLayout!!.setOnRefreshListener(this)
        recyclerView = recycler_view
        deviceAdapter = DeviceAdapter(devices, this)
        recyclerView!!.adapter = deviceAdapter
        val layoutManager = LinearLayoutManager(this)
        recyclerView!!.layoutManager = layoutManager
    }

    private fun initData() {
        bluetoothServiceIntent = Intent(this, BluetoothServiceV2::class.java)
        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()
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

    override fun onConncetionClick(device: Device) {
        pairDevice(device.bluetoothDevice)

        // remove callbacks
        handler.removeCallbacks(scanTask)

        // add callback
        Handler().postDelayed({ handler.post(scanTask) }, 3000)
    }

    override fun onDisConncetionClick(device: Device) {
        unpairDevice(device.bluetoothDevice)
    }
    //endregion

}