package com.blockstream.green.ui.settings

import android.os.Bundle
import android.view.View
import android.widget.Toast
import androidx.compose.ui.platform.ViewCompositionStrategy
import androidx.navigation.fragment.navArgs
import com.blockstream.common.models.GreenViewModel
import com.blockstream.common.models.settings.WalletSettingsSection
import com.blockstream.common.models.settings.WalletSettingsViewModel
import com.blockstream.compose.AppFragmentBridge
import com.blockstream.compose.screens.settings.ChangePinScreen
import com.blockstream.compose.screens.settings.WatchOnlyScreen
import com.blockstream.green.R
import com.blockstream.green.databinding.ChangePinFragmentBinding
import com.blockstream.green.databinding.ComposeViewBinding
import com.blockstream.green.ui.AppFragment
import com.blockstream.green.views.GreenPinViewListener
import org.koin.androidx.viewmodel.ext.android.viewModel
import org.koin.core.parameter.parametersOf

class ChangePinFragment : AppFragment<ComposeViewBinding>(R.layout.compose_view, 0) {

    val args : WalletSettingsFragmentArgs by navArgs()

    val viewModel: WalletSettingsViewModel by viewModel {
        parametersOf(args.wallet, WalletSettingsSection.ChangePin, null)
    }

    override fun getGreenViewModel() = viewModel

    override val useCompose: Boolean = true

    override fun onViewCreated(view: View, savedInstanceState: Bundle?) {
        super.onViewCreated(view, savedInstanceState)

        binding.composeView.apply {
            setViewCompositionStrategy(
                ViewCompositionStrategy.DisposeOnViewTreeLifecycleDestroyed
            )
            setContent {
                AppFragmentBridge {
                    ChangePinScreen(viewModel = viewModel)
                }
            }
        }
    }
}
