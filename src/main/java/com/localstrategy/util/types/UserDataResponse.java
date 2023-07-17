package com.localstrategy.util.types;

import java.util.ArrayList;

import com.localstrategy.UserAssets;
import com.localstrategy.Position;

public class UserDataResponse {
    
    private UserAssets userAssets;

    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedPositions = new ArrayList<Position>();

    public UserDataResponse(
        UserAssets userAssets,
        ArrayList<Position> newPositions, 
        ArrayList<Position> filledPositions,
        ArrayList<Position> cancelledPositions,
        ArrayList<Position> rejectedPositions) {

            this.userAssets = new UserAssets(userAssets);
            this.filledPositions = Position.deepCopyPositionList(filledPositions);
            this.newPositions = Position.deepCopyPositionList(newPositions);
            this.cancelledPositions = Position.deepCopyPositionList(cancelledPositions);
            this.rejectedPositions = Position.deepCopyPositionList(rejectedPositions);
    }

    public UserAssets getUserAssets() {
        return this.userAssets;
    }

    public ArrayList<Position> getFilledPositions() {
        return this.filledPositions;
    }

    public ArrayList<Position> getNewPositions() {
        return this.newPositions;
    }

    public ArrayList<Position> getCancelledPositions() {
        return this.cancelledPositions;
    }

    public ArrayList<Position> getRejectedPositions() {
        return this.rejectedPositions;
    }
}
