package com.ledgercore.fraud.domain;

import java.nio.charset.StandardCharsets;
import java.util.BitSet;

/**
 * Bloom filter with Kirsch-Mitzenmacher double hashing: k probes derived from two 64-bit hashes.
 * A negative answer is certain, a positive one has to be confirmed against the database.
 */
public final class BloomFilter {

    private final BitSet bits;
    private final int size;
    private final int probes;

    private BloomFilter(int size, int probes) {
        this.bits = new BitSet(size);
        this.size = size;
        this.probes = probes;
    }

    public static BloomFilter forCapacity(int expectedItems, double falsePositiveRate) {
        if (expectedItems <= 0 || falsePositiveRate <= 0 || falsePositiveRate >= 1) {
            throw new IllegalArgumentException("Need positive capacity and a rate in (0, 1)");
        }
        double ln2 = Math.log(2);
        int size = (int) Math.ceil(-expectedItems * Math.log(falsePositiveRate) / (ln2 * ln2));
        int probes = Math.max(1, (int) Math.round((double) size / expectedItems * ln2));
        return new BloomFilter(size, probes);
    }

    public void add(String value) {
        long[] h = hashes(value);
        for (int i = 0; i < probes; i++) {
            bits.set(index(h, i));
        }
    }

    public boolean mightContain(String value) {
        long[] h = hashes(value);
        for (int i = 0; i < probes; i++) {
            if (!bits.get(index(h, i))) {
                return false;
            }
        }
        return true;
    }

    public int size() {
        return size;
    }

    public int probes() {
        return probes;
    }

    private int index(long[] h, int i) {
        return (int) Math.floorMod(h[0] + i * h[1], (long) size);
    }

    private static long[] hashes(String value) {
        byte[] data = value.getBytes(StandardCharsets.UTF_8);
        long h1 = fnv1a(data);
        long h2 = mix(h1 ^ data.length) | 1L;
        return new long[] {h1, h2};
    }

    private static long fnv1a(byte[] data) {
        long hash = 0xcbf29ce484222325L;
        for (byte b : data) {
            hash ^= b & 0xff;
            hash *= 0x100000001b3L;
        }
        return hash;
    }

    private static long mix(long z) {
        z = (z ^ (z >>> 33)) * 0xff51afd7ed558ccdL;
        z = (z ^ (z >>> 33)) * 0xc4ceb9fe1a85ec53L;
        return z ^ (z >>> 33);
    }
}
