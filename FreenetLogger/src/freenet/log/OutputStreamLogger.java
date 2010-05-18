package freenet.log;

import java.io.OutputStream;

public class OutputStreamLogger extends OutputStream {

	final int prio;
	final String prefix;
	
	public OutputStreamLogger(int prio, String prefix) {
		this.prio = prio;
		this.prefix = prefix;
	}

	@Override
	public void write(int b) {
		Logger.logStatic(this, prefix+(char)b, prio);
	}
	
	@Override
	public void write(byte[] buf, int offset, int length) {
		Logger.logStatic(this, prefix+new String(buf, offset, length), prio);
	}
	
	@Override
	public void write(byte[] buf) {
		write(buf, 0, buf.length);
	}
}
