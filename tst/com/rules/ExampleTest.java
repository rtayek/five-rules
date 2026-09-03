package com.rules;

import static org.junit.jupiter.api.Assertions.assertEquals;

import org.junit.jupiter.api.Test;

class ExampleTest {
    @Test
    void addsTwoNumbers() {
        assertEquals(5, new Example().add(2, 3));
    }
}
