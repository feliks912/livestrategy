package com.localstrategy.util.types;

import java.util.ArrayList;
import java.util.HashMap;
import java.util.Map;

import com.localstrategy.UserAssets;
import com.localstrategy.Position;
import com.localstrategy.util.enums.RejectionReason;

public class UserDataResponse {
    
    private UserAssets userAssets;

    private ArrayList<Position> filledPositions = new ArrayList<Position>();
    private ArrayList<Position> newPositions = new ArrayList<Position>();
    private ArrayList<Position> rejectedPositions = new ArrayList<Position>();
    private ArrayList<Position> cancelledPositions = new ArrayList<Position>();

    //FIXME: A map cannot hold more than one unique key therefore we couldn't have more than one RejectionReason per iteration
    private ArrayList<Map<RejectionReason, Position>> rejectedActions = new ArrayList<Map<RejectionReason, Position>>();

    public UserDataResponse(
        UserAssets userAssets,
        ArrayList<Position> newPositions, 
        ArrayList<Position> filledPositions,
        ArrayList<Position> cancelledPositions,
        ArrayList<Position> rejectedPositions ,
        ArrayList<Map<RejectionReason, Position>> rejectedActions) {

            this.userAssets = new UserAssets(userAssets);
            this.filledPositions = Position.deepCopyPositionList(filledPositions);
            this.newPositions = Position.deepCopyPositionList(newPositions);
            this.rejectedPositions = Position.deepCopyPositionList(rejectedPositions);
            this.cancelledPositions = Position.deepCopyPositionList(cancelledPositions);
            this.rejectedActions = deepCopyRejectionActionList(rejectedActions);
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

    public ArrayList<Position> getRejectedPositions() {
        return this.rejectedPositions;
    }

    public ArrayList<Position> getCancelledPositions() {
        return this.cancelledPositions;
    }

    public ArrayList<Map<RejectionReason,Position>> getRejectedActions() {
        return this.rejectedActions;
    }

    private ArrayList<Map<RejectionReason, Position>> deepCopyRejectionActionList(ArrayList<Map<RejectionReason, Position>> originalList) {
        ArrayList<Map<RejectionReason, Position>> newList = new ArrayList<>();

        for (Map<RejectionReason, Position> map : originalList) {
            Map<RejectionReason, Position> newMap = new HashMap<>();
            for (Map.Entry<RejectionReason, Position> entry : map.entrySet()) {
                RejectionReason reason = entry.getKey(); // Assuming RejectionReason is immutable
                Position newPos = new Position(entry.getValue());
                newMap.put(reason, newPos);
            }
            newList.add(newMap);
        }
        return newList;
    }
}
