package ccw.util.osgi;

import java.net.URL;
import java.util.List;

import org.osgi.framework.Bundle;

import ccw.CCWPlugin;
import ccw.TraceOptions;
import ccw.util.DisplayUtil;
import clojure.lang.Compiler;
import clojure.lang.DynamicClassLoader;
import clojure.lang.IPersistentMap;
import clojure.lang.RT;
import clojure.lang.Symbol;
import clojure.lang.Var;

public class ClojureOSGi {
	private static volatile boolean initialized;
	private static void initialize() {
		if (initialized) return;
		synchronizedInitialize();
	}
	private synchronized static void synchronizedInitialize() {
		if (initialized) return;

		CCWPlugin plugin = CCWPlugin.getDefault();
		if (plugin == null) {
			CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI,
					"ClojureOSGi.initialize will fail because ccw.core plugin not activated yet");
		}
		ClassLoader loader = new BundleClassLoader(plugin.getBundle());
		ClassLoader saved = Thread.currentThread().getContextClassLoader();
		try {
			Thread.currentThread().setContextClassLoader(loader);
			Class.forName("clojure.lang.RT", true, loader); // very important, uses the right classloader
			CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI, "namespace clojure.core loaded");
			initialized = true;
		} catch (Exception e) {
			throw new RuntimeException(
					"Exception while loading namespace clojure.core", e);
		} finally {
			Thread.currentThread().setContextClassLoader(saved);
		}

	}
	public static Object withBundle(Bundle aBundle, RunnableWithException aCode)
			throws RuntimeException {
		return withBundle(aBundle, aCode, null);
	}

	public static Object withBundle(Bundle aBundle, RunnableWithException aCode, List<URL> additionalURLs)
			throws RuntimeException {

		if (DisplayUtil.isUIThread()) {
			CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI, "should not be called from UI Tread");
			CCWPlugin.getTracer().traceDumpStack(TraceOptions.CLOJURE_OSGI);
		}

		initialize();

		// TODO cache the dynamic class loader
		ClassLoader bundleLoader = new BundleClassLoader(aBundle);
		DynamicClassLoader loader = new DynamicClassLoader(bundleLoader);
		if (additionalURLs != null) {
			for (URL url: additionalURLs) {
				loader.addURL(url);
			}
		}
		IPersistentMap bindings = RT.map(Compiler.LOADER, loader);
		bindings = bindings.assoc(RT.USE_CONTEXT_CLASSLOADER, true);

		boolean pushed = true;

		ClassLoader saved = Thread.currentThread().getContextClassLoader();

		try {
			Thread.currentThread().setContextClassLoader(loader);
			try {
				Var.pushThreadBindings(bindings);
			} catch (RuntimeException aEx) {
				pushed = false;
				throw aEx;
			}
			return aCode.run();
		} catch (Exception e) {
			String msg = "Exception while executing code from bundle "
					+ aBundle.getSymbolicName();
			CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI, e, msg);
			throw new RuntimeException(msg, e);
		} finally {
			if (pushed) {
				Var.popThreadBindings();
			}
			Thread.currentThread().setContextClassLoader(saved);
		}
	}

	public synchronized static void require(final Bundle bundle, final String namespace) {
		ClojureOSGi.withBundle(bundle, new RunnableWithException() {
			@Override
			public Object run() throws Exception {
				try {
					RT.var("clojure.core", "require").invoke(Symbol.intern(namespace));
					String msg = "Namespace " + namespace + " loaded from bundle " + bundle.getSymbolicName();
					CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI, msg);
					return null;
				} catch (Exception e) {
					String msg = "Exception loading namespace " + namespace + " from bundle " + bundle.getSymbolicName();
					CCWPlugin.getTracer().trace(TraceOptions.CLOJURE_OSGI, e, msg);
					throw new RuntimeException(msg, e);
				}
			}
		});
	}
}
