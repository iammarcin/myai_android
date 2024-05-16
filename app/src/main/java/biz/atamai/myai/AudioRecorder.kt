package biz.atamai.myai

import android.Manifest
import android.bluetooth.BluetoothAdapter
import android.bluetooth.BluetoothHeadset
import android.bluetooth.BluetoothProfile
import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import android.content.IntentFilter
import android.content.pm.PackageManager
import android.media.AudioManager
import android.media.MediaRecorder
import android.net.Uri
import android.os.Build
import android.os.Handler
import android.os.Looper
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class AudioRecorder(private val activity: MainActivity, var useBluetoothIfConnected: Boolean) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isRecording = false
    private val REQUEST_AUDIO_PERMISSION_CODE = 1

    private val permissionsUtil: PermissionsUtil

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var audioManager: AudioManager = activity.getSystemService(Context.AUDIO_SERVICE) as AudioManager
    private var isBluetoothScoOn = false

    // delay to ensure SCO connection is established
    private var bluetoothStartupDelay = 200L

    private val bluetoothProfileListener = object : BluetoothProfile.ServiceListener {
        override fun onServiceConnected(profile: Int, proxy: BluetoothProfile) {
            if (profile == BluetoothProfile.HEADSET) {
                if (useBluetoothIfConnected) {
                    bluetoothHeadset = proxy as BluetoothHeadset
                }
            }
        }

        override fun onServiceDisconnected(profile: Int) {
            if (profile == BluetoothProfile.HEADSET) {
                bluetoothHeadset = null
            }
        }
    }

    private val bluetoothReceiver = object : BroadcastReceiver() {
        override fun onReceive(context: Context?, intent: Intent?) {
            val action = intent?.action
            if (action == BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED) {
                val state = intent.getIntExtra(BluetoothHeadset.EXTRA_STATE, -1)
                if (state == BluetoothHeadset.STATE_CONNECTED) {
                    Toast.makeText(activity, "Bluetooth headset connected", Toast.LENGTH_SHORT).show()
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    Toast.makeText(activity, "Bluetooth headset disconnected", Toast.LENGTH_SHORT).show()
                }
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(activity, Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.getProfileProxy(activity, bluetoothProfileListener, BluetoothProfile.HEADSET)
            } else {
                ActivityCompat.requestPermissions(activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_AUDIO_PERMISSION_CODE)
            }
        } else {
            if (useBluetoothIfConnected) {
                bluetoothAdapter?.getProfileProxy(activity, bluetoothProfileListener, BluetoothProfile.HEADSET)
            }
        }
        if (useBluetoothIfConnected) {
            activity.registerReceiver(bluetoothReceiver, IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
        }

        permissionsUtil = PermissionsUtil(activity)
    }

    fun handleRecordButtonClick() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        if (permissionsUtil.checkPermissions()) {
            activity.setRecordButtonImageResource(R.drawable.ic_stop_24)
            val timestamp = System.currentTimeMillis()
            audioFilePath = "${activity.externalCacheDir?.absolutePath}/audiorecord_${timestamp}.mp3"

            if (useBluetoothIfConnected && isBluetoothHeadsetConnected()) {
                startBluetoothScoAndRecord()
            } else {
                startMediaRecorder(MediaRecorder.AudioSource.MIC)
            }
        } else {
            permissionsUtil.requestPermissions()
        }
    }

    private fun startBluetoothScoAndRecord() {
        startBluetoothSco()

        Handler(Looper.getMainLooper()).postDelayed({
            startMediaRecorder(MediaRecorder.AudioSource.VOICE_COMMUNICATION)
        }, bluetoothStartupDelay) // delay to ensure SCO connection is established
    }

    private fun startMediaRecorder(audioSource: Int) {
        mediaRecorder = MediaRecorder().apply {
            setAudioSource(audioSource)
            setOutputFormat(MediaRecorder.OutputFormat.MPEG_4)
            setAudioEncoder(MediaRecorder.AudioEncoder.AAC)
            setOutputFile(audioFilePath)
            setAudioSamplingRate(44100)
            setAudioEncodingBitRate(96000)

            try {
                prepare()
                start()
                isRecording = true
                Toast.makeText(activity, "Recording started", Toast.LENGTH_SHORT).show()
            } catch (e: IOException) {
                Toast.makeText(activity, "Recording failed: ${e.message}", Toast.LENGTH_SHORT).show()
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
            mediaRecorder = null
            isRecording = false
            activity.setRecordButtonImageResource(R.drawable.ic_mic_none)
            Toast.makeText(activity, "Recording stopped", Toast.LENGTH_SHORT).show()
            addRecordingToFileList(audioFilePath)
        }

        if (isBluetoothScoOn) {
            stopBluetoothSco()
        }
    }

    private fun addRecordingToFileList(filePath: String?) {
        filePath?.let {
            val fileUri = Uri.parse(it)
            activity.addMessageToChat("", listOf(), listOf(fileUri))
        }
    }

    private fun isBluetoothHeadsetConnected(): Boolean {
        bluetoothHeadset?.connectedDevices?.let { devices ->
            return devices.isNotEmpty()
        }
        return false
    }

    private fun startBluetoothSco() {
        if (useBluetoothIfConnected) {
            audioManager.startBluetoothSco()
            audioManager.isBluetoothScoOn = true
            isBluetoothScoOn = true
        }
    }

    private fun stopBluetoothSco() {
        audioManager.stopBluetoothSco()
        audioManager.isBluetoothScoOn = false
        isBluetoothScoOn = false
    }

    fun release() {
        activity.unregisterReceiver(bluetoothReceiver)
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
    }
}
