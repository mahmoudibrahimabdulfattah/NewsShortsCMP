# Content-rights audit

Audited 31 August 2026 and updated 5 September 2026 against the sources
currently declared in `FeedCatalog.kt`. The catalog contains **58 entries**.
Every live entry is listed below.

This is an operational permissions review, not legal advice. “Current use”
means the product's actual transformation: ingest an RSS item, publish its
headline with a newly AI-written or translated summary, show the outlet name,
and link to the original article. A public RSS endpoint is a delivery mechanism,
not by itself a redistribution licence.

## Findings key

- **P1 — public-domain VOA text:** material produced exclusively by VOA may be
  summarised, translated, and used commercially with credit to VOA or
  `voanews.com`. AP, Reuters, and AFP material is outside that grant and must
  be removed by the source's third-party-credit filter before ingestion.
- **R1 — permission required:** the published terms limit material to personal
  or non-commercial use, or prohibit copying, republication, modification, or
  derivative works. The current product use is not expressly permitted.
- **R2 — permission required for the transformation:** RSS display is allowed
  only under stated conditions, generally using the feed text unchanged with
  attribution and a direct link. The new AI summary or translation falls
  outside that grant.
- **R3 — permission not established:** no published content-redistribution grant
  was located. An all-rights-reserved/copyright or privacy notice is linked as
  the outlet's available notice. Treat current use as requiring written
  permission.
- **R4 — explicit AI/automation restriction:** the term expressly restricts AI,
  machine-learning, text/data-mining, scraping, or automated extraction in
  addition to ordinary republication. Written permission is required before
  this source is processed.
- **A1 — source + direct link:** the outlet/feed name and a functional link to
  the complete original must remain visible. The app already shows the source
  on feed cards and article details, with a button to the original.
- **A2 — preserve notices:** any licensed use must retain the credited author,
  copyright, trademark, and other notices supplied with the item. The current
  source label is useful attribution but is not a substitute for permission or
  for item-specific credits.
- **A3 — format unspecified:** no usable attribution licence was published.
  The app's source label and direct link should remain, and the permission
  request must establish the exact credit wording.

## Source-by-source record

| # | Catalog source | Feed URL | Governing published term and what it says | Headline + AI summary + link | Attribution |
|---:|---|---|---|---|---|
| 1 | BBC عربي | <https://feeds.bbci.co.uk/arabic/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit BBC content to personal/non-business use and restrict adaptation unless separately permitted. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 2 | اندبندنت عربية | <https://www.independentarabia.com/rss.xml> | [Site copyright notice](https://www.independentarabia.com/) reserves the site's material; its published [privacy policy](https://www.independentarabia.com/privacy-policy) supplies no redistribution grant. | **Not established — R3** | **A3**; keep the Arabic outlet name and direct link, and obtain its required credit wording. |
| 3 | RT Arabic | <https://arabic.rt.com/rss/> | [RT Arabic copyright notice](https://arabic.rt.com/) reserves site content; the published [privacy policy](https://arabic.rt.com/privacy-policy/) does not license republication or derivatives. | **Not established — R3** | **A3**; keep `RT Arabic` and the original link pending written terms. |
| 4 | CNN بالعربية | <https://arabic.cnn.com/api/v1/rss/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) reserve CNN content for personal/non-commercial use and prohibit copying, modification, and derivative exploitation without consent. | **No — R1** | **A2**; the CNN Arabic label/link remain necessary but do not cure the restriction. |
| 5 | RT Arabic تكنولوجيا | <https://arabic.rt.com/rss/technology/> | [RT Arabic copyright notice](https://arabic.rt.com/) publishes no licence for redistribution or AI-derived summaries. | **Not established — R3** | **A3**; keep the catalog name and direct article link pending permission. |
| 6 | يورونيوز عربية تكنولوجيا | <https://arabic.euronews.com/rss?level=vertical&name=next> | [Euronews Terms of Use](https://www.euronews.com/terms-and-conditions) allow one personal, non-commercial copy and prohibit modifying, translating, publishing, redistributing, or creating derivative works. | **No — R1** | **A2**; permitted copies must retain attribution and notices. |
| 7 | RT Arabic رياضة | <https://arabic.rt.com/rss/sport/> | [RT Arabic copyright notice](https://arabic.rt.com/) publishes no redistribution or derivative-work grant. | **Not established — R3** | **A3**; keep the source name and direct link pending permission. |
| 8 | RT Arabic اقتصاد | <https://arabic.rt.com/rss/business/> | [RT Arabic copyright notice](https://arabic.rt.com/) publishes no redistribution or derivative-work grant. | **Not established — R3** | **A3**; keep the source name and direct link pending permission. |
| 9 | CNN بالعربية اقتصاد | <https://arabic.cnn.com/api/v1/rss/business/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) restrict copying, modification, derivative use, and commercial exploitation. | **No — R1** | **A2**; retain CNN Arabic and the direct original link if licensed. |
| 10 | CNN بالعربية رياضة | <https://arabic.cnn.com/api/v1/rss/sport/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) restrict copying, modification, derivative use, and commercial exploitation. | **No — R1** | **A2**; retain CNN Arabic and the direct original link if licensed. |
| 11 | CNN بالعربية صحة وعلوم | <https://arabic.cnn.com/api/v1/rss/science_and_health/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) restrict copying, modification, derivative use, and commercial exploitation. | **No — R1** | **A2**; retain CNN Arabic and the direct original link if licensed. |
| 12 | CNN بالعربية منوعات | <https://arabic.cnn.com/api/v1/rss/entertainment/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) restrict copying, modification, derivative use, and commercial exploitation. | **No — R1** | **A2**; retain CNN Arabic and the direct original link if licensed. |
| 13 | CNN بالعربية ستايل | <https://arabic.cnn.com/api/v1/rss/style/rss.xml> | [CNN Terms of Use](https://www.cnn.com/terms) restrict copying, modification, derivative use, and commercial exploitation. | **No — R1** | **A2**; retain CNN Arabic and the direct original link if licensed. |
| 14 | RT Arabic صحة | <https://arabic.rt.com/rss/health/> | [RT Arabic copyright notice](https://arabic.rt.com/) publishes no redistribution or derivative-work grant. | **Not established — R3** | **A3**; keep the source name and direct link pending permission. |
| 15 | اليوم السابع | <https://www.youm7.com/rss/SectionRss?SectionID=65> | [Youm7's published notice](https://www.youm7.com/privacy) contains privacy terms but no content-redistribution licence; the site footer reserves copyright. | **Not established — R3** | **A3**; keep `اليوم السابع` and establish the required Arabic credit in writing. |
| 16 | المصري اليوم | <https://www.almasryalyoum.com/rss/rssfeeds> | [Al-Masry Al-Youm copyright notice](https://www.almasryalyoum.com/html/copyright) reserves its material and publishes no grant for transformed commercial reuse. | **Not established — R3** | **A3**; keep `المصري اليوم` and the direct original link pending permission. |
| 17 | الشرق الأوسط | <https://aawsat.com/feed> | [Asharq Al-Awsat's published notice](https://aawsat.com/privacy-policy) contains no redistribution grant; the site retains copyright in its content. | **Not established — R3** | **A3**; keep `الشرق الأوسط` and agree the credit form with the outlet. |
| 18 | BBC News | <https://feeds.bbci.co.uk/news/world/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit BBC content to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 19 | The Guardian | <https://www.theguardian.com/world/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) permits feeds for personal, non-commercial use under its terms, not a transformed commercial summary service. | **No — R2** | **A1/A2**; retain `The Guardian`, supplied credits, and the direct article link. |
| 20 | VOA Middle East | <https://www.voanews.com/api/zrbopl-vomx-tpeovm_> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 21 | BBC Technology | <https://feeds.bbci.co.uk/news/technology/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 22 | BBC Business | <https://feeds.bbci.co.uk/news/business/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 23 | BBC Science | <https://feeds.bbci.co.uk/news/science_and_environment/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 24 | BBC Health | <https://feeds.bbci.co.uk/news/health/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 25 | BBC Entertainment | <https://feeds.bbci.co.uk/news/entertainment_and_arts/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 26 | BBC Sport | <https://feeds.bbci.co.uk/sport/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 27 | VOA Technology | <https://www.voanews.com/api/zyritl-vomx-tpettmq> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 28 | VOA Economy | <https://www.voanews.com/api/zyboql-vomx-tpetvmi> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 29 | VOA Science & Health | <https://www.voanews.com/api/ztbopl-vomx-tpekvmm> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 30 | VOA Arts & Culture | <https://www.voanews.com/api/zpbovl-vomx-tpe_vmr> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 31 | Sky Sports | <https://www.skysports.com/rss/12040> | [Sky.com Terms and Conditions](https://www.sky.com/help/articles/skycom-terms-and-conditions) reserve site material for personal/non-commercial use and do not grant transformed republication. | **No — R1** | **A2**; keep Sky Sports and the direct link if permission is obtained. |
| 32 | TechCrunch | <https://techcrunch.com/feed/> | [TechCrunch RSS Terms](https://techcrunch.com/rss-terms-of-use/) allow only feed content as supplied, require TechCrunch attribution and a full-article link, and prohibit modifying feed text or links. | **No — R2** | **A1**; the app has the required name/link, but its new summary is outside the grant. |
| 33 | Engadget | <https://www.engadget.com/rss.xml> | [Static Media Terms](https://www.static.com/terms) reserve publisher content and restrict copying, modification, derivative works, and commercial reuse without consent. | **No — R1** | **A2**; keep Engadget and the original link if licensed. |
| 34 | The Guardian Science | <https://www.theguardian.com/science/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 35 | The Guardian Sport | <https://www.theguardian.com/uk/sport/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 36 | The Guardian Culture | <https://www.theguardian.com/uk/culture/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 37 | The Guardian Technology | <https://www.theguardian.com/uk/technology/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 38 | The Guardian Business | <https://www.theguardian.com/uk/business/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 39 | ScienceDaily | <https://www.sciencedaily.com/rss/all.xml> | [ScienceDaily Terms](https://www.sciencedaily.com/terms.htm) permit commercial and non-commercial RSS display only as supplied, with `Science Daily`, a direct link, and no edits; images are excluded. | **No — R2** | **A1**; name/link are present, but the replacement AI summary and feed images are not licensed by this grant. |
| 40 | Phys.org | <https://phys.org/rss-feed/> | [Science X Terms](https://sciencex.com/help/terms/) restrict copying, public availability, adaptation, and derivative use to personal/non-commercial use. | **No — R1** | **A2**; preserve Phys.org and all item credits if licensed. |
| 41 | STAT | <https://www.statnews.com/feed/> | [STAT Terms and Conditions](https://www.statnews.com/terms-conditions/) reserve its content and restrict republication, redistribution, and commercial exploitation without permission. | **No — R1** | **A2**; keep STAT and the direct link if permission is granted. |
| 42 | The Guardian Health | <https://www.theguardian.com/society/health/rss> | [Guardian RSS guidance](https://www.theguardian.com/help/feeds) limits feed use to personal, non-commercial purposes under its terms. | **No — R2** | **A1/A2**; retain the outlet, supplied credit, and direct link. |
| 43 | VOA | <https://www.voanews.com/api/zqboml-vomx-tpeivmy> | [VOA's published policy](https://www.voanews.com/p/5338.html) places material produced exclusively by VOA in the public domain, but excludes licensed AP, Reuters, and AFP material; the source filter drops items carrying those credits. VOA is a U.S. government broadcaster and must not count as independent confirmation alongside another state outlet. | **Yes — P1** | **A1**; retain the VOA source name and direct article link, satisfying the required credit to VOA or `voanews.com`. |
| 44 | BBC UK | <https://feeds.bbci.co.uk/news/uk/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 45 | Egypt Independent | <https://www.egyptindependent.com/feed/> | [Egypt Independent copyright notice](https://www.egyptindependent.com/copyright/) reserves Al-Masry Al-Youm material; its [site terms](https://www.egyptindependent.com/terms/) do not grant transformed redistribution. | **Not established — R3** | **A3**; keep Egypt Independent and the direct link, and obtain the credit form in writing. |
| 46 | Saudi Gazette | <https://saudigazette.com.sa/rssFeed/74> | [Saudi Gazette copyright notice](https://saudigazette.com.sa/) publishes no RSS/content redistribution grant; its [privacy policy](https://saudigazette.com.sa/privacy-policy) does not authorize reuse. | **Not established — R3** | **A3**; keep Saudi Gazette and agree attribution as part of permission. |
| 47 | The National | <https://www.thenationalnews.com/arc/outboundfeeds/rss/?outputType=xml> | [The National Terms](https://www.thenationalnews.com/terms-and-conditions/) make material personal/non-commercial and prohibit copying, republication, modification, storage, or distribution without prior written consent. | **No — R1** | **A2**; keep The National, author credits, and direct link if licensed. |
| 48 | DW | <https://rss.dw.com/rdf/rss-en-all> | [DW Legal Notice](https://www.dw.com/en/legal-notice/a-63500643) reserves DW content and does not grant this automated, transformed commercial reuse. | **No — R1** | **A2**; keep DW and item-specific credits; obtain syndication permission. |
| 49 | The Local Germany | <https://www.thelocal.de/feeds/rss.php> | [The Local Terms of Use](https://www.thelocal.de/terms-of-use) reserve site content and do not authorize republication or derivative commercial use through the feed. | **No — R1** | **A2**; keep The Local Germany and the original link if licensed. |
| 50 | France 24 | <https://www.france24.com/en/rss> | [France Médias Monde Legal Notice](https://www.francemm.com/en/legal-notice) identifies site text, articles, images, and other elements as protected and reserves reproduction/representation outside its stated personal access. | **No — R1** | **A2**; keep France 24, author/agency credits, and the original link. |
| 51 | RFI | <https://www.rfi.fr/en/rss> | [France Médias Monde Legal Notice](https://www.francemm.com/en/legal-notice) identifies site text, articles, images, and other elements as protected and supplies no grant for AI-derived republication. | **No — R1** | **A2**; keep RFI, author/agency credits, and the original link. |
| 52 | BBC India | <https://feeds.bbci.co.uk/news/world/asia/india/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 53 | BBC China | <https://feeds.bbci.co.uk/news/world/asia/china/rss.xml> | [BBC Terms of Use](https://www.bbc.com/usingthebbc/terms/) limit feed/content use to personal/non-business use and restrict adaptation. | **No — R2** | **A1/A2**; BBC name and original link are already shown. |
| 54 | China Daily | <https://www.chinadaily.com.cn/rss/china_rss.xml> | [China Daily Terms and Conditions](https://www.chinadaily.com.cn/static_e/termsandconditions.html) retain copyright and publish no express licence for transformed redistribution in this product. | **No — R1** | **A2**; retain China Daily and supplied credits if permission is granted. |
| 55 | The Japan Times | <https://www.japantimes.co.jp/feed/> | [Japan Times Terms and Conditions](https://www.japantimes.co.jp/about-us/terms-and-conditions/) reserve site/RSS content and do not grant transformed commercial republication. | **No — R1** | **A2**; keep The Japan Times and the direct link if licensed. |
| 56 | ABC News Australia | <https://www.abc.net.au/news/feed/51120/rss.xml> | [ABC Terms of Use](https://help.abc.net.au/hc/en-us/articles/360001548096-ABC-Terms-of-Use#Use_of_ABC_Content) limit reuse of ABC content and require permission outside personal/non-commercial and expressly licensed uses. | **No — R1** | **A2**; retain ABC, author/copyright credits, and direct link if licensed. |
| 57 | Global News | <https://globalnews.ca/feed/> | [Corus Terms of Use](https://www.corusent.com/terms-of-use/) allow individual pages only for non-commercial use and prohibit selling, modifying, distributing, copying, publishing, adapting, or creating derivative works. | **No — R1** | **A2**; keep Global News and item credits if permission is obtained. |
| 58 | The Rio Times | <https://riotimesonline.com/feed/> | [Rio Times published notice](https://www.riotimesonline.com/privacy-policy/) supplies privacy terms but no RSS/content redistribution licence; the site footer reserves copyright. | **Not established — R3** | **A3**; keep The Rio Times and agree the credit form as part of permission. |

## Product consequence

The app already renders `article.source.name` on every feed card and article
detail, and the adjacent action opens `article.url`. That supplies VOA's
required credit and the basic source-name/direct-link shape required by R2
outlets, so this audit does not add another attribution label. It does **not**
make transformations of the non-VOA catalog entries licensed: those sources
still require either written permission or a term change before commercial
distribution.

The permission request should expressly cover automated RSS ingestion,
headline display, AI summarisation, translation, caching, publication on two
CDNs, mobile/web display, and any article images. Image reuse is not cleared by
an RSS-text permission; ScienceDaily expressly excludes it, and the other
outlets commonly carry third-party photo/agency credits.

Four sources with express AI restrictions were removed in the prior catalog
review. VOA is the first current source cleared for summarisation and
translation, subject to its third-party-credit filter.
