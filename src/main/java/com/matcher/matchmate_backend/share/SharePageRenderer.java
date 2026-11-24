package com.matcher.matchmate_backend.share;

import org.springframework.web.util.HtmlUtils;

import java.net.URLEncoder;
import java.nio.charset.StandardCharsets;

/**
 * Renders the public page behind a shared link (https://mateeazy.in/room/{id},
 * https://mateeazy.in/post/{id}).
 *
 * <p>With the app installed, Android App Links open the app and this page is never seen.
 * It is what link-preview bots read (Open Graph tags) and what people without the app land
 * on: the room's photo and headline, an "Open in the app" button (an {@code intent:} link,
 * which falls back to Google Play when the app is missing) and a Google Play button.
 *
 * <p>Pure string building with every dynamic value HTML-escaped, so it is unit-testable.
 */
public final class SharePageRenderer {

    public static final String PACKAGE_NAME = "com.mateeazy.app";
    public static final String PLAY_URL = "https://play.google.com/store/apps/details?id=" + PACKAGE_NAME;

    private final String siteBaseUrl;

    public SharePageRenderer(String siteBaseUrl) {
        this.siteBaseUrl = siteBaseUrl.endsWith("/")
                ? siteBaseUrl.substring(0, siteBaseUrl.length() - 1)
                : siteBaseUrl;
    }

    /** The canonical public link for a shared item. */
    public String linkFor(SharePreview.Kind kind, String id) {
        return siteBaseUrl + "/" + kind.segment + "/" + id;
    }

    public String render(SharePreview preview) {
        String link = linkFor(preview.kind(), preview.id());
        boolean hasPhoto = preview.imageUrl() != null && !preview.imageUrl().isBlank();
        String image = hasPhoto ? preview.imageUrl() : siteBaseUrl + "/assets/img/og-image.jpg";
        // The site banner is 1200x630 with the logo at its left edge: show it whole.
        String coverClass = hasPhoto ? "cover" : "cover wide";
        String openInApp = intentUrl(preview.kind(), preview.id());
        String heading = preview.title();
        String cta = preview.kind() == SharePreview.Kind.ROOM ? "View this room in MateEazy" : "View this post in MateEazy";

        return page(
                heading + " | MateEazy",
                """
                  <meta property="og:type" content="website">
                  <meta property="og:site_name" content="MateEazy">
                  <meta property="og:title" content="%s">
                  <meta property="og:description" content="%s">
                  <meta property="og:url" content="%s">
                  <meta property="og:image" content="%s">
                  <meta name="twitter:card" content="summary_large_image">
                  <meta name="description" content="%s">
                  <link rel="canonical" href="%s">
                """.formatted(e(heading), e(preview.summary()), e(link), e(image), e(preview.summary()), e(link)),
                """
                  <img class="%s" src="%s" alt="">
                  <h1>%s</h1>
                  <p class="summary">%s</p>
                  <a class="primary" id="open" href="%s" data-intent="%s">%s</a>
                  <a class="secondary" href="%s">Get MateEazy on Google Play</a>
                  <p class="note">Free on Android. Sign in to see the full details and message the host.</p>
                """.formatted(coverClass, e(image), e(heading), e(preview.summary()), e(PLAY_URL), e(openInApp), e(cta), e(PLAY_URL)));
    }

    /** Shown when the room / post was removed, hidden, or never existed. */
    public String renderMissing(SharePreview.Kind kind) {
        String heading = "This " + kind.noun + " is no longer available";
        String text = kind == SharePreview.Kind.ROOM
                ? "It may have been taken or removed by the host. Find more rooms and flatmates near you on MateEazy."
                : "It may have been removed. See what your neighbours are sharing on MateEazy.";
        return page(
                heading + " | MateEazy",
                """
                  <meta property="og:type" content="website">
                  <meta property="og:site_name" content="MateEazy">
                  <meta property="og:title" content="MateEazy: find your space, find your people">
                  <meta property="og:image" content="%s/assets/img/og-image.jpg">
                """.formatted(e(siteBaseUrl)),
                """
                  <img class="logo" src="%s/assets/img/logo.svg" alt="">
                  <h1>%s</h1>
                  <p class="summary">%s</p>
                  <a class="primary" href="%s">Get MateEazy on Google Play</a>
                  <a class="secondary" href="%s/">Visit mateeazy.in</a>
                """.formatted(e(siteBaseUrl), e(heading), e(text), e(PLAY_URL), e(siteBaseUrl)));
    }

    /**
     * Opens the app on this item even where App Links aren't verified (in-app browsers);
     * Chrome goes to Google Play instead when the app isn't installed.
     */
    String intentUrl(SharePreview.Kind kind, String id) {
        String hostAndPath = linkFor(kind, id).replaceFirst("^https?://", "");
        return "intent://" + hostAndPath + "#Intent;scheme=https;package=" + PACKAGE_NAME
                + ";S.browser_fallback_url=" + URLEncoder.encode(PLAY_URL, StandardCharsets.UTF_8) + ";end";
    }

    private static String page(String title, String head, String body) {
        return """
                <!doctype html>
                <html lang="en">
                <head>
                  <meta charset="utf-8">
                  <meta name="viewport" content="width=device-width, initial-scale=1">
                  <meta name="robots" content="noindex">
                  <title>%s</title>
                %s  <style>
                    body{margin:0;min-height:100vh;display:grid;place-items:center;background:#2B1B3D;color:#FFF8F0;font-family:Poppins,system-ui,-apple-system,"Segoe UI",Roboto,sans-serif;padding:24px;box-sizing:border-box;text-align:center}
                    main{width:100%%;max-width:420px}
                    .cover{width:100%%;aspect-ratio:4/3;object-fit:cover;border-radius:24px;display:block;margin:0 0 20px;background:#3A2850}
                    .cover.wide{aspect-ratio:1200/630}
                    .logo{width:96px;height:96px;margin:0 auto 20px;display:block}
                    h1{font-size:24px;line-height:1.25;margin:0 0 8px}
                    .summary{color:#C1B0CC;margin:0 0 24px;font-size:16px}
                    a{display:block;text-decoration:none;font-weight:600;padding:14px 20px;border-radius:999px;margin:0 0 12px}
                    .primary{background:#FF6B5B;color:#fff}
                    .secondary{border:1.5px solid #6B5A7D;color:#FFF8F0}
                    .note{color:#9C8BAA;font-size:13px;margin:8px 0 0}
                  </style>
                </head>
                <body>
                <main>
                %s</main>
                <script>
                  // Android: open the app on this item (or Google Play if it isn't installed).
                  var open = document.getElementById('open');
                  if (open && /Android/i.test(navigator.userAgent)) open.href = open.getAttribute('data-intent');
                </script>
                </body>
                </html>
                """.formatted(e(title), head, body);
    }

    private static String e(String value) {
        return value == null ? "" : HtmlUtils.htmlEscape(value, "UTF-8");
    }
}
