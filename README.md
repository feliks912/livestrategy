# livestrategy

A backtesting engine uses historical data from Binance to test market strategies.

Features:
  transaction-level price movement
  candle construction
  exchange api latency
  linear orderbook-based slippage model
  margin leverage per Binance spot margin tier list, interest included
  market and limit orders (limit WIP as of 9/7/2023)
  trailing stoploss (price-based and Binance percentage based)
  breakeven
