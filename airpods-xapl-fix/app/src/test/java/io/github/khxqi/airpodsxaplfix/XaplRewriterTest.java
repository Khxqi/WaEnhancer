package io.github.khxqi.airpodsxaplfix;

import static org.junit.Assert.assertEquals;
import static org.junit.Assert.assertNull;
import static org.junit.Assert.assertSame;

import org.junit.Test;

public class XaplRewriterTest {
    @Test
    public void rewritesExactResponse() {
        assertEquals("+XAPL=iPhone,0", XaplRewriter.rewriteIfNeeded("+XAPL=iPhone,2"));
    }

    @Test
    public void preservesCrLf() {
        assertEquals("+XAPL=iPhone,0\r\n", XaplRewriter.rewriteIfNeeded("+XAPL=iPhone,2\r\n"));
    }

    @Test
    public void doesNotRewriteOtherFeatureMasks() {
        String value = "+XAPL=iPhone,20";
        assertSame(value, XaplRewriter.rewriteIfNeeded(value));
    }

    @Test
    public void doesNotRewriteOtherAtResponses() {
        String value = "+CIND: 1,0,0";
        assertSame(value, XaplRewriter.rewriteIfNeeded(value));
    }

    @Test
    public void nullStaysNull() {
        assertNull(XaplRewriter.rewriteIfNeeded(null));
    }
}
