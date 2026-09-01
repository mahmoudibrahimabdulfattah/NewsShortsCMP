# Feed-origin outage drill

This drill proves an installed app keeps serving news while its primary static
host is unavailable, then returns to the primary after it recovers. It does not
touch Google Play, publishing tracks, or the production GitHub Pages project.

## Before the drill

1. Provision the Cloudflare mirror in [hosting.md](hosting.md) and verify these
   two URLs return the same `generatedAt` value:

   ```text
   https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP/v1/health.json
   https://news-shorts-feed.<account-subdomain>.workers.dev/v1/health.json
   ```

2. Put a controlled primary first and the real mirror second in the development
   machine's uncommitted `local.properties`. An unused loopback address on the
   device's network is preferable because it simulates a timeout; the reserved
   `.invalid` name gives a quicker DNS-failure drill:

   ```properties
   BACKEND_ORIGINS=https://primary-outage.invalid,https://news-shorts-feed.<account-subdomain>.workers.dev
   ```

3. Build and install the debug app on the physical Android or iOS device. Clear
   the app's data first so an origin remembered by an earlier run cannot make
   the initial sequence ambiguous.
4. Capture traffic with the development proxy of choice, or watch Android
   Studio Logcat/Xcode's console. Debug builds enable Ktor request logging; do
   not use production credentials or personal article URLs in the capture.

## Simulate the outage

1. Launch the app and open **For You**. The first feed request must attempt the
   controlled primary, receive a connection failure, then request the identical
   `v1/feed/...json` path from Cloudflare.
2. Confirm articles render and the offline/error state does not replace the
   feed. Pull to refresh once more.
3. Confirm the second refresh starts at Cloudflare and does not touch the failed
   primary. The first successful mirror response is stored in app settings, so
   the outage should cost one failed request rather than one per feed page.
4. Open a category and a country feed. Both must continue to use Cloudflare and
   display articles; a missing file must still remain a 404 rather than being
   looked up on another host.

## Recover the primary

1. Replace the controlled primary with a healthy local/static origin at the
   same URL, or remove the network rule that blocked it. Leave the app running
   and keep the mirror configured second.
2. Wait at least 15 minutes from the last primary attempt, then refresh. The
   app should probe the primary first, accept its successful response, and save
   it as the preferred origin.
3. Refresh again. Only the primary should receive the feed request. Recovery is
   complete when fresh articles render, no offline indicator appears, and the
   mirror receives no ordinary request after the successful primary response.

## Pass criteria and evidence

- Outage request order: failed primary, then successful mirror.
- The feed remains readable through the outage; no new error path appears.
- Next request during the outage: mirror first, with no repeated primary delay.
- Recovery request after 15 minutes: primary first and successful.
- Next request after recovery: primary only.

Save timestamps and request URLs for those five observations. Do not save feed
response bodies; the URL sequence and UI state are sufficient evidence.

The automated companion is
`FeedOriginOutageIntegrationTest`: it starts a real 503 loopback server and a
real healthy mirror server, performs two app feed calls, and proves the primary
is hit once while both calls return mirror news.
