package com.localstrategy;

import com.localstrategy.util.enums.OrderSide;
import com.localstrategy.util.helper.SlippageHandler;

import java.math.BigDecimal;

public class SimplePosition {
    public double entry;
    public double stop;
    public double close;
    public double size;
    public double profit;
    public long openTime;
    public SimpleExecutor.Side side;
    public SimpleExecutor.State state;
    public double lockedMoney;

    public long openDelay;

    public double initialStop;

    SimplePosition(double entry, double stop, double lockedMoney, long openTime, long openDelay){
        this.entry = entry;
        this.stop = stop;
        this.initialStop = stop;
        this.lockedMoney = lockedMoney;
        this.openTime = openTime;
        this.openDelay = openDelay;

        this.size = lockedMoney / Math.abs(entry - initialStop);

        this.side = entry > initialStop ? SimpleExecutor.Side.LONG : SimpleExecutor.Side.SHORT;

        size = Math.min(size, SlippageHandler.getMaximumOrderSizeFromBook(
                BigDecimal.valueOf(entry),
                BigDecimal.valueOf(Math.abs(entry - initialStop)),
                BigDecimal.valueOf(5),
                side.equals(SimpleExecutor.Side.LONG) ? OrderSide.BUY : OrderSide.SELL
        ).doubleValue());

        size = Math.max(size, 0.00001);
        size = Math.min(size, 152);

        this.state = SimpleExecutor.State.NEW;
    }

    void closePosition(double closePrice){
        this.close = closePrice;
        this.profit = (close - entry) / (entry - initialStop) * size;
        this.state = SimpleExecutor.State.CLOSED;
    }
}
