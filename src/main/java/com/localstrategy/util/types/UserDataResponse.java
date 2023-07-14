package com.localstrategy.util.types;

import java.util.ArrayList;
import java.util.Map;

import com.localstrategy.AssetHandler;
import com.localstrategy.Position;
import com.localstrategy.util.enums.RejectionReason;

public class UserDataResponse {
    
    private AssetHandler userAssets;

    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<Map<RejectionReason, Position>>();

    public UserDataResponse(
        AssetHandler userAssets,
        ArrayList<Position> newPositions, 
        ArrayList<Position> filledPositions,
        ArrayList<Position> cancelledPositions,
        ArrayList<Position> rejectedPositions ,
        ArrayList<Map<RejectionReason, Position>> rejectedActions) {

            this.userAssets = userAssets;
            this.filledPositions = filledPositions;
            this.newPositions = newPositions;
            this.rejectedPositions = rejectedPositions;
            this.cancelledPositions = cancelledPositions;
            this.rejectedActions = rejectedActions;
    }


    public AssetHandler getUserAssets() {
        return this.userAssets;
    }

    public ArrayList<Position> getFilledPositions() {
        return this.filledPositions;
    }

    public ArrayList<Position> getNewPositions() {
        return this.newPositions;
    }

    public ArrayList<Position> getRejectedPositions() {
        return this.rejectedPositions;
    }

    public ArrayList<Position> getCancelledPositions() {
        return this.cancelledPositions;
    }

    public ArrayList<Map<RejectionReason,Position>> getRejectedActions() {
        return this.rejectedActions;
    }

    
}
