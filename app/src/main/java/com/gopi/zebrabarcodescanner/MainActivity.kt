package com.gopi.zebrabarcodescanner

import android.content.Intent
import android.content.IntentFilter
import androidx.appcompat.app.AppCompatActivity
import android.os.Bundle
import android.util.Log
import android.view.View
import com.gopi.zebrabarcodescanner.scanningModule.DWInterface
import com.gopi.zebrabarcodescanner.scanningModule.DWReceiver
import com.gopi.zebrabarcodescanner.scanningModule.ObservableObject
import kotlinx.android.synthetic.main.activity_main.*
import java.util.*

class MainActivity : AppCompatActivity(), Observer {

    private var tag: String = MainActivity::class.java.simpleName
    private val dwInterface = DWInterface()
    private val receiver = DWReceiver()
    private var version65OrOver = false
    private var initialized = false

    companion object {
        const val PROFILE_NAME = "ZebraBarcodeScanner"
        const val PROFILE_INTENT_ACTION = "com.gopi.zebrabarcodescanner.SCAN"
        const val PROFILE_INTENT_START_ACTIVITY = "0"
    }

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        setContentView(R.layout.activity_main)

        ObservableObject.instance.addObserver(this)

        initView()
        registerScannerReceiver()
    }

    private fun initView() {
        //scan button action
        scanBtn.setOnClickListener {
            callScanner()
        }
    }

    private fun registerScannerReceiver() {
        //  Register broadcast receiver to listen for responses from DW API
        val intentFilter = IntentFilter()
        intentFilter.addAction(DWInterface.DATAWEDGE_RETURN_ACTION)
        //intentFilter.addAction("com.symbol.datawedge.api.NOTIFICATION_ACTION")
        intentFilter.addCategory(DWInterface.DATAWEDGE_RETURN_CATEGORY)
        registerReceiver(receiver, intentFilter)
    }

    override fun onNewIntent(intent: Intent) {
        super.onNewIntent(intent)
        //  DataWedge intents received here
        if (intent.hasExtra(DWInterface.DATAWEDGE_SCAN_EXTRA_DATA_STRING)) {
            Log.v(tag, "onNewIntent()")
            //hideProgress()
            //  Handle scan intent received from DataWedge
            val barcodeData = intent.getStringExtra(DWInterface.DATAWEDGE_SCAN_EXTRA_DATA_STRING)
            Log.v(tag, "scanData: $barcodeData")

            progressBar.visibility = View.GONE

            //set success message
            tvScannedItem.text = ""
            tvScannedItem.text = barcodeData!!.trim()
        }
    }

    override fun onPause() {
        super.onPause()
        Log.v(tag, "onPause() - Disable scanner")
        dwInterface.sendCommandString(
            this, DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT,
            DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT_DISABLE
        )
    }

    override fun onResume() {
        super.onResume()
        //  initialized variable is a bit clunky but onResume() is called on each newIntent()
        if (!initialized) {
            //  Create profile to be associated with this application
            Log.v(tag, "onResume() - Create profile")
            initialized = true
            dwInterface.sendCommandString(this, DWInterface.DATAWEDGE_SEND_GET_VERSION, "")
            updateScannerConfig()
        } else {
            Log.v(tag, "onResume() - Enable scanner")
            dwInterface.sendCommandString(
                this, DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT,
                DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT_ENABLE
            )
        }
    }

    private fun callScanner() {
        Log.v(tag, "callScanner()")

        progressBar.visibility = View.VISIBLE
        dwInterface.sendCommandString(
            this, DWInterface.DATAWEDGE_SEND_SET_SOFT_SCAN,
            "START_SCANNING"
        )
    }

    override fun onDestroy() {
        super.onDestroy()
        Log.v(tag, "onDestroy()")
        unregisterReceiver(receiver)
    }

    private fun updateScannerConfig() {
        dwInterface.setConfigForDecoder(
            this, PROFILE_NAME, true, true, true, true,
            "torch", "0"
        )
        //  It seems whenever we change the scanner configuration it re-enables the scanner plugin
        //  Workaround this by just disabling the scanner input again
        dwInterface.sendCommandString(
            this, DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT,
            DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT_DISABLE
        )
    }

    override fun update(o: Observable?, p1: Any?) {
        Log.v(tag, "update()")
        //  Invoked in response to the DWReceiver broadcast receiver
        val receivedIntent = p1 as Intent
        //  This activity will only receive DataWedge version since that is all we ask for, the
        //  configuration activity is responsible for other return values such as enumerated scanners
        //  If the version is <= 6.5 we reduce the amount of configuration available.  There are
        //  smarter ways to do this, e.g. DW 6.4 introduces profile creation (without profile
        //  configuration) but to keep it simple, we just define a minimum of 6.5 for configuration
        //  functionality
        if (receivedIntent.hasExtra(DWInterface.DATAWEDGE_RETURN_VERSION)) {
            val version = receivedIntent.getBundleExtra(DWInterface.DATAWEDGE_RETURN_VERSION)
            val dataWedgeVersion = version.getString(DWInterface.DATAWEDGE_RETURN_VERSION_DATAWEDGE)
            if (dataWedgeVersion != null && dataWedgeVersion >= "6.5" && !version65OrOver) {
                Log.v(tag, "createDataWedgeProfile()")
                version65OrOver = true
                createDataWedgeProfile()
            }
        }

        val command = intent.getStringExtra(DWInterface.DATAWEDGE_EXTRA_COMMAND)
        if(command == DWInterface.DATAWEDGE_SEND_SET_SCANNER_INPUT){
            val result = intent.getStringExtra(DWInterface.DATAWEDGE_EXTRA_RESULT)
            if(result == DWInterface.DATAWEDGE_RESULT_FAILURE){
                var bundle = Bundle()
                var resultInfo = ""
                if (intent.hasExtra(DWInterface.DATAWEDGE_EXTRA_RESULT_INFO)) {
                    bundle = intent.getBundleExtra(DWInterface.DATAWEDGE_EXTRA_RESULT_INFO)
                    val keys = bundle.keySet()
                    for (key in keys) {
                        resultInfo += """
                    $key: ${bundle.getString(key)}
                    
                    """.trimIndent()
                    }
                }

                progressBar.visibility = View.GONE

                //set failure message
                tvScannedItem.text = ""
                tvScannedItem.text = resultInfo
            }
        } else if(command == null){
            Log.v(tag, "command is null")
            progressBar.visibility = View.GONE
        }
    }

    private fun createDataWedgeProfile() {
        //  Create and configure the DataWedge profile associated with this application
        //  For readability's sake, I have not defined each of the keys in the DWInterface file
        dwInterface.sendCommandString(
            this, DWInterface.DATAWEDGE_SEND_CREATE_PROFILE,
            PROFILE_NAME
        )
        val profileConfig = Bundle()
        profileConfig.putString("PROFILE_NAME", PROFILE_NAME)
        profileConfig.putString("PROFILE_ENABLED", "true") //  These are all strings
        profileConfig.putString("CONFIG_MODE", "UPDATE")
        val barcodeConfig = Bundle()
        barcodeConfig.putString("PLUGIN_NAME", "BARCODE")
        barcodeConfig.putString(
            "RESET_CONFIG",
            "true"
        ) //  This is the default but never hurts to specify
        val barcodeProps = Bundle()
        barcodeConfig.putBundle("PARAM_LIST", barcodeProps)
        profileConfig.putBundle("PLUGIN_CONFIG", barcodeConfig)
        val appConfig = Bundle()
        appConfig.putString(
            "PACKAGE_NAME",
            packageName
        )      //  Associate the profile with this app
        appConfig.putStringArray("ACTIVITY_LIST", arrayOf("*"))
        profileConfig.putParcelableArray("APP_LIST", arrayOf(appConfig))
        dwInterface.sendCommandBundle(this, DWInterface.DATAWEDGE_SEND_SET_CONFIG, profileConfig)
        //  You can only configure one plugin at a time in some versions of DW, now do the intent output
        profileConfig.remove("PLUGIN_CONFIG")
        val intentConfig = Bundle()
        intentConfig.putString("PLUGIN_NAME", "INTENT")
        intentConfig.putString("RESET_CONFIG", "true")
        val intentProps = Bundle()
        intentProps.putString("intent_output_enabled", "true")
        intentProps.putString("intent_action", PROFILE_INTENT_ACTION)
        intentProps.putString("intent_delivery", PROFILE_INTENT_START_ACTIVITY)  //  "0"
        intentConfig.putBundle("PARAM_LIST", intentProps)
        profileConfig.putBundle("PLUGIN_CONFIG", intentConfig)
        dwInterface.sendCommandBundle(this, DWInterface.DATAWEDGE_SEND_SET_CONFIG, profileConfig)
    }
}