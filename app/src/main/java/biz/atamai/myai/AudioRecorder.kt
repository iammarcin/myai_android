package biz.atamai.myai

import android.Manifest
import android.content.pm.PackageManager
import android.media.MediaRecorder
import android.net.Uri
import android.widget.Toast
import androidx.core.app.ActivityCompat
import androidx.core.content.ContextCompat
import java.io.IOException

class AudioRecorder(private val activity: MainActivity) {
    private var mediaRecorder: MediaRecorder? = null
    private var audioFilePath: String? = null
    private var isRecording = false
    private val REQUEST_AUDIO_PERMISSION_CODE = 1

    fun handleRecordButtonClick() {
        if (isRecording) {
            stopRecording()
        } else {
            startRecording()
        }
    }

    @Suppress("DEPRECATION")
    private fun startRecording() {
        if (checkPermissions()) {
            activity.setRecordButtonImageResource(R.drawable.baseline_stop_24)
            val timestamp = System.currentTimeMillis()
            audioFilePath = "${activity.externalCacheDir?.absolutePath}/audiorecord_${timestamp}.mp3"

            mediaRecorder = MediaRecorder().apply {
                setAudioSource(MediaRecorder.AudioSource.MIC)
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
        } else {
            requestPermissions()
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
    }

    private fun addRecordingToFileList(filePath: String?) {
        filePath?.let {
            val fileUri = Uri.parse(it)
            activity.addMessageToChat("", listOf(), listOf(fileUri))
        }
    }

    private fun checkPermissions(): Boolean {
        val recordPermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.RECORD_AUDIO) == PackageManager.PERMISSION_GRANTED
        val storagePermission = ContextCompat.checkSelfPermission(activity, Manifest.permission.WRITE_EXTERNAL_STORAGE) == PackageManager.PERMISSION_GRANTED

        return recordPermission && storagePermission
    }

    private fun requestPermissions() {
        ActivityCompat.requestPermissions(
            activity,
            arrayOf(Manifest.permission.RECORD_AUDIO, Manifest.permission.WRITE_EXTERNAL_STORAGE),
            REQUEST_AUDIO_PERMISSION_CODE
        )
    }
}