package com.example.b3.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;

import java.util.List;
import java.util.concurrent.TimeUnit;

import com.github.benmanes.caffeine.cache.Caffeine;

@Configuration
public class CacheConfig {
  @Bean
  public CacheManager simpleCacheManager() {
    var manager = new SimpleCacheManager();
    var c1 = new CaffeineCache("tickers_top", Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10).build());
    var c2 = new CaffeineCache("quotes_batch", Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.MINUTES).maximumSize(100).build());
    manager.setCaches(List.of(c1, c2));
    return manager;
  }
}
