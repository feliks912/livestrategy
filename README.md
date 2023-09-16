# livestrategy

Java backtesting engine - utilizes historical data from Binance for rigorous testing of market strategies.

## Features:

- **Transaction-Level Price Movement:** Analyze price movements at a granular level to gain insights into market dynamics.

- **Candle Construction:** Create candlestick charts for comprehensive technical analysis.

- **Exchange API Latency:** Measure and account for exchange API latency to ensure realistic backtesting results.

- **Orderbook-Based Slippage:** Simulate real-world trading conditions with an accurate slippage model.

- **Margin Leverage per Binance Spot Margin Tier List:** Incorporates Binance's margin tiers and interest calculations for margin trading.

- **Market and Limit Orders:** Execute both market and limit orders (please note that limit orders are a work in progress as of 9/7/2023).

- **Breakeven Analysis:** Evaluate your strategies' breakeven points to optimize risk and reward.

- **Trailing Stoploss:** Implement trailing stop-loss mechanisms, both price-based and using Binance's percentage-based options.

- **Candlestick chart:** Track positions as they're made on a historical candlestick chart.
