package com.example.b3.web;

import com.example.b3.service.ScreenerService;
import com.example.b3.service.ScreenerService.Method;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.*;

@Controller
public class HomeController {

  private final ScreenerService screener;

  public HomeController(ScreenerService screener) {
    this.screener = screener;
  }

  @GetMapping("/")
  public String index(@RequestParam(name = "limit", defaultValue = "20") int limit,
                      @RequestParam(name = "method", defaultValue = "GRAHAM") Method method,
                      @RequestParam(name = "peTarget", required = false) Double peTarget,
                      @RequestParam(name = "evTarget", required = false) Double evTarget,
                      @RequestParam(name = "columns", required = false) String columns,
                      Model model) {
    var list = screener.run(Math.max(1, Math.min(limit, 100)), method, peTarget, evTarget);

    // selected columns
    Set<String> selected = new LinkedHashSet<>();
    if (columns != null && !columns.isBlank()) {
      for (var c : columns.split(",")) selected.add(c.trim());
    }

    model.addAttribute("stocks", list);
    model.addAttribute("limit", limit);
    model.addAttribute("method", method.name());
    model.addAttribute("peTarget", peTarget);
    model.addAttribute("evTarget", evTarget);
    model.addAttribute("selectedCols", String.join(",", selected));
    return "index";
  }
}
