// LoginScreen.kt
package com.moneromerchant.pos.ui.payment.login

import androidx.compose.foundation.layout.*
import androidx.compose.foundation.rememberScrollState
import androidx.compose.foundation.verticalScroll
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.text.KeyboardOptions
import androidx.activity.compose.rememberLauncherForActivityResult
import androidx.compose.material3.*
import androidx.compose.runtime.*
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import androidx.navigation.NavHostController
import com.moneromerchant.pos.ui.common.composables.CustomAlertDialog
import com.moneromerchant.pos.R
import com.moneromerchant.pos.ui.common.composables.CustomOutlinedTextField
import com.journeyapps.barcodescanner.ScanContract
import com.journeyapps.barcodescanner.ScanOptions

// LoginScreenRoot
@Composable
fun LoginScreenRoot(viewModel: LoginViewModel, navController: NavHostController) {
    viewModel.setNavController(navController)
    val scanner = rememberLauncherForActivityResult(ScanContract()) { result ->
        result.contents?.let(viewModel::loginFromQr)
    }
    LoginScreen(
        instanceUrl = viewModel.instanceUrl,
        vendorID = viewModel.vendorID,
        username = viewModel.username,
        password = viewModel.password,
        errorMessage = viewModel.errorMessage,
        inProgress = viewModel.inProgress,
        updateInstanceUrl = viewModel::updateInstanceUrl,
        updateVendorID = viewModel::updateVendorID,
        updateUsername = viewModel::updateUsername,
        updatePassword = viewModel::updatePassword,
        resetErrorMessage = viewModel::resetErrorMessage,
        loginPressed = viewModel::loginPressed,
        scanQrPressed = {
            scanner.launch(ScanOptions().apply {
                setDesiredBarcodeFormats(ScanOptions.QR_CODE)
                addExtra("CHARACTER_SET", "UTF-8")
                setPrompt("Scan the POS login QR code")
                setBeepEnabled(false)
                setBarcodeImageEnabled(false)
                setOrientationLocked(false)
            })
        },
    )
}

// LoginScreen
@Composable
fun LoginScreen(
    instanceUrl: String,
    vendorID: String,
    username: String,
    password: String,
    errorMessage: String,
    inProgress: Boolean,
    updateInstanceUrl: (String) -> Unit,
    updateVendorID: (String) -> Unit,
    updateUsername: (String) -> Unit,
    updatePassword: (String) -> Unit,
    resetErrorMessage: () -> Unit,
    loginPressed: () -> Unit,
    scanQrPressed: () -> Unit,

) {
    Scaffold(modifier = Modifier.fillMaxSize().imePadding()) { innerPadding ->
        Column(
            verticalArrangement = Arrangement.Center,
            horizontalAlignment = Alignment.CenterHorizontally,
            modifier = Modifier.padding(innerPadding).fillMaxSize().verticalScroll(rememberScrollState()).padding(horizontal = 80.dp)
        ) {
            Text("Login to POS account", style = MaterialTheme.typography.titleLarge)
            Spacer(modifier = Modifier.height(48.dp))
            CustomOutlinedTextField(
                value = instanceUrl, label = "Instance url", onValueChange = {updateInstanceUrl(it)}
            )
            CustomOutlinedTextField(
                value = vendorID, label = "Vendor id", onValueChange = {updateVendorID(it)}, keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Number)
            )
            CustomOutlinedTextField(
                value = username, label = "Username", onValueChange = {updateUsername(it)}
            )
            CustomOutlinedTextField(
                value = password, label = "Password", onValueChange = {updatePassword(it)}, visualTransformation = PasswordVisualTransformation(), keyboardOptions = KeyboardOptions.Default.copy(keyboardType = KeyboardType.Password)
            )
            Spacer(modifier = Modifier.height(16.dp))
            Row(
                horizontalArrangement = Arrangement.spacedBy(12.dp),
                verticalAlignment = Alignment.CenterVertically,
            ) {
                FilledTonalButton(
                    onClick = loginPressed,
                    enabled = !inProgress,
                ) {
                    Row {
                        Text("Login")
                        if (inProgress) {
                            Spacer(modifier = Modifier.width(8.dp))
                            CircularProgressIndicator(modifier = Modifier.height(20.dp).width(20.dp))
                        }
                    }
                }
                FilledTonalButton(onClick = scanQrPressed, enabled = !inProgress) {
                    Text("Scan QR")
                }
            }
        }
        when {
            errorMessage != "" -> {
                CustomAlertDialog(
                    onDismissRequest = { resetErrorMessage() },
                    onConfirmation = {
                        resetErrorMessage()
                    },
                    dialogTitle = "Error",
                    dialogText = errorMessage,
                    confirmButtonText = "Ok",
                    icon = {
                        Icon(painter = painterResource(R.drawable.warning_24px), tint = MaterialTheme.colorScheme.primary, contentDescription = "Warning")
                    }
                )
            }
        }

    }
}

