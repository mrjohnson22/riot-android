/*
 * Copyright 2019 New Vector Ltd
 *
 * Licensed under the Apache License, Version 2.0 (the "License");
 * you may not use this file except in compliance with the License.
 * You may obtain a copy of the License at
 *
 *     http://www.apache.org/licenses/LICENSE-2.0
 *
 * Unless required by applicable law or agreed to in writing, software
 * distributed under the License is distributed on an "AS IS" BASIS,
 * WITHOUT WARRANTIES OR CONDITIONS OF ANY KIND, either express or implied.
 * See the License for the specific language governing permissions and
 * limitations under the License.
 */
package im.vector.fragments.discovery

import androidx.lifecycle.MutableLiveData
import com.airbnb.mvrx.BaseMvRxViewModel
import com.airbnb.mvrx.MvRxState
import com.airbnb.mvrx.MvRxViewModelFactory
import com.airbnb.mvrx.ViewModelContext
import im.vector.Matrix
import im.vector.R
import im.vector.activity.ReviewTermsActivity
import im.vector.ui.arch.LiveEvent
import org.matrix.androidsdk.MXSession
import org.matrix.androidsdk.core.callback.ApiCallback
import org.matrix.androidsdk.core.model.MatrixError
import org.matrix.androidsdk.features.terms.GetTermsResponse
import org.matrix.androidsdk.features.terms.TermsManager

data class SetIdentityServerState(
        val existingIdentityServer: String? = null,
        val newIdentityServer: String? = null,
        val errorMessageId: Int? = null,
        val isVerifyingServer: Boolean = false
) : MvRxState

class SetIdentityServerViewModel(private val mxSession: MXSession?,
                                 private val userLanguage: String,
                                 initialState: SetIdentityServerState)
    : BaseMvRxViewModel<SetIdentityServerState>(initialState, false) {

    var navigateEvent = MutableLiveData<LiveEvent<String>>()

    fun updateServerName(server: String) {
        setState {
            copy(
                    newIdentityServer = server,
                    errorMessageId = null
            )
        }
    }

    fun doChangeServerName() = withState {
        var baseUrl: String? = it.newIdentityServer
        if (baseUrl.isNullOrBlank()) {
            setState {
                copy(errorMessageId = R.string.settings_discovery_please_enter_server)
            }
            return@withState
        }
        baseUrl = sanitatizeBaseURL(baseUrl)
        setState {
            copy(isVerifyingServer = true)
        }

        mxSession?.termsManager?.get(TermsManager.ServiceType.IdentityService, baseUrl,
                object : ApiCallback<GetTermsResponse> {
                    override fun onSuccess(info: GetTermsResponse) {
                        //has all been accepted?
                        setState {
                            copy(isVerifyingServer = false)
                        }
                        val resp = info.serverResponse
                        val tos = resp.getLocalizedPrivacyPolicies(userLanguage)
                        val policy = resp.getLocalizedPrivacyPolicies(userLanguage)
                        if (tos == null && policy == null) {
                            //prompt do not define policy
                            navigateEvent.value = LiveEvent(NAVIGATE_NO_TERMS)
                        } else {
                            val shouldPrompt = listOf(tos, policy)
                                    .filter { it != null && !info.alreadyAcceptedTermUrls.contains(it.localizedUrl) }.isNotEmpty()
                            if (shouldPrompt) {
                                navigateEvent.value = LiveEvent(NAVIGATE_SHOW_TERMS)
                            } else {
                                navigateEvent.value = LiveEvent(NAVIGATE_TERMS_ACCEPTED)
                            }
                        }
                    }

                    override fun onUnexpectedError(e: Exception) {
                        setState {
                            copy(
                                    isVerifyingServer = false,
                                    errorMessageId = R.string.settings_discovery_bad_indentity_server
                            )
                        }
                    }

                    override fun onNetworkError(e: Exception) {
                        setState {
                            copy(
                                    isVerifyingServer = false,
                                    errorMessageId = R.string.settings_discovery_bad_indentity_server
                            )
                        }
                    }

                    override fun onMatrixError(e: MatrixError) {
                        setState {
                            copy(
                                    isVerifyingServer = false,
                                    errorMessageId = R.string.settings_discovery_bad_indentity_server
                            )
                        }
                    }

                })
    }

    fun sanitatizeBaseURL(baseUrl: String): String {
        var baseUrl1 = baseUrl
        if (!baseUrl1.startsWith("http://") && !baseUrl1.startsWith("https://")) {
            baseUrl1 = "https://$baseUrl1"
        }
        return baseUrl1
    }


    companion object : MvRxViewModelFactory<SetIdentityServerViewModel, SetIdentityServerState> {

        const val NAVIGATE_SHOW_TERMS = "NAVIGATE_SHOW_TERMS"
        const val NAVIGATE_NO_TERMS = "NAVIGATE_NO_TERMS"
        const val NAVIGATE_TERMS_ACCEPTED = "NAVIGATE_TERMS_ACCEPTED"

        override fun create(viewModelContext: ViewModelContext, state: SetIdentityServerState): SetIdentityServerViewModel? {
            val fArgs = viewModelContext.args<SetIdentityServerFragmentArgs>()
            val session = Matrix.getInstance(viewModelContext.activity).getSession(fArgs.matrixId)
            return SetIdentityServerViewModel(session, viewModelContext.activity.getString(R.string.resources_language), SetIdentityServerState(fArgs.serverName))
        }
    }
}