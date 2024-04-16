package com.blockstream.compose.components

import androidx.compose.foundation.layout.padding
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.ui.Modifier
import androidx.compose.ui.res.painterResource
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.tooling.preview.Preview
import androidx.compose.ui.unit.dp
import androidx.lifecycle.compose.collectAsStateWithLifecycle
import com.blockstream.common.data.Banner
import com.blockstream.common.events.Events
import com.blockstream.common.models.GreenViewModel
import com.blockstream.compose.R
import com.blockstream.compose.theme.GreenTheme
import com.blockstream.compose.utils.AnimatedNullableVisibility

@Composable
fun Banner(viewModel: GreenViewModel, withTopPadding: Boolean = false) {
    val bannerOrNull by viewModel.banner.collectAsStateWithLifecycle()

    AnimatedNullableVisibility(bannerOrNull) { banner ->
        Banner(
            banner,
            modifier = Modifier.padding(top = if (withTopPadding) 16.dp else 0.dp),
            onClick = {
                viewModel.postEvent(Events.BannerAction)
            },
            onClose = {
                viewModel.postEvent(Events.BannerDismiss)
            })
    }
}

@Composable
fun Banner(
    banner: Banner,
    modifier: Modifier = Modifier,
    onClick: (url: String) -> Unit = {},
    onClose: () -> Unit = {},
) {
    GreenAlert(
        modifier = modifier,
        title = banner.title,
        message = banner.message,
        maxLines = 5,
        icon = if (banner.isWarning) painterResource(id = R.drawable.warning) else null,
        primaryButton = if (banner.link != null) stringResource(R.string.id_learn_more) else null,
        onPrimaryClick = {
            onClick.invoke(banner.link ?: "")
        },

        onCloseClick = if (banner.dismissable == true) {
            {
                onClose.invoke()
            }
        } else null
    )
}


@Composable
@Preview
fun BannerPreview() {
    GreenTheme {
        GreenColumn {
            Banner(Banner.preview1)
            Banner(Banner.preview2)
            Banner(Banner.preview3)
        }
    }
}