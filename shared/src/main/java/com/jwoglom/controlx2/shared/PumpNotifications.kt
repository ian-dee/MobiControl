package com.jwoglom.controlx2.shared

import com.jwoglom.pumpx2.pump.messages.Message
import com.jwoglom.pumpx2.pump.messages.models.ApiVersion
import com.jwoglom.pumpx2.pump.messages.models.NotificationBundle
import com.jwoglom.pumpx2.pump.messages.request.currentStatus.ReminderStatusRequest
import com.jwoglom.pumpx2.pump.messages.response.currentStatus.ReminderStatusResponse

/**
 * The pumpx2 notification bundle parses reminder responses, but its request list currently omits
 * [ReminderStatusRequest]. Keep one app-owned request set until that omission is fixed upstream.
 * The explicit de-duplication makes this safe when a future pumpx2 release adds the request.
 */
fun pumpNotificationStatusRequests(apiVersion: ApiVersion? = null): List<Message> {
    val requests = NotificationBundle.allRequests(apiVersion).toMutableList()
    val reminderRequest = ReminderStatusRequest()
    val reminderSupported = apiVersion == null ||
        !reminderRequest.props().minApi.get().greaterThan(apiVersion)
    if (reminderSupported && requests.none { it is ReminderStatusRequest }) {
        // Keep this last so consumers can treat its fresh response as completion of the bundle.
        requests += reminderRequest
    }
    return requests
}

data class PumpReminderPresentation(
    val headline: String,
    val supportingText: String,
)

/** Human-readable pump reminder copy without changing the protocol reminder identity. */
fun pumpReminderPresentation(
    reminder: ReminderStatusResponse.ReminderType,
): PumpReminderPresentation = when (reminder) {
    ReminderStatusResponse.ReminderType.LOW_BG_REMINDER -> PumpReminderPresentation(
        headline = "Low BG Reminder",
        supportingText = "The pump's low glucose reminder is active.",
    )
    ReminderStatusResponse.ReminderType.HIGH_BG_REMINDER -> PumpReminderPresentation(
        headline = "High BG Reminder",
        supportingText = "The pump's high glucose reminder is active.",
    )
    ReminderStatusResponse.ReminderType.SITE_CHANGE_REMINDER -> PumpReminderPresentation(
        headline = "Site Reminder",
        supportingText = "It's time to change your infusion set.",
    )
    ReminderStatusResponse.ReminderType.MISSED_MEAL_REMINDER,
    ReminderStatusResponse.ReminderType.MISSED_MEAL_REMINDER1,
    ReminderStatusResponse.ReminderType.MISSED_MEAL_REMINDER2,
    ReminderStatusResponse.ReminderType.MISSED_MEAL_REMINDER3,
    -> PumpReminderPresentation(
        headline = "Missed Meal Reminder",
        supportingText = "The pump's missed meal reminder is active.",
    )
    ReminderStatusResponse.ReminderType.AFTER_BOLUS_BG_REMINDER -> PumpReminderPresentation(
        headline = "After Bolus BG Reminder",
        supportingText = "The pump's after-bolus glucose reminder is active.",
    )
    ReminderStatusResponse.ReminderType.ADDITIONAL_BOLUS_REMINDER -> PumpReminderPresentation(
        headline = "Additional Bolus Reminder",
        supportingText = "The pump's additional bolus reminder is active.",
    )
    else -> PumpReminderPresentation(
        headline = "Pump Reminder",
        supportingText = "The pump reported reminder ${reminder.id()}.",
    )
}
