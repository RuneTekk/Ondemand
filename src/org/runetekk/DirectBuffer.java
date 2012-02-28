package org.runetekk;

import java.nio.ByteBuffer;

/**
 * DirectBuffer.java
 * @version 1.0.0
 * @author RuneTekk Development (SiniSoul)
 */
public final class DirectBuffer {
    
    /**
     * The buffer to map to the memory.
     */
    private ByteBuffer buffer;
    
    /**
     * Puts a byte source into the buffer.
     * @param src The byte array source.
     */
    public void put(byte[] src) {
        buffer = ByteBuffer.allocateDirect(src.length);
        buffer.position(0);
        buffer.put(src);
    } 
    
    /**
     * Gets bytes from the buffer.
     * @param pos The position of the bytes to get.
     * @param len The amount of bytes to get.
     * @return The bytes.
     */
    public byte[] get(int pos, int len) {
	byte[] is = new byte[len];
	buffer.position(pos);
	buffer.get(is, 0, len);
	return is;
    }
    
    /**
     * Gets the payload of the buffer.
     * @return The payload.
     */
    public byte[] getPayload() {
        byte[] payload = new byte[buffer.capacity()];
        buffer.position(0);
        buffer.get(payload);
        return payload;
    }
    
    /**
     * Gets the capacity of the {@link ByteBuffer}.
     * @return The capacity.
     */
    public int getCapacity() {
        return buffer.capacity();
    }
}
