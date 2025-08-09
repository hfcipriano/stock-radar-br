package com.example.b3.model;

public record StockView(
    String ticker,
    String name,
    double price,
    double eps,
    double bvps,
    double grahamNumber,
    double intrinsic,
    double marginOfSafety
) {}
