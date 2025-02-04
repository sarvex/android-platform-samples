/*
 * Copyright 2023 The Android Open Source Project
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     https://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */

package com.example.platform.location.geofencing

import android.Manifest
import android.annotation.SuppressLint
import android.content.Intent
import android.location.Location
import android.net.Uri
import android.os.Build
import android.provider.Settings
import android.widget.Toast
import androidx.compose.animation.animateContentSize
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Box
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.outlined.Settings
import androidx.compose.material3.Button
import androidx.compose.material3.Checkbox
import androidx.compose.material3.FloatingActionButton
import androidx.compose.material3.Icon
import androidx.compose.material3.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.DisposableEffect
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.LocalContext
import androidx.compose.ui.platform.LocalLifecycleOwner
import androidx.compose.ui.unit.dp
import com.example.platform.location.permission.PermissionRationaleDialog
import com.example.platform.location.permission.PermissionRequestButton
import com.example.platform.location.permission.RationaleState
import com.example.platform.location.utils.CUSTOM_INTENT_GEOFENCE
import com.google.accompanist.permissions.ExperimentalPermissionsApi
import com.google.accompanist.permissions.isGranted
import com.google.accompanist.permissions.rememberMultiplePermissionsState
import com.google.accompanist.permissions.rememberPermissionState
import com.google.accompanist.permissions.shouldShowRationale
import com.google.android.catalog.framework.annotations.Sample
import kotlinx.coroutines.Dispatchers
import kotlinx.coroutines.launch


@SuppressLint("InlinedApi")
@OptIn(ExperimentalPermissionsApi::class)
@Sample(
    name = "Location - Create and monitor Geofence",
    description = "This Sample demonstrate best practices for Creating and monitoring geofence",
    documentation = "https://developer.android.com/training/location/geofencing"
)
@Composable
fun GeofencingScreen() {

    val context = LocalContext.current
    val geofenceManager = remember { GeofenceManager(context) }
    val scope = rememberCoroutineScope()
    val foregroundLocationState = rememberMultiplePermissionsState(
        listOf(
            Manifest.permission.ACCESS_COARSE_LOCATION, Manifest.permission.ACCESS_FINE_LOCATION
        )
    )

    val bgLocationPermissionState = rememberPermissionState(
        Manifest.permission.ACCESS_BACKGROUND_LOCATION
    )

    // Keeps track of the rationale dialog state, needed when the user requires further rationale
    var rationaleState by remember {
        mutableStateOf<RationaleState?>(null)
    }
    var geofenceTransitionEventInfo by remember {
        mutableStateOf("")
    }

    DisposableEffect(LocalLifecycleOwner.current) {
        onDispose {
            scope.launch(Dispatchers.IO) {
                geofenceManager.deregisterGeofence()
            }
        }
    }

    // Register a local broadcast to receive activity transition updates
    GeofenceBroadcastReceiver(systemAction = CUSTOM_INTENT_GEOFENCE) { event ->
        geofenceTransitionEventInfo = event
    }
    Box(
        Modifier
            .fillMaxSize()
            .padding(16.dp)
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .animateContentSize(),
            verticalArrangement = Arrangement.spacedBy(16.dp)
        ) {
            // show permission rationale
            rationaleState?.run { PermissionRationaleDialog(rationaleState = this) }

            PermissionRequestButton(
                isGranted = foregroundLocationState.allPermissionsGranted,
                title = "Precise location access"
            ) {
                if (foregroundLocationState.shouldShowRationale) {
                    rationaleState = RationaleState(
                        "Request Precise Location",
                        "In order to use this feature please grant access by accepting " + "the location permission dialog." + "\n\nWould you like to continue?"
                    ) { accepted ->
                        if (accepted) {
                            foregroundLocationState.launchMultiplePermissionRequest()
                            rationaleState = null
                        }
                    }
                } else {
                    foregroundLocationState.launchMultiplePermissionRequest()
                }
            }

            // Background location permission needed from Android Q,
            // before Android Q, granting Fine or Coarse location access automatically grants Background
            // location access
            if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q) {
                PermissionRequestButton(
                    isGranted = bgLocationPermissionState.status.isGranted,
                    title = "Background location access"
                ) {
                    if (foregroundLocationState.permissions[0].status.isGranted || foregroundLocationState.permissions[1].status.isGranted) {
                        if (bgLocationPermissionState.status.shouldShowRationale) {
                            rationaleState = RationaleState(
                                "Request background location",
                                "In order to use this feature please grant access by accepting " + "the background location permission dialog." + "\n\nWould you like to continue?"
                            ) { accepted ->
                                if (accepted) {
                                    bgLocationPermissionState.launchPermissionRequest()
                                    rationaleState = null
                                }
                            }
                        } else {
                            bgLocationPermissionState.launchPermissionRequest()
                        }

                    } else {
                        Toast.makeText(
                            context,
                            "Please grant either Approximate location access permission or Fine" + "location access permission",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }

            }
            if ((Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q
                        && bgLocationPermissionState.status.isGranted) ||
                (Build.VERSION.SDK_INT < Build.VERSION_CODES.Q
                        && foregroundLocationState.allPermissionsGranted)
            ) {
                GeofenceList(geofenceManager)
                Button(onClick = {
                    if (geofenceManager.geofenceList.isNotEmpty()) {
                        geofenceManager.registerGeofence()
                    } else {
                        Toast.makeText(
                            context,
                            "Please add at least one geofence to monitor",
                            Toast.LENGTH_SHORT
                        ).show()
                    }
                }) {
                    Text(text = "Register Geofences")
                }

                Button(onClick = {
                    scope.launch(Dispatchers.IO) {
                        geofenceManager.deregisterGeofence()
                    }
                }) {
                    Text(text = "Deregister Geofences")
                }
                Spacer(modifier = Modifier.height(16.dp))
                Text(text = geofenceTransitionEventInfo)
            }
        }

        FloatingActionButton(modifier = Modifier.align(Alignment.BottomEnd),
            onClick = {
                context.startActivity(
                    Intent(
                        Settings.ACTION_APPLICATION_DETAILS_SETTINGS,
                        Uri.parse("package:" + context.packageName)
                    )
                )
            }) {
            Icon(Icons.Outlined.Settings, "App Settings")
        }

    }
}

@Composable
fun GeofenceList(geofenceManager: GeofenceManager) {
    // for geofences
    val checkedGeoFence1 = remember { mutableStateOf(false) }
    val checkedGeoFence2 = remember { mutableStateOf(false) }
    val checkedGeoFence3 = remember { mutableStateOf(false) }

    Text(text = "Available Geofence")
    Row(
        Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checkedGeoFence1.value, onCheckedChange = { checked ->
            if (checked) {
                geofenceManager.addGeofence(
                    "statue_of_liberty",
                    location = Location("").apply {
                        latitude = 40.689403968838015
                        longitude = -74.04453795094359
                    })
            } else {
                geofenceManager.removeGeofence("statue_of_libery")
            }
            checkedGeoFence1.value = checked
        })
        Text(text = "Statue of Liberty")
    }
    Row(
        Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checkedGeoFence2.value, onCheckedChange = { checked ->
            if (checked) {
                geofenceManager.addGeofence(
                    "eiffel_tower",
                    location = Location("").apply {
                        latitude = 48.85850
                        longitude = 2.29455
                    })
            } else {
                geofenceManager.removeGeofence("eiffel_tower")
            }
            checkedGeoFence2.value = checked
        })
        Text(text = "Eiffel Tower")
    }
    Row(
        Modifier.padding(8.dp), verticalAlignment = Alignment.CenterVertically
    ) {
        Checkbox(checked = checkedGeoFence3.value, onCheckedChange = { checked ->
            if (checked) {
                geofenceManager.addGeofence(
                    "vatican_city",
                    location = Location("").apply {
                        latitude = 41.90238
                        longitude = 12.45398
                    })
            } else {
                geofenceManager.removeGeofence("vatican_city")
            }
            checkedGeoFence3.value = checked
        })
        Text(text = "Vatican City")
    }
}
