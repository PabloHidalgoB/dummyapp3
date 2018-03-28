package com.example.trabajo.dummyapp3

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothDevice
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.os.Build
import android.os.Bundle
import android.os.CountDownTimer
import android.support.v7.app.AppCompatActivity
import android.util.Log
import android.view.View
import android.widget.AdapterView
import android.widget.Button
import android.widget.EditText
import android.widget.ListView

import java.nio.charset.Charset
import java.util.ArrayList
import java.util.UUID


class MainActivity : AppCompatActivity(), AdapterView.OnItemClickListener {

    private var countDownTimer: CountDownTimer? = null
    private val INTERVAL_TIMER = 10000 // 10 Second


    private var mBluetoothAdapter: BluetoothAdapter? = null

    private var mBluetoothConnection: BluetoothConnectionService? = null

    private var btnStartConnection: Button? = null

    private var mBTDevice: BluetoothDevice? = null

    var mBTDevices = ArrayList<BluetoothDevice>()


    private var lvNewDevices: ListView? = null

    companion object {
        private val TAG = "MainActivity"

        private val MY_UUID_INSECURE = UUID.fromString("8ce255c0-200a-11e0-ac64-0800200c9a66")
    }

    // Create a BroadcastReceiver for ACTION_FOUND
    private val mBroadcastReceiver1 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            // When discovery finds a device
            if (action == BluetoothAdapter.ACTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothAdapter.EXTRA_STATE, BluetoothAdapter.ERROR)

                when (state) {
                    BluetoothAdapter.STATE_OFF -> Log.d(TAG, "onReceive: STATE OFF")
                    BluetoothAdapter.STATE_TURNING_OFF -> Log.d(TAG, "mBroadcastReceiver1: STATE TURNING OFF")
                    BluetoothAdapter.STATE_ON -> Log.d(TAG, "mBroadcastReceiver1: STATE ON")
                    BluetoothAdapter.STATE_TURNING_ON -> Log.d(TAG, "mBroadcastReceiver1: STATE TURNING ON")
                }
            }
        }
    }

    /**
     * Broadcast Receiver for changes made to bluetooth states such as:
     * 1) Discoverability mode on/off or expire.
     */
    private val mBroadcastReceiver2 = object : BroadcastReceiver() {

        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothAdapter.ACTION_SCAN_MODE_CHANGED) {

                val mode = intent.getIntExtra(BluetoothAdapter.EXTRA_SCAN_MODE, BluetoothAdapter.ERROR)

                when (mode) {
                //Device is in Discoverable Mode
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE_DISCOVERABLE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Enabled.")
                //Device not in discoverable mode
                    BluetoothAdapter.SCAN_MODE_CONNECTABLE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Able to receive connections.")
                    BluetoothAdapter.SCAN_MODE_NONE -> Log.d(TAG, "mBroadcastReceiver2: Discoverability Disabled. Not able to receive connections.")
                    BluetoothAdapter.STATE_CONNECTING -> Log.d(TAG, "mBroadcastReceiver2: Connecting....")
                    BluetoothAdapter.STATE_CONNECTED -> Log.d(TAG, "mBroadcastReceiver2: Connected.")
                }

            }
        }
    }


    /**
     * Broadcast Receiver for listing devices that are not yet paired
     * -Executed by btnDiscover() method.
     */
    private val mBroadcastReceiver3 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action
            Log.d(TAG, "onReceive: ACTION FOUND.")

            if (action == BluetoothDevice.ACTION_FOUND) {
                val device = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)

                Log.d(TAG, "onReceive: " + device.name + ": " + device.address)
                val mOrigen = "28:BE:03:EF:89:09"
                if (mOrigen == device.address) {

                    startBTConnection(device, MY_UUID_INSECURE)
                }
            }
        }
    }

    /**
     * Broadcast Receiver that detects bond state changes (Pairing status changes)
     */
    private val mBroadcastReceiver4 = object : BroadcastReceiver() {
        override fun onReceive(context: Context, intent: Intent) {
            val action = intent.action

            if (action == BluetoothDevice.ACTION_BOND_STATE_CHANGED) {
                val mDevice = intent.getParcelableExtra<BluetoothDevice>(BluetoothDevice.EXTRA_DEVICE)
                //3 cases:
                //case1: bonded already
                if (mDevice.bondState == BluetoothDevice.BOND_BONDED) {
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDED.")
                    //inside BroadcastReceiver4
                    mBTDevice = mDevice
                }
                //case2: creating a bone
                if (mDevice.bondState == BluetoothDevice.BOND_BONDING) {
                    Log.d(TAG, "BroadcastReceiver: BOND_BONDING.")
                }
                //case3: breaking a bond
                if (mDevice.bondState == BluetoothDevice.BOND_NONE) {
                    Log.d(TAG, "BroadcastReceiver: BOND_NONE.")
                }
            }
        }
    }


    override fun onDestroy() {
        Log.d(TAG, "onDestroy: called.")
        super.onDestroy()
        unregisterReceiver(mBroadcastReceiver1)
        unregisterReceiver(mBroadcastReceiver2)
        unregisterReceiver(mBroadcastReceiver3)
        unregisterReceiver(mBroadcastReceiver4)
        //mBluetoothAdapter.cancelDiscovery();
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        lvNewDevices = findViewById<View>(R.id.lvNewDevices) as ListView
        mBTDevices = ArrayList()

        btnStartConnection = findViewById<View>(R.id.btnStartConnection) as Button


        //Broadcasts when bond state changes (ie:pairing)
        val filter = IntentFilter(BluetoothDevice.ACTION_BOND_STATE_CHANGED)
        registerReceiver(mBroadcastReceiver4, filter)

        mBluetoothAdapter = BluetoothAdapter.getDefaultAdapter()

        lvNewDevices!!.onItemClickListener = this@MainActivity

        btnStartConnection!!.setOnClickListener { startConnection() }

        checkBTPermissions()
        discover()

        startCount()

    }

    private fun startCount() {
        try {
            countDownTimer = object : CountDownTimer(INTERVAL_TIMER.toLong(), 1000) {

                override fun onTick(millisUntilFinished: Long) {
                    Log.d(TAG, "==> next check in: " + millisUntilFinished / 1000)
                }

                override fun onFinish() {
                    discover()
                }
            }.start()

        } catch (e: Exception) {
            e.printStackTrace()
        }
    }

    //create method for starting connection
    //***remember the conncction will fail and app will crash if you haven't paired first
    fun startConnection() {
        startBTConnection(mBTDevice!!, MY_UUID_INSECURE)
    }

    /**
     * starting chat service method
     */
    fun startBTConnection(device: BluetoothDevice, uuid: UUID) {
        Log.d(TAG, "startBTConnection: Initializing RFCOM Bluetooth Connection.")
        mBluetoothConnection = BluetoothConnectionService(this@MainActivity)
        mBluetoothConnection!!.startClient(device, uuid)
    }

    fun discover() {
        Log.d(TAG, "btnDiscover: Looking for unpaired devices.")

        if (mBluetoothAdapter!!.isDiscovering) {
            mBluetoothAdapter!!.cancelDiscovery()
            Log.d(TAG, "btnDiscover: Canceling discovery.")

            //check BT permissions in manifest
            checkBTPermissions()

            mBluetoothAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
        if (!mBluetoothAdapter!!.isDiscovering) {

            //check BT permissions in manifest
            checkBTPermissions()

            mBluetoothAdapter!!.startDiscovery()
            val discoverDevicesIntent = IntentFilter(BluetoothDevice.ACTION_FOUND)
            registerReceiver(mBroadcastReceiver3, discoverDevicesIntent)
        }
    }

    /**
     * This method is required for all devices running API23+
     * Android must programmatically check the permissions for bluetooth. Putting the proper permissions
     * in the manifest is not enough.
     *
     * NOTE: This will only execute on versions > LOLLIPOP because it is not needed otherwise.
     */
    private fun checkBTPermissions() {
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.LOLLIPOP) {
            var permissionCheck = this.checkSelfPermission("Manifest.permission.ACCESS_FINE_LOCATION")
            permissionCheck += this.checkSelfPermission("Manifest.permission.ACCESS_COARSE_LOCATION")
            if (permissionCheck != 0) {

                this.requestPermissions(arrayOf(Manifest.permission.ACCESS_FINE_LOCATION, Manifest.permission.ACCESS_COARSE_LOCATION), 1001) //Any number
            }
        } else {
            Log.d(TAG, "checkBTPermissions: No need to check permissions. SDK version < LOLLIPOP.")
        }
    }

    override fun onItemClick(adapterView: AdapterView<*>, view: View, i: Int, l: Long) {
        //first cancel discovery because its very memory intensive.
        mBluetoothAdapter!!.cancelDiscovery()

        Log.d(TAG, "onItemClick: You Clicked on a device.")
        val deviceName = mBTDevices[i].name
        val deviceAddress = mBTDevices[i].address

        Log.d(TAG, "onItemClick: deviceName = $deviceName")
        Log.d(TAG, "onItemClick: deviceAddress = $deviceAddress")

        //create the bond.
        //NOTE: Requires API 17+? I think this is JellyBean
        if (Build.VERSION.SDK_INT > Build.VERSION_CODES.JELLY_BEAN_MR2) {
            Log.d(TAG, "Trying to pair with $deviceName")
            mBTDevices[i].createBond()

            mBTDevice = mBTDevices[i]
            mBluetoothConnection = BluetoothConnectionService(this@MainActivity)
        }
    }


}
