package com.shvmpk.shortener_service.util;

import org.junit.jupiter.api.Test;

import static org.assertj.core.api.Assertions.assertThat;

class IdObfuscatorAndBase62Test {

    @Test
    void obfuscateUnobfuscate_roundTrips() {
        long[] ids = {
            0, 1, 62, 1000, 100_000_000_000L, 100_000_000_001L,
            Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE, -1
        };
        for (long id : ids) {
            long obfuscated = IdObfuscator.obfuscate(id);
            long restored = IdObfuscator.unobfuscate(obfuscated);
            assertThat(restored).isEqualTo(id);
        }
    }

    @Test
    void fullPipeline_roundTrips() {
        long[] ids = {
            0, 1, 62, 1000, 100_000_000_000L, 100_000_000_001L, 999_999_999_999L,
            Long.MAX_VALUE, Long.MAX_VALUE - 1, Long.MIN_VALUE, -1
        };
        for (long id : ids) {
            long obfuscated = IdObfuscator.obfuscate(id);
            String code = Base62Encoder.encode(obfuscated);
            long decoded = Base62Encoder.decode(code);
            long restored = IdObfuscator.unobfuscate(decoded);
            assertThat(restored).isEqualTo(id);
        }
    }

    @Test
    void sequentialIds_produceDifferentCodes() {
        String code1 = Base62Encoder.encode(IdObfuscator.obfuscate(100_000_000_000L));
        String code2 = Base62Encoder.encode(IdObfuscator.obfuscate(100_000_000_001L));
        assertThat(code1).isNotEqualTo(code2);
    }

    @Test
    void encode_handlesValuesThatWrapNegative() {
        int found = 0;
        for (long id = 100_000_000_000L; id < 100_001_000_000L; id++) {
            long obfuscated = IdObfuscator.obfuscate(id);
            if (obfuscated < 0) {
                String code = Base62Encoder.encode(obfuscated);
                assertThat(code).isNotEmpty();
                assertThat(code).doesNotContain("-");
                assertThat(code).matches("^[a-zA-Z0-9]+$");
                found++;
            }
        }
        assertThat(found).isGreaterThan(0);
    }

    @Test
    void encode_fullUnsignedRange() {
        long[] values = {
            0, 1, 61, 62, 63, 1000, Long.MAX_VALUE, Long.MIN_VALUE, -1, -1000
        };
        for (long val : values) {
            String code = Base62Encoder.encode(val);
            assertThat(code).isNotEmpty();
            assertThat(code).doesNotContain("-");
            assertThat(code).matches("^[a-zA-Z0-9]+$");
        }
    }

    @Test
    void encodeDecode_knownValues() {
        assertThat(Base62Encoder.encode(0)).isEqualTo("a");
        assertThat(Base62Encoder.encode(1)).isEqualTo("b");
        assertThat(Base62Encoder.encode(61)).isEqualTo("9");
        assertThat(Base62Encoder.encode(62)).isEqualTo("ba");
        assertThat(Base62Encoder.encode(63)).isEqualTo("bb");
        assertThat(Base62Encoder.decode("a")).isEqualTo(0);
        assertThat(Base62Encoder.decode("b")).isEqualTo(1);
        assertThat(Base62Encoder.decode("9")).isEqualTo(61);
        assertThat(Base62Encoder.decode("ba")).isEqualTo(62);
        assertThat(Base62Encoder.decode("bb")).isEqualTo(63);
    }
}
