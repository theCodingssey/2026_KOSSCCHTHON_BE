package com.kosscchthon.Icelink.room;

import static org.assertj.core.api.Assertions.assertThat;
import static org.assertj.core.api.Assertions.assertThatThrownBy;
import static org.mockito.ArgumentMatchers.anyString;
import static org.mockito.Mockito.times;
import static org.mockito.Mockito.verify;
import static org.mockito.Mockito.when;

import java.util.Random;
import org.junit.jupiter.api.Test;
import org.junit.jupiter.api.extension.ExtendWith;
import org.mockito.Mock;
import org.mockito.junit.jupiter.MockitoExtension;

@ExtendWith(MockitoExtension.class)
class RoomCodeGeneratorTest {

    @Mock RoomRepository roomRepository;

    @Test
    void generate_isSixCharsFromAlphabetWithoutConfusingLetters() {
        RoomCodeGenerator generator = new RoomCodeGenerator(roomRepository, new Random(1));

        for (int i = 0; i < 500; i++) {
            String code = generator.generate();
            assertThat(code).hasSize(6).matches("^[A-HJ-NP-Z2-9]{6}$");
            assertThat(code).doesNotContain("0", "O", "1", "I");
        }
    }

    @Test
    void generateUnique_retriesWhenCodeAlreadyExists() {
        RoomCodeGenerator generator = new RoomCodeGenerator(roomRepository, new Random(1));
        when(roomRepository.existsByCode(anyString())).thenReturn(true, true, false);

        String code = generator.generateUnique();

        assertThat(code).hasSize(6);
        verify(roomRepository, times(3)).existsByCode(anyString());
    }

    @Test
    void generateUnique_givesUpAfterMaxAttempts() {
        RoomCodeGenerator generator = new RoomCodeGenerator(roomRepository, new Random(1));
        when(roomRepository.existsByCode(anyString())).thenReturn(true);

        assertThatThrownBy(generator::generateUnique).isInstanceOf(IllegalStateException.class);
    }

    @Test
    void normalize_trimsAndUppercases() {
        assertThat(RoomCodeGenerator.normalize("  k7m3pq ")).isEqualTo("K7M3PQ");
        assertThat(RoomCodeGenerator.normalize(null)).isEmpty();
    }
}
