package com.example.b3.web;

import com.example.b3.model.StockView;
import com.example.b3.service.GrahamService;
import org.springframework.stereotype.Controller;
import org.springframework.ui.Model;
import org.springframework.web.bind.annotation.GetMapping;
import org.springframework.web.bind.annotation.RequestParam;

import java.util.List;

@Controller
public class HomeController {

  private final GrahamService grahamService;

  public HomeController(GrahamService grahamService) {
    this.grahamService = grahamService;
  }

  @GetMapping("/")
  public String index(@RequestParam(name = "limit", defaultValue = "15") int limit, Model model) {
    List<StockView> stocks = grahamService.topDiscounted(Math.max(1, Math.min(limit, 100)));
    model.addAttribute("stocks", stocks);
    model.addAttribute("limit", limit);
    return "index";
  }
}
