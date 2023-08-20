package com.localstrategy.util.misc;

import com.localstrategy.util.types.Candle;

import java.nio.file.Path;
import java.util.ArrayList;

public class BinaryCandleHandler {

    Path folder;

    public BinaryCandleHandler(String dataFolder){
       this.folder = Path.of(dataFolder);
    }

    public boolean appendCandle(Candle candle){

        return false;
    }

    public Candle readNextCandle(){

        return null;
    }

    public ArrayList<Candle> readAllCandles(){

        return null;
    }
}
