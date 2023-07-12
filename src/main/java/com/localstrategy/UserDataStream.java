package com.localstrategy;

import java.util.ArrayList;

public class UserDataStream {
    
    private AssetHandler userAssets;
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();
    private ArrayList<Position> rapidMoveRejectedStopPositions = new ArrayList<Position>();
    private ArrayList<Position> excessOrderRejectedStopPositions = new ArrayList<Position>();


    public UserDataStream(
        AssetHandler userAssets, 
        ArrayList<Position> activePositions, 
        ArrayList<Position> unfilledPositions,
        ArrayList<Position> rapidMoveRejectedStopPositions,
        ArrayList<Position> excessOrderRejectedStopPositions) {

        this.userAssets = userAssets;
        this.filledPositions = activePositions;
        this.unfilledPositions = unfilledPositions;
        this.rapidMoveRejectedStopPositions = rapidMoveRejectedStopPositions;
        this.excessOrderRejectedStopPositions = excessOrderRejectedStopPositions;
    }


    public AssetHandler getUserAssets() {
        return this.userAssets;
    }

    public ArrayList<Position> getFilledPositions() {
        return this.filledPositions;
    }

    public ArrayList<Position> getUnfilledPositions() {
        return this.unfilledPositions;
    }
    
    public ArrayList<Position> getRapidMoveRejectedStopPositions(){
        return this.rapidMoveRejectedStopPositions;
    }

    public ArrayList<Position> getExcessOrderRejectedStopPositions(){
        return this.excessOrderRejectedStopPositions;
    }
}
