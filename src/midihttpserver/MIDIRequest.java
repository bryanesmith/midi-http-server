/*
 * MIDIRequest.java
 *
 * Created on January 26, 2008, 8:13 PM
 *
 * To change this template, choose Tools | Template Manager
 * and open the template in the editor.
 */

package midihttpserver;

import java.util.HashMap;
import java.util.Map;

/**
 * Encapsultes a single HTTP request to play a MIDI note.
 * @author Bryan E. Smith <bryanesmith at gmail dot com>
 */
public class MIDIRequest {
    
    private String clientIP, requestString;
    
    private boolean valid = false;
    
    private boolean isLazyLoaded = false;
    
    private String tone = null;
    
    private int duration = -1;
    
    /** Creates a new instance of MIDIRequest */
    public MIDIRequest(String clientIP,String requestString) {
        this.clientIP = clientIP;
        this.requestString = requestString;
    }
    
    /**
     * Lazy parse the request once.
     */
    private void lazyLoad() {
        if (isLazyLoaded) {
            return;
        }
        isLazyLoaded = true;
        
        // If request string is null, return
        if (requestString == null) {
            return;
        }
        
        try {
            // Need to parse tone=<String>&duration=<Long>
            String[] nameValuePairs = requestString.split("&");
            
            Map<String,String> nameValueMap = new HashMap();
            
            // Show request parameters in map so simply to query
            String nextName, nextValue;
            for (String nameValuePair : nameValuePairs) {
                nextName = nameValuePair.split("=")[0];
                nextValue = nameValuePair.split("=")[1];
                nameValueMap.put(nextName.trim(),nextValue.trim());
            }
            
            this.tone = nameValueMap.get("tone");
            this.duration = Integer.parseInt(nameValueMap.get("duration"));
        
            if (this.tone == null || this.duration < 0) {
                throw new Exception("Syntax error.");
            }
            
            // Everything looks good.
            valid = true;
        } catch (Exception ex) {
            // nope, print debug message if problem
        }
    }
    
    /**
     * Verifies request is valid.
     * @return True if valid, false otherwise.
     */
    public boolean isValid() {
        lazyLoad();
        return valid;
    }
    
    /**
     * Print out tracers for the MIDI request. Useful if behavior is unexpected, verbose.
     */
    public synchronized void printDebugMessage() {
        lazyLoad();
        System.out.println("DEBUG> MIDI request from "+this.getClientIP());
        System.out.println("DEBUG>   query string: "+this.requestString);
        System.out.println("DEBUG>   tone: "+this.tone);
        System.out.println("DEBUG>   duration: "+this.duration);
    }

    /**
     * Returns the request's tone.
     * @return The tone, e.g., C, Cs, Db, D, Ds, etc.
     */
    public String getTone() {
        return tone;
    }

    /**
     * Return the tone's duration.
     * @return The duration in milliseconds.
     */
    public int getDuration() {
        return duration;
    }

    /**
     * Returns the client's IP address. IPv4/IPv6 are not guarenteed.
     */
    public String getClientIP() {
        return clientIP;
    }
}
