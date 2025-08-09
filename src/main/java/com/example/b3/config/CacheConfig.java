package com.example.b3.config;

import org.springframework.cache.CacheManager;
import org.springframework.cache.caffeine.CaffeineCache;
import org.springframework.cache.support.SimpleCacheManager;
import org.springframework.context.annotation.Bean;
import org.springframework.context.annotation.Configuration;
import com.github.benmanes.caffeine.cache.Caffeine;
import java.util.List;
import java.util.concurrent.TimeUnit;

@Configuration
public class CacheConfig {
  @Bean
  public CacheManager cacheManager() {
    var cm = new SimpleCacheManager();
    cm.setCaches(List.of(
      new CaffeineCache("tickers_top", Caffeine.newBuilder().expireAfterWrite(5, TimeUnit.MINUTES).maximumSize(10).build()),
      new CaffeineCache("quotes_batch", Caffeine.newBuilder().expireAfterWrite(2, TimeUnit.MINUTES).maximumSize(100).build())
    ));
    return cm;
  }
}
