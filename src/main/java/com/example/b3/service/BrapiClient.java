package com.example.b3.service;

import org.slf4j.Logger;
import org.slf4j.LoggerFactory;
import org.springframework.beans.factory.annotation.Value;
import org.springframework.cache.annotation.Cacheable;
import org.springframework.http.HttpHeaders;
import org.springframework.stereotype.Service;
import org.springframework.web.client.RestClient;

import java.util.*;

/**
 * Minimal brapi.dev client focused on quotes + fundamentals needed for Graham.
 * Docs: https://brapi.dev/docs/acoes , https://brapi.dev/docs/acoes/list
 */
@Service
public class BrapiClient {

  private static final Logger log = LoggerFactory.getLogger(BrapiClient.class);
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

  public static record QuoteListResponse(List<Map<String,Object>> indexes, List<Map<String,Object>> stocks) {}
  public static record QuoteListItem(String stock, String name) {}
  public static record QuoteResponse(List<Map<String,Object>> results) {}

  @Cacheable(cacheNames = "tickers_top", unless = "#result == null")
  public List<QuoteListItem> listTopByMarketCap() {
    //log.info("start listTopByMarketCap");
    String url = baseUrl + "/quote/list?type=stock&sortBy=market_cap_basic&sortOrder=desc&limit=" + 300 + "&page=1";
    var resp = withAuth(http.get().uri(url)).retrieve().body(QuoteListResponse.class);
    if (resp == null || resp.stocks() == null) return List.of();
    List<QuoteListItem> out = new ArrayList<>();
    for (var s : resp.stocks()) {
      out.add(new QuoteListItem(
        (String) s.getOrDefault("stock",""),
        (String) s.getOrDefault("name","")
      ));
    }
    //log.info("finish listTopByMarketCap");
    return out;
  }

  @Cacheable(cacheNames = "quotes_batch", unless = "#result == null")
  public List<Map<String,Object>> getQuotesWithModules(List<String> tickers) {
    //log.info("start getQuotesWithModules");
    if (tickers == null || tickers.isEmpty()) return List.of();
    final int MAX = 20;
    // Use only available modules (no summaryDetail)
    final String modules = "financialData,defaultKeyStatistics,incomeStatementHistory,balanceSheetHistory,financialDataHistory,cashflowHistory";

    // Build chunks and fetch in parallel
    List<String> chunks = new ArrayList<>();
    for (int i = 0; i < tickers.size(); i += MAX) {
      var slice = tickers.subList(i, Math.min(i + MAX, tickers.size()));
      chunks.add(String.join(",", slice));
    }

    List<Map<String,Object>> results = chunks.parallelStream()
      .map(joined -> {
        log.info("start chunck " + joined);
        String url = baseUrl + "/quote/" + joined + "?fundamental=true&dividends=false&modules=" + modules;
        var resp = withAuth(http.get().uri(url)).retrieve().body(QuoteResponse.class);
        return (resp != null && resp.results() != null) ? resp.results() : Collections.<Map<String,Object>>emptyList();
      })
      .flatMap(List::stream)
      .toList();

    return results;
  }

  @SuppressWarnings("unchecked")
  private static Map<String,Object> sub(Map<String,Object> m, String key) {
    Object s = m.get(key);
    if (s instanceof Map<?,?> sm) return (Map<String,Object>) sm;
    return null;
  }
  @SuppressWarnings("unchecked")
  private static List<Map<String,Object>> arr(Map<String,Object> m, String key) {
    Object s = m.get(key);
    if (s instanceof List<?> l) return (List<Map<String,Object>>) l;
    return null;
  }
  private static Double num(Object v) { return v instanceof Number n ? n.doubleValue() : null; }

  public Map<String,Object> normalize(Map<String,Object> m) {
    Map<String,Object> out = new HashMap<>();
    // basics
    out.put("symbol", m.getOrDefault("symbol",""));
    out.put("name", m.getOrDefault("shortName", m.getOrDefault("longName", "")));

    // price
    Double price = num(m.get("regularMarketPrice"));
    if (price == null) price = num(m.get("close"));
    var priceMap = sub(m,"price");
    if (price == null && priceMap != null) price = num(priceMap.get("regularMarketPrice"));
    out.put("price", price);

    // defaultKeyStatistics
    var dks = sub(m,"defaultKeyStatistics");
    Double eps = num(m.get("earningsPerShare"));
    if (eps == null && dks != null) eps = num(dks.get("earningsPerShare"));
    out.put("eps", eps);
    Double bookValue = num(m.get("bookValue"));
    if (bookValue == null && dks != null) bookValue = num(dks.get("bookValue"));
    out.put("bvps", bookValue);
    Double pe = num(m.get("priceEarnings"));
    if (pe == null && dks != null) pe = num(dks.get("priceEarnings"));
    out.put("pe", pe);
    Double pb = num(m.get("priceToBook"));
    if (pb == null && dks != null) pb = num(dks.get("priceToBook"));
    out.put("pb", pb);
    Double shares = dks != null ? num(dks.get("sharesOutstanding")) : null;
    out.put("sharesOutstanding", shares);

    // financialData (may include ebitda, totalDebt, cash)
    var fin = sub(m,"financialData");
    if (fin != null) {
      out.put("ebitda", num(fin.get("ebitda")));
      out.put("totalDebt", num(fin.get("totalDebt")));
      out.put("cash", num(fin.get("totalCash")));
    }

    // balanceSheetHistory
    var bsh = sub(m,"balanceSheetHistory");
    var bshArr = bsh == null ? null : (List<Map<String,Object>>) bsh.get("balanceSheetStatements");
    if (bshArr != null && !bshArr.isEmpty()) {
      var last = bshArr.get(0);
      if (!out.containsKey("totalDebt") || out.get("totalDebt") == null) out.put("totalDebt", num(last.get("totalDebt")));
      if (!out.containsKey("cash") || out.get("cash") == null) out.put("cash", num(last.get("cash")));
      out.put("equity", num(last.get("totalStockholderEquity")));
    }

    // incomeStatementHistory
    var ish = sub(m,"incomeStatementHistory");
    var ishArr = ish == null ? null : (List<Map<String,Object>>) ish.get("incomeStatementHistory");
    if (ishArr != null && !ishArr.isEmpty()) {
      var last = ishArr.get(0);
      out.put("revenue", num(last.get("totalRevenue")));
      out.put("netIncome", num(last.get("netIncome")));
      if (!out.containsKey("ebitda") || out.get("ebitda") == null) out.put("ebitda", num(last.get("ebitda")));
    }

    // derived
    Double p = (Double) out.get("price");
    Double sh = (Double) out.get("sharesOutstanding");
    Double mcap = (p != null && sh != null) ? p * sh : null;
    out.put("marketCap", mcap);
    Double debt = (Double) out.get("totalDebt");
    Double cash = (Double) out.get("cash");
    Double ev = (mcap != null ? mcap : 0d) + (debt != null ? debt : 0d) - (cash != null ? cash : 0d);
    out.put("ev", ev);
    Double ebitda = (Double) out.get("ebitda");
    out.put("evEbitda", (ebitda != null && ebitda != 0) ? ev / ebitda : null);
    Double revenue = (Double) out.get("revenue");
    Double ni = (Double) out.get("netIncome");
    out.put("netMargin", (revenue != null && revenue != 0 && ni != null) ? (ni / revenue) * 100.0 : null);
    Double eq = (Double) out.get("equity");
    out.put("roe", (eq != null && eq != 0 && ni != null) ? (ni / eq) * 100.0 : null);

    // Fallback BVPS using PB if needed
    if (out.get("bvps") == null && pb != null && pb > 0 && p != null) {
      out.put("bvps", p / pb);
    }
    return out;
  }
}
