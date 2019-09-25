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

import android.os.Bundle
import android.view.View
import androidx.appcompat.app.AlertDialog
import androidx.core.view.isVisible
import androidx.lifecycle.Observer
import androidx.lifecycle.ViewModelProviders
import butterknife.BindView
import com.airbnb.epoxy.EpoxyRecyclerView
import com.airbnb.mvrx.*
import im.vector.R
import im.vector.activity.MXCActionBarActivity
import im.vector.extensions.withArgs
import im.vector.fragments.VectorBaseMvRxFragment
import org.matrix.androidsdk.rest.model.pid.ThreePid


class VectorSettingsDiscoveryFragment : VectorBaseMvRxFragment(), SettingsDiscoveryController.InteractionListener {


    override fun getLayoutResId() = R.layout.fragment_simple_epoxy

    private val viewModel by fragmentViewModel(DiscoverySettingsViewModel::class)

    private lateinit var controller: SettingsDiscoveryController

    private var mLoadingView: View? = null

    lateinit var sharedViewModel: DiscoverySharedViewModel

    @BindView(R.id.epoxyRecyclerView)
    lateinit var epoxyRecyclerView: EpoxyRecyclerView

    override fun onActivityCreated(savedInstanceState: Bundle?) {
        super.onActivityCreated(savedInstanceState)

        sharedViewModel = ViewModelProviders.of(requireActivity()).get(DiscoverySharedViewModel::class.java)

        controller = SettingsDiscoveryController(requireContext(), this).also {
            epoxyRecyclerView.setController(it)
        }

        mLoadingView = requireActivity().findViewById(R.id.vector_settings_spinner_views)

        sharedViewModel.navigateEvent.observe(this, Observer {
            if (it.peekContent().first == DiscoverySharedViewModel.NEW_IDENTITY_SERVER_SET_REQUEST) {
                viewModel.changeIdentityServer(it.peekContent().second)
            }
        })
    }

    override fun invalidate() = withState(viewModel) { state ->
        mLoadingView?.isVisible = state.modalLoadingState is Loading

        controller.setData(state)
    }

    override fun onResume() {
        super.onResume()
        (activity as? MXCActionBarActivity)?.supportActionBar?.setTitle(R.string.settings_discovery_category)
        viewModel.startListenToIdentityManager()

        //If some 3pids are pending, we can try to check if they have been verified here
        withState(viewModel) { state ->
            state.emailList.invoke()?.forEach { info ->
                when (info.isShared.invoke()) {
                    PidInfo.SharedState.NOT_VERIFIED_FOR_BIND,
                    PidInfo.SharedState.NOT_VERIFIED_FOR_UNBIND -> {
                        val bind = info.isShared.invoke() == PidInfo.SharedState.NOT_VERIFIED_FOR_BIND
                        viewModel.add3pid(ThreePid.MEDIUM_EMAIL, info.value, bind)
                    }
                }
            }
        }
    }

    override fun onPause() {
        mLoadingView?.isVisible = false
        super.onPause()
        viewModel.stopListenToIdentityManager()
    }

    override fun onSelectIdentityServer() = withState(viewModel) { state ->

    }

    override fun onTapRevokeEmail(email: String) {
        viewModel.revokeEmail(email)
    }

    override fun onTapShareEmail(email: String) {
        viewModel.shareEmail(email)
    }

    override fun checkEmailVerification(email: String, bind: Boolean) {
        viewModel.add3pid(ThreePid.MEDIUM_EMAIL, email, bind)
    }

    override fun checkPNVerification(msisdn: String, code: String, bind: Boolean) {
        viewModel.submitPNToken(msisdn, code, bind)
    }

    override fun onTapRevokePN(pn: String) {
        viewModel.revokePN(pn)
    }

    override fun onTapSharePN(pn: String) {
        viewModel.sharePN(pn)
    }

    override fun onTapChangeIdentityServer(): Unit = withState(viewModel) { state ->

        //we should prompt if there are bound items with current is
        withState(viewModel) { state ->
            val pidList = ArrayList<PidInfo>().apply {
                state.emailList.invoke()?.let { addAll(it) }
                state.phoneNumbersList.invoke()?.let { addAll(it) }
            }

            val hasBoundIds = pidList.any { it.isShared.invoke() == PidInfo.SharedState.SHARED }

            if (hasBoundIds) {
                //we should prompt
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.change_identity_server)
                        .setMessage(
                                requireContext().getString(R.string.settings_discovery_disconnect_with_bound_pid,
                                        state.identityServer.invoke(), state.identityServer.invoke())
                        )
                        .setNegativeButton(R.string._continue) { _, _ ->
                            navigateToChangeIsFragment(state)
                        }
                        .setPositiveButton(R.string.cancel, null)
                        .show()
                return@withState
            }
            navigateToChangeIsFragment(state)
        }
    }

    override fun onTapDisconnectIdentityServer() {
        //we should prompt if there are bound items with current is
        withState(viewModel) { state ->
            val pidList = ArrayList<PidInfo>().apply {
                state.emailList.invoke()?.let { addAll(it) }
                state.phoneNumbersList.invoke()?.let { addAll(it) }
            }

            val hasBoundIds = pidList.any { it.isShared.invoke() == PidInfo.SharedState.SHARED }

            if (hasBoundIds) {
                //we should prompt
                AlertDialog.Builder(requireContext())
                        .setTitle(R.string.disconnect_identity_server)
                        .setMessage(
                                requireContext().getString(R.string.settings_discovery_disconnect_with_bound_pid,
                                        state.identityServer.invoke(), state.identityServer.invoke())
                        )
                        .setNegativeButton(R.string._continue) { _, _ ->
                            viewModel.changeIdentityServer(null)
                        }
                        .setPositiveButton(R.string.cancel, null)
                        .show()
                return@withState
            }
            viewModel.changeIdentityServer(null)
        }
    }

    private fun navigateToChangeIsFragment(state: DiscoverySettingsState) {
        SetIdentityServerFragment.newInstance(args<String>().toString(), state.identityServer.invoke()).also {
            requireFragmentManager().beginTransaction()
                    .setCustomAnimations(R.anim.anim_slide_in_bottom, R.anim.anim_slide_out_bottom,
                            R.anim.anim_slide_in_bottom, R.anim.anim_slide_out_bottom)
                    .replace(R.id.vector_settings_page, it, getString(R.string.identity_server))
                    .addToBackStack(null)
                    .commit()
        }
    }

    companion object {
        fun newInstance(matrixId: String) = VectorSettingsDiscoveryFragment()
                .withArgs {
                    putString(MvRx.KEY_ARG, matrixId)
                }

    }

}