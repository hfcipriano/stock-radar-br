package com.example.b3.model;

public record StockView(
    String ticker,
    String name,
    double price,
    Double eps,
    Double bvps,
    Double pe,
    Double pb,
    Double marketCap,
    Double sharesOutstanding,
    Double totalDebt,
    Double cash,
    Double ebitda,
    Double equity,
    Double netIncome,
    Double revenue,
    Double ev,
    Double evEbitda,
    Double netMargin,
    Double roe,
    // valuation outputs
    Double intrinsic,
    Double marginOfSafety
) {}
