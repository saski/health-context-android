package com.example.review

import android.content.BroadcastReceiver
import android.content.Context
import android.content.Intent
import java.time.LocalDate

class NightlyFeelingReceiver : BroadcastReceiver() {
    override fun onReceive(context: Context, intent: Intent) {
        val date = intent.getStringExtra(EXTRA_DATE)
            ?.let { runCatching { LocalDate.parse(it) }.getOrNull() }
            ?: return
        val feeling = intent.getStringExtra(EXTRA_FEELING)
            ?.let { runCatching { NightlyFeeling.valueOf(it) }.getOrNull() }
            ?: return
        SharedPreferencesNightlyReviewStore(context).recordFeeling(date, feeling)
    }

    companion object {
        private const val EXTRA_DATE = "review_date"
        private const val EXTRA_FEELING = "review_feeling"

        fun intent(context: Context, date: LocalDate, feeling: NightlyFeeling): Intent =
            Intent(context, NightlyFeelingReceiver::class.java).apply {
                putExtra(EXTRA_DATE, date.toString())
                putExtra(EXTRA_FEELING, feeling.name)
            }
    }
}
