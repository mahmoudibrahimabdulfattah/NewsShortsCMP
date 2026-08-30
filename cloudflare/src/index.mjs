/** Serves the generated site unchanged while allowing browser clients to read it. */
export default {
  async fetch(request, env) {
    const response = await env.ASSETS.fetch(request);
    const path = new URL(request.url).pathname;
    if (response.ok && /^\/v1\/feed\/[^/]+\.json$/.test(path)) {
      // Only successful feed pages spend the page-sized bandwidth behind the
      // 30-day ceiling; share pages and small diagnostics do not inflate it.
      env.FEED_ANALYTICS.writeDataPoint({ indexes: ["feed-page"] });
    }
    const headers = new Headers(response.headers);
    headers.set("Access-Control-Allow-Origin", "*");
    return new Response(response.body, {
      status: response.status,
      statusText: response.statusText,
      headers,
    });
  },
};
