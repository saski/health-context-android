# Health Context Android

`health-context-android` is the Android companion for the Health Context
system. It reads a broad set of relevant Health Connect records and shows
whether each daily domain is available. Its purpose is to make missing
data visible before it is used in a health conversation; it is not a medical
app or a cloud sync service.

The companion repository,
[`health-context-pipeline`](https://github.com/saski/health-context-pipeline),
defines the cross-platform tracking outcome, the daily-context contract, and
the path by which an explicitly enabled summary can be used in ChatGPT Health.

## What the app reads

After you grant the corresponding Health Connect permissions, the app groups
today's or yesterday's information into six compact domains:

- daily activity: steps, active and total calories, distance, elevation,
  floors and cadence;
- workouts: reconciled exercise sessions plus available speed, power and
  cycling cadence summaries;
- sleep: every session and its duration;
- body: weight, body fat, water, bone and lean mass, height and basal metabolism;
- nutrition: distinct food entries, populated nutrient totals and hydration;
- indicators: heart rate, resting heart rate, HRV, oxygen saturation,
  respiratory rate, VO2 max, blood pressure, blood glucose and temperature.

It reports a domain as available, partial, unavailable, or requiring
permission. No record is treated as zero, and a missing manual nutrition entry
remains an explicit gap rather than an error.

## Reading the dashboard

The dashboard leads with the latest daily review: one supported conclusion and
one suggested action. Daily domains follow below it. Each domain card shows up
to three observed metrics with their measured value and, when useful, their
time or interval. Open **View details** to see the remaining observations,
named gaps, and missing permissions.

An observed domain can also open its source app. If one installed source is
available, the app launches it; if several sources contributed, Android shows
a chooser. This opens the source app at its normal launcher entry point unless
that app provides a stable public deep link. The app does not guess private
vendor screens.

Daily automation is summarized as **Ready**, **Needs attention**, or
**Paused**. Its controls, folder selection, permissions, and manual recovery
actions remain collapsed under **Configure** during normal use. A missing
optional health observation is a data gap, not an automation failure.

Color communicates data state, not health quality:

- green means a value was observed;
- amber means the domain mixes observed metrics and gaps;
- gray means no record was available for the selected day;
- red means user action, normally a missing permission, is required;
- blue is reserved for controls and domain identity.

An unavailable value is never displayed as zero and does not imply inactivity,
poor sleep, or another medical conclusion.

### Avoiding duplicate activity

More than one app can write the same walk or workout to Health Connect. The
app treats only a near-identical overlap of the same exercise type from two
different apps as one session. It preserves the chosen source and writes the
excluded duplicate source into the daily Markdown reason. Concurrent workouts
of a different type and merely adjacent sessions remain separate.

For consistent daily totals, make Zepp/Amazfit the priority source once in the
Health Connect app's data-management or app-priority settings for activity.
Leave Google Fit and the phone enabled if they add data Zepp does not produce;
the app's reconciliation protects the workout list, while Health Connect's own
priority controls its aggregated activity totals. The companion app never
deletes or changes any Health Connect record.

## Privacy boundary

The app reads Health Connect in the foreground when opened. If you explicitly
enable daily synchronization, it also requests background-read access for the
same summary domains and exports the previous day in a flexible morning
window. It requests full history access when the installed provider supports
it so existing trends can be backfilled later. It has no Internet permission,
does not write Health Connect records, and does not read exercise routes or
location. It writes only inside the folder selected through Android's system
picker; it has no OAuth credentials or broad Drive access.

## Use it

1. Open the repository in Android Studio and run the `app` configuration on an
   Android device with Health Connect available.
2. Open **Mi salud**.
3. Tap **Conceder permisos** and approve the listed read permissions. The first
   update after this coverage expansion asks for more categories once.
4. The app refreshes when opened; **Actualizar** remains available as recovery.
5. Open **Configure**, tap **Choose folder**, and choose the
   `Health context` folder in Google Drive. Android will remember only that
   folder until you change it.
6. Tap **Enable export** and grant Health Connect background-read access.
   The app immediately attempts yesterday's export and schedules a daily run
   in the morning. Android may defer the exact execution time.
7. Use **Export the selected day now** only as a manual
   recovery path.
8. Wait for Google Drive to show the file, then start a conversation in the
   ChatGPT Health project. The app reports a local write only; it does not
   claim that the project has already read the file.

## Nightly review experiment

The optional nightly review turns the daily snapshot into a small, local
reflection loop. Enable it once after the Health context folder and background
read access are configured. On Android 13 or later, also approve notifications.

Around 22:30 local time, WorkManager reads the current day, writes a
**provisional** snapshot, stores the review locally, and posts a low-priority
notification. Android may delay the exact time. Every successful export writes
both the dated archive `health-context-YYYY-MM-DD.md` and the stable
`health-context-latest.md` entry point. Opening the notification shows:

- one supported conclusion instead of a raw metric dump;
- interpreted evidence against the previous seven days and, when coverage is
  sufficient, evolution against the preceding twenty-one days;
- confidence limits, never values inferred as zero;
- at most two cautious suggestions for the next day;
- a one-tap **Good**, **Loaded**, or **Unwell** feeling, also available from the
  review screen and stored only on the phone;
- `useful` / `not useful` feedback stored only on the phone.

The review is deterministic: it uses no AI, network service, diagnosis, or
automatic training prescription. A personal comparison requires enough
comparable observations in both periods. Workout count and total duration use
reconciled exercise-session records and Health Connect's priority-aware daily
duration when available; isolated speed, cadence, or power cannot create a
workout. A recorded feeling is subjective context, never a clinical measurement.

**Review now** runs the same path for initial verification or recovery; it is
not intended as a daily requirement.
Enabling the experiment also keeps the morning export enabled. The following
morning the app silently recalculates yesterday, incorporates late source
synchronization and the recorded feeling, and replaces both the dated file and
`health-context-latest.md` with a **final** snapshot. It does not send a second
notification. If Android missed scheduled work, the next run fills missing
dated artifacts within the previous seven days and always finalizes yesterday,
without duplicating dates. Pausing daily synchronization also pauses the nightly
review because that self-correction is part of the experiment.

Run the experiment for seven nights before expanding it. Its first success
criterion is simple: did the notification save a manual end-of-day review, and
did its two actions help plan the next day?

An unavailable domain can simply mean that the relevant device was not worn,
has not synchronized, or that no manual entry was made. It is not a diagnosis.

## Development and synchronization

Android Studio is the primary build, test and device-install environment. Google
AI Studio remains optional for experiments and must synchronize through this
repository's `main` branch instead of acting as a separate source of truth.

See [DEVELOPMENT.md](DEVELOPMENT.md) for the verified local commands, USB setup
and the one-time migration note for builds previously installed by AI Studio.

## Status

The expanded Health Connect read path has been exercised on a physical device
with all supported permissions granted. Activity, Fit exercise sessions, Zepp
body measurements and manual nutrition entries have appeared in the app.
Foreground export and automatic previous-day export have both written the
expected date-named Markdown files to the selected Drive folder. ChatGPT Health
has read the earlier contract with provenance and explicit gaps intact; the
latest expanded artifact still needs a final conversational read check.
