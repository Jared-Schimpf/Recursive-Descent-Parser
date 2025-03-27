package group.tcpclient;

import java.net.*;
import java.io.*;

/***
 * This class provides a functionality to interact with an echo server.
 *  
 * @author Paul
 */
public class MyClient {
    /***
     * The user of this class must implement this callback interface.
     */
    public interface ClientCallback {
        /***
         * Callback for the current status of the client
         * @param msg status message
         */
        public void clientStatus(String msg);
    }
    
    private ClientCallback mCallback = null ;
    private Socket mSocket = null;
    private String mServerName ;
    private int mPort ;
    private PrintWriter mOut ;
    private BufferedReader mIn ;
 
    /***
     * Constructor
     * 
     * @param callback Implementer of the interface. Usually "this"
     * @param serverName Network name of the server. Could be an IP address.
     * @param port Port used by the server.
     * @throws java.lang.Exception if the constructor parameters are bad
     */
    public MyClient(ClientCallback callback, String serverName, int port) throws Exception {
        if (callback==null)
            throw new Exception("Callback cannot be null");
        if (serverName==null)
            throw new Exception("Server name cannot be null");
        
        mCallback = callback ;
        mServerName = serverName ;
        mPort = port ;
    }
   
    /***
     * Attempts to connect to the server
     * @param keepTrying If True it will keep trying until success or timeout.
     *        Otherwise it tries only once.
     * @param timeoutMillis If retrying, this specifies a timeout. If less than
     *        or equal to 0 then it retries indefinitely. Indefinite retries 
     *        will NOT work for the loopback address 127.0.0.1, which will 
     *        eventually (after many attempts)report success without a server.
     *        The input stream will be connected to the output stream. So if you 
     *        want indefinite retries, and the server is known to be on this 
     *        same machine, then use this host address instead, which you
     *        can obtain from getMyAddress(false)).
     * @return  whether the connection was successful.
     */
    public boolean makeConnection(boolean keepTrying, int timeoutMillis){
        mCallback.clientStatus("Connecting");
        long maxTime = System.currentTimeMillis() + timeoutMillis ;

        int attempts = 0 ;
        boolean success = false ;
        boolean doneTrying = false ;
        while (!doneTrying) {
            try {
                attempts++;
                mSocket = new Socket(mServerName, mPort) ;  
                mOut = new PrintWriter(mSocket.getOutputStream(),true);
                mIn = new BufferedReader(
                        new InputStreamReader(mSocket.getInputStream()));
                doneTrying = true ;
                success = true ;
            }
            catch (IOException e) {
                if (timeoutMillis>0 && System.currentTimeMillis()>maxTime) {
                    mCallback.clientStatus(e.getMessage());
                    System.out.println("Error: " + e.getMessage());
                    doneTrying = true ;
                    success = false ;
                }
            }
            catch (Exception e) {
                mCallback.clientStatus(e.getMessage());
                System.out.println("Unexpected Error: " + e.getMessage());
                doneTrying = true ;
                success = false ;
            }
        }
        if (!success) {
            mCallback.clientStatus("Connection Failed on attempt: "+attempts);
            return false ;
        }
        mCallback.clientStatus("Connection Successful on attempt: "+attempts);
        return true ;
    }

    /***
     * Send a message to the server.
     * 
     * @param msg The message to be sent. If the server has not yet been 
     * connected it attempts to make one with an optional timeout.
     * @param timeoutMillis Applies only to making a connection. If a 
     *        connection exists this is ignored. If less than 0 only 1 attempt
     *        is made. If equal 0, the connection will wait indefinitely. 
     *        If greater than 0 it is the number of milliseconds to wait for a 
     *        connection.
     * @return False if a timed connection attempt fails. Otherwise true.
     */
    public boolean sendMessage(String msg, int timeoutMillis) {
        if (mSocket==null) {
            boolean keepTrying = (timeoutMillis>=0) ;
            boolean success = makeConnection(keepTrying, timeoutMillis);
            if (!success)
                return false;
        }
        mCallback.clientStatus("Sending "+msg);
        mOut.println(msg);
        return true;
    }
    
    /***
     * Sends a message to the server and waits for a response, 
     * with an optional timeout. If the server has not yet been connected
     * it will attempt to do so, with an optional timeout. See the
     * description of makeConnection().
     * 
     * @param msg The message to be sent.
     * @param connectTimeout Applies only to making a connection. If a 
     *        connection exists this is ignored. If less than 0 only 1 attempt 
     *        is made. If equal 0, the connection will wait indefinitely. 
     *        If greater than 0 it is the number of milliseconds to wait for a 
     *        connection.
     * @param messageTimeout Timeout in msec for response wait. If less than
     *          or equal to 0 it will wait indefinitely.
     * @return The response message or null if an attempted connection or 
     *         wait for a response times out.
     */
    public String sendMessageGetResponse(String msg, 
                        int connectTimeout, int messageTimeout) {
        String response ;
        boolean success = sendMessage(msg, connectTimeout) ;
        if (!success) return null ;
        
        // Get a response
        response = waitForMessage(messageTimeout);
        return response;
    }
    
    /***
     * Waits for a message from the server, with optional timeout.
     * If a specified timeout expires, it returns null, otherwise
     * it returns the message.
     * 
     * @param timeoutMillis Timeout time in milliseconds. If less than or equal
     *        to 0 then wait indefinitely.
     * @return The received message. Null if the wait times out or an
     *         IOException occurs.
     */
    public String waitForMessage(int timeoutMillis) {
        mCallback.clientStatus("Waiting for message");
        String response = null ;
        if (timeoutMillis<=0)
            try {
                response = mIn.readLine();
        } catch (IOException e) {
            mCallback.clientStatus(e.getMessage());
            System.out.println("Error: " + e.getMessage());
        }
        else {
            long maxTime = System.currentTimeMillis() + timeoutMillis ;
            boolean timedOut = true ;
            while (System.currentTimeMillis()<maxTime) {
                try {
                    if (mIn.ready()) {
                        response = mIn.readLine();
                        timedOut = false ;
                        break ;
                    }
                } catch (IOException e) {
                    mCallback.clientStatus(e.getMessage());
                    System.out.println("Error: " + e.getMessage());
                }
            }
            if (timedOut)
                mCallback.clientStatus("Timed out waiting for message");
        }
        return response ;
    }

    /***
     * Closes the connection. This should be called before exiting the
     * program. 
     */
    public void closeConnection() {
        try {
            if (mSocket != null)
                mSocket.close();
        } catch (IOException e) {
            mCallback.clientStatus(e.getMessage());
            System.out.println("Error: " + e.getMessage());
        }
    }   
    
    /***
     * Returns one of two IP addresses for this host. 
     * @param loopback If false, returns this host IP address. Otherwise it
     *        returns the reported loopback address. This is one of the
     *        127.*.*.* addresses, almost always 127.0.0.1, but not guaranteed
     *        to be so.
     * @return A string representation of the IP address
     */
    public static String getMyAddress(boolean loopback) {
        InetAddress addr = null ;
        if (loopback) {
            addr = InetAddress.getLoopbackAddress() ;        
        }
        else {
            try {
                addr = InetAddress.getLocalHost() ;
            } catch (UnknownHostException e) {
                System.out.println("Error: " + e.getMessage());                
            } 
        }
        if (addr==null)
            return "127.0.0.1";
        
        return addr.getHostAddress() ;
    }
}
