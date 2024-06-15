// GPSLocationManager.kt

package biz.atamai.myai

import android.Manifest
import android.app.AlertDialog
import android.content.Context
import android.content.pm.PackageManager
import android.location.Location
import android.location.LocationManager
import android.net.Uri
import android.os.Handler
import android.os.Looper
import android.view.View
import android.widget.ProgressBar
import android.widget.TextView
import androidx.core.app.ActivityCompat
import biz.atamai.myai.databinding.DialogGpsAccuracyBinding
import com.google.android.gms.location.*

class GPSLocationManager(private val mainHandler: MainHandler) {

    private var fusedLocationClient: FusedLocationProviderClient =
        LocationServices.getFusedLocationProviderClient(mainHandler.context)

    private val handler = Handler(Looper.getMainLooper())
    private var accuracyUpdateHandler: Handler? = null
    private var accuracyUpdateRunnable: Runnable? = null
    private var currentLocation: Location? = null

    fun getCurrentLocation(callback: (Location?) -> Unit) {
        if (ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_FINE_LOCATION) != PackageManager.PERMISSION_GRANTED &&
            ActivityCompat.checkSelfPermission(mainHandler.context, Manifest.permission.ACCESS_COARSE_LOCATION) != PackageManager.PERMISSION_GRANTED) {
            callback(null)
            return
        }

        handler.postDelayed({
            fusedLocationClient.lastLocation.addOnSuccessListener { location: Location? ->
                if (location != null) {
                    callback(location)
                } else {
                    // Request new location if last location is null
                    val gpsInterval = 20
                    val locationRequest = LocationRequest.Builder(
                        Priority.PRIORITY_HIGH_ACCURACY, gpsInterval * 1000L
                    ).apply {
                        setWaitForAccurateLocation(true)
                    }.build()

                    val locationCallback = object : LocationCallback() {
                        override fun onLocationResult(locationResult: LocationResult) {
                            locationResult.locations.firstOrNull()?.let { location ->
                                callback(location)
                                fusedLocationClient.removeLocationUpdates(this)
                            }
                        }
                    }

                    fusedLocationClient.requestLocationUpdates(
                        locationRequest,
                        locationCallback,
                        Looper.getMainLooper()
                    )
                }
            }
        }, 1500)
    }

    fun areLocationServicesEnabled(): Boolean {
        val locationManager = mainHandler.context.getSystemService(Context.LOCATION_SERVICE) as LocationManager
        return locationManager.isProviderEnabled(LocationManager.GPS_PROVIDER) ||
                locationManager.isProviderEnabled(LocationManager.NETWORK_PROVIDER)
    }

    fun showGPSAccuracyDialog() {
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
            currentLocation?.let {
                val uri = Uri.parse("${it.latitude},${it.longitude}")
                val message = "GPS location: $uri"
                mainHandler.handleTextMessage(message, emptyList(), emptyList(), true)
            }
            alertDialog.dismiss()
            stopAccuracyUpdates()
        }

        alertDialog.show()
        startAccuracyUpdates(accuracyText, progressBar)
    }

    private fun startAccuracyUpdates(accuracyText: TextView, progressBar: ProgressBar) {
        accuracyUpdateHandler = Handler(Looper.getMainLooper())
        accuracyUpdateRunnable = object : Runnable {
            override fun run() {
                progressBar.visibility = View.VISIBLE
                getCurrentLocation { location ->
                    if (location != null) {
                        currentLocation = location
                        val accuracy = location.accuracy
                        accuracyText.text = "GPS Accuracy: $accuracy meters"
                        progressBar.visibility = View.GONE
                    } else {
                        accuracyText.text = "Unable to get location accuracy"
                    }
                    accuracyUpdateHandler?.postDelayed(this, 1500)
                }
            }
        }
        accuracyUpdateHandler?.post(accuracyUpdateRunnable!!)
    }

    private fun stopAccuracyUpdates() {
        accuracyUpdateHandler?.removeCallbacks(accuracyUpdateRunnable!!)
        accuracyUpdateHandler = null
        accuracyUpdateRunnable = null
    }
}
