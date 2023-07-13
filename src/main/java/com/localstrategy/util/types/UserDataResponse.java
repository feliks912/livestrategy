package com.localstrategy.util.types;

import java.util.ArrayList;
import java.util.Map;

import com.localstrategy.AssetHandler;
import com.localstrategy.Position;
import com.localstrategy.util.enums.RejectionReason;

public class UserDataResponse {
    
    private AssetHandler userAssets;

    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> unfilledPositions = new ArrayList<Position>();
    private ArrayList<Position> closedPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();

    private ArrayList<Map<RejectionReason, Position>> rejectedPositionsActions = new ArrayList<Map<RejectionReason, Position>>();

    public UserDataResponse(
        AssetHandler userAssets, 
        ArrayList<Position> filledPositions,
        ArrayList<Position> unfilledPositions, 
        ArrayList<Position> closedPositions, 
        ArrayList<Position> cancelledPositions, 
        ArrayList<Map<RejectionReason, Position>> rejectedPositionsActions) {

            this.userAssets = userAssets;
            this.filledPositions = filledPositions;
            this.unfilledPositions = unfilledPositions;
            this.closedPositions = closedPositions;
            this.cancelledPositions = cancelledPositions;
            this.rejectedPositionsActions = rejectedPositionsActions;
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

    public ArrayList<Position> getClosedPositions() {
        return this.closedPositions;
    }

    public ArrayList<Position> getCancelledPositions() {
        return this.cancelledPositions;
    }

    public ArrayList<Map<RejectionReason, Position>> getRejectedPositionsActions() {
        return this.rejectedPositionsActions;
    }
    
}
