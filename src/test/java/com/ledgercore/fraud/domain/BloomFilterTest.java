package com.ledgercore.fraud.domain;

import static org.assertj.core.api.Assertions.assertThat;

import java.util.stream.IntStream;
import org.junit.jupiter.api.Test;

class BloomFilterTest {

    @Test
    void neverForgetsAnInsertedValue() {
        BloomFilter filter = BloomFilter.forCapacity(1_000, 0.01);
        IntStream.range(0, 1_000).forEach(i -> filter.add("device-" + i));

        assertThat(IntStream.range(0, 1_000).allMatch(i -> filter.mightContain("device-" + i))).isTrue();
    }

    @Test
    void falsePositiveRateStaysNearTheTarget() {
        BloomFilter filter = BloomFilter.forCapacity(10_000, 0.01);
        IntStream.range(0, 10_000).forEach(i -> filter.add("present-" + i));

        long falsePositives = IntStream.range(0, 100_000).filter(i -> filter.mightContain("absent-" + i)).count();

        // Target is 1%; allow 2x for hash quality on short, similar keys.
        assertThat(falsePositives).isLessThan(2_000);
    }

    @Test
    void sizesItselfFromCapacityAndRate() {
        BloomFilter filter = BloomFilter.forCapacity(10_000, 0.001);

        // m = -n ln p / (ln 2)^2 ≈ 143,776 bits, k = m/n ln 2 ≈ 10
        assertThat(filter.size()).isBetween(143_000, 145_000);
        assertThat(filter.probes()).isEqualTo(10);
    }
}
