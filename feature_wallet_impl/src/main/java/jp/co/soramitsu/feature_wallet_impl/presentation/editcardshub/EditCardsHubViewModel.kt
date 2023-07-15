package jp.co.soramitsu.feature_wallet_impl.presentation.editcardshub

import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import javax.inject.Inject
import jp.co.soramitsu.common.R
import jp.co.soramitsu.common.base.theOnlyRoute
import jp.co.soramitsu.common.domain.CardHubType
import jp.co.soramitsu.common.presentation.compose.uikit.tokens.Text
import jp.co.soramitsu.common.presentation.viewmodel.BaseViewModel
import jp.co.soramitsu.feature_wallet_api.launcher.WalletRouter
import jp.co.soramitsu.feature_wallet_impl.domain.CardsHubInteractorImpl
import jp.co.soramitsu.ui_core.component.toolbar.BasicToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarState
import jp.co.soramitsu.ui_core.component.toolbar.SoramitsuToolbarType
import kotlinx.coroutines.channels.BufferOverflow
import kotlinx.coroutines.delay
import kotlinx.coroutines.flow.MutableSharedFlow
import kotlinx.coroutines.flow.SharingStarted
import kotlinx.coroutines.flow.StateFlow
import kotlinx.coroutines.flow.launchIn
import kotlinx.coroutines.flow.map
import kotlinx.coroutines.flow.onEach
import kotlinx.coroutines.flow.stateIn
import kotlinx.coroutines.launch

@HiltViewModel
class EditCardsHubViewModel @Inject constructor(
    private val walletRouter: WalletRouter,
    private val cardsHubInteractor: CardsHubInteractorImpl
) : BaseViewModel() {

    private val mutableState = MutableSharedFlow<List<Pair<CardHubType, Boolean>>>(
        replay = 1,
        extraBufferCapacity = 0,
        onBufferOverflow = BufferOverflow.DROP_OLDEST
    )

    override fun startScreen() = theOnlyRoute

    val state: StateFlow<EditCardsHubScreenState> = mutableState.map { cardsHub ->
        EditCardsHubScreenState(
            toolbarState = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = R.string.edit_cards_screen_edit_view,
                    navIcon = R.drawable.ic_cross_24
                ),
                type = SoramitsuToolbarType.SmallCentered()
            ),
            enabledCardsHeader = Text.StringRes(id = R.string.edit_cards_screen_enabled_card_header),
            enabledCards = cardsHub.filter {
                it.second
            }.map { (cardType, isSelected) ->
                cardType.mapToText() to isSelected
            },
            disabledCardsHeader = Text.StringRes(id = R.string.edit_cards_screen_disabled_card_header),
            disabledCards = cardsHub.filter {
                !it.second
            }.map { (cardType, isSelected) ->
                cardType.mapToText() to isSelected
            }
        )
    }.stateIn(
        scope = viewModelScope,
        started = SharingStarted.WhileSubscribed(),
        initialValue = EditCardsHubScreenState(
            toolbarState = SoramitsuToolbarState(
                basic = BasicToolbarState(
                    title = "",
                    navIcon = null
                ),
                type = SoramitsuToolbarType.SmallCentered()
            ),
            enabledCardsHeader = Text.SimpleText(""),
            enabledCards = emptyList(),
            disabledCardsHeader = Text.SimpleText(""),
            disabledCards = emptyList()
        )
    )

    private fun CardHubType.mapToText() = when (this) {
        CardHubType.ASSETS -> Text.StringRes(R.string.liquid_assets)
        CardHubType.POOLS -> Text.StringRes(R.string.pooled_assets)
        CardHubType.GET_SORA_CARD -> Text.SimpleText("Sora Card")
        CardHubType.BUY_XOR_TOKEN -> Text.SimpleText("Buy Xor")
    }

    init {
        cardsHubInteractor.subscribeVisibleCardsHubList().onEach { (_, cardsHubListState) ->
            mutableState.tryEmit(
                value = cardsHubListState.map {
                    it.cardType to it.visibility
                }
            )
        }.launchIn(viewModelScope)
    }

    override fun onNavIcon() {
        super.onNavIcon()
        walletRouter.popBackStackFragment()
    }

    fun onEnabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            mutableState.replayCache.firstOrNull()?.filter {
                it.second
            }?.getOrNull(position)?.let { (cardHub, isSelected) ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = !isSelected
                )
            }
        }

    fun onDisabledCardItemClick(position: Int) =
        viewModelScope.launch {
            delay(ANIMATION_DURATION_DELAY)
            mutableState.replayCache.firstOrNull()?.filter {
                !it.second
            }?.getOrNull(position)?.let { (cardHub, isSelected) ->
                cardsHubInteractor.updateCardVisibilityOnCardHub(
                    cardId = cardHub.hubName,
                    visible = !isSelected
                )
            }
        }

    private companion object {
        const val ANIMATION_DURATION_DELAY = 450L
    }
}
