// GPSLocationManager.kt

package biz.atamai.myai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Looper
import android.view.View
import android.widget.FrameLayout
import android.widget.ImageView
import android.widget.LinearLayout
import android.widget.ProgressBar
import android.widget.TextView
import android.widget.Toast
import androidx.core.app.ActivityCompat
import biz.atamai.myai.databinding.DialogGpsAccuracyBinding
import com.google.android.gms.location.*

class GPSLocationManager(private val mainHandler: MainHandler) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(mainHandler.context)

    private var locationCallback: LocationCallback? = null
    private var currentLocation: Location? = null

    private fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
            if (location != null) {
                callback(location)
            } else {
                // Request new location if last location is null
                startLocationUpdates(callback)
            }
        }
    }

    private fun startLocationUpdates(callback: (Location?) -> Unit) {
        val gpsInterval = 5
        val locationRequest = LocationRequest.Builder(Priority.PRIORITY_HIGH_ACCURACY, gpsInterval * 1000L)
            .setMinUpdateIntervalMillis(1000L)
            .setWaitForAccurateLocation(true)
            .build()

        locationCallback = object : LocationCallback() {
            override fun onLocationResult(locationResult: LocationResult) {
                locationResult.locations.firstOrNull()?.let { location ->
                    callback(location)
                }
            }
        }

        if (ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            return
        }
        fusedLocationClient.requestLocationUpdates(locationRequest, locationCallback!!, Looper.getMainLooper())
    }

    fun areLocationServicesEnabled(): Boolean {
        val locationManager = mainHandler.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun showGPSAccuracyDialog(imagePreviewContainer: LinearLayout) {
        val bindingGPSDialog = DialogGpsAccuracyBinding.inflate(mainHandler.mainLayoutInflaterInstance)

        val accuracyText = bindingGPSDialog.accuracyText
        val progressBar = bindingGPSDialog.progressBar

        val cancelButton = bindingGPSDialog.cancelButton
        val shareButton = bindingGPSDialog.shareButton

        val alertDialog = AlertDialog.Builder(mainHandler.context)
            .setView(bindingGPSDialog.root)
            .setCancelable(false)
            .create()

        cancelButton.setOnClickListener {
            alertDialog.dismiss()
            stopAccuracyUpdates()
        }

        shareButton.setOnClickListener {
            val attachedImageLocations = mutableListOf<String>()
            val attachedFilePaths = mutableListOf<Uri>()
            var allImagesUploaded = true

            for (i in 0 until imagePreviewContainer.childCount) {
                val frameLayout = imagePreviewContainer.getChildAt(i) as FrameLayout
                if (frameLayout.getChildAt(0) is ImageView) {
                    val imageView = frameLayout.getChildAt(0) as ImageView
                    if (imageView.tag == null) {
                        allImagesUploaded = false
                        break
                    } else {
                        attachedImageLocations.add(imageView.tag as String)
                    }
                } else {
                    val placeholder = frameLayout.getChildAt(0) as View
                    attachedFilePaths.add(placeholder.tag as Uri)
                }
            }

            if (!allImagesUploaded) {
                mainHandler.createToastMessage("Images still uploading")
                return@setOnClickListener
            }

            currentLocation?.let {
                val uri = Uri.parse("${it.latitude},${it.longitude}")
                val message = "GPS location: $uri"

                mainHandler.handleTextMessage(message, attachedImageLocations, attachedFilePaths, true)
            }

            alertDialog.dismiss()
            stopAccuracyUpdates()
        }

        alertDialog.show()
        startAccuracyUpdates(accuracyText, progressBar)
    }

    private fun startAccuracyUpdates(accuracyText: TextView, progressBar: ProgressBar) {
        getCurrentLocation { location ->
            if (location != null) {
                println("Accuracy: ${location.accuracy}")
                currentLocation = location
                val accuracy = location.accuracy
                accuracyText.text = "GPS Accuracy: $accuracy meters"
                progressBar.visibility = View.GONE
                if (accuracy > DESIRED_ACCURACY) {
                    startLocationUpdates { newLocation ->
                        progressBar.visibility = View.VISIBLE
                        if (newLocation != null) {
                            println("Updated Accuracy: ${newLocation.accuracy}")
                            currentLocation = newLocation
                            val newAccuracy = newLocation.accuracy
                            accuracyText.text = "GPS Accuracy: $newAccuracy meters"
                        }
                    }
                } else {
                    progressBar.visibility = View.GONE
                }
            } else {
                accuracyText.text = "Unable to get location accuracy"
            }
        }
    }

    private fun stopAccuracyUpdates() {
        locationCallback?.let { fusedLocationClient.removeLocationUpdates(it) }
        locationCallback = null
    }

    companion object {
        // set on purpose really low - because it was causing some problems
        private const val DESIRED_ACCURACY = 0.1f // Desired accuracy in meters
    }
}
