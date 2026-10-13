package fr.gildasledrogoff.fwknop.presentation.viewmodel

import androidx.lifecycle.ViewModel
import androidx.lifecycle.viewModelScope
import dagger.hilt.android.lifecycle.HiltViewModel
import fr.gildasledrogoff.fwknop.domain.usecase.MarkHelpShownUseCase
import kotlinx.coroutines.launch
import javax.inject.Inject

@HiltViewModel
class HelpViewModel @Inject constructor(markHelpShown: MarkHelpShownUseCase) : ViewModel() {
    init {
        viewModelScope.launch { markHelpShown() }
    }
}
