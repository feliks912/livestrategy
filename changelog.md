14.7.23 19:15
    Removed manual BORROW and REPAY options
    Implemented automatic borrow and repay on order fill
        Calculating borrow amount at time of filling
            per https://www.binance.com/en/support/faq/how-to-use-the-one-click-borrow-repay-function-360032609851
        Repaying on new order cancel
    Edited a bunch of other stuff

    TODOS in ExchangeHandler
        + Edit wall logic
        + Rewrite LocalStrategyExecutor as an interface function?
        + Write basic strategy
        + Test.
        + Implement candle & position display
        + implement backtesting concurrency

[IMPORTANT] 13.7.23 17:24 Talked to customer support regarding locking funds when opening limit orders.
    Stop-limit orders lock funds at the time of executing a limit order, which is when the price crosses the stop line
    By using sideEffectType and autoRepayAtCancel we can ask Binance to borrow funds for us at the time of opening an order, and automatically repay if the order is canceled before being executed
        NOTE: Out stoplosses don't have to borrow funds. We already have them unlocked in our account if the market / limit order got filled.

13.7.23 13:36
    From ChatGPT, when shorting an asset - "The margin requirement doesn't change with fluctuations in the asset's price."
        When we borrow bitcoin we lock a fixed amount of funds with the exchange

13.7.23 4:27
    TODO: Attempt to filter entries by
        Number of candles with distances larger than set
        Distance from the previous group
        
        Edit CandlestickChart to display lowest lows and highest highs of the dataset on a press of a button
        Construct and store candles, then randomly select each one

        Entry strategy - market on range break OR close while in range
            Case a: close of tick < distance
                
            Case b: close of tick < distance below / above last range candle

        New rules:
            previous range break candle can't make a new range
                What does this mean?
            set minimum and maximum range length?
            set minimum and maximum range value? As opposed to what? Absolute? Volatility?

                We'll probably have all those statistics figured out.
                    A pure candle-based strategy doesn't have many iterations overall. We could in theory test tens of candle volumes at once given 4-8 iterations of 

                If we remove the maximum order size limit we could become risk percentage agnostic at low percentages.

                To determine the proper stoploss / exit strategy we can use trading results combined with formed candle datasets. That will give me an abillity to have formed entry statistics.

                Really we go step by step primarily for entries and stoplosses. The stoploss is mostly same for every order. Therefore the key is to get the entry figured out, and then we can iterate though candles to see where to exit etc.

12.7.23 14:33
    Mayor rewrite in progress, adding functional blocks to a simulated exchange engine
    It's a oversimplified model but 10x more reliable than the previous version. The goal is to say how we can expect similar results from live testing.

12.7.23 4:34
    Todo: Build a functional backtesting engine.
        Market orders
        Limit orders
        Stoplosses
        Slippage
        Latencies
        Price walls
        Margin borrows

12.7.23 1:32
    Looking to implement the following strategy - If the first candle whos' tick < distance closes outside of the last candle whos' tick >= distance in the oposite direction of the move, open a limit order between the last candle's high / low and the top / bottom. Discard position after ~15 candles

    Observations:
        Unstable

11.7.23 18:00
    Days 2022-11-22 (632) to 2023-03-23 (753) (121 days total) terrible performance ~50% drawdown in a volatile period

11.7.23 15:48
    Revisited observed latencies on AWS Tokyo-based VPS t3.small

    Observations:
        TODO: More statistical analysis must be put in trade event latencies. It occurs to me there are 'walls' of transactions which all lag behind the stream during large moves. Each transaction after the next one lags by a tiny bit more ( a few milliseconds ) but they all seem to have a common offset with which they start. Example 100 102 103 104 105 106 etc but normal transactions are in the realm of 10-20 ms of latency.

        The current observed statistic is as follows
                    --- Trade Execution latency ---
            Median: 11 ms
            Mode: 10 ms
            Standard deviation: 19.115001613980795 ms
            Second standard deviation: 38.23000322796159 ms

                --- Trade Execution Response latency ---
            Median: 16 ms
            Mode: 16 ms
            Standard deviation: 22.450363561377916 ms
            Second standard deviation: 44.90072712275583 ms

                --- Transaction latency for dt < 5ms ---
            Median: 109 ms
            Mode: 7 ms
            Standard deviation: 203.8502417844852 ms
            Second standard deviation: 407.7004835689704 ms

        Where 
            Trade execution latency is the time it takes for the exchange to execute our order in their engine as opposed to the time of sending the request

            Trade execution response latency is the time it takes to receive a response from the exechange regarding the execution of our trade as opposed to the time of sending the request

            Transaction latency for dt < 5ms are the latencies of trade events when the trade is executed on the exchange in 5ms or less after the previous one. This servers as a filter to walls since we observed (and wish to prove) the highest latency trades are those which got executed in the close proximity during large price movements (but not necessary during large price movements).
                This statistical approach is not suitable for backtesting. Running a normal distribution would cause chaos. The wall starts with a seemingly random latency but each order from the wall we get after it's reported is not delayed compared to the one before it.

                Meaning when walls occur, it takes binance some time to process them and report on all of them. Meaning we can't act during wall execution. At the time of reporting the wall is already finished.

                FIXME: 
                    Implement walls in backtesting (currently only available for the symbol we live test it for).
                    
                    I'll have to implement a positive lookahead on the historical data to identify walls ahead of time. Because the delay is instantaneous (the exchange lags at the moment of processing all orders)

                    Effectivelly I'll havet to 'freeze' the program during walls because during live we can't act on that price movement in real time, only after it already happened. Regardless, since those moves are relatively small it shouldn't be an issue.

                    Also, potentially implement latency checks in a live program. It can't iterate over transactions if those transactions happened 2 seconds ago. It must ignore the wall and jump to the first price after it.

                    The bad new is, our order will likely be filled during those spikes which introduce spread
                        Please leave spread for later. It should not make or break the strategy completely.

                        On further inspection it can definitely break the strategy.
                        The price on rapid movements can be $11+. This means if we have a limit order on that level it will be filled at first available orderbook level (depending on when the exchange processes our order.) making us pay heavy fines.

                        The alternative is to be dumb. Follow price movement an act only when a level is reached. This allows us to decide whether we'll take the order at that price (also requires only market orders as entries).

                        FIXME:
                        These observations however tell a different story for our stoplosses. The fill price could be horendous and far from our linearized orderbook model.
                            Here's how we can still use the model:
                                We make it static on each price stagnation that is, we refresh it during horizontal moves. If the price moves rapidly we adjust the model so the orderbook is used by the move of the market, giving us filling prices of the half-used orderbook.

                        Good news is - no more latency issues! If we wait for the market to stabilize (especially during non-volatile periods) we will always get a fair price + slippage from the model. Afaik that would make us makers on every order made. Hopefully irl it doesn't trigger unwanted concequences...

                        TODO: The trigger for a rest period is... how many transactions?
                            Tested on non volatile and volatile period
                                The volatile period has walls more often than it has consolidations
                                Non volatile period vice versa

                                The orderbook model is crucial during a volatile period

10.7.23 20:00 looking at implemented features

    price update
        transaction price deviation calculation
        stoploss active / closed before stoploss check
        (delayed) long/short TpRR calculation
        (delayed) new candle case
            fixedRR
            breakeven
            trailing stop
        stoploss check
            stoploss
            closed before stoploss
        remove closed positions
        margin test
        entry logic
        TODO: (delayed) entry logic change


    candle update
        set new candle flag
        this candle request latency (event + trade)
        interest calculation
        fetch candle
        closed fixedRR trades
        breakeven / trailing stop
        indicator calculation

10.7.23 6:21
    Todo: revert back to original strategy, match TpRRs to opposite orders of similar distances. That should, in theory, let the runners run while getting some scalping action.

10.7.23 4:15
    2000000 candles work with 0% profit on rocky period and ~50% after with 7% drawdown check.

    Todo:
        Reintroduce limit orders
            Set stoploss at the retouch level.

9.7.2023 21:50
    Added trailing stoploss at a percentage between previous candle close and entry.

    Observations:
        Some days are catastrophically profitable, but on others the losses recouperate

    Todo:
        500000 volume candles just don't seem to cut it on days ~450 to ~700. Try with 2mil.
        TODO: Priority - cut losses
            Loss prevention methods:
                Opposite reordering ?
                Higher distance values
                Higher ZigZag parameter values
                Rollback to matching ZigZag values with highest / lowest values
                Lower risk
                New detection method (but refactor first)
                    Note previous potential limit levels (but don't take them) and only open positions when price crosses that level. Hopefully those are potential reversal levels so them, and onwards would be a good place to start checking for levels

9.7.2023 20:33
    Reintroduced ranges to entry triggers

    Observations:
        Higher profits

    Todo:
        Introduce BE on a percentage move from the entry price, this way we'll avoid losses where price moves away from entry but hits the stoploss in the same candle. Especially true in volatile markets. Prioritize minimizing losses.

9.7.2023 20:00
    Added latency between order activation and setting a stoploss

    Observation:
        We lost some on that one

    Todo: FIXME:
        Currently we place a stop-limit order entry request when when a new stoploss level is recognized, but I forgot to cancel the previous one on new candle formation. That takes 2*trade request latency because we must cancel, then create an order (binance, of course, doesn't support closing and creating a new order in the same request ofc ofc). I must implement the latency those two requests take.

        Also, it takes LONGER for us to get a response from the server because I've been analyzing how long it takes for the exchange to execute and order compared to when the request was sent, not how long it takes for us to receive that response. I've got to test that aswell, then implement it here.

9.7.2023 18:30
    Added trailing stoploss on previous candle high / low
    Changed entry strategy - last low / high no longer have to match the lowest low / highest recent high (but new orders can still only be made on a lower low / higher high using usedHigh / usedLow flags)
    The entry price is always ZigZag low / high, no more range.

    Observations:
        More positions - crazy profits, crazy losses.
    
    Todo:
        Increase distance and ZigZag values
        Introduce reversing a trade on stop
            Fixed profit to recouperate loss (depends on stoploss percentage)
        Convert to multi-threaded