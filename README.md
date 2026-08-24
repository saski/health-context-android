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
- workouts: every exercise session plus available speed, power and cycling
  cadence summaries;
- sleep: every session and its duration;
- body: weight, body fat, water, bone and lean mass, height and basal metabolism;
- nutrition: distinct food entries, populated nutrient totals and hydration;
- indicators: heart rate, resting heart rate, HRV, oxygen saturation,
  respiratory rate, VO2 max, blood pressure, blood glucose and temperature.

It reports a domain as available, partial, unavailable, or requiring
permission. No record is treated as zero, and a missing manual nutrition entry
remains an explicit gap rather than an error.

## Reading the dashboard

Each domain card names the metrics Health Connect represents for the selected
day. Observed metrics show their measured value and, when useful, their time or
interval. Metrics without a record are named together instead of being hidden
behind a count.

Color communicates data state, not health quality:

- green means a value was observed;
- amber means the domain mixes observed metrics and gaps;
- gray means no record was available for the selected day;
- red means user action, normally a missing permission, is required;
- blue is reserved for controls and domain identity.

An unavailable value is never displayed as zero and does not imply inactivity,
poor sleep, or another medical conclusion.

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
2. Open **Salud Disponibilidad**.
3. Tap **Conceder permisos** and approve the listed read permissions. The first
   update after this coverage expansion asks for more categories once.
4. The app refreshes when opened; **Actualizar** remains available as recovery.
5. Under **Exportación diaria a Drive**, tap **Elegir carpeta** and choose the
   `Health context` folder in Google Drive. Android will remember only that
   folder until you change it.
6. Tap **Activar automático** and grant Health Connect background-read access.
   The app immediately attempts yesterday's export and schedules a daily run
   around 09:00 local time. Android may defer the exact execution time.
7. Use **Exportar hoy ahora** or **Exportar ayer ahora** only as a manual
   recovery path.
8. Wait for Google Drive to show the file, then start a conversation in the
   ChatGPT Health project. The app reports a local write only; it does not
   claim that the project has already read the file.

## Nightly review experiment

The optional nightly review turns the daily snapshot into a small, local
reflection loop. Enable it once after the Health context folder and background
read access are configured. On Android 13 or later, also approve notifications.

Around 22:30 local time, WorkManager reads the current day, updates the same
`health-context-YYYY-MM-DD.md` file, stores the review locally, and posts a
low-priority notification. Android may delay the exact time. Opening the
notification shows:

- one supported conclusion instead of a raw metric dump;
- interpreted evidence and evolution against the previous seven days;
- confidence limits, never values inferred as zero;
- at most two cautious suggestions for the next day;
- `useful` / `not useful` feedback stored only on the phone.

The review is deterministic: it uses no AI, network service, diagnosis, or
automatic training prescription. A personal comparison requires at least three
comparable observations within the previous seven days. A same-day review is
marked provisional, and a morning review does not judge unfinished activity or
nutrition. A workout appears only when Health Connect supplies an actual
exercise-session record; isolated speed, cadence, or power cannot create one.

**Review now** runs the same path for initial verification or recovery; it is
not intended as a daily requirement.
Enabling the experiment also keeps the existing morning export enabled. The
following morning it recalculates and replaces the same Markdown file so late
source synchronization and the recent comparison are included. Pausing daily synchronization also pauses
the nightly review because that self-correction is part of the experiment.

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
