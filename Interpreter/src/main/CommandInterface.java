package main;

import group.tcpclient.MyClient;

import java.util.ArrayList;
import java.util.Arrays;

/**
 * This class obfuscates the process of sending and validating commands to the robbie app
 * using the MyClient class. This class also handles some of the environment information locally
 * to reduce the number of commands sent to the robbie environment host.
 *<p>
 * Valid commands and responses are specified in the RobbieCommands document
 * @author Jared Schimpf
 */

public class CommandInterface implements MyClient.ClientCallback {

    //tracking current coordinates locally

    /**
     * Private record class that represents x and y coordinates on a grid
     * @param x coordinate
     * @param y coordinate
     */
    private record Coord(
        int x, int y
    ){}

    private int MSG_TIMEOUT = 5000;
    private int CONNECT_TIMEOUT = 0;


    private Coord gridSize;
    private Coord currLoc;
    private String currDir;
    private Integer currGems; //needs to be Integer object to avoid default value

    public MyClient mMyClient;

    /**
     * Constructor
     * <p>
     * Establishes the connection to the robbie app host listening at the given address and port.
     * ShowMsg enables or disables the messaging feature of the robbie app once a connection is established.
     * @param address the address to attempt the connection at
     * @param port the port the address should be listening on
     * @param showMsg boolean that toggles the detailed messaging display on the robbie app
     * @throws Exception if there is address or port values are null or if mMyClient fails to establish a connection.
     */
    public CommandInterface(String address, int port, boolean showMsg) throws Exception {
        mMyClient = new MyClient(this, address, port);
        try {
            setShowMsg(showMsg);
        }
        catch (Exception e){
            stop();
            throw e;
        }

    }

    /**
     * Override of the constructor that also attempts to initialize the grid during object construction
     * @param address the address to attempt the connection at
     * @param port the port the address should be listening on
     * @param showMsg boolean value that toggles the detailed messaging display on the robbie app
     * @param grid the name of the grid file for the application to load,
     *            which must be in the same working directory as the robbie app on the host machine
     * @throws Exception if there is address or port values are null, if mMyClient fails to establish a connection, or if grid initialization fails.
     */
    public CommandInterface(String address, int port, boolean showMsg, String grid) throws Exception {
        this(address, port, showMsg);
        try {
            initGrid(grid);
        }
        catch (Exception e){
            stop();
            throw e;
        }
    }

//------------------------------------------------------------------------------------------------------------------------------

    /*
    The following methods are wrappers for the MyClient sendMessageGetResponse method.
    These handle errors and interpret response messages
    */

    /**
     *Internal method: Sends out a message, then returns the response. Throws an exception if the response was null, or a CMDFAIL or CMDERR response.
     * @param msg The message to send
     * @return The received response message
     * @throws Exception if no response is sent within the timeout window
     */
    private String sendRespond(String msg) throws Exception {
        String response =  mMyClient.sendMessageGetResponse(msg,CONNECT_TIMEOUT, MSG_TIMEOUT);
        if(response == null) throw new Exception("No Response: timed out");
        if(response.equals("CMDFAIL") || response.equals("CMDERR"))throw new Exception(String.format("Bad Response: \"%s\"", response));
        return response;
    }

    /**
     * Internal method: Sends out a message and checks if the returned message matches the expected string, Throws an exception if it does not match.
     * @param msg The message to send
     * @param expected The expected response message
     * @throws Exception if the received message doesn't match the expected response.
     */
    private void sendRequireResponse(String msg, String expected) throws Exception {
        String response =  sendRespond(msg);
        if(!response.equals(expected))throw new Exception(String.format("Bad Response: expected \"%s\" but received \"%s\"", expected, response));

    }


    /**
     * Internal method: Sends out a message and checks if the start of the returned message matches the expected string, then returns the response message
     * @param msg The message to send
     * @param expected The text that the response should start with
     * @return the received response message
     * @throws Exception if the received message doesn't start with the expected response
     */
    private String sendRequireStartsWith(String msg, String expected) throws Exception {
        String response = sendRespond(msg);
        if(!response.startsWith(expected))throw new Exception(String.format("Bad Response: expected to contain\"%s\" but received \"%s\"", expected, response));
        return response;
    }

//------------------------------------------------------------------------------------------------------------------------------------------


    /**
     *Sends a message to the host to toggle the socket messages display on or off.
     * @param b boolean representing enabling or disabling messaging: True= messaging on, False= messaging off
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void setShowMsg(boolean b) throws Exception {
        if(b) sendRequireResponse("SHOWMSGS ON","ACK SHOWMSGS ON");
        else  sendRequireResponse("SHOWMSGS OFF", "ACK SHOWMSGS OFF");
    }

    /**
     * Attempts to establish a connection to the host, returns True or False depending on success.
     * <p>
     * (this method is unused because the MyClient class already automatically attempts to establish
     * a connection when a message is sent for the first time)
     * @return boolean indicating the success of the connection attempt
     */
    public boolean Connect(){
        return mMyClient.makeConnection(true,CONNECT_TIMEOUT);
    }

    /**
     * Sends a message to the host to end program execution.
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void stop() throws Exception {
        sendRequireResponse("STOP", "ACK STOP");
    }

    /**
     * Sends a message to the host to load the grid with the given file name
     * @param filename the name of the grid file to load
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void initGrid(String filename) throws Exception {
        String msg = "LOADGRID "+filename;
        String expected = "ACK " + msg;
        sendRequireResponse(msg, expected);
        gridSize = getSize();

    }

    /**
     * Internal method: sends a message that queries the host for the dimensions of the currently loaded grid
     * @return Coord record that holds the current x and y dimensions of the grid
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private Coord getSize() throws Exception{
        String response = sendRequireStartsWith("GETSIZE","GRIDSIZE");
        String[] split = response.split(" ");
        return new Coord(Integer.valueOf(split[1]), Integer.valueOf(split[2]));
    }

    /**
     * Internal method: sends a message that queries the host for the number of gems robbie has currently collected.
     * This value is also tracked and updated locally. This method queries the host for the value only if there is no local record.
     * This is done to reduce the number of queries to the host as they can slow execution quite a bit.
     * @return The current collected gem count
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private int getGems() throws Exception {
        if(currGems == null) {
            String response = sendRequireStartsWith("GETJEWLCNT", "ROBHAS");
            currGems = Integer.valueOf(response.split(" ")[1]);
        }
        return currGems;
    }

    /**
     * Sends a message to the host to overwrite robbie's current collected gem count with the value of the passed in integer.
     * This is typically done to initialize robbie with a certain number of gems.
     * @param gems The value to set the collected gem count to.
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void initGems (int gems) throws Exception {
        String msg = "GIVEROB " + gems;
        String expected = "ACK " + msg;
        sendRequireResponse(msg, expected);
        currGems = gems;
    }

    /**
     * Internal method: sends a message to the host to increase robbie's current collected gem count by the passed in integer.
     * Negative values will decrease this count.
     * @param gems Value to modify the gem count with
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private void addGems(int gems) throws Exception {
        int oldGems = getGems();
        initGems(oldGems+gems);
        currGems = oldGems+gems;
    }

    /**
     * Sends a message to the host that queries the current direction that robbie is facing.
     * Expected responses form the host are: "UP", "DOWN", "LEFT", "RIGHT"
     * <p>
     *     This information is also tracked locally by the program, a query is only sent if the local record is blank
     *     otherwise, this returns the local value.
     * @return A string representing the direction robbie is facing, either: "UP", "DOWN", "LEFT", or "RIGHT"
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public String getDir() throws Exception {
        if(currDir == null) {
            String response = sendRequireStartsWith("GETDIR", "ROBISFACING");
            currDir = response.split(" ")[1];
        }
        return currDir;
    }

    /**
     * Internal method: sends a message to the host to change the direction robbie is facing.
     * Expected inputs are: "UP", "DOWN", "LEFT", "RIGHT"
     * @param dir A string representing the direction to set robbie to face, <p>
     *                either: "UP", "DOWN", "LEFT", or "RIGHT"
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private void faceDir(String dir) throws Exception{
        switch (dir){
            case "UP":
                sendRequireResponse("FACE UP", "ACK FACE UP");
                break;
            case "DOWN":
                sendRequireResponse("FACE DOWN", "ACK FACE DOWN");
                break;
            case "LEFT":
                sendRequireResponse("FACE LEFT", "ACK FACE LEFT");
                break;
            case "RIGHT":
                sendRequireResponse("FACE RIGHT", "ACK FACE RIGHT");
                break;
        }
        currDir = dir;
    }

    /**
     * Sends a message to the host that sets the direction robbie is facing to the right of its current direction
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void turnRight() throws Exception {
        switch(getDir()){
            case "UP":
                faceDir("RIGHT");
                break;
            case "RIGHT":
                faceDir("DOWN");
                break;
            case "DOWN":
                faceDir("LEFT");
                break;
            case "LEFT":
                faceDir("UP");
                break;
        }
    }

    /**
     * Sends a message to the host that sets the direction robbie is facing to the left of its current direction
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void turnLeft() throws Exception {
        switch(getDir()){
            case "UP":
                faceDir("LEFT");
                break;
            case "LEFT":
                faceDir("DOWN");
                break;
            case "DOWN":
                faceDir("RIGHT");
                break;
            case "RIGHT":
                faceDir("UP");
                break;
        }
    }

    /**
     * Internal method: sends a message to the host that queries for robbie's current x and y coordinates on the grid.
     * @return A Coord record that contains robbie's x and y grid coordinates
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private Coord getLoc() throws Exception{
        if(currLoc == null) {
            String response = sendRequireStartsWith("GETLOC", "ROBISAT");
            String[] split = response.split(" ");
            currLoc = new Coord(Integer.parseInt(split[1]), Integer.parseInt(split[2]));
        }
        return currLoc;
    }

    /**
     * Sends a message to the host to move robbie forward 1 space in the direction it's currently facing.
     * This is done by incrementing/decrementing robbie's current coordinate location by 1 based on his direction.
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    public void step() throws Exception {
        Coord loc = getLoc();
        switch (getDir()){
            case "UP":
                if(loc.y > 0){
                    goTo(new Coord(loc.x, loc.y-1));
                }
                break;
            case "DOWN":
                if(loc.y < gridSize.y-1){
                    goTo(new Coord(loc.x, loc.y+1));
                }
                break;
            case "LEFT":
                if(loc.x > 0){
                    goTo(new Coord(loc.x-1, loc.y));
                }
                break;
            case "RIGHT":
                if(loc.x < gridSize.x-1){
                    goTo(new Coord(loc.x+1, loc.y));
                }
                break;
        }
    }

    /**
     * Internal method: sends a message to the host to change robbie's position on the grid to the passed in coordinates
     * @param loc the x and y coordinates to move robbie
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private void goTo(Coord loc) throws Exception {
        if(getGrid(loc) != "X") {
            String cmd = String.format("GOTO %d %d", loc.x, loc.y);
            String expected = String.format("ACK GOTO %d %d", loc.x, loc.y);
            sendRequireResponse(cmd, expected);
            //since sendRequireResponse completed without exception we can accurately update robbie's new position
            currLoc = loc;
        }
    }

    /**
     * Internal method: sends a message to the host that queries the content of the grid at the cell of the passed in coordinate
     * @param pos the coordinates of the cell to check
     * @return the content of the grid at the cell, expected:
     * <p>
     *     "X": represents a wall
     * <p>
     *     "0".."9": represents a gem count, 0 being an empty cell
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private String getGrid(Coord pos) throws Exception{
        String msg = String.format("GETGRID %d %d", pos.x, pos.y);
        String matches = String.format("GRID %d %d", pos.x, pos.y);
        String response = sendRequireStartsWith(msg, matches);
        return response.split(" ")[3];
    }

    /**
     * Internal Method: sends a message to the host to modify the grid cell at 'pos' to the passed in value 'val'
     * @param pos the coordinates of the cell to modify
     * @param val the value to set the cell
     * @throws Exception if there's an unexpected response or the host fails to respond
     */
    private void setGrid(Coord pos, String val) throws Exception{
        String msg = String.format("SETGRID %d %d %s", pos.x, pos.y, val);
        String expected = "ACK "+ msg;
        sendRequireResponse(msg, expected);
    }

    /**
     * Handles the 'take' robbie instruction by 'taking' the gems in the cell adjacent to robbie in the direction it's facing
     * via a series of commands to the host which are handled by the takeAtCoord helper method.
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    public void take() throws Exception {
        Coord loc = getLoc();
        switch (getDir()){
            case "UP":
                takeAtCoord(new Coord(loc.x, loc.y+1));
                break;

            case "DOWN":
                takeAtCoord(new Coord(loc.x, loc.y-1));
                break;
            case "LEFT":
                takeAtCoord(new Coord(loc.x-1, loc.y));
                break;
            case "RIGHT":
                takeAtCoord(new Coord(loc.x+1, loc.y));
                break;
        }
    }

    /**
     * Internal method: Sends messages to the host to set the gem count to zero at the cell located at the passed in coordinate 'loc'
     * and increase robbie's gem count by the value that was at the cell.
     * @param loc
     * @throws Exception
     */
    private void takeAtCoord(Coord loc) throws Exception {
        String look = (getGrid(loc));
        if(look.matches("\\d")){
            setGrid(loc,"0");
            addGems(Integer.valueOf(look));
        }
    }

    /**
     * Handles the 'drop' robbie instruction by 'dropping' a single gem in the cell adjacent to robbie in the direction it's facing
     * via a series of commands to the host which are handled by the DropAtCoord helper method.
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    public void drop() throws Exception {
        Coord loc = getLoc();

        if (currGems > 0) switch (getDir()){
            case "UP":
                dropAtCoord(new Coord(loc.x, loc.y+1));
                break;
            case "DOWN":
                dropAtCoord(new Coord(loc.x, loc.y-1));
                break;
            case "LEFT":
                dropAtCoord(new Coord(loc.x-1, loc.y));
                break;
            case "RIGHT":
                dropAtCoord(new Coord(loc.x+1, loc.y));
                break;
        }
    }

    /**
     * Internal method: sends command messages to the host that query for the contents at the passed in coordinate 'loc',
     * then increases the gem count at that cell by 1 if it's a valid location (not a wall cell or a cell with 9 gems).
     * Robbie's total gem count is then decremented by 1.
     * @param loc the location to drop a gem
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    private void dropAtCoord(Coord loc) throws Exception {
        String look = (getGrid(loc));
        switch (look){
            case "X", "9": //cannot place gem on wall, and cannot stack more than 9 gems in one tile
                return;
            case "0", ".":
                setGrid(loc, "1");
                addGems(-1);
                return;
            default:
                int amount = Integer.valueOf(look)+1;
                setGrid(loc,String.valueOf(amount));
                addGems(-1);
        }
    }

    /**
     * Checks if robbie's direction matches the passed in direction.
     * <p>
     * Valid directions: "UP", "DOWN", "LEFT", or "RIGHT"
     * @param dir the expected direction
     * @return boolean value representing whether robbie is facing the passed in direction.
     */
    public boolean checkFacing(String dir){
        return currDir.equals(dir);
    }

    /**
     * Checks if robbie's gem count is greater than 0
     * @return boolean value representing whether robbie's gem count is greater than 0
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    public boolean hasGem() throws Exception {
        return (getGems() > 0);
    }

    /**
     * Checks if there's a gem adjacent to robbie in the direction its facing
     * @return boolean value representing whether robbie sees a gem
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    public boolean seeGem() throws Exception {
        Coord loc = getLoc();
        String look = null;
        switch (getDir()){
            case "UP":
                look = getGrid(new Coord(loc.x, loc.y+1));
                break;
            case "DOWN":
                look = getGrid(new Coord(loc.x, loc.y-1));
                break;
            case "LEFT":
                look = getGrid(new Coord(loc.x-1, loc.y));
                break;
            case "RIGHT":
                look = getGrid(new Coord(loc.x+1, loc.y));
                break;
        }
        return look.matches("\\d") && !look.equals("0");
    }

    /**
     * Converts a direction relative to the direction robbie is facing:
     * <p>
     * ("FRONT", "RIGHT", "BACK", "LEFT")
     * <p> to a grid absolute direction:
     * <p>
     * ("UP", "RIGHT", "DOWN", "LEFT")
     * <p>
     * For example, if robbie is facing left, his front would = left, back = right, left = down, and right =  up
     * @param relDir the direction relative to robbie's direction: ("FRONT", "RIGHT", "BACK", "LEFT")
     * @return the absolute direction of the relative direction: ("UP", "RIGHT", "DOWN", "LEFT")
     * @throws Exception if there's an unexpected response or the host fails to respond.
     * @throws IllegalArgumentException if the passed in relative direction is invalid, or if querying robbie's direction returns an unexpected response.
     */
    private String getAbsDir (String relDir) throws Exception{
        String facing = getDir();
        ArrayList dirs = new ArrayList(Arrays.asList("UP", "RIGHT", "DOWN", "LEFT"));
        int i;

        switch(facing){
            case "UP":
                i = 0;
                break;
            case "RIGHT":
                i = 1;
                break;
            case "DOWN":
                i = 2;
                break;
            case "LEFT":
                i = 3;
                break;
            default: throw new IllegalArgumentException(String.format("unrecognized direction: facing = \"%s\"", facing));
        }

        switch (relDir){
            case "FRONT": return dirs.get(i).toString();
            case "RIGHT": return dirs.get( (i+1)%3 ).toString();
            case "BACK": return dirs.get( (i+2)%3 ).toString();
            case "LEFT": return dirs.get( (i+3)%3 ).toString();
        }
        throw new IllegalArgumentException(String.format("unrecognized direction: relative direction = \"%s\"", relDir));
    }

    /**
     * Checks whether there is a wall adjacent to robbie in the specified relative direction
     * @param relDir the direction relative to robbie's direction to check
     * @return a boolean value indicating if the checked cell is clear of walls
     * @throws Exception if there's an unexpected response or the host fails to respond.
     */
    public boolean checkClear(String relDir) throws Exception {
        Coord loc = getLoc();
        String look = null;

        switch (getAbsDir(relDir)){
            case "UP":
                look = getGrid(new Coord(loc.x, loc.y+1));
                break;
            case "DOWN":
                look = getGrid(new Coord(loc.x, loc.y-1));
                break;
            case "LEFT":
                look = getGrid(new Coord(loc.x-1, loc.y));
                break;
            case "RIGHT":
                look = getGrid(new Coord(loc.x+1, loc.y));
                break;
        }
        return !look.equals("X");
    }

    /**
     * Override of MyClient's client status that specifies the format of the messages.
     * @param msg status message
     */
    @Override
    public void clientStatus(String msg) {
        System.out.println("STATUS: "+ msg);
    }
}
