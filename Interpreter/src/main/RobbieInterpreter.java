package main;

import java.io.RandomAccessFile;
import group.tcpclient.MyClient;

/**
 * This is the class that handles execution of the interpreter program.
 * It contains functionality for initializing the program with command line arguments
 * and handling execution.
 * <p>
 * The first argument to the program must always be the location of the instruction file to run
 * Aside from the first argument, program can take up to 4 arguments in any order, and has default values for each if none are provided:
 * <p> - "--msg": toggles the socket messaging display on for the RobbieApp. Off by default.
 * <p> - "--port=&lt;port&gt;": sets the port number of the socket to &lt;port&gt;. 1024 by default, which is also the default of the RobbieApp.
 * <p> - "--address=&lt;address&gt;": sets the address of the socket to &lt;address&gt;. By default this is the local machine's external IP address,
 *       which is also the default of the RobbieApp if it is ran on the same machine.
 * <p> - "--grid=&lt;filename&gt;": initializes the grid to use during execution to &lt;filename&gt;
 *       this is optional by default because the grid can be initialized within the robbie script.
 *       However, if a grid is not initialized before commands are ran, the program will error out.
 */
public class RobbieInterpreter {


    public static void main(String[] args) throws Exception {
        String fileName = args[0];
        RandomAccessFile file = new RandomAccessFile(fileName, "r");


        String address = MyClient.getMyAddress(false);
        boolean showMsg = false;
        int port = 1024;
        String grid = null;

        for(String arg: args){
            if(arg.equals("--msg")) showMsg = true;
            if(arg.startsWith("--port=")){
               port = Integer.valueOf(arg.substring(7));
            }
            if(arg.startsWith("--address=")){
                address = (arg.substring(10));
            }
            if(arg.startsWith("--grid=")){
                grid = (arg.substring(7));
            }
        }
        CommandInterface commandInterface;
        if(grid == null) {
            commandInterface = new CommandInterface(address, port, showMsg);
        }
        else {
            commandInterface = new CommandInterface(address, port, showMsg, grid);
        }

        Parser parser = new Parser(file, commandInterface);
        try {
            parser.start();
        }
        catch (Exception e){
            commandInterface.stop();
            throw e;
        }
    }
}