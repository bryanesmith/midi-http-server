/*
 * Main.java
 *
 * Created on January 26, 2008, 6:07 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package midihttpserver;

import java.io.IOException;

/**
 * <p>Starts a midi server and handles usage.</p>
 * @author Bryan E. Smith <bryanesmith at gmail dot com>
 */
public class Main {
    
    /**
     * <p>Start a MIDI Server listing on a port.</p>
     * @param args the command line arguments
     */
    public static void main(String[] args) {
        
        // Initialize parameter vars with default values
        String mode = "mono";
        int port = 1500;
        boolean isDebug = false;
        
        // Parse command-line args
        String nextArg;
        
        try {
            for (int i=0; i<args.length; i++) {
                nextArg = args[i];
                
                // Port
                if (nextArg.equals("-p") || nextArg.equals("--port")) {
                    port = Integer.parseInt(args[i+1]);
                    // Skip next arg, just read
                    i++;
                }
                
                // Mode
                else if (nextArg.equals("-m") || nextArg.equals("--mode")) {
                    mode = args[i+1];
                    // Skip next arg, just read
                    i++;
                }
                
                // Debug
                else if (nextArg.equals("-d") || nextArg.equals("--debug")) {
                    isDebug = true;
                }
                
                // Help/usage
                else if (nextArg.equals("-h") || nextArg.equals("--help")) {
                    printUsage();
                    System.exit(0);
                }
                
                // Uh-oh
                else {
                    System.err.println("Unrecognized argument at position "+i+": "+nextArg);
                    printUsage();
                    System.exit(1);
                }
            }
        } catch (Exception ex) {
            System.err.println("Problem parsing arguments: "+ex.getMessage());
            printUsage();
            System.exit(1);
        }
        
        // Start the server
        MIDIServer server = new MIDIServer(mode,port);
        try {
            server.setDebug(isDebug);
            server.start();
        } catch (IOException e) {
            System.err.println("Cannot start server: "+e.getMessage());
            e.printStackTrace(System.err);
            System.exit(1);
        }
    }
    
    /**
     * <p>Print out command-line usage.</p>
     */
    private static void printUsage() {
        System.out.println("");
        System.out.println("Usage: java -jar dist/MIDIHTTPServer.jar [options]");
        System.out.println("");
        System.out.println("Options:");
        System.out.println("  -p, --port: Specify the port. Default is 1500.");
        System.out.println("  -m, --mode: Mode for server. Default is mono. (Currently, only mono supported.)");
        System.out.println("              mono: Queues all requests and services one at a time.");
        System.out.println("  -d, --debug: Prints out tracers to stdout.");
        System.out.println("  -h, --help: Print this message.");
        System.out.println("");
    }
    
}
