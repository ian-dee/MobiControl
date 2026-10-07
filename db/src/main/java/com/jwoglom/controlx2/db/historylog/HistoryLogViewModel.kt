package com.jwoglom.controlx2.db.historylog

import androidx.lifecycle.LiveData
import androidx.lifecycle.MutableLiveData
import androidx.lifecycle.ViewModel
import androidx.lifecycle.ViewModelProvider
import androidx.lifecycle.asLiveData
import androidx.lifecycle.viewModelScope
import com.jwoglom.controlx2.shared.util.phoneLocalTimeAsTandemEpochSeconds
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.HistoryLogParser.LOG_MESSAGE_CLASS_TO_ID
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmBleCalibrationEvtG7HistoryLog
import com.jwoglom.pumpx2.pump.messages.response.historyLog.CgmCalibrationG7HistoryLog
import kotlinx.coroutines.ExperimentalCoroutinesApi
import kotlinx.coroutines.flow.MutableStateFlow
import kotlinx.coroutines.flow.flatMapLatest
import kotlinx.coroutines.launch
import timber.log.Timber

@OptIn(ExperimentalCoroutinesApi::class)
class HistoryLogViewModel(private val repo: HistoryLogRepo, pumpSid: Int): ViewModel() {
    private val pumpSidFlow = MutableStateFlow(pumpSid)

    val count: LiveData<Long?> = pumpSidFlow
        .flatMapLatest { repo.getCount(it) }
        .asLiveData()
    val all: LiveData<List<HistoryLogItem>> = pumpSidFlow
        .flatMapLatest { repo.getAll(it) }
        .asLiveData()
    val latest: LiveData<HistoryLogItem?> = pumpSidFlow
        .flatMapLatest { repo.getLatest(it) }
        .asLiveData()
    val oldest: LiveData<HistoryLogItem?> = pumpSidFlow
        .flatMapLatest { repo.getOldest(it) }
        .asLiveData()

    /**
     * A clean installation creates this ViewModel before pump discovery, when the stored SID is
     * still -1. Switch every database query atomically once onboarding identifies the pump rather
     * than leaving the dashboard permanently attached to the empty -1 partition.
     */
    fun setPumpSid(pumpSid: Int) {
        if (pumpSidFlow.value == pumpSid) return
        Timber.i("HistoryLogViewModel switching pump SID ${pumpSidFlow.value} -> $pumpSid")
        pumpSidFlow.value = pumpSid
    }

    fun getCountAboveSeqId(minSeqId: Long): LiveData<Long?> {
        return pumpSidFlow
            .flatMapLatest { repo.getCountAboveSeqId(it, minSeqId) }
            .asLiveData()
    }

    fun getCountInRange(minSeqId: Long, maxSeqId: Long): LiveData<Long?> {
        return pumpSidFlow
            .flatMapLatest { repo.getCount(it, minSeqId, maxSeqId) }
            .asLiveData()
    }

    fun latestForType(typeId: Int): LiveData<HistoryLogItem?> {
        return pumpSidFlow
            .flatMapLatest { repo.getLatestForType(it, typeId) }
            .asLiveData()
    }

    fun latestItemsForType(typeId: Int, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return pumpSidFlow
            .flatMapLatest { repo.getLatestItemsForType(it, typeId, maxItems) }
            .asLiveData()
    }

    fun latestItemsForType(typeClass: Class<out HistoryLog>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        val typeId = historyLogTypeId(typeClass)
            ?: return latestItemsForTypes(emptyArray(), maxItems)
        return latestItemsForType(typeId, maxItems)
    }

    fun allItemsForType(typeClass: Class<out HistoryLog>): LiveData<List<HistoryLogItem>> {
        val typeId = historyLogTypeId(typeClass)
            ?: return MutableLiveData(emptyList())
        return pumpSidFlow
            .flatMapLatest { repo.allForType(it, typeId) }
            .asLiveData()
    }

    fun latestItemsForTypes(typeIds: Array<Int>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return pumpSidFlow
            .flatMapLatest { repo.getLatestItemsForTypes(it, typeIds.toList(), maxItems) }
            .asLiveData()
    }

    fun latestItemsForTypes(typeClasses: List<Class<out HistoryLog>>, maxItems: Int): LiveData<List<HistoryLogItem>> {
        return latestItemsForTypes(
            typeClasses.mapNotNull(::historyLogTypeId).toTypedArray(),
            maxItems,
        )
    }

    /**
     * Query history log items of the given types at or after [minPumpTimeSec].
     *
     * [minPumpTimeSec] is in the pump's time domain: local wall-clock seconds since
     * 2008-01-01. Compute it by subtracting from [com.jwoglom.pumpx2.pump.messages
     * .response.currentStatus.TimeSinceResetResponse.getCurrentTime], e.g.:
     *   `currentPumpTime - (6 * 3600)` for the last 6 hours.
     *
     * No timezone conversion needed — pump seconds compared to pump seconds.
     */
    fun itemsForTypesSince(typeClasses: List<Class<out HistoryLog>>, minPumpTimeSec: Long): LiveData<List<HistoryLogItem>> {
        val typeIds = typeClasses.mapNotNull(::historyLogTypeId)
        return pumpSidFlow
            .flatMapLatest { repo.getItemsForTypesSince(it, typeIds, minPumpTimeSec) }
            .asLiveData()
    }

    fun itemsForTypesInTimeRange(
        typeClasses: List<Class<out HistoryLog>>,
        minPumpTimeSec: Long,
        maxPumpTimeSec: Long,
    ): LiveData<List<HistoryLogItem>> {
        val typeIds = typeClasses.mapNotNull(::historyLogTypeId)
        return pumpSidFlow
            .flatMapLatest {
                repo.getItemsForTypesInTimeRange(
                    it,
                    typeIds,
                    minPumpTimeSec,
                    maxPumpTimeSec,
                )
            }
            .asLiveData()
    }

    companion object {
        /**
         * Estimate the current pump clock value from [Instant.now] when
         * [TimeSinceResetResponse] is not yet available. The pump clock tracks
         * local wall-clock time as seconds since 2008-01-01, so:
         *   pumpTimeSec ≈ localEpochSec - JANUARY_1_2008_UNIX_EPOCH
         */
        fun estimateCurrentPumpTimeSec(): Long {
            return phoneLocalTimeAsTandemEpochSeconds()
        }
    }

    fun insert(historyLogItem: HistoryLogItem) = viewModelScope.launch {
        repo.insert(historyLogItem)
    }

}

/**
 * PumpX2 defines the two G7 calibration history messages but does not yet include them in its
 * generic HistoryLogParser registry. Keep the compatibility shim here so MobiControl can query
 * and decode the audited records without modifying the shared PumpX2 dependency.
 */
private fun historyLogTypeId(typeClass: Class<out HistoryLog>): Int? =
    LOG_MESSAGE_CLASS_TO_ID[typeClass] ?: when (typeClass) {
        CgmCalibrationG7HistoryLog::class.java -> CGM_CALIBRATION_G7_HISTORY_TYPE_ID
        CgmBleCalibrationEvtG7HistoryLog::class.java -> CGM_BLE_CALIBRATION_EVENT_G7_HISTORY_TYPE_ID
        else -> null
    }

class HistoryLogViewModelFactory(private val repo: HistoryLogRepo, private val pumpSid: Int) : ViewModelProvider.Factory {
    override fun <T : ViewModel> create(modelClass: Class<T>): T {
        if (modelClass.isAssignableFrom(HistoryLogViewModel::class.java)) {
            Timber.i("HistoryLogViewModel created with pumpSid=$pumpSid")
            @Suppress("UNCHECKED_CAST")
            return HistoryLogViewModel(repo, pumpSid) as T
        }
        throw IllegalArgumentException("Unknown ViewModel class")
    }
}
