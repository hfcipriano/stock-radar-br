package com.example.b3.service;

import com.example.b3.model.StockView;
import org.slf4j.LoggerFactory;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class ScreenerService {
  private static final org.slf4j.Logger log = LoggerFactory.getLogger(ScreenerService.class);

  public enum Method { GRAHAM, PE_TARGET, EV_EBITDA_TARGET }

  private final BrapiClient brapi;

  public ScreenerService(BrapiClient brapi) { this.brapi = brapi; }

  public List<StockView> run(int howMany, Method method, Double peTarget, Double evEbitdaTarget) {
    log.info("start run");
    log.info("run pre listTopByMarketCap");
    var universe = brapi.listTopByMarketCap();
    log.info("run pos listTopByMarketCap");
    var tickers = universe.stream().map(BrapiClient.QuoteListItem::stock).toList();

    log.info("run pre getQuotesWithModules");
    List<Map<String,Object>> raw = brapi.getQuotesWithModules(tickers);
    log.info("run pos getQuotesWithModules");
    List<StockView> out = new ArrayList<>();

    for (var m : raw) {
      var n = brapi.normalize(m);
      String ticker = (String) n.getOrDefault("symbol","");
      String name = (String) n.getOrDefault("name","");
      Double price = (Double) n.get("price");
      Double eps = (Double) n.get("eps");
      Double bvps = (Double) n.get("bvps");
      Double pe = (Double) n.get("pe");
      Double pb = (Double) n.get("pb");
      Double marketCap = (Double) n.get("marketCap");
      Double shares = (Double) n.get("sharesOutstanding");
      Double totalDebt = (Double) n.get("totalDebt");
      Double cash = (Double) n.get("cash");
      Double ebitda = (Double) n.get("ebitda");
      Double equity = (Double) n.get("equity");
      Double netIncome = (Double) n.get("netIncome");
      Double revenue = (Double) n.get("revenue");
      Double ev = (Double) n.get("ev");
      Double evEbitda = (Double) n.get("evEbitda");
      Double netMargin = (Double) n.get("netMargin");
      Double roe = (Double) n.get("roe");

      // ---- fallbacks: compute derived fields if absent ----
      final double EPS = 1e-9;
      if (netMargin == null && revenue != null && Math.abs(revenue) > EPS && netIncome != null) {
        netMargin = (netIncome / revenue) * 100.0;
      }
      if (roe == null && equity != null && Math.abs(equity) > EPS && netIncome != null) {
        roe = (netIncome / equity) * 100.0;
      }

      if (price == null || price <= 0) continue;

      Double intrinsic = null;
      switch (method) {
        case GRAHAM -> {
          if (eps != null && eps > 0 && bvps != null && bvps > 0) {
            intrinsic = Math.sqrt(22.5 * eps * bvps);
          }
        }
        case PE_TARGET -> {
          double tgt = peTarget != null && peTarget > 0 ? peTarget : 12.0;
          if (eps != null && eps > 0) intrinsic = eps * tgt;
        }
        case EV_EBITDA_TARGET -> {
          double tgt = evEbitdaTarget != null && evEbitdaTarget > 0 ? evEbitdaTarget : 6.0;
          if (ebitda != null && ebitda > 0 && shares != null) {
            // Intrinsic via EV/EBITDA alvo => EV_intr = tgt * EBITDA; Price_intr = (EV_intr - Debt + Cash)/Shares
            double evIntr = tgt * ebitda;
            double debt = totalDebt != null ? totalDebt : 0d;
            double c = cash != null ? cash : 0d;
            intrinsic = (evIntr - debt + c) / shares;
          }
        }
      }

      Double mos = (intrinsic != null && intrinsic > 0) ? (intrinsic - price) / intrinsic : null;
      out.add(new StockView(
        ticker, name, price, eps, bvps, pe, pb, marketCap, shares, totalDebt, cash, ebitda, equity,
        netIncome, revenue, ev, evEbitda, netMargin, roe, intrinsic, mos
      ));
    }

    return out.stream()
      .filter(s -> s.marginOfSafety() != null)
      .sorted(Comparator.comparingDouble(s -> -s.marginOfSafety()))
      .limit(howMany)
      .collect(Collectors.toList());
  }
}
