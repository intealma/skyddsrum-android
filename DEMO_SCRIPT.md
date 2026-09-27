# Demo video script (under 2 minutes)

Target length: about 1:50. Record the Android Studio emulator (Pixel 8) with a screen recorder, or use the
emulator's own recorder: *Extended controls → Record and playback*.

## Before recording

1. Build and install: `./gradlew installDebug`. Make sure `REVENUECAT_API_KEY` in `local.properties`
   holds your **Test Store** key, and that the `default` offering has a paywall (see README).
2. Start from a clean state: *Settings → Apps → Skyddsrum → Storage → Clear storage*, so the
   location permission prompt and the locked premium screen appear.
3. **Set the emulator location to Linköping:**
   - Click `⋯` (**Extended controls**) in the emulator's side toolbar.
   - Open **Location**, then the **Single points** tab.
   - Search for **Linköping** (or type latitude `58.4108`, longitude `15.6214`).
   - Click **Set location**. Close the panel.
4. Open the Map tab once so the tiles around Linköping are cached, then go back to Home.
5. Tap **Directions** once and press **Skip** on Google Maps' first-run screen, so it goes straight to the pin
   during recording.
6. Optional: set the phone language to English for the video (the app also runs in Swedish).

## Script

### 1. Elevator pitch (0:00–0:20)

*On screen: app icon, then the Home screen.*

> "If the sirens went off right now, would you know where your nearest shelter is? Sweden has over
> sixty thousand public shelters, but almost nobody knows theirs. **Skyddsrum** finds the closest one
> in a single tap, and it works offline, because all 63,000 shelters are built into the app."

### 2. Core flow: nearest shelter in Linköping, then directions (0:20–0:55)

*On screen:* tap **Find nearest shelter**, choose **Allow** in the permission dialog, and the detail sheet
opens for the closest shelter.

> "One tap. The app gets my location, here in Linköping, and opens the closest shelter: the address,
> how many people it holds, and how far away it is."

*On screen:* tap **Directions**; the maps app opens with the route. Go back.

> "Directions hands off to any maps app, and there's a web fallback."

*On screen:* close the sheet and scroll the **Nearest shelters** list (10 items, m/km). Open the **Map**
tab, zoom out to show the clusters, then zoom in and tap a single shelter.

> "The ten nearest are listed by distance, and the map shows every shelter in Sweden, clustered
> so it stays fast. None of this is behind a paywall, and it never will be."

### 3. Monetization: paywall and test purchase (0:55–1:30)

*On screen:* **My places** tab, which shows the lock screen. Tap **Unlock with Premium** and the RevenueCat paywall appears.

> "Premium is powered by RevenueCat. It adds saved places, like home, work and school, each with its own
> nearest shelters. The paywall is a RevenueCat Paywall, with a monthly or yearly plan or a one-time
> Supporter purchase."

*On screen:* choose a package, complete the **Test Store** purchase, and the paywall closes.
**My places** unlocks immediately. Tap **Set → Use my current location** for Home, and the 3 nearest shelters appear.

> "This is RevenueCat's Test Store, so I can demo a real purchase flow in the emulator. The app listens to
> CustomerInfo, and the premium entitlement unlocks the feature live, with no restart."

*On screen:* **Settings**, showing *Premium active*, **Support the project**, **Restore purchases**, and the About section.

> "Settings has 'Support the project', restore purchases, and full attribution of the open shelter data."

### 4. Why it deserves the Next Gen Award, and the Peace Prize angle (1:30–1:50)

*On screen:* Home screen, then the About section.

> "I'm a student in Sweden, and I built this because preparedness shouldn't depend on a good connection
> or on knowing where to look. It's open source under MIT, it runs on open government data and
> OpenStreetMap, and safety features stay free for everyone, forever. Premium just helps keep it running.
> Skyddsrum: calm, fast help to find safety."

*End card:* app icon, the text "Skyddsrum · open source (MIT)", and the GitHub URL.

## Checklist for the submission

- [ ] Video is under 2 minutes and shows a RevenueCat purchase unlocking a feature
- [ ] Public GitHub repo with the MIT `LICENSE` at the root
- [ ] README explains RevenueCat usage (entitlement `premium`, offering `default`, Test Store)
- [ ] Category: **Next Gen Award** (student); mention the social impact angle
