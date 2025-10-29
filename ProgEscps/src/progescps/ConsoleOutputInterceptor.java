package progescps;

import javax.swing.*;
import java.io.IOException;
import java.io.OutputStream;

/**
 * OutputStream that appends text to a JTextArea on the EDT.
 * Used to redirect System.out/System.err to the game's UI.
 */
public class ConsoleOutputInterceptor extends OutputStream {
    private final JTextArea area;
    private final StringBuilder buffer = new StringBuilder();

    public ConsoleOutputInterceptor(JTextArea area) {
        this.area = area;
    }

    @Override
    public void write(int b) throws IOException {
        synchronized (buffer) {
            buffer.append((char) b);
            if (b == '\n') {
                flushBuffer();
            }
        }
    }

    @Override
    public void write(byte[] b, int off, int len) throws IOException {
        synchronized (buffer) {
            buffer.append(new String(b, off, len));
            if (buffer.indexOf("\n") >= 0) {
                flushBuffer();
            }
        }
    }

    private void flushBuffer() {
        final String text;
        synchronized (buffer) {
            text = buffer.toString();
            buffer.setLength(0);
        }
        if (text.isEmpty()) return;
        SwingUtilities.invokeLater(() -> {
            area.append(text);
            area.setCaretPosition(area.getDocument().getLength());
        });
    }

    @Override
    public void flush() throws IOException {
        synchronized (buffer) {
            if (buffer.length() > 0) {
                flushBuffer();
            }
        }
    }

    @Override
    public void close() throws IOException {
        flush();
    }
}