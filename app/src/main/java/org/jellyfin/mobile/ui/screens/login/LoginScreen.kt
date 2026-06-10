package org.jellyfin.mobile.ui.screens.login

import android.view.KeyEvent
import androidx.compose.animation.AnimatedVisibility
import androidx.compose.foundation.Image
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxSize
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.systemBarsPadding
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.CircularProgressIndicator
import androidx.compose.material.MaterialTheme
import androidx.compose.material.OutlinedTextField
import androidx.compose.material.Surface
import androidx.compose.material.Text
import androidx.compose.runtime.Composable
import androidx.compose.runtime.LaunchedEffect
import androidx.compose.runtime.MutableState
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.rememberCoroutineScope
import androidx.compose.runtime.setValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.input.key.onKeyEvent
import androidx.compose.ui.platform.LocalSoftwareKeyboardController
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.text.input.KeyboardType
import androidx.compose.ui.text.input.PasswordVisualTransformation
import androidx.compose.ui.unit.dp
import kotlinx.coroutines.launch
import org.jellyfin.mobile.R
import org.jellyfin.mobile.ui.screens.connect.LogoHeader
import org.jellyfin.mobile.ui.screens.connect.StyledTextButton
import org.jellyfin.sdk.api.client.ApiClient
import org.jellyfin.sdk.api.client.extensions.userApi
import org.jellyfin.sdk.api.operations.UserApi
import org.jellyfin.sdk.model.api.AuthenticateUserByName
import org.koin.compose.koinInject

sealed class LoginState {
    data object Idle : LoginState()
    data object Pending : LoginState()
    data class Error(val message: String) : LoginState()
    data class Success(val userId: String, val accessToken: String, val serverId: String) : LoginState()
}

@Composable
fun LoginScreen(
    serverHostname: String,
    onAuthenticated: (userId: String, accessToken: String, serverId: String) -> Unit,
    onSwitchServer: () -> Unit,
    apiClient: ApiClient = koinInject(),
) {
    Surface(color = MaterialTheme.colors.background) {
        Column(
            modifier = Modifier
                .fillMaxSize()
                .systemBarsPadding()
                .padding(horizontal = 16.dp),
        ) {
            LogoHeader()

            Text(
                text = stringResource(R.string.login_title),
                style = MaterialTheme.typography.h5,
                modifier = Modifier.padding(bottom = 4.dp),
            )
            Text(
                text = stringResource(R.string.login_subtitle, prettyHost(serverHostname)),
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(bottom = 16.dp),
            )

            val invalidMessage = stringResource(R.string.login_error_invalid)
            val networkMessage = stringResource(R.string.login_error_network)
            LoginForm(
                userApi = apiClient.userApi,
                invalidMessage = invalidMessage,
                networkMessage = networkMessage,
                onAuthenticated = onAuthenticated,
                onSwitchServer = onSwitchServer,
            )
        }
    }
}

@Composable
private fun LoginForm(
    userApi: UserApi,
    invalidMessage: String,
    networkMessage: String,
    onAuthenticated: (String, String, String) -> Unit,
    onSwitchServer: () -> Unit,
) {
    val coroutineScope = rememberCoroutineScope()
    val keyboardController = LocalSoftwareKeyboardController.current

    var username by remember { mutableStateOf("") }
    var password by remember { mutableStateOf("") }
    var loginState: LoginState by remember<MutableState<LoginState>> { mutableStateOf(LoginState.Idle) }

    LaunchedEffect(loginState) {
        if (loginState is LoginState.Success) {
            val s = loginState as LoginState.Success
            onAuthenticated(s.userId, s.accessToken, s.serverId)
        }
    }

    fun submit() {
        if (username.isBlank() || password.isBlank() || loginState is LoginState.Pending) return
        keyboardController?.hide()
        loginState = LoginState.Pending
        coroutineScope.launch {
            loginState = try {
                val response = userApi.authenticateUserByName(
                    AuthenticateUserByName(username = username, pw = password),
                )
                val auth = response.content
                val token = auth.accessToken
                val userId = auth.user?.id?.toString()
                val serverId = auth.serverId
                if (token != null && userId != null && serverId != null) {
                    LoginState.Success(userId = userId, accessToken = token, serverId = serverId)
                } else {
                    LoginState.Error(invalidMessage)
                }
            } catch (e: Throwable) {
                val msg = e.message.orEmpty()
                if (msg.contains("401") || msg.contains("Unauthorized", ignoreCase = true)) {
                    LoginState.Error(invalidMessage)
                } else {
                    LoginState.Error(networkMessage)
                }
            }
        }
    }

    Column {
        OutlinedTextField(
            value = username,
            onValueChange = {
                username = it
                if (loginState is LoginState.Error) loginState = LoginState.Idle
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp),
            label = { Text(stringResource(R.string.username_input_hint)) },
            singleLine = true,
            enabled = loginState !is LoginState.Pending,
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Text,
                imeAction = ImeAction.Next,
            ),
        )

        OutlinedTextField(
            value = password,
            onValueChange = {
                password = it
                if (loginState is LoginState.Error) loginState = LoginState.Idle
            },
            modifier = Modifier
                .fillMaxWidth()
                .padding(bottom = 8.dp)
                .onKeyEvent { keyEvent ->
                    if (keyEvent.nativeKeyEvent.keyCode == KeyEvent.KEYCODE_ENTER) {
                        submit()
                        true
                    } else false
                },
            label = { Text(stringResource(R.string.password_input_hint)) },
            singleLine = true,
            enabled = loginState !is LoginState.Pending,
            visualTransformation = PasswordVisualTransformation(),
            keyboardOptions = KeyboardOptions(
                keyboardType = KeyboardType.Password,
                imeAction = ImeAction.Go,
            ),
            keyboardActions = KeyboardActions(onGo = { submit() }),
        )

        AnimatedVisibility(visible = loginState is LoginState.Error) {
            Text(
                text = (loginState as? LoginState.Error)?.message.orEmpty(),
                color = MaterialTheme.colors.error,
                style = MaterialTheme.typography.caption,
                modifier = Modifier.padding(top = 4.dp, bottom = 4.dp),
            )
        }

        if (loginState is LoginState.Pending) {
            Spacer(modifier = Modifier.height(12.dp))
            androidx.compose.foundation.layout.Box(
                modifier = Modifier.fillMaxWidth(),
                contentAlignment = androidx.compose.ui.Alignment.Center,
            ) {
                CircularProgressIndicator()
            }
        } else {
            Spacer(modifier = Modifier.height(12.dp))
            StyledTextButton(
                text = stringResource(R.string.login_button_text),
                enabled = username.isNotBlank() && password.isNotBlank(),
                onClick = ::submit,
            )
            StyledTextButton(
                text = stringResource(R.string.login_change_server),
                onClick = onSwitchServer,
                primary = false,
            )
        }
    }
}

private fun prettyHost(hostname: String): String =
    hostname.removePrefix("https://").removePrefix("http://").removeSuffix("/")
