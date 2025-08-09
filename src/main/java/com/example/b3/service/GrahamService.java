package com.example.b3.service;

import com.example.b3.model.StockView;
import org.springframework.stereotype.Service;

import java.util.*;
import java.util.stream.Collectors;

@Service
public class GrahamService {

  private final BrapiClient brapi;

  public GrahamService(BrapiClient brapi) {
    this.brapi = brapi;
  }

  public List<StockView> topDiscounted(int howMany) {
    // 1) choose universe (top by market cap for liquidity)
    var universe = brapi.listTopByMarketCap(Math.max(howMany * 2, 40));
    var tickers = universe.stream().map(BrapiClient.QuoteListItem::stock).toList();

    // 2) batch in chunks of 10 tickers (API-friendly) and collect quotes
    List<Map<String,Object>> raw = new ArrayList<>();
    for (int i=0; i<tickers.size(); i+=10) {
      var slice = tickers.subList(i, Math.min(i+10, tickers.size()));
      raw.addAll(brapi.getQuotesWithModules(slice));
    }

    // 3) map to Graham fields and compute
    List<StockView> computed = new ArrayList<>();
    for (var m : raw) {
      var gf = brapi.toGrahamFields(m);
      if (gf.eps == null || gf.eps <= 0) continue;
      if (gf.bvps == null || gf.bvps <= 0) continue;
      double graham = Math.sqrt(22.5 * gf.eps * gf.bvps);
      if (graham <= 0) continue;
      double intrinsic = graham;
      double mos = (intrinsic - gf.price) / intrinsic;
      if (mos <= 0) continue;

      computed.add(new StockView(
          gf.ticker, gf.name, round(gf.price), round(gf.eps), round(gf.bvps),
          round(graham), round(intrinsic), round(mos)
      ));
    }

    // 4) sort by margin of safety desc and pick top N
    return computed.stream()
        .sorted(Comparator.comparingDouble(StockView::marginOfSafety).reversed())
        .limit(howMany)
        .collect(Collectors.toList());
  }

  private double round(double v) { return Math.round(v * 10000.0) / 10000.0; }
}
