# Feed hosting

Every publish produces one static site in `build/site`. GitHub Pages remains
the primary host, and the Cloudflare Worker in this repository serves that
same directory as an independent mirror. Both origins therefore expose the
same `v1/**` paths and can be listed in `BACKEND_ORIGINS` without a path
translation layer.

## Provision the Cloudflare mirror

1. Create or select a Cloudflare account, then copy its account ID from the
   account overview.
2. In **My Profile → API Tokens**, create a custom token with **Workers Scripts:
   Edit** and **Account Analytics: Read**. Restrict its account resources to
   the account that will own `news-shorts-feed`.
3. Add two GitHub Actions repository secrets:
   `CLOUDFLARE_API_TOKEN` with that token and `CLOUDFLARE_ACCOUNT_ID` with the
   account ID. The environment variable overrides the explicit placeholder in
   `wrangler.toml`; leaving the token absent makes the mirror step a no-op.
4. Run the existing **Publish news feed** workflow, or wait for its next
   scheduled run. Its generator writes `build/site`, Wrangler uploads that
   exact directory, and the existing GitHub Pages steps upload it again.
5. In **Workers & Pages → news-shorts-feed → Settings → Domains & Routes**,
   copy the assigned `workers.dev` URL or attach a custom domain. Verify
   `<mirror-origin>/v1/health.json` and
   `<mirror-origin>/v1/feed/en.json` return the same publish as GitHub Pages.
6. Configure app builds with the primary first and the mirror second:

   ```properties
   BACKEND_ORIGINS=https://mahmoudibrahimabdulfattah.github.io/NewsShortsCMP,https://news-shorts-feed.<account-subdomain>.workers.dev
   ```

The Worker adds only the cross-origin response header browser builds need; it
does not rewrite paths or content. A request for a file missing from the
artifact remains a 404, which lets the app distinguish a missing publish from
an unavailable host.

Successful `v1/feed/*.json` responses are also counted in the
`news_shorts_feed_requests` Analytics Engine dataset. Every publish queries its
rolling 30-day sampled total. The result and the fixed 5,000,000-response limit
are added to `v1/health.json`; crossing the limit fails the publish so the
existing `publish-failure` issue carries the diagnostic. With no Cloudflare
token, the report records the check as skipped and Pages publishing continues.

## Local verification

Generate a representative artifact and validate the Worker bundle without
deploying it:

```bash
./gradlew :server:generateStaticFeed -PoutputDir=build/site
npx --yes wrangler@4.33.1 deploy --dry-run
```

Do not put either Cloudflare credential in `wrangler.toml` or
`local.properties`. The account ID placeholder is intentional; CI supplies the
real value only to the deploy process.
