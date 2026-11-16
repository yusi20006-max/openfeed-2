package com.example.ui.components

import androidx.compose.foundation.BorderStroke
import androidx.compose.foundation.layout.Arrangement
import androidx.compose.foundation.layout.Column
import androidx.compose.foundation.layout.ExperimentalLayoutApi
import androidx.compose.foundation.layout.FlowRow
import androidx.compose.foundation.layout.Row
import androidx.compose.foundation.layout.Spacer
import androidx.compose.foundation.layout.fillMaxWidth
import androidx.compose.foundation.layout.height
import androidx.compose.foundation.layout.navigationBarsPadding
import androidx.compose.foundation.layout.padding
import androidx.compose.foundation.layout.size
import androidx.compose.foundation.layout.width
import androidx.compose.foundation.shape.RoundedCornerShape
import androidx.compose.foundation.text.KeyboardActions
import androidx.compose.foundation.text.KeyboardOptions
import androidx.compose.material.icons.Icons
import androidx.compose.material.icons.rounded.Add
import androidx.compose.material.icons.rounded.Explore
import androidx.compose.material.icons.rounded.RssFeed
import androidx.compose.material3.Button
import androidx.compose.material3.CircularProgressIndicator
import androidx.compose.material3.ExperimentalMaterial3Api
import androidx.compose.material3.Icon
import androidx.compose.material3.MaterialTheme
import androidx.compose.material3.ModalBottomSheet
import androidx.compose.material3.OutlinedButton
import androidx.compose.material3.OutlinedTextField
import androidx.compose.material3.SuggestionChip
import androidx.compose.material3.Text
import androidx.compose.material3.rememberModalBottomSheetState
import androidx.compose.runtime.Composable
import androidx.compose.runtime.getValue
import androidx.compose.runtime.mutableStateOf
import androidx.compose.runtime.remember
import androidx.compose.runtime.setValue
import androidx.compose.ui.Alignment
import androidx.compose.ui.Modifier
import androidx.compose.ui.platform.testTag
import androidx.compose.ui.res.stringResource
import androidx.compose.ui.text.input.ImeAction
import androidx.compose.ui.unit.dp
import com.example.R
import com.example.data.model.StarterChannels
import com.example.data.model.SuggestedChannel
import com.example.data.model.TransportMode

@OptIn(ExperimentalMaterial3Api::class, ExperimentalLayoutApi::class)
@Composable
fun AddChannelBottomSheet(
    isAdding: Boolean,
    errorMessage: String?,
    subscribedUsernames: Set<String>,
    transportMode: TransportMode,
    onAddChannel: (String) -> Unit,
    onSubscribeSuggested: (SuggestedChannel) -> Unit,
    onOpenDiscover: () -> Unit,
    onDismiss: () -> Unit
) {
    val sheetState = rememberModalBottomSheetState(skipPartiallyExpanded = true)
    var input by remember { mutableStateOf("") }

    val quickPicks = remember {
        StarterChannels.persianTelegramChannels.take(4) + StarterChannels.rssFeeds.take(4)
    }

    ModalBottomSheet(
        onDismissRequest = onDismiss,
        sheetState = sheetState,
        containerColor = MaterialTheme.colorScheme.surface
    ) {
        Column(
            modifier = Modifier
                .fillMaxWidth()
                .navigationBarsPadding()
                .padding(horizontal = 20.dp)
                .padding(bottom = 24.dp)
        ) {
            Text(
                text = stringResource(R.string.add_channel_title),
                style = MaterialTheme.typography.headlineSmall,
                color = MaterialTheme.colorScheme.onSurface
            )
            Spacer(modifier = Modifier.height(6.dp))
            Text(
                text = "Paste @username, t.me/channel, or any https://.../feed RSS/Atom URL.",
                style = MaterialTheme.typography.bodySmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(16.dp))

            Row(
                verticalAlignment = Alignment.CenterVertically,
                modifier = Modifier.fillMaxWidth()
            ) {
                OutlinedTextField(
                    value = input,
                    onValueChange = { input = it },
                    placeholder = { Text(stringResource(R.string.add_channel_hint)) },
                    leadingIcon = {
                        Icon(
                            imageVector = Icons.Rounded.RssFeed,
                            contentDescription = null
                        )
                    },
                    singleLine = true,
                    isError = !errorMessage.isNullOrBlank(),
                    keyboardOptions = KeyboardOptions(imeAction = ImeAction.Done),
                    keyboardActions = KeyboardActions(
                        onDone = {
                            if (input.isNotBlank() && !isAdding) {
                                onAddChannel(input)
                            }
                        }
                    ),
                    modifier = Modifier
                        .weight(1f)
                        .testTag("add_channel_input")
                )
                Spacer(modifier = Modifier.width(10.dp))
                Button(
                    onClick = { onAddChannel(input) },
                    enabled = input.isNotBlank() && !isAdding,
                    shape = RoundedCornerShape(12.dp),
                    modifier = Modifier
                        .height(54.dp)
                        .testTag("confirm_add_channel_button")
                ) {
                    if (isAdding) {
                        CircularProgressIndicator(
                            modifier = Modifier.size(18.dp),
                            strokeWidth = 2.dp,
                            color = MaterialTheme.colorScheme.onPrimary
                        )
                    } else {
                        Icon(
                            imageVector = Icons.Rounded.Add,
                            contentDescription = null,
                            modifier = Modifier.size(18.dp)
                        )
                        Spacer(modifier = Modifier.width(4.dp))
                        Text(stringResource(R.string.add_channel_button))
                    }
                }
            }

            if (!errorMessage.isNullOrBlank()) {
                Spacer(modifier = Modifier.height(8.dp))
                Text(
                    text = errorMessage,
                    style = MaterialTheme.typography.bodySmall,
                    color = MaterialTheme.colorScheme.error
                )
            }

            Spacer(modifier = Modifier.height(18.dp))
            Text(
                text = "Quick Starter Picks (Telegram & RSS)",
                style = MaterialTheme.typography.titleSmall,
                color = MaterialTheme.colorScheme.onSurfaceVariant
            )
            Spacer(modifier = Modifier.height(10.dp))

            FlowRow(
                horizontalArrangement = Arrangement.spacedBy(8.dp),
                verticalArrangement = Arrangement.spacedBy(8.dp)
            ) {
                quickPicks.forEach { suggested ->
                    val alreadyAdded = subscribedUsernames.any {
                        it.equals(suggested.username, ignoreCase = true)
                    }
                    SuggestionChip(
                        onClick = {
                            if (!alreadyAdded && !isAdding) {
                                onSubscribeSuggested(suggested)
                            }
                        },
                        enabled = !alreadyAdded && !isAdding,
                        icon = {
                            ChannelAvatar(
                                title = suggested.title,
                                avatarUrl = suggested.logoUrl,
                                transportMode = transportMode,
                                sizeDp = 22,
                                sourceType = suggested.sourceType,
                                username = suggested.username
                            )
                        },
                        border = BorderStroke(
                            1.dp,
                            if (alreadyAdded) MaterialTheme.colorScheme.primary
                            else MaterialTheme.colorScheme.outline
                        ),
                        label = {
                            Text(
                                text = if (alreadyAdded) "✓ ${suggested.title}" else suggested.title,
                                style = MaterialTheme.typography.labelMedium
                            )
                        },
                        modifier = Modifier.testTag("starter_chip_${suggested.username}")
                    )
                }
            }

            Spacer(modifier = Modifier.height(14.dp))
            OutlinedButton(
                onClick = {
                    onDismiss()
                    onOpenDiscover()
                },
                shape = RoundedCornerShape(12.dp),
                modifier = Modifier
                    .fillMaxWidth()
                    .testTag("sheet_open_discover_button")
            ) {
                Icon(
                    imageVector = Icons.Rounded.Explore,
                    contentDescription = null,
                    modifier = Modifier.size(18.dp)
                )
                Spacer(modifier = Modifier.width(8.dp))
                Text("Browse 50 Persian Channels & 36 RSS Feeds")
            }
        }
    }
}
