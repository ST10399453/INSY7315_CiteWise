package com.example.citewise_mobile

import android.content.Context
import android.content.Intent
import android.net.ConnectivityManager
import android.net.Network
import android.net.NetworkCapabilities
import android.net.NetworkRequest
import android.os.Build
import android.os.Bundle
import android.provider.Settings
import android.view.View
import androidx.activity.enableEdgeToEdge
import androidx.appcompat.app.AppCompatActivity
import androidx.core.view.ViewCompat
import androidx.core.view.WindowInsetsCompat
import com.example.citewise_mobile.databinding.ActivityResourcesBinding
import com.google.android.material.snackbar.Snackbar

/**
 * ResourcesActivity
 *
 * - Detects network connectivity and toggles between:
 *      • Online: shows the documents RecyclerView
 *      • Offline: shows a retry/“no internet” state
 * - Uses ViewBinding for activity_resources.xml
 * - Safe across API levels (M+ capabilities, pre-M fallback)
 *
 * Requires in AndroidManifest:
 * <uses-permission android:name="android.permission.ACCESS_NETWORK_STATE"/>
 */
class ResourcesActivity : AppCompatActivity() {

    private lateinit var binding: ActivityResourcesBinding
    private lateinit var connectivityManager: ConnectivityManager
    private var networkCallback: ConnectivityManager.NetworkCallback? = null

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)
        enableEdgeToEdge()

        binding = ActivityResourcesBinding.inflate(layoutInflater)
        setContentView(binding.root)

        // Apply system bar insets to the top-level container (fallback to root if @id/main is absent)
        val insetTarget: View = findViewById(R.id.main) ?: binding.root
        ViewCompat.setOnApplyWindowInsetsListener(insetTarget) { v, insets ->
            val bars = insets.getInsets(WindowInsetsCompat.Type.systemBars())
            v.setPadding(bars.left, bars.top, bars.right, bars.bottom)
            insets
        }

        connectivityManager = getSystemService(Context.CONNECTIVITY_SERVICE) as ConnectivityManager

        // Retry button -> re-check connectivity or open Internet Settings panel
        binding.btnRetry.setOnClickListener {
            if (isConnected()) {
                showOnline()
                reloadData()
            } else {
                // Try to open the system Internet connectivity panel (Android 10+)
                runCatching {
                    startActivity(Intent(Settings.Panel.ACTION_INTERNET_CONNECTIVITY))
                }.onFailure {
                    Snackbar.make(binding.root, "Still offline. Check your connection.", Snackbar.LENGTH_SHORT).show()
                }
            }
        }

        // Initial state
        if (isConnected()) {
            showOnline()
            reloadData()
        } else {
            showOffline()
        }
    }

    override fun onStart() {
        super.onStart()
        registerNetworkListener()
    }

    override fun onStop() {
        super.onStop()
        unregisterNetworkListener()
    }

    /**
     * Show content for online state.
     */
    private fun showOnline() {
        binding.rvDocuments.visibility = View.VISIBLE
        binding.stateNoInternet.visibility = View.GONE
    }

    /**
     * Show content for offline state.
     */
    private fun showOffline() {
        binding.rvDocuments.visibility = View.GONE
        binding.stateNoInternet.visibility = View.VISIBLE
    }

    /**
     * Determine whether device has validated internet connectivity.
     */
    private fun isConnected(): Boolean {
        return if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.M) {
            val active = connectivityManager.activeNetwork ?: return false
            val caps = connectivityManager.getNetworkCapabilities(active) ?: return false
            val hasTransport =
                caps.hasTransport(NetworkCapabilities.TRANSPORT_WIFI) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_CELLULAR) ||
                        caps.hasTransport(NetworkCapabilities.TRANSPORT_ETHERNET)
            val hasInternet = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
            val validated = caps.hasCapability(NetworkCapabilities.NET_CAPABILITY_VALIDATED)
            hasTransport && hasInternet && validated
        } else {
            @Suppress("DEPRECATION")
            connectivityManager.activeNetworkInfo?.isConnected == true
        }
    }

    /**
     * Register a network listener that updates UI as connectivity changes.
     */
    private fun registerNetworkListener() {
        networkCallback = object : ConnectivityManager.NetworkCallback() {
            override fun onAvailable(network: Network) {
                runOnUiThread {
                    showOnline()
                    // Optionally only reload if we came from offline -> online
                    reloadData()
                }
            }

            override fun onLost(network: Network) {
                runOnUiThread { showOffline() }
            }

            override fun onCapabilitiesChanged(network: Network, caps: NetworkCapabilities) {
                runOnUiThread {
                    if (isConnected()) showOnline() else showOffline()
                }
            }
        }

        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.N) {
            connectivityManager.registerDefaultNetworkCallback(networkCallback!!)
        } else {
            val request = NetworkRequest.Builder()
                .addCapability(NetworkCapabilities.NET_CAPABILITY_INTERNET)
                .build()
            connectivityManager.registerNetworkCallback(request, networkCallback!!)
        }
    }

    /**
     * Unregister the previously registered network listener.
     */
    private fun unregisterNetworkListener() {
        networkCallback?.let {
            runCatching { connectivityManager.unregisterNetworkCallback(it) }
            networkCallback = null
        }
    }

    /**
     * Hook to (re)load your documents for the RecyclerView.
     * Call your ViewModel/Repository here.
     */
    private fun reloadData() {
        // TODO: viewModel.loadDocuments() or trigger your adapter data refresh here.
        // Example:
        // viewModel.refresh(query = binding.etSearch.text?.toString(), ...)
    }
}
