package biz.atamai.myai

import android.Manifest
import android.app.Activity
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
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class AudioRecorder(private val mainHandler: MainHandler, var useBluetoothIfConnected: Boolean, var apiUrl: String) {

    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isRecording = false
    private val REQUEST_AUDIO_PERMISSION_CODE = 1

    private val permissionsUtil: PermissionsUtil

    private var bluetoothAdapter: BluetoothAdapter? = BluetoothAdapter.getDefaultAdapter()
    private var bluetoothHeadset: BluetoothHeadset? = null
    private var audioManager: AudioManager = mainHandler.getMainBindingContext().getSystemService(Context.AUDIO_SERVICE) as AudioManager
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
                    mainHandler.createToastMessage("Bluetooth headset connected")
                } else if (state == BluetoothHeadset.STATE_DISCONNECTED) {
                    mainHandler.createToastMessage("Bluetooth headset disconnected")
                }
            }
        }
    }

    init {
        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.S) {
            if (ContextCompat.checkSelfPermission(mainHandler.getMainBindingContext(), Manifest.permission.BLUETOOTH_CONNECT) == PackageManager.PERMISSION_GRANTED) {
                bluetoothAdapter?.getProfileProxy(mainHandler.getMainBindingContext(), bluetoothProfileListener, BluetoothProfile.HEADSET)
            } else {
                ActivityCompat.requestPermissions(mainHandler.getMainBindingContext() as Activity, arrayOf(Manifest.permission.BLUETOOTH_CONNECT), REQUEST_AUDIO_PERMISSION_CODE)
            }
        } else {
            if (useBluetoothIfConnected) {
                bluetoothAdapter?.getProfileProxy(mainHandler.getMainBindingContext(), bluetoothProfileListener, BluetoothProfile.HEADSET)
            }
        }
        if (useBluetoothIfConnected) {
            mainHandler.getMainBindingContext().registerReceiver(bluetoothReceiver, IntentFilter(BluetoothHeadset.ACTION_CONNECTION_STATE_CHANGED))
        }

        permissionsUtil = PermissionsUtil(mainHandler)
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
            mainHandler.setRecordButtonImageResource(R.drawable.ic_stop_24)
            val timestamp = System.currentTimeMillis()
            audioFilePath = "${mainHandler.getMainBindingContext().externalCacheDir?.absolutePath}/audiorecord_${timestamp}.mp3"

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
                mainHandler.createToastMessage("Recording started")
            } catch (e: IOException) {
                mainHandler.createToastMessage("Recording failed: ${e.message}")
            }
        }
    }

    private fun stopRecording() {
        mediaRecorder?.apply {
            stop()
            release()
            mediaRecorder = null
            isRecording = false
            mainHandler.setRecordButtonImageResource(R.drawable.ic_mic_none)
            mainHandler.createToastMessage("Recording stopped")
            addRecordingToFileList(audioFilePath)
            sendAudioFileToBackend(audioFilePath)
        }

        if (isBluetoothScoOn) {
            stopBluetoothSco()
        }
    }

    private fun sendAudioFileToBackend(audioFilePath: String?) {
        // collect attachments (images , etc) - so when recording is done and file is attached
        // it is taken into account when sending the message
        val attachedImageLocations = mutableListOf<String>()
        val attachedFilePaths = mutableListOf<Uri>()

        for (i in 0 until mainHandler.getMainBinding().imagePreviewContainer.childCount) {
            val frameLayout = mainHandler.getMainBinding().imagePreviewContainer.getChildAt(i) as FrameLayout
            // if it's an image
            if (frameLayout.getChildAt(0) is ImageView) {
                val imageView = frameLayout.getChildAt(0) as ImageView
                if (imageView.tag == null) {
                    mainHandler.createToastMessage("Problems with uploading files. Try again")
                    continue
                }
                attachedImageLocations.add(imageView.tag as String)
            } else {
                // if it's a file
                val placeholder = frameLayout.getChildAt(0) as View
                attachedFilePaths.add(placeholder.tag as Uri)
            }
        }

        val utilityTools = UtilityTools(
            context = mainHandler.getMainBindingContext(),
            onResponseReceived = { response ->
                mainHandler.executeOnUIThread {
                    mainHandler.handleTextMessage(response, attachedImageLocations, attachedFilePaths)
                }
            },
            onError = { error ->
                mainHandler.executeOnUIThread {
                    mainHandler.createToastMessage("Error: ${error.message}")
                }
            }
        )
        utilityTools.uploadFileToServer(audioFilePath, apiUrl, "chat_audio2text", "speech", "chat")
    }

    private fun addRecordingToFileList(filePath: String?) {
        filePath?.let {
            val fileUri = Uri.parse(it)
            mainHandler.addMessageToChat("", listOf(), listOf(fileUri))
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
        mainHandler.getMainBindingContext().unregisterReceiver(bluetoothReceiver)
        bluetoothAdapter?.closeProfileProxy(BluetoothProfile.HEADSET, bluetoothHeadset)
    }
}
