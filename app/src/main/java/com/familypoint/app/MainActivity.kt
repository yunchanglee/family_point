package com.familypoint.app

import android.Manifest
import android.content.pm.PackageManager
import android.os.Build
import android.os.Bundle
import androidx.activity.ComponentActivity
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.activity.compose.setContent
import androidx.activity.result.contract.ActivityResultContracts
import androidx.activity.viewModels
import androidx.compose.material3.MaterialTheme
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.collectAsState
import androidx.compose.runtime.getValue
import androidx.core.content.ContextCompat
import com.familypoint.app.data.UserPreferences
import com.familypoint.app.service.LocationSyncService
import com.familypoint.app.service.LocationSyncWorker
import com.familypoint.app.ui.MainViewModel
import com.familypoint.app.ui.MapScreen
import com.familypoint.app.ui.OnboardingScreen

class MainActivity : ComponentActivity() {

    private val viewModel: MainViewModel by viewModels()

    override fun onCreate(savedInstanceState: Bundle?) {
        super.onCreate(savedInstanceState)

        setContent {
            MaterialTheme {
                val uiState by viewModel.uiState.collectAsState()

                val bgPermissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestPermission()
                ) { _ ->
                    val prefs = UserPreferences(this@MainActivity)
                    if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
                        LocationSyncWorker.schedulePeriodic(this@MainActivity)
                        LocationSyncService.start(this@MainActivity)
                    }
                }

                val permissionLauncher = rememberLauncherForActivityResult(
                    contract = ActivityResultContracts.RequestMultiplePermissions()
                ) { grants ->
                    val locationGranted = grants[Manifest.permission.ACCESS_FINE_LOCATION] == true ||
                        grants[Manifest.permission.ACCESS_COARSE_LOCATION] == true
                    if (locationGranted) {
                        val prefs = UserPreferences(this@MainActivity)
                        if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
                            LocationSyncWorker.schedulePeriodic(this@MainActivity)
                            LocationSyncService.start(this@MainActivity)
                        }
                        if (!uiState.isOnboarding) {
                            viewModel.sendMyLocationNow()
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }

                LaunchedEffect(Unit) {
                    val needed = mutableListOf(
                        Manifest.permission.ACCESS_FINE_LOCATION,
                        Manifest.permission.ACCESS_COARSE_LOCATION
                    )
                    if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.TIRAMISU) {
                        needed.add(Manifest.permission.POST_NOTIFICATIONS)
                    }
                    val missing = needed.filter {
                        ContextCompat.checkSelfPermission(this@MainActivity, it) !=
                            PackageManager.PERMISSION_GRANTED
                    }
                    if (missing.isNotEmpty()) {
                        permissionLauncher.launch(missing.toTypedArray())
                    } else {
                        val prefs = UserPreferences(this@MainActivity)
                        if (prefs.isProfileConfigured && prefs.backgroundSharingEnabled) {
                            LocationSyncWorker.schedulePeriodic(this@MainActivity)
                            LocationSyncService.start(this@MainActivity)
                        }
                        if (Build.VERSION.SDK_INT >= Build.VERSION_CODES.Q &&
                            ContextCompat.checkSelfPermission(
                                this@MainActivity,
                                Manifest.permission.ACCESS_BACKGROUND_LOCATION
                            ) != PackageManager.PERMISSION_GRANTED
                        ) {
                            bgPermissionLauncher.launch(Manifest.permission.ACCESS_BACKGROUND_LOCATION)
                        }
                    }
                }

                if (uiState.isOnboarding) {
                    OnboardingScreen(
                        initialNickname = uiState.nickname,
                        initialAvatarCode = uiState.avatarCode,
                        initialInviteCode = uiState.inviteCode,
                        isSaving = uiState.isLoading,
                        errorMessage = uiState.errorMessage,
                        onSaveProfile = { nickname, avatarCode, inviteCode ->
                            viewModel.saveProfile(nickname, avatarCode, inviteCode)
                        }
                    )
                } else {
                    MapScreen(
                        myMemberId = uiState.myMemberId,
                        inviteCode = uiState.inviteCode,
                        familyMembers = uiState.familyMembers,
                        selectedMember = uiState.selectedMember,
                        historyPoints24h = uiState.historyPoints24h,
                        show24hTrail = uiState.show24hTrail,
                        backgroundSharingEnabled = uiState.backgroundSharingEnabled,
                        isLoading = uiState.isLoading,
                        statusMessage = uiState.statusMessage,
                        onSelectMember = { viewModel.selectMember(it) },
                        onToggle24hTrail = { viewModel.toggle24hTrail(it) },
                        onToggleBackgroundSharing = { viewModel.toggleBackgroundSharing(it) },
                        onSendMyLocationNow = { viewModel.sendMyLocationNow() },
                        onRefreshFamilyLocations = { viewModel.refreshFamilyLocations() },
                        onEditProfile = { viewModel.openProfileEditor() },
                        onDeleteFamilyMember = { viewModel.deleteFamilyMember(it) }
                    )
                }
            }
        }
    }
}
