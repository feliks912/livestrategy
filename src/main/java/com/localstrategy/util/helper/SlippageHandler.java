package com.localstrategy.util.helper;

import com.localstrategy.util.enums.OrderSide;

import java.math.BigDecimal;
import java.math.RoundingMode;
import java.util.Map;

public class SlippageHandler {

    private static final BigDecimal ORDERBOOK_PCT = BigDecimal.valueOf(0.4); // 0.4%
    private static final BigDecimal ORDERBOOK_QTY = BigDecimal.valueOf(150); // 150 BTC
    private static final BigDecimal SQUARE_ROOT_MODEL_CONSTANT = BigDecimal.valueOf(0.5);
    private static final long BTCUSDT_DAILY_VOLUME = 8_570_000_000L;

    public static BigDecimal getSlippageFillFromBook(BigDecimal price, BigDecimal orderSize, OrderSide orderSide){

        //Shift price and difference and compare to existing map without the need of shifting the entire orderbook

        double priceDiff = price.doubleValue() - BinaryOrderbookReader.getMidprice();

        Map<Double, Double> map = orderSide.equals(OrderSide.BUY) ? BinaryOrderbookReader.asks : BinaryOrderbookReader.bids;

        double size = orderSize.doubleValue();

        double cumQ = 0;

        double weight = 0;

        for(Map.Entry<Double, Double> e : map.entrySet()){
            if(cumQ + Math.abs(e.getValue()) < size){
                weight += Math.abs(e.getValue()) * e.getKey();
                cumQ += Math.abs(e.getValue());
            } else {
                weight += (size - cumQ) * e.getKey();
                break;
            }
        }

        double fillPrice = weight / size;

        return BigDecimal.valueOf(fillPrice + priceDiff);
    }

    public static BigDecimal getMaximumOrderSizeFromBook(BigDecimal price, BigDecimal priceDifference,
                                                         BigDecimal percentage, OrderSide orderSide){

        BigDecimal direction = (orderSide.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
        BigDecimal fillingPrice = price.add(direction.multiply(percentage.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(priceDifference)));

        double diff = Math.abs(fillingPrice.doubleValue() - price.doubleValue());

        double cumQ = 0;
        double weight = 0;

        Map<Double, Double> map = orderSide.equals(OrderSide.BUY) ? BinaryOrderbookReader.asks : BinaryOrderbookReader.bids;

        for(Map.Entry<Double, Double> e : map.entrySet()){

            double currentDiff = Math.abs((weight + e.getKey() * Math.abs(e.getValue())) / (cumQ + Math.abs(e.getValue())) - fillingPrice.doubleValue());

            if(currentDiff > diff){

                if(cumQ == 0 && weight == 0){
                    return BigDecimal.valueOf(Math.abs(e.getValue()));
                }

                cumQ += ((fillingPrice.doubleValue() * cumQ) - weight) / (price.doubleValue() - fillingPrice.doubleValue());
                return BigDecimal.valueOf(cumQ);
            } else {
                cumQ += Math.abs(e.getValue());
                weight += Math.abs(e.getValue()) * e.getKey();
            }
        }

        return BigDecimal.ZERO;
    }

    public static BigDecimal getSlippageFillPrice(BigDecimal price, BigDecimal orderSize, OrderSide orderSide) {

        BigDecimal direction = (orderSide.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
        BigDecimal fillPrice = price.multiply(BigDecimal.ONE.add(ORDERBOOK_PCT.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .divide(ORDERBOOK_QTY, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(Math.sqrt(2)), 10, RoundingMode.HALF_UP)
                .multiply(orderSize).multiply(direction)));

        return fillPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal getUnadjustedFillPrice(BigDecimal price, BigDecimal orderSize, OrderSide orderSide){
        BigDecimal direction = (orderSide.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
        BigDecimal fillPrice = price.multiply(BigDecimal.ONE.add(ORDERBOOK_PCT.divide(BigDecimal.valueOf(100))
                .divide(ORDERBOOK_QTY, RoundingMode.HALF_UP).divide(BigDecimal.valueOf(Math.sqrt(2)))
                .multiply(orderSize).multiply(direction)));

        return fillPrice.setScale(2, RoundingMode.HALF_UP);
    }

    public static BigDecimal getMaximumOrderSize(BigDecimal price, BigDecimal priceDifference,
                                                 BigDecimal percentage, OrderSide orderSide) {

        BigDecimal direction = (orderSide.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());
        BigDecimal fillingPrice = price.add(direction.multiply(percentage.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP)
                .multiply(priceDifference)));
        BigDecimal priceRatio = fillingPrice.subtract(price).abs().divide(price, 8, RoundingMode.HALF_UP);

        BigDecimal maximumOrderSize = priceRatio.multiply(ORDERBOOK_QTY).multiply(BigDecimal.valueOf(Math.sqrt(2)))
                .divide(ORDERBOOK_PCT.divide(BigDecimal.valueOf(100), 10, RoundingMode.HALF_UP), 10, RoundingMode.HALF_UP);

        return maximumOrderSize.setScale(8, RoundingMode.HALF_UP);
    }

    public static BigDecimal getRootSlippageFillPrice(BigDecimal price, BigDecimal volatility,
                                                      BigDecimal orderSize, OrderSide orderSide) {

        BigDecimal deltaPrice = volatility.multiply(SQUARE_ROOT_MODEL_CONSTANT)
                .multiply(BigDecimal.valueOf(Math.sqrt(orderSize.multiply(price).divide(BigDecimal.valueOf(2).multiply(BigDecimal.valueOf(BTCUSDT_DAILY_VOLUME)), RoundingMode.HALF_UP).doubleValue())));
        deltaPrice = deltaPrice.multiply(orderSide.equals(OrderSide.BUY) ? BigDecimal.ONE : BigDecimal.ONE.negate());

        BigDecimal rootFillPrice = price.add(deltaPrice);

        return rootFillPrice.setScale(2, RoundingMode.HALF_UP);
    }

    //FIXME: Fixme.
    public static BigDecimal getRootMaximumOrderSize(BigDecimal price, BigDecimal priceDifference,
                                                     BigDecimal percentage, BigDecimal volatility, OrderSide orderSide) {
        return BigDecimal.ZERO;
    }


}
