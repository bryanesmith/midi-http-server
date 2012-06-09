package midihttpserver;
import java.io.BufferedReader;
import java.io.IOException;
import java.io.InputStreamReader;
import java.io.OutputStreamWriter;
import java.io.PrintWriter;
import java.net.ServerSocket;
import java.net.Socket;
import java.util.concurrent.ArrayBlockingQueue;
import java.util.regex.Matcher;
import java.util.regex.Pattern;
import javax.sound.midi.MidiChannel;
import javax.sound.midi.MidiSystem;
import javax.sound.midi.Synthesizer;
/*
 * MIDIServer.java
 *
 * Created on January 26, 2008, 6:11 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

/**
 * <p>A frivolous, RESTFUL server that plays MIDI tones in response to HTTP requests.</p>
 * <p>A lot of this code recycles logic from Learning Java, Second Edition, O'Reilly, pp. 340-341</p>
 * @author Bryan E. Smith <bryanesmith at gmail dot com>
 */
public class MIDIServer {
    
    private String mode;
    private int port;
    private boolean isDebug = false;
    private boolean isStopped = false;
    private ServerSocket serverSocket;
    private MIDIRequestProcessorThread requestProcessor;
    
    /** Creates a new instance of MIDIServer */
    public MIDIServer(String mode, int port) {
        this.mode = mode;
        this.port = port;
        
        // Build the request processor
        requestProcessor = new MIDIRequestProcessorThread(MIDIServer.this);
    }
    
    /**
     * Turn on debugging statements for the tool. Useful if not behaving as expected, verbose.
     */
    public void setDebug(boolean isDebug) {
        this.isDebug = isDebug;
    }
    
    /**
     * Start the MIDI server.
     * @throws IOException Problems binding server.
     */
    public void start() throws IOException {
        
        // Fire off the request processor
        requestProcessor.start();
        
        serverSocket = new ServerSocket(port);
        System.out.println("");
        System.out.println("Started server on port "+port+"...");
        System.out.println("");
        System.out.println("To play a note, point your browser to:");
        System.out.println("  http://127.0.0.1:"+port+"/?tone=[tone]&duration=[duration]");
        System.out.println("");
        System.out.println("  - tone: C, Db, D, Ds, Eb, E, etc.");
        System.out.println("  - duration: time in milliseconds, e.g. 500 for half second.");
        System.out.println("");
        printTracer("Server listening for HTTP requests at http://localhost:"+port);
        
        try {
            while (!isStopped) {
                try {
                    // Process connection in separate worker thread
                    new MIDIServerWorkerThread(serverSocket.accept()).start();
                } catch (Exception ex) {
                    System.err.println("Problem with client request: "+ex.getMessage());
                    ex.printStackTrace(System.err);
                }
            }
            
        } finally {
            System.out.println("Server shutting down...");
            serverSocket.close();
        }
    }
    
    /**
     * Request the server to stop after processing next request.
     */
    public void stop() {
        isStopped = true;
    }
    
    /**
     * Handles the client's request, and returns proper HTTP codes.
     */
    private class MIDIServerWorkerThread extends Thread {
        Socket clientSocket;
        private MIDIServerWorkerThread(Socket clientSocket) {
            this.clientSocket = clientSocket;
            setDaemon(true);
            setPriority(Thread.MIN_PRIORITY);
        }
        
        /**
         *
         */
        public void run() {
            final long start = System.currentTimeMillis();
            printTracer("Connected to "+clientSocket.getInetAddress());
            
            PrintWriter out = null;
            
            // Used to very temporarily hold the HTTP response
            String response = null;
            
            try {
                // Buffer input stream
                BufferedReader in = new BufferedReader(new InputStreamReader(clientSocket.getInputStream(),"8859_1"));
                
                // Easy print writer to client for response
                out = new PrintWriter(new OutputStreamWriter(clientSocket.getOutputStream(),"8859_1"));
                
                // Get client request
                String clientRequest = in.readLine().trim();
                printTracer("Client request from "+clientSocket.getInetAddress()+": "+clientRequest);
                
                // Ignore FAVICON requests
                if (clientRequest.contains("favicon.ico")) {
                    response = "404 Not Found: no favicons for service";
                    return;
                }
                
                // Parse out the query string. Throw away everything else.
                Matcher matcher = Pattern.compile("^GET .*?\\?(\\S*).*").matcher(clientRequest);
                String queryString = null;
                
                try {
                    if (matcher.matches()) {
                        queryString = matcher.group(1);
                    }
                } catch (Exception ex) {
                    // Nope, we'll craft a better message below
                    printTracer("Problem parsing HTTP request: "+ex.getMessage());
                }
                
                MIDIRequest requestWrapper = new MIDIRequest(clientSocket.getInetAddress().toString(),queryString);
                
                // Request the wrapper to print debug statements if set
                if (isDebug) {
                    requestWrapper.printDebugMessage();
                }
                
                if (queryString == null) {
                    response = "400 Bad Request, no query string found. Must provide a query string with tone and duration (in milliseconds), e.g., http://www.yourserver.com/?tone=c4&duration=100";
                } else if (!requestWrapper.isValid()) {
                    response = "400 Bad Request, syntax error. Must provide a valid query string with tone and duration (in milliseconds), e.g., http://www.yourserver.com/?tone=c4&duration=100";
                } else {
                    
                    // Submit to queue
                    requestProcessor.submit(requestWrapper);
                    
                    // Everything went fine
                    response = "200 OK";
                }
            } catch (Exception ex) {
                System.err.println("Problem processing client request: "+ex.getMessage());
                ex.printStackTrace(System.err);
                response = "500 Internal Server Error: "+ex.getMessage();
            } finally {
                try {
                    // Send response to server
                    if (out != null) {
                        out.println(response);
                        printTracer("Sending client "+clientSocket.getInetAddress()+" response: "+response);
                        out.flush();
                    }
                    clientSocket.close();
                } catch (IOException ex) {
                    System.err.println("Problem closing client socket: "+ex.getMessage());
                    ex.printStackTrace(System.err);
                }
                
                printTracer("Client request from "+clientSocket.getInetAddress()+" took "+(System.currentTimeMillis()-start)+" ms.");
            }
        }
    } // MIDIServerWorkerThread
    
    /**
     * Consumer. Queues up requests and services them in order.
     */
    private class MIDIRequestProcessorThread extends Thread {
        
        private ArrayBlockingQueue<MIDIRequest> requests;
        private Synthesizer synth;
        private MidiChannel[] mc = null;
        private javax.sound.midi.Instrument[] instr = null;
        private MIDIServer server;
        
        private MIDIRequestProcessorThread(MIDIServer server) {
            requests = new ArrayBlockingQueue(10000);
            this.server = server;
            
            // Thread parameters
            setDaemon(true);
            setPriority(Thread.NORM_PRIORITY);
        }
        
        /**
         * Loads up the MIDI synthesizer and instruments.
         */
        private void loadInstruments() throws Exception {
            this.synth = MidiSystem.getSynthesizer();
            this.synth.open();
            
            this.mc    = synth.getChannels();
            this.instr = this.synth.getDefaultSoundbank().getInstruments();
            // TODO Change instruments
            synth.loadInstrument(instr[0]);
        }
        
        /**
         * <p>Queues up a MIDI request.</p>
         * @return True if submitted, false if queue too large.
         */
        public boolean submit(MIDIRequest request) {
            synchronized(requests) {
                return requests.offer(request);
            }
        }
        
        /**
         *
         */
        public void run() {
            printTracer("Starting the MIDI request processor thread...");
            try {
                loadInstruments();
                printTracer("... and loaded MIDI instruments.");
                ALWAYS: while(true) {
                    
                    // If any requests available, grab one and process
                    if (requests.size() > 0) {
                        MIDIRequest request = null;
                        synchronized(requests) {
                            try {
                                request = requests.take();
                                performMIDIRequest(request);
                            } catch (InterruptedException ex) {
                                // If interrupted, bail
                                break ALWAYS;
                            }
                        }
                    }
                    
                    // Yield to prevent burning CPU cycles
                    Thread.yield();
                }
                
            } catch (Exception ex) {
                System.err.println("Failed to load MIDI instruments: "+ex.getMessage());
                ex.printStackTrace(System.err);
                
                // Request server to stop
                server.stop();
            }
        } // run
        
        /**
         * Parses up notes and plays it!
         */
        private void performMIDIRequest(MIDIRequest request) {
            // Yuck, parse note. Regex out everything but number.
            String note = null;
            int octave = -1;
            
            Matcher matcher = Pattern.compile("^(\\D+?)(\\d+)").matcher(request.getTone());
            
            try {
                if (matcher.matches()) {
                    note = matcher.group(1);
                    octave = Integer.parseInt(matcher.group(2));
                }
            } catch (Exception ex) {
                printTracer("Problem parsing MIDI request: "+ex.getMessage());
                printTracer("  Ignoring MIDI request "+request.getTone()+" for "+request.getDuration()+" ms from "+request.getClientIP());
                return;
            }
            
            // Calculate the sound, which is note + 12 * octave
            int soundCode = 12 * octave;
            
            // Support uncommon notes Cb and Fb
            
            // Yuck...
            if (note.equalsIgnoreCase("Ab") || note.equalsIgnoreCase("Gs")) {
                soundCode+=8;
            } else if (note.equalsIgnoreCase("A")) {
                soundCode+=9;
            } else if (note.equalsIgnoreCase("As") || note.equalsIgnoreCase("Bb")) {
                soundCode+=10;
            } else if (note.equalsIgnoreCase("B") || note.equalsIgnoreCase("Cb")) {
                soundCode+=11;
            } else if (note.equalsIgnoreCase("C")) {
                soundCode+=0;
            } else if (note.equalsIgnoreCase("Cs") || note.equalsIgnoreCase("Db")) {
                soundCode+=1;
            } else if (note.equalsIgnoreCase("D")) {
                soundCode+=2;
            } else if (note.equalsIgnoreCase("Ds") || note.equalsIgnoreCase("Eb")) {
                soundCode+=3;
            } else if (note.equalsIgnoreCase("E") || note.equalsIgnoreCase("Fb")) {
                soundCode+=4;
            } else if (note.equalsIgnoreCase("F")) {
                soundCode+=5;
            } else if (note.equalsIgnoreCase("Fs") || note.equalsIgnoreCase("Gb")) {
                soundCode+=6;
            } else if (note.equalsIgnoreCase("G")) {
                soundCode+=7;
            }
            
            // Start note, sleep then kill sound
            mc[4].noteOn(soundCode,request.getDuration());
            try {
                Thread.sleep(request.getDuration());
            } catch (InterruptedException ex) {
                ex.printStackTrace();
            }
            mc[4].allNotesOff();
            mc[4].allSoundOff();
        }
        
    } // MIDIRequestProcessorThread
    
    /**
     * Prints out debug statements (tracers) if debug flag set to true.
     */
    private void printTracer(String msg) {
        if (isDebug) {
            System.out.println("DEBUG> "+msg);
        }
    }
}