# Stock Radar BR

Mini app Spring Boot que busca ações da B3 via **brapi.dev**, calcula **Graham Number** e ordena por **Margin of Safety**.

## Rodar
1. JDK 17+ e Maven 3.9+
2. Exportar o token: `export BRAPI_TOKEN=SEU_TOKEN`
3. `mvn spring-boot:run`
4. Abrir http://localhost:8080

## Notas
- Universo: top por market cap (via `/api/quote/list`). Depois consultamos `/api/quote/{tickers}` com `fundamental=true` e `modules=defaultKeyStatistics`.
- Campos usados:
  - **Preço**: `regularMarketPrice` (fallback `close`)
  - **EPS (LPA)**: `earningsPerShare` (ou `fundamental=true`)
  - **BVPS (VPA)**: `bookValue` (ou `defaultKeyStatistics.bookValue` ou derivado de `priceToBook`)
- Cache: Caffeine (5 min para lista; 2 min para quotes).
