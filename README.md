# Stock Radar BR — Pro (UI dinâmica)
- Módulos usados: `financialData, defaultKeyStatistics, incomeStatementHistory, balanceSheetHistory, financialDataHistory, cashflowHistory` (sem `summaryDetail`).
- Chunk de 10 tickers por request.
- UI permite:
  - escolher método de valor intrínseco: **Graham**, **P/E alvo** (default 12), **EV/EBITDA alvo** (default 6).
  - ligar/desligar colunas (persistência via query `columns`).
- Ranking por **Margin of Safety** do método escolhido.

## Rodar
1. `export BRAPI_TOKEN=...`
2. `mvn spring-boot:run`
3. `http://localhost:8080`
