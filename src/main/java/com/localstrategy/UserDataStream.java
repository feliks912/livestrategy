package com.localstrategy;

import java.util.ArrayList;

public class UserDataStream {
    
    private AssetHandler userAssets;
    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();


    public UserDataStream(AssetHandler userAssets, ArrayList<Position> activePositions, ArrayList<Position> unfilledPositions) {
        this.userAssets = userAssets;
        this.filledPositions = activePositions;
        this.unfilledPositions = unfilledPositions;
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
}
