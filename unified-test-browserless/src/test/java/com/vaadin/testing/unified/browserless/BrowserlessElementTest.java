package com.vaadin.testing.unified.browserless;

import static org.junit.jupiter.api.Assertions.assertEquals;
import static org.junit.jupiter.api.Assertions.assertNull;

import java.math.BigDecimal;
import org.junit.jupiter.api.Test;

/** Property values are written as the browsers write them (#255). */
class BrowserlessElementTest {

    @Test
    void writesAWholeDoubleWithoutItsFraction() {
        assertEquals("3", BrowserlessElement.text(3.0));
        assertEquals("-12", BrowserlessElement.text(-12.0));
        assertEquals("0", BrowserlessElement.text(0.0));
        assertEquals("7", BrowserlessElement.text(7.0f));
    }

    @Test
    void keepsFractionsAndSpecialValues() {
        assertEquals("3.5", BrowserlessElement.text(3.5));
        assertEquals("NaN", BrowserlessElement.text(Double.NaN));
        assertEquals("Infinity", BrowserlessElement.text(Double.POSITIVE_INFINITY));
        assertEquals("1.0E20", BrowserlessElement.text(1e20));
    }

    @Test
    void leavesOtherTypesAsTheyAre() {
        assertEquals("54.20", BrowserlessElement.text(new BigDecimal("54.20")));
        assertEquals("4", BrowserlessElement.text(4));
        assertEquals("true", BrowserlessElement.text(true));
        assertEquals("Alice", BrowserlessElement.text("Alice"));
        assertNull(BrowserlessElement.text(null));
    }

    @Test
    void hidesPasswordValuesInTheComponentTree() {
        String tree = """
                ├── TextField[label='Name', value='Alan']
                ├── PasswordField[label='Password', value='s3cret!', required='true']
                └── PasswordField[label='Repeat']
                """;
        assertEquals("""
                ├── TextField[label='Name', value='Alan']
                ├── PasswordField[label='Password', value='••••', required='true']
                └── PasswordField[label='Repeat']
                """, BrowserlessSession.maskPasswords(tree));
    }
}
