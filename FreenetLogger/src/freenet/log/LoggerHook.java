package freenet.log;

import java.util.ArrayList;
import java.util.StringTokenizer;
import java.util.concurrent.CopyOnWriteArrayList;

import org.osgi.service.log.LogService;

public abstract class LoggerHook extends Logger {

	protected int threshold;

	public static final class DetailedThreshold {
		final String section;
		final int dThreshold;
		public DetailedThreshold(String section, int thresh) {
			this.section = section;
			this.dThreshold = thresh;
		}
	}

	protected LoggerHook(int thresh){
		this.threshold = thresh;
	}

	LoggerHook(String thresh) throws InvalidThresholdException{
		this.threshold = priorityOf(thresh);
	}

	public DetailedThreshold[] detailedThresholds = new DetailedThreshold[0];
	private CopyOnWriteArrayList<LogThresholdCallback> thresholdsCallbacks = new CopyOnWriteArrayList<LogThresholdCallback>();

	/**
	 * Log a message
	 * 
	 * @param o
	 *            The object where this message was generated.
	 * @param source
	 *            The class where this message was generated.
	 * @param message
	 *            A clear and verbose message describing the event
	 * @param e
	 *            Logs this exception with the message.
	 * @param priority
	 *            The priority of the mesage, one of Logger.ERROR,
	 *            Logger.NORMAL, Logger.MINOR, or Logger.DEBUG.
	 */
	@Override
	public abstract void log(
			Object o,
			Class<?> source,
			String message,
			Throwable e,
			int priority);

	/**
	 * Log a message.
	 * @param source        The source object where this message was generated
	 * @param message A clear and verbose message describing the event
	 * @param priority The priority of the mesage, one of Logger.ERROR,
	 *                 Logger.NORMAL, Logger.MINOR, or Logger.DEBUG.
	 **/
	@Override
	public void log(Object source, String message, int priority) {
		if (!instanceShouldLog(priority,source)) return;
		log(source, source == null ? null : source.getClass(), 
				message, null, priority);
	}

	/** 
	 * Log a message with an exception.
	 * @param o   The source object where this message was generated.
	 * @param message  A clear and verbose message describing the event.
	 * @param e        Logs this exception with the message.
	 * @param priority The priority of the mesage, one of Logger.ERROR,
	 *                 Logger.NORMAL, Logger.MINOR, or Logger.DEBUG.
	 * @see #log(Object o, String message, int priority)
	 */
	@Override
	public void log(Object o, String message, Throwable e, 
			int priority) {
		if (!instanceShouldLog(priority,o)) return;
		log(o, o == null ? null : o.getClass(), message, e, priority);
	}

	/**
	 * Log a message from static code.
	 * @param c        The class where this message was generated.
	 * @param message  A clear and verbose message describing the event
	 * @param priority The priority of the mesage, one of Logger.ERROR,
	 *                 Logger.NORMAL, Logger.MINOR, or Logger.DEBUG.
	 */
	@Override
	public void log(Class<?> c, String message, int priority) {
		if (!instanceShouldLog(priority,c)) return;
		log(null, c, message, null, priority);
	}


	@Override
	public void log(Class<?> c, String message, Throwable e, int priority) {
		if (!instanceShouldLog(priority, c))
			return;
		log(null, c, message, e, priority);
	}

	public boolean acceptPriority(int prio) {
		return prio >= threshold;
	}

	@Override
	public void setThreshold(int thresh) {
		this.threshold = thresh;
		notifyLogThresholdCallbacks();
	}

	@Override
	public int getThreshold() {
		return threshold;
	}

	@Override
	public void setThreshold(String symbolicThreshold) throws InvalidThresholdException {
		setThreshold(priorityOf(symbolicThreshold));
	}

	@Override
	public void setDetailedThresholds(String details) throws InvalidThresholdException {
		if (details == null)
			return;
		StringTokenizer st = new StringTokenizer(details, ",", false);
		ArrayList<DetailedThreshold> stuff = new ArrayList<DetailedThreshold>();
		while (st.hasMoreTokens()) {
			String token = st.nextToken();
			if (token.length() == 0)
				continue;
			int x = token.indexOf(':');
			if (x < 0)
				continue;
			if (x == token.length() - 1)
				continue;
			String section = token.substring(0, x);
			String value = token.substring(x + 1, token.length());
			int thresh = LoggerHook.priorityOf(value);
			stuff.add(new DetailedThreshold(section, thresh));
		}
		DetailedThreshold[] newThresholds = new DetailedThreshold[stuff.size()];
		stuff.toArray(newThresholds);
		synchronized(this) {
			detailedThresholds = newThresholds;
			notifyLogThresholdCallbacks();
		}
	}

	public String getDetailedThresholds() {
		DetailedThreshold[] thresh = null;
		synchronized(this) {
			thresh = detailedThresholds;
		}
		StringBuilder sb = new StringBuilder();
		for(int i=0;i<thresh.length;i++) {
			if(i != 0)
				sb.append(',');
			sb.append(thresh[i].section);
			sb.append(':');
			sb.append(LoggerHook.priorityOf(thresh[i].dThreshold));
		}
		return sb.toString();
	}

	/**
	 * Returns the priority level matching the string. If no priority
	 * matches, Logger.NORMAL is returned.
	 * @param s  A string matching one of the logging priorities, case
	 *           insensitive.
	 **/
	public static int priorityOf(String s) throws InvalidThresholdException {
		if (s.equalsIgnoreCase("error"))
			return Logger.ERROR;
		else if (s.equalsIgnoreCase("warning"))
			return Logger.WARNING;
		else if (s.equalsIgnoreCase("normal"))
			return Logger.NORMAL;
		else if (s.equalsIgnoreCase("minor"))
			return Logger.MINOR;
		else if (s.equalsIgnoreCase("debugging"))
			return Logger.DEBUG;
		else if (s.equalsIgnoreCase("debug"))
			return Logger.DEBUG;
		else
			throw new InvalidThresholdException("LoggerHook.unrecognisedPriority: "+s);
		// return Logger.NORMAL;
	}

	public static class InvalidThresholdException extends Exception {
		private static final long serialVersionUID = -1;

		InvalidThresholdException(String msg) {
			super(msg);
		}
	}

	/**
	 * Returns the name of the priority matching a number, null if none do
	 * @param priority  The priority
	 */
	public static String priorityOf(int priority) {
		switch (priority) {
			case ERROR:     return "ERROR";
			case WARNING:	return "WARNING";
			case NORMAL:    return "NORMAL";
			case MINOR:     return "MINOR";
			case DEBUG:     return "DEBUG";
			default:        return null;
		}
	}

	@Override
	public boolean instanceShouldLog(int priority, Class<?> c) {
		DetailedThreshold[] thresholds;
		int thresh;
		synchronized(this) {
			thresholds = detailedThresholds;
			thresh = threshold;
		}
		if ((c != null) && (thresholds.length > 0)) {
			String cname = c.getName();
				for(DetailedThreshold dt : thresholds) {
					if(cname.startsWith(dt.section))
						thresh = dt.dThreshold;
				}
		}
		return priority >= thresh;
	}

	@Override
	public final boolean instanceShouldLog(int prio, Object o) {
		return instanceShouldLog(prio, o == null ? null : o.getClass());
	}

	@Override
	public final void instanceRegisterLogThresholdCallback(LogThresholdCallback ltc) {
		thresholdsCallbacks.add(ltc);

		// Call the new callback to avoid code duplication
		ltc.shouldUpdate();
	}
	
	@Override
	public final void instanceUnregisterLogThresholdCallback(LogThresholdCallback ltc) {
		thresholdsCallbacks.remove(ltc);
	}

	private final void notifyLogThresholdCallbacks() {
		for(LogThresholdCallback ltc : thresholdsCallbacks)
			ltc.shouldUpdate();
	}

	public abstract long minFlags(); // ignore unless all these bits set

	public abstract long notFlags(); // reject if any of these bits set

	public abstract long anyFlags(); // accept if any of these bits set
	
	// TODO/FIXME
	// transform freenet level to osgi level
	protected int transformLogLevelFO(int level) {
		switch (level) {
		case ERROR:     return LogService.LOG_ERROR;
		case WARNING:	return LogService.LOG_WARNING;
		case NORMAL:    return LogService.LOG_INFO;
		case MINOR:     return LogService.LOG_DEBUG;
		case DEBUG:     return LogService.LOG_DEBUG;
		default:        {
			new Error("hui, fix log levels.").printStackTrace();
			return 1;
		}
		}
	}

	// TODO/FIXME
	// transform osgi level to freenet level
	protected int transformLogLevelOF(int level) {
		switch (level) {
		case LogService.LOG_ERROR: return ERROR;
		case LogService.LOG_WARNING: return WARNING;
		case LogService.LOG_INFO: return NORMAL;
		case LogService.LOG_DEBUG: return DEBUG;
		default:        {
			new Error("hui, fix log levels.").printStackTrace();
			return 1;
		}
		}
	}

}
