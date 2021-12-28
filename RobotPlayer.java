package Robot_1;

import battlecode.common.*;

import java.sql.Time;
import java.util.*;

public strictfp class RobotPlayer {
    static RobotController rc;

    static Direction[] directions = {
            Direction.NORTH,
            Direction.NORTHEAST,
            Direction.EAST,
            Direction.SOUTHEAST,
            Direction.SOUTH,
            Direction.SOUTHWEST,
            Direction.WEST,
            Direction.NORTHWEST
    };
    static RobotType[] spawnedByMiner = {RobotType.REFINERY, RobotType.VAPORATOR, RobotType.DESIGN_SCHOOL,
            RobotType.FULFILLMENT_CENTER, RobotType.NET_GUN};

    static int turnCount;
//    static ArrayList<MapLocation> wallCoord1 = new ArrayList<MapLocation>();
    static int unitsmade = 0;

    // For msgs
    static int pw; // For team differentiation
    static int bid = 10;
    static Node log = new Node(); // To hold all msgs
    static int[] prevmsg; // For checking if msg went through
    static boolean sentmsg; // For checking if msg went through
    static Node queue = new Node(); // For keeping msgs that haven't been sent yet

    // For testLandscaper: 0-need to dig, 1-need to deposit, 2-need to move
    static int mode = 0;

    /**
     * run() is the method that is called when a robot is instantiated in the Battlecode world.
     * If this method returns, the robot dies!
     **/
    @SuppressWarnings("unused")
    public static void run(RobotController rc) throws GameActionException {

        // This is the RobotController object. You use it to perform actions from this robot,
        // and to get information on its current status.
        RobotPlayer.rc = rc;
        turnCount = 0;

        // For msgs
        if (rc.getTeam()==Team.A)
            pw = 71984652;
        else pw = 71984653;
        log.initiate();

        System.out.println("I'm a " + rc.getType() + " and I just got created!");
        while (true) {
            turnCount += 1;
            // Try/catch blocks stop unhandled exceptions, which cause your robot to explode
            try {
                // For checking all manners of stuff :)
                sync();

                switch (rc.getType()) {
                    case HQ: runHQ(); break;
                    case MINER: runMiner(); break;
                    case REFINERY: runRefinery(); break;
                    case VAPORATOR: runVaporator(); break;
                    case DESIGN_SCHOOL: runDesignSchool(); break;
                    case FULFILLMENT_CENTER: runFulfillmentCenter(); break;
                    case LANDSCAPER: testLandscaper(); break;
                    case DELIVERY_DRONE: runDeliveryDrone(); break;
                    case NET_GUN: runNetGun(); break;
                }

                // Clock.yield() makes the robot wait until the next turn, then it will perform this loop again
                Clock.yield();

            } catch (Exception e) {
                System.out.println(rc.getType() + " Exception");
                e.printStackTrace();
            }
        }
    }
    
    static class Node {
        // A node class used to store info in the msg tree
        int[] keys = new int[0];
        Node[] vals = new Node[0];

        void initiate() throws GameActionException {
            System.out.println("start initiate():" + Clock.getBytecodeNum());
            // Checks all existing blocks
            if (rc.getRoundNum()!=1) {
                for (int i = 1; i < rc.getRoundNum(); i++) {
                    Transaction[] transactions = rc.getBlock(i);
                    for (Transaction transaction : transactions) {
                        int[] msg = transaction.getMessage();
                        if (msg[0]==pw)
                            this.add(msg);
                    }
                }
            }
            System.out.println("end initiate():" + Clock.getBytecodeNum());
        }

        void update() throws GameActionException {
            // Adds all msgs from current block
            Transaction[] newblock = rc.getBlock(rc.getRoundNum()-1);
            for (Transaction transaction : newblock) {
                int[] msg = transaction.getMessage();

                // Makes sure msgs went through, if sent msg & enemy msg went through & not ours
                if (msg[0]!=pw) { // msg not from our team
                    if (sentmsg && newblock.length == 7 && !log.exists(prevmsg)) {
                        System.out.println("Transaction blocked. Raising bid!");
                        bid += 5;
                    }
                }
                else if (msg[1]==1 && msg[4]==1) { // It's a soup depletion, remove known loc of soup
                    msg[4] = 0;
                    this.remove(msg);
                }
                else if (!log.exists(msg))// msg is from our team
                    this.add(msg);
                sentmsg = false;
            }
        }

        void add(int[] entries) {add(entries,0);}
        void add(int[] entries, int index) {
            // If entry already exists and even more entries to go, skip one step
            int keyindex = indexOf(keys, entries[index]);
            if (keyindex!=-1 && index<entries.length-1)
                vals[keyindex].add(entries, index+1);
            else {
                // Add additional nodes containing the entries
                Node next = new Node();
                int keyslength = keys.length;
                int[] newkeys = new int[keyslength+1];
                Node[] newvals = new Node[keyslength+1];
                for (int i=0; i<keyslength; i++) {
                    newkeys[i] = keys[i];
                    newvals[i] = vals[i];
                }
                newkeys[keyslength] = entries[index];
                newvals[keyslength] = next;
                keys = newkeys;
                vals = newvals;

                // If there's more entries
                if (index<entries.length-1)
                    next.add(entries, index+1);
            }
        }

        boolean remove(int[] entries) {
            if (remove(entries, 0)) {
                keys = new int[0];
                vals = new Node[0];
            }
            return false;
        }
        boolean remove(int[] entries, int index) {
            // If there are no other children nodes, reached end
            if (index==entries.length-1)
                return true;
            // Delete path
            int keyindex = indexOf(keys, entries[index]);
            if (keyindex!=-1 && vals[keyindex].remove(entries, index+1)) {
                int keyslength = keys.length;
                // If this one also remove...don't waste time, remove the higher element
                if (keyslength==1)
                    return true;

                int[] newkeys = new int[keyslength-1];
                Node[] newvals = new Node[keyslength-1];
                for (int i=0; i<keyslength; i++) {
                    // Don't add if it's the remove element
                    if (i<keyindex) {
                        newkeys[i] = keys[i];
                        newvals[i] = vals[i];
                    }
                    else if (i>keyindex) {
                        newkeys[i-1] = keys[i];
                        newvals[i-1] = vals[i];
                    }
                }
                keys = newkeys;
                vals = newvals;
            }
            return false;
        }

        boolean exists(int[] entries) {return exists(entries, 0);}
        boolean exists(int[] entries, int index) {
            // Checks to see if the entries are contained in the (sub)tree
            int keyindex = indexOf(keys, entries[index]);
            if (keyindex!=-1) {
                // If last entry
                if (index==entries.length-1) return true;
                // Otherwise keep checking
                return vals[keyindex].exists(entries, index+1);
            }
            return false;
        }

        int[][] search(int[] entries) {return search(entries,0);}
        int[][] search(int[] entries, int index) {
            // Returns all transactions that start with entries
            // If base case
            if (index==entries.length && keys.length==0)
                return new int[0][];

            int keyindex = 0;
            // If pre-base case
            int[] keysofinterest = new int[0];
            if (index==entries.length || entries.length==0)
                keysofinterest = keys;
            // Regular case?
            else {
                keyindex = indexOf(keys, entries[index]);
                if (keyindex!=-1)
                    keysofinterest = new int[]{entries[index]};
            }

            // Recursion!
            int[][] result = new int[0][];
            for (int key : keysofinterest) {
                int[][] subsubresult;
                if (index<entries.length) {
                    subsubresult = vals[keyindex].search(entries, index + 1);
                    // If full key not found
                    if (subsubresult.length==0)
                        return subsubresult;
                }
                else
                    subsubresult = vals[keyindex].search(entries, index);

                int[][] subresult = new int[subsubresult.length][];
                // If empty then success!
                if (subsubresult.length==0) {
                    subresult = new int[][]{{key}};
                }

                int i = 0;
                // Add each tail to this key
                for (int[] tail : subsubresult) {
                    int[] newtail = new int[tail.length + 1];
                    newtail[0] = key;
                    for (int j = 1; j <= tail.length; j++)
                        newtail[j] = tail[j - 1];
                    subresult[i] = newtail;
                    i++;
                }
                // Add each key to the total result
                int[][] newresult = new int[result.length+subresult.length][];
                for (int k=0; k<result.length; k++)
                    newresult[k] = result[k];
                for (int k=0; k<subresult.length; k++)
                    newresult[k+result.length] = subresult[k];
                // Update the result
                result = newresult;
            }

            return result;
        }
    }

    static int indexOf(int[] keys, int key) {
        // A helper that searches for the given key in the array and returns the index, returns -1 if not found
        for (int i=0; i<keys.length; i++) {
            if (keys[i]==key) {
                return i;
            }
        }
        return -1;
    }

    static void sync() throws  GameActionException {
        // Get new msgs
        if (rc.getRoundNum()!=1)
            log.update();

        // Send msgs that haven't been sent yet
        int[][] oldqueue = queue.search(new int[0]);
        for (int[] msg : oldqueue) {
            queue.remove(msg);
            if (!log.exists(msg))
                System.out.println("Trying to submit msg again");
                sendMsg(msg);
        }
    }

    static void sendMsg(int[] msg) throws GameActionException {
        // A helper function that handles sending transactions
        if (!log.exists(msg) && !queue.exists(msg)) {
            if (rc.canSubmitTransaction(msg, bid)) {
                rc.submitTransaction(msg, bid);
                prevmsg = msg;
                sentmsg = true;
                System.out.println("Transaction submitted!");
            } else {
                // Not enough soup, queue it for now
                queue.add(msg);
                System.out.println("Transaction queued! " + queue.keys.length + "msgs");
            }
        }
    }

    static void runHQ() throws GameActionException {
        // Msg out the HQ location in the blockchain
        if (rc.getRoundNum() == 1) {
            // Code test area
//            Node test = new Node();
//            System.out.println(!test.exists((new int[]{1,2,3})));
//            test.add(new int[]{1,2,3});
//            boolean worked = test.exists(new int[]{1,2,3});
//            test.remove(new int[]{1,2,3});
//            boolean dworked = !test.exists((new int[]{1,2,3}));
//            System.out.println(worked);
//            System.out.println(dworked);
//            test.add(new int[]{1,2,3});
//            test.add(new int[]{1,3,3});
//            test.add(new int[]{2,4,2});
//            test.add(new int[]{1,8,25});
//            int [][] result = test.search(new int[]{1});
//            System.out.println(result.length==3);
//            System.out.println("start test:" + Clock.getBytecodeNum());
//            Map<Integer, Node> map = new HashMap<Integer, Node>();
//            map.put(1, new Node());
//            map.get(1);
//            int[] keys = new int[0];
//            Node[] vals = new Node[0];
//            keys = new int[]{3,2,1};
//            vals = new Node[]{new Node(),new Node(),new Node()};
//            for (int i=0; i<keys.length; i++) {
//                if (keys[i]==1) {
//                    Node blah = vals[keys[i]];
//                }
//            }
//            System.out.println("end test:" + Clock.getBytecodeNum());

            MapLocation hqloc = rc.getLocation();
            int[] msg = {pw,0,0,0,0, hqloc.x, hqloc.y};
            sendMsg(msg);
        }
        // Ted's code, needs to run once
//        else if (rc.getRoundNum() == 2) getWallCoord(wallCoord1);

        // Shoot down enemies first!
        runNetGun();

        // If no enemies were attacked and not too many miners
        if (rc.isReady() && unitsmade<2) {
            for (Direction dir : directions)
                if (tryBuild(RobotType.MINER, dir)) {
                    unitsmade++;
                    break;
                }
        }
    }


    static void runMiner() throws GameActionException {
        // Can still mine more soup!
        if (rc.getSoupCarrying() < RobotType.MINER.soupLimit) {
            // Build Design_School if can and haven't already
            if (unitsmade==0 && rc.getTeamSoup()>=150) {
                MapLocation hqloc = findHQ();
                int disttohq = rc.getLocation().distanceSquaredTo(hqloc);
                if (disttohq>15) { // Outside than build radius
                    if (disttohq < 21) {// Build
                        if (tryBuild(RobotType.DESIGN_SCHOOL, rc.getLocation().directionTo(hqloc)))
                            unitsmade++;
                        else randMove();
                    } else returnHQ();
                } else if (disttohq<9) {// Inside build radius
                    Direction oppdir = rc.getLocation().directionTo(rc.getLocation().subtract(rc.getLocation().directionTo(hqloc)));
                    if (disttohq>2) { // Build
                        if (tryBuild(RobotType.DESIGN_SCHOOL, oppdir))
                            unitsmade++;
                        else randMove();
                    } else {
                        if (!tryMove(oppdir))
                            randMove();
                    }
                }
            }
            // Try to mine
            for (Direction each : directions){
                if (tryMine(each)) {
                    System.out.println("Mined soup");
                    return;
                }
            }
            // If no soup nearby, find soup!
            MapLocation closestsoup = findSoup();
            System.out.println("Found closest soup:" + Clock.getBytecodeNum());
            if (closestsoup.x!=0 && closestsoup.y!=0)
                go(closestsoup);
            for (Direction dir : directions) {
                randMove();
                return;
            }
        }
        // Refine soup!
        else {
            System.out.println("Carrying" + rc.getSoupCarrying() + ", go home!");
            if (returnHQ()) {
                System.out.println("Deposit!");
                for (Direction dir : directions) {
                    if (tryRefine(dir))
                        break;
                }
            }
        }
    }

    static MapLocation findSoup() throws GameActionException {
        System.out.println("findSoup() start:" + Clock.getBytecodeNum());
        // A miner helper that finds the closest soup
        MapLocation[] nearbysoup = new MapLocation[0];
//        boolean first = true; // For checking if first nearby location
        // Make sure nearby soup loc in the block
        for (MapLocation loc : rc.senseNearbySoup()) {
//            if (first) {
//                sendMsg(new int[]{pw, 1, 0, 0, 0, loc.x, loc.y});
//            }
            // Add the new location
            MapLocation[] newnearbysoup = new MapLocation[nearbysoup.length+1];
            newnearbysoup[nearbysoup.length] = loc;
            for (int i=0; i<nearbysoup.length; i++)
                newnearbysoup[i] = nearbysoup[i];
            nearbysoup = newnearbysoup;
        }

        // Check global msgs only if no nearby
//        if (nearbysoup.length==0) {
//            int[][] souplocs = log.search(new int[]{pw, 1, 0, 0, 0});
//            for (int[] souploc : souplocs) {
//                // If there's no soup there!
//                if (rc.canSenseLocation(new MapLocation(souploc[5],souploc[6])))
//                    sendMsg(new int[]{pw, 1, 0, 0, 1, souploc[5], souploc[6]});
//                // Otherwise add the known location
//                MapLocation[] newnearbysoup = new MapLocation[nearbysoup.length + 1];
//                newnearbysoup[nearbysoup.length] = new MapLocation(souploc[4], souploc[5]);
//                for (int i = 0; i < nearbysoup.length; i++)
//                    newnearbysoup[i] = nearbysoup[i];
//                nearbysoup = newnearbysoup;
//            }
//        }
//        System.out.println(nearbysoup.length);
//        System.out.println("found all nearbysoup:" + Clock.getBytecodeNum());

        // Find closest soup!
        return closest(nearbysoup);
    }

    static void runRefinery() throws GameActionException {
        // System.out.println("Pollution: " + rc.sensePollution(rc.getLocation()));
        // look out for pollution levels...
    }

    static void runVaporator() throws GameActionException {

    }

    static void runDesignSchool() throws GameActionException {
        if(unitsmade < 2 && rc.isReady()) {
            for (Direction dir : directions) {
                if(tryBuild(RobotType.LANDSCAPER, dir)) {
                    System.out.println("Landscaper created.");
                    unitsmade++;
                    break;
                }
            }
        }
    }

    static void runFulfillmentCenter() throws GameActionException {
        for (Direction dir : directions)
            tryBuild(RobotType.DELIVERY_DRONE, dir);
    }

    static void testLandscaper() throws GameActionException {
        // Go to hq first
        if (returnHQ()) {
            // Get dir away from hq
            Direction dir = rc.getLocation().directionTo(rc.getLocation().subtract(rc.getLocation().directionTo(findHQ())));
            Direction clockdir = Direction.CENTER;
            switch (rc.getLocation().directionTo(findHQ())) {
                case SOUTH: clockdir = Direction.EAST; break;
                case SOUTHWEST: clockdir = Direction.SOUTH; break;
                case WEST: clockdir = Direction.SOUTH; break;
                case NORTHWEST: clockdir = Direction.WEST; break;
                case NORTH: clockdir = Direction.WEST; break;
                case NORTHEAST: clockdir = Direction.NORTH; break;
                case EAST: clockdir = Direction.NORTH; break;
                case SOUTHEAST: clockdir = Direction.EAST; break;
            }

            switch (mode) {
                case 0: // Dig opposite of hq!
                    if (rc.canDigDirt(dir))
                        rc.digDirt(dir);
                    if (rc.getDirtCarrying()==3)
                        mode = 1;
                    break;
                case 1: // Deposit at current spot!
                    rc.depositDirt(Direction.CENTER);
                    if (rc.getDirtCarrying()==0)
                        mode = 2;
                    break;
                case 2: // Move to next location, clockwise!
                    if (tryMove(clockdir)) {// Moved successfully, restart
                        mode = 0;
                        break;
                    }
                    else if(rc.senseRobotAtLocation(rc.getLocation().add(clockdir))==null) { // Blocked by depth diff
                        if (rc.senseElevation(rc.getLocation().add(clockdir)) >= rc.senseElevation(rc.getLocation())-3) { // Front is higher
                            mode = 0;
                            break;
                        }
                        mode = 3; // Front is lower
                    }
                case 3: // Blocked, deposit in front
                    if (rc.getDirtCarrying()==0 && rc.canDigDirt(dir)) {// Get dirt if empty
                        System.out.println("Stuck: Dug");
                        rc.digDirt(dir);
                    } else if (rc.getDirtCarrying()>=1 && rc.senseRobotAtLocation(rc.getLocation().add(clockdir))==null) { // Deposit dirt in front
                        System.out.println("Stuck: tryDeposit");
                        tryDeposit(clockdir);
                        if (rc.senseElevation(rc.getLocation().add(clockdir)) >= rc.senseElevation(rc.getLocation())-3) {
                            System.out.println("Stuck: unstuck!");
                            mode = 2;
                        }
                    }
                    break;
            }
        }
    }

    static void runLandscaper() throws GameActionException {
        Direction[] strait = {Direction.EAST, Direction.WEST, Direction.SOUTH, Direction.NORTH};
        MapLocation self = rc.getLocation();
        int dirtInv = rc.getDirtCarrying();
        int actionDecide = (int) (Math.random() * 2);
        int attemptedMoves = 0;
        boolean atHQ = false;

        // determine flood level based on round
        int rountCount = rc.getRoundNum();
        double calcPower = 0.0028 * rountCount - 1.38 * Math.sin(0.00157 * rountCount - 1.73) + 1.38 * Math.sin(-1.73);
        int floodLevel = (int) (Math.pow(Math.E, calcPower) - 1);
//        System.out.println("This flood level: " + floodLevel);

        // Getting wall coordinates
        MapLocation wallCoord[] = new MapLocation[8];
        MapLocation loc = findHQ();
        int x = loc.x;
        int y = loc.y;
        wallCoord[0] = new MapLocation(x - 1, y + 1);       // Northwest
        wallCoord[1] = new MapLocation(x - 1, y);              // West
        wallCoord[2] = new MapLocation(x - 1, y - 1);       // Southwest
        wallCoord[3] = new MapLocation(x, y + 1);              // North
        wallCoord[4] = new MapLocation(x, y - 1);              // South
        wallCoord[5] = new MapLocation(x + 1, y + 1);       // Northeast
        wallCoord[6] = new MapLocation(x + 1, y);              // East
        wallCoord[7] = new MapLocation(x + 1, y - 1);       // Southeast

//        for(int i = 0; i < wallCoord.length; i++) System.out.println(wallCoord[i].x + " " + wallCoord[i].y);

        isTrapped(self, floodLevel, rc.senseElevation(self));

        for (Direction dir : directions) {
            MapLocation selfAja = rc.adjacentLocation(dir);
            for(int i = 0; i < wallCoord.length; i++) {
                if(selfAja.x == wallCoord[i].x && selfAja.y == wallCoord[i].y) {
                    if(i == 0) tryDeposit(Direction.SOUTHEAST);
                    else if(i == 1) tryDeposit(Direction.EAST);
                    else if(i == 2) tryDeposit(Direction.NORTHEAST);
                    else if(i == 3) tryDeposit(Direction.SOUTH);
                    else if(i == 4) tryDeposit(Direction.NORTH);
                    else if(i == 5) tryDeposit(Direction.SOUTHWEST);
                    else if(i == 6) tryDeposit(Direction.WEST);
                    else if(i == 7) tryDeposit(Direction.NORTHWEST);
                    break;
                }
            }
        }

        if(dirtInv != 15) { //dig outside border
            if(self.isWithinDistanceSquared(loc, 3)) {
                int distance = self.distanceSquaredTo(loc);
                while(true) {
                    Direction dir = directions[(int) (Math.random() * 8)];
                    MapLocation selfMove = rc.adjacentLocation(dir);
                    int distance1 = selfMove.distanceSquaredTo(loc);
                    if(distance1 > distance) {
                        tryMove(dir);
                        return;
                    }
                }
            }
            if(actionDecide % 2 == 0) randMove();
            else tryDig(floodLevel, self);
            return;
        } else if(dirtInv >= 15) {
            atHQ = returnHQ();
            if(!atHQ) {
                randMove();
                return;
            }
        }
//        atHQ = returnHQ();
        if (atHQ) { //at HQ, so start depositing phase
//            System.out.println("\n\n\n" + wallCoord.length);
//            System.out.println(x + " " + y);
//            for(int i = 0; i < wallCoord.length; i++) {
////                if(wallCoord[i] == null) {
////                    System.out.println("it is null");
////                }
//                System.out.println("\n\n" + wallCoord[i].x + " " + wallCoord[i].y);
//            }
            for (Direction dir : directions) { //check adjacent squares for deposit
                MapLocation locBot = rc.adjacentLocation(dir);
                RobotInfo robotID = rc.senseRobotAtLocation(locBot);
                if(robotID == null) continue;
                if(robotID.type == RobotType.HQ) continue;
                for (int i = 0; i < wallCoord.length; i++) { //match wall coordinates with robot's adjacent squares
//                    System.out.println(wallCoord[i].x + " " + wallCoord[i].y);
//                    System.out.println(i);
                    if (locBot.x == wallCoord[i].x && locBot.y == wallCoord[i].y) {
//                        System.out.println("\n\n\n" + loc.x + " " + loc.y);
                        tryDeposit(dir);
                        return;
                    }
                }
            }
        }
//        else if (dirtInv != 25){
//            tryDig(floodLevel);
//            return;
//        }
//        // last scenario: all squares but the CENTER have been filled so try to move out and fill
//        for(Direction dir : strait) {
//            if(tryMove(dir)) didMove = true;
//
//        }
//        if(didMove = false) {
//            if(dirtInv == 0 && !randMove()) tryDig(floodLevel - 1);
////            randMove();
//        }
    }

    /*
    TODO:
        must deal with CENTER trap
     */
    static boolean isTrapped(MapLocation self, int floodHeight, int robotElevaton) throws GameActionException{
        for(Direction dir : directions) { // robot is not trapped
            if(rc.canMove(dir)) return false;
        }
        for(Direction dir : directions) {
            MapLocation adj = rc.adjacentLocation(dir);
            int adjElev = rc.senseElevation(adj);
            if(robotElevaton > adjElev && rc.canDigDirt(dir)) {
                rc.digDirt(dir);
                return true;
            }
            if(robotElevaton < adjElev) {
                tryDeposit(Direction.CENTER);
                return true;
            }
        }
        return false;
    }
    /*
    TODO:
        implement height adjustment -- too high all the time...
        implement a didDeposit boolean variable across robots
     */
    static boolean tryDeposit(Direction dir) throws GameActionException {
        if(rc.canDepositDirt(dir)) {
            rc.depositDirt(dir);
            return true;
        }
        return false;
    }

    static boolean tryDig(int floodHeight, MapLocation self) throws GameActionException {
        int currentElev = rc.senseElevation(self);
        for(int i = 0; i < directions.length; i++) {
            int randomDir = (int) (Math.random() * 8);
            Direction dir = directions[randomDir];
            if(rc.canDigDirt(dir)) {
                MapLocation adjRobot = rc.adjacentLocation(dir);
                int elevationLevel = rc.senseElevation(adjRobot);
                System.out.println("\nelevation level: " + elevationLevel);
                System.out.println("floodHeight level: " + floodHeight);
                if(elevationLevel > floodHeight && Math.abs(currentElev - elevationLevel) != 3) {
                    System.out.println("Dug");
                    rc.digDirt(dir);
                }
                return true;
            }
        }
        return false;
    }

    static void runDeliveryDrone() throws GameActionException {
        Team enemy = rc.getTeam().opponent();
        if (!rc.isCurrentlyHoldingUnit()) {
            // See if there are any enemy robots within striking range (distance 1 from lumberjack's radius)
            RobotInfo[] robots = rc.senseNearbyRobots(GameConstants.DELIVERY_DRONE_PICKUP_RADIUS_SQUARED, enemy);

            if (robots.length > 0) {
                // Pick up a first robot within range
                rc.pickUpUnit(robots[0].getID());
                System.out.println("I picked up " + robots[0].getID() + "!");
            }
        } else {
            // No close robots, so search for robots within sight radius
            tryMove(randomDirection());
        }
    }

    static void runNetGun() throws GameActionException {
        // Shoot down detected enemy units!
        Team enemy = rc.getTeam().opponent();
        RobotInfo[] enemies = rc.senseNearbyRobots(GameConstants.NET_GUN_SHOOT_RADIUS_SQUARED, enemy);
        for (RobotInfo each : enemies) {
            if (each.type==RobotType.DELIVERY_DRONE) {
                rc.shootUnit(each.ID);
                return;
            }
        }
    }

    static Direction randomDirection() {
        return directions[(int) (Math.random() * directions.length)];
    }

    static RobotType randomSpawnedByMiner() {
        return spawnedByMiner[(int) (Math.random() * spawnedByMiner.length)];
    }

    static boolean randMove() throws GameActionException {
        // Checks to make sure a move is possible, then moves randomly
        for (Direction dir : directions)
            if (rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
                System.out.println("Begin random move...");
                while (true) {
                    if (tryMove(randomDirection())) return true;
                }
            }
        return false;
    }

    static boolean tryMove(Direction dir) throws GameActionException {
        // System.out.println("I am trying to move " + dir + "; " + rc.isReady() + " " + rc.getCooldownTurns() + " " + rc.canMove(dir));
        if (rc.isReady() && rc.canMove(dir) && !rc.senseFlooding(rc.adjacentLocation(dir))) {
            rc.move(dir);
            return true;
        } else return false;
    }

    static boolean tryBuild(RobotType type, Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canBuildRobot(type, dir)) {
            rc.buildRobot(type, dir);
            return true;
        } else return false;
    }

    static boolean tryMine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canMineSoup(dir)) {
            rc.mineSoup(dir);
            return true;
        } else return false;
    }

    static boolean tryRefine(Direction dir) throws GameActionException {
        if (rc.isReady() && rc.canDepositSoup(dir)) {
            rc.depositSoup(dir, rc.getSoupCarrying());
            return true;
        } else return false;
    }

    static boolean go(MapLocation loc) throws GameActionException {
        System.out.println("Going towards " + loc.x + " " + loc.y);
        // A helper that navigates unit towards desired location
        if (rc.getLocation().isAdjacentTo(loc))
            return true;
        tryMove(rc.getLocation().directionTo(loc));
        // If blocked
        if (rc.isReady())
            randMove();

        // Not there yet
        return false;
    }

    static MapLocation closest(MapLocation[] locs) {
        // A helper that returns the closet location in given set, returns (0,0) if empty
        MapLocation result = new MapLocation(0,0);
        int dist = 0;
        for (MapLocation loc : locs) {
            if (dist == 0 || rc.getLocation().distanceSquaredTo(loc) < dist) {
                result = loc;
                dist = rc.getLocation().distanceSquaredTo(loc);
            }
        }
        return result;
    }

    static MapLocation findHQ() throws GameActionException {
        // Finds the HQ coord, returns (0,0) otherwise
        int[][] hqmsg = log.search(new int[]{pw,0,0,0,0});
        if (hqmsg.length!=0) {
            int x = hqmsg[0][5];
            int y = hqmsg[0][6];
            return new MapLocation(x,y);
        }
        return new MapLocation(0, 0);
    }

    static boolean returnHQ() throws GameActionException {
        // Get hq maplocations
        MapLocation hqloc = findHQ();
        return go(hqloc);
    }

//    static void getWallCoord(ArrayList<MapLocation> wall) throws GameActionException {
//        int i = 0;
////        MapLocation loc = findHQ();
////        MapLocation borders[] = new MapLocation[directions.length];
//        for(Direction dir : directions) {
////            MapLocation loc1 =
//            wall.add(new MapLocation(1, 2));
////            wall.add(rc.adjacentLocation(dir));
////            wall[i] = rc.adjacentLocation(dir);
////            System.out.println(wall[i].x + " " + wall[i].y);
//            i++;
//        }
//    }
}
