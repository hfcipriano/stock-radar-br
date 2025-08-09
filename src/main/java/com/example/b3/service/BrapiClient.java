package com.example.b3.service;

import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.util.LinkedMultiValueMap;
import org.springframework.util.MultiValueMap;
import org.springframework.web.client.RestClient;

import java.util.*;
import java.util.stream.Collectors;

/**
 * Minimal brapi.dev client focused on quotes + fundamentals needed for Graham.
 * Docs: https://brapi.dev/docs/acoes , https://brapi.dev/docs/acoes/list
 */
@Service
public class BrapiClient {

  private final RestClient http;
  private final String baseUrl;
  private final String token;

  public BrapiClient(RestClient http,
                     @Value("${brapi.base-url:https://brapi.dev/api}") String baseUrl,
                     @Value("${brapi.token:}") String token) {
    this.http = http;
    this.baseUrl = baseUrl;
    this.token = token;
  }

  private RestClient.RequestHeadersSpec<?> withAuth(RestClient.RequestHeadersSpec<?> spec) {
    if (token != null && !token.isBlank()) {
      return spec.header(HttpHeaders.AUTHORIZATION, "Bearer " + token);
    }
    return spec;
  }

  public static record QuoteListItem(String stock, String name, Double close, Double volume, String sector, String type) {}
  public static record QuoteListResponse(List<Map<String,Object>> indexes, List<Map<String,Object>> stocks) {}

  public static record QuoteResponse(List<Map<String,Object>> results) {}

  @Cacheable(cacheNames = "tickers_top", unless = "#result == null", cacheManager = "simpleCacheManager")
  public List<QuoteListItem> listTopByMarketCap(int limit) {
    String url = baseUrl + "/quote/list?type=stock&sortBy=market_cap_basic&sortOrder=desc&limit=" + limit + "&page=1";
    var resp = withAuth(http.get().uri(url)).retrieve().body(QuoteListResponse.class);
    if (resp == null || resp.stocks() == null) return List.of();
    List<QuoteListItem> out = new ArrayList<>();
    for (var s : resp.stocks()) {
      out.add(new QuoteListItem(
          (String) s.getOrDefault("stock",""),
          (String) s.getOrDefault("name",""),
          s.get("close") instanceof Number ? ((Number) s.get("close")).doubleValue() : null,
          s.get("volume") instanceof Number ? ((Number) s.get("volume")).doubleValue() : null,
          (String) s.getOrDefault("sector",""),
          (String) s.getOrDefault("type","")
      ));
    }
    return out;
  }

  @Cacheable(cacheNames = "quotes_batch", unless = "#result == null")
  public List<Map<String,Object>> getQuotesWithModules(List<String> tickers) {
    if (tickers == null || tickers.isEmpty()) return List.of();
    List<Map<String,Object>> all = new ArrayList<>();
    final int MAX = 10; // limite por requisição no plano atual
    for (int i = 0; i < tickers.size(); i += MAX) {
      List<String> slice = tickers.subList(i, Math.min(i + MAX, tickers.size()));
      String joined = String.join(",", slice);
      String url = baseUrl + "/quote/" + joined + "?fundamental=true&dividends=false&modules=defaultKeyStatistics";
      var resp = withAuth(http.get().uri(url)).retrieve().body(QuoteResponse.class);
      if (resp != null && resp.results() != null) {
        all.addAll(resp.results());
      }
    }
    return all;
  }

  public static class GrahamFields {
    public String ticker;
    public String name;
    public double price;
    public Double eps;   // earningsPerShare (LPA)
    public Double bvps;  // bookValue per share (VPA)
    public Double pe;    // priceEarnings
    public Double pb;    // priceToBook
  }

  /**
   * Map the raw result into GrahamFields using a few fallbacks.
   */
  public GrahamFields toGrahamFields(Map<String,Object> m) {
    GrahamFields g = new GrahamFields();
    g.ticker = (String) m.getOrDefault("symbol","");
    g.name = (String) m.getOrDefault("shortName", m.getOrDefault("longName", g.ticker));
    g.price = num(m, "regularMarketPrice", num(m, "close", 0d));
    g.eps = n(m, "earningsPerShare");
    g.pb  = n(m, "priceToBook");
    g.pe  = n(m, "priceEarnings");

    // Try to find bookValue in top-level or inside defaultKeyStatistics
    g.bvps = n(m, "bookValue");
    if (g.bvps == null) {
      Object dks = m.get("defaultKeyStatistics");
      if (dks instanceof Map<?,?> dk) {
        Object val = ((Map<?,?>) dk).get("bookValue");
        if (val instanceof Number v) g.bvps = v.doubleValue();
      }
    }
    // If still missing BVPS but we have PB and price, compute: BVPS = price / PB
    if (g.bvps == null && g.pb != null && g.pb > 0) {
      g.bvps = g.price / g.pb;
    }
    return g;
  }

  private Double n(Map<String,Object> m, String key) {
    Object v = m.get(key);
    if (v instanceof Number n) return n.doubleValue();
    return null;
  }
  private double num(Map<String,Object> m, String key, double def) {
    Double d = n(m, key);
    return d == null ? def : d;
  }
}
