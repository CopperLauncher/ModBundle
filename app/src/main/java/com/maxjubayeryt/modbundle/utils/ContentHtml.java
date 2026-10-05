package com.maxjubayeryt.modbundle.utils;

import android.content.Context;
import android.content.Intent;
import android.graphics.Color;
import android.net.Uri;
import android.view.View;
import android.webkit.WebResourceRequest;
import android.webkit.WebSettings;
import android.webkit.WebView;
import android.webkit.WebViewClient;

import org.commonmark.Extension;
import org.commonmark.ext.gfm.strikethrough.StrikethroughExtension;
import org.commonmark.ext.gfm.tables.TablesExtension;
import org.commonmark.parser.Parser;
import org.commonmark.renderer.html.HtmlRenderer;

import java.util.Arrays;
import java.util.List;

/** Markdown/HTML rendering for the description and changelog tabs. */
public final class ContentHtml {

    private static final Parser PARSER;
    private static final HtmlRenderer RENDERER;

    static {
        List<Extension> ext = Arrays.asList(TablesExtension.create(), StrikethroughExtension.create());
        PARSER = Parser.builder().extensions(ext).build();
        // Raw HTML in Modrinth bodies (centered images, badges) is passed through; scripts never
        // run because JavaScript is disabled on the WebView below.
        RENDERER = HtmlRenderer.builder().extensions(ext).build();
    }

    private ContentHtml() {}

    public static String markdownToHtml(String md) {
        if (md == null || md.trim().isEmpty()) return "";
        return RENDERER.render(PARSER.parse(md));
    }

    public static String escape(String s) {
        if (s == null) return "";
        return s.replace("&", "&amp;").replace("<", "&lt;").replace(">", "&gt;");
    }

    private static String hex(int color) {
        return String.format("#%06X", 0xFFFFFF & color);
    }

    /** Wraps body HTML in a page styled with the current theme colours. */
    public static String page(View themeSource, String bodyHtml) {
        String text = hex(ThemeColors.onSurface(themeSource));
        String muted = hex(ThemeColors.onSurfaceVariant(themeSource));
        String accent = hex(ThemeColors.primary(themeSource));
        return "<!DOCTYPE html><html><head><meta charset=\"utf-8\">"
            + "<meta name=\"viewport\" content=\"width=device-width, initial-scale=1\">"
            + "<style>"
            + "body{margin:0;color:" + text + ";font-family:sans-serif;font-size:14px;line-height:1.55;word-wrap:break-word;overflow-wrap:anywhere}"
            + "a{color:" + accent + "}"
            + "img,video,iframe{max-width:100%;height:auto}"
            + "h1{font-size:20px}h2{font-size:18px}h3{font-size:16px}h4,h5,h6{font-size:14px}"
            + "h1,h2,h3{margin:18px 0 8px}"
            + "pre,code{background:rgba(128,128,128,.18);border-radius:4px}"
            + "code{padding:1px 4px}pre{padding:8px;overflow-x:auto}pre code{background:none;padding:0}"
            + "blockquote{margin:8px 0;padding:0 12px;border-left:3px solid " + accent + ";color:" + muted + "}"
            + "table{border-collapse:collapse;display:block;overflow-x:auto}"
            + "td,th{border:1px solid rgba(128,128,128,.45);padding:4px 8px}"
            + "hr{border:0;border-top:1px solid rgba(128,128,128,.4)}"
            + ".v{margin:0 0 22px}.v h3{margin:0}.meta{color:" + muted + ";font-size:12px;margin:2px 0 6px}.none{color:" + muted + "}"
            + "</style></head><body>" + bodyHtml + "</body></html>";
    }

    /** Locks a WebView down to static rendering; links open in the browser. */
    public static void setup(WebView web, Context ctx) {
        web.setBackgroundColor(Color.TRANSPARENT);
        web.setVerticalScrollBarEnabled(false);
        web.setOverScrollMode(View.OVER_SCROLL_NEVER);
        WebSettings s = web.getSettings();
        s.setJavaScriptEnabled(false);
        s.setAllowFileAccess(false);
        s.setAllowContentAccess(false);
        s.setDomStorageEnabled(false);
        s.setMixedContentMode(WebSettings.MIXED_CONTENT_NEVER_ALLOW);
        web.setWebViewClient(new WebViewClient() {
            @Override
            public boolean shouldOverrideUrlLoading(WebView view, WebResourceRequest request) {
                Uri u = request.getUrl();
                String scheme = u.getScheme();
                if ("http".equals(scheme) || "https".equals(scheme)) {
                    try { ctx.startActivity(new Intent(Intent.ACTION_VIEW, u)); } catch (Exception ignored) { }
                }
                return true;
            }
        });
    }

    public static void show(WebView web, View themeSource, String bodyHtml, String baseUrl) {
        web.loadDataWithBaseURL(baseUrl, page(themeSource, bodyHtml), "text/html", "UTF-8", null);
    }
}
