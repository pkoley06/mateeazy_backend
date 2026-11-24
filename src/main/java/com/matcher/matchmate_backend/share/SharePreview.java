package com.matcher.matchmate_backend.share;

/**
 * What a shared-link page shows: the link-preview card (WhatsApp, Telegram...) and the
 * fallback page for people without the app.
 *
 * @param kind     what was shared
 * @param id       the room / post id, as it appears in the link
 * @param title    card title; never the poster's name
 * @param summary  one line under the title (rent, area...); never the exact address
 * @param imageUrl absolute URL of the cover photo, or null for the site's default image
 */
public record SharePreview(Kind kind, String id, String title, String summary, String imageUrl) {

    public enum Kind {
        ROOM("room", "room"),
        POST("post", "post");

        /** Path segment in https://mateeazy.in/{segment}/{id}. */
        public final String segment;
        /** Word used in copy ("This room is no longer available"). */
        public final String noun;

        Kind(String segment, String noun) {
            this.segment = segment;
            this.noun = noun;
        }
    }
}
