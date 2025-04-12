package org.digitalmodular.maruneko;

import java.io.IOException;
import java.nio.file.FileSystems;
import java.nio.file.Path;
import java.nio.file.Paths;
import java.nio.file.StandardWatchEventKinds;
import java.nio.file.WatchEvent;
import java.nio.file.WatchEvent.Kind;
import java.nio.file.WatchKey;
import java.nio.file.WatchService;
import java.util.Map;
import java.util.concurrent.ConcurrentHashMap;
import java.util.logging.Level;
import java.util.logging.Logger;
import com.sun.nio.file.ExtendedWatchEventModifier;

import org.jetbrains.annotations.Nullable;

/**
 * @author Mark Jeronimus
 */
// Created 2017-05-25
public class FileWatcherTestMain implements Runnable {
	private static final Kind<?>[] WATCH_KINDS = {StandardWatchEventKinds.ENTRY_CREATE,
	                                              StandardWatchEventKinds.ENTRY_DELETE,
	                                              StandardWatchEventKinds.ENTRY_MODIFY};

	public static void main(String... args) throws IOException, InterruptedException {
		FileWatcherTestMain watch = new FileWatcherTestMain();
	}

	private @Nullable WatchService        watchService      = null;
	private           Map<Path, WatchKey> registeredWatches = new ConcurrentHashMap<>(32);

	public FileWatcherTestMain() throws InterruptedException, IOException {
		Thread thread = new Thread(this);
		thread.setDaemon(true);
		thread.start();

		Thread.sleep(1000);
		addWatch(Paths.get("d:\\"));
		Thread.sleep(3000);
		for (WatchKey key : registeredWatches.values())
			System.out.println(key.pollEvents());
		Thread.sleep(30000);
		for (WatchKey key : registeredWatches.values())
			System.out.println(key.pollEvents());
		System.exit(0);
	}

	public void addWatch(Path path) throws IOException {
		Logger.getGlobal().log(Level.INFO, "Registering path " + path);

		WatchKey key = path.register(watchService, WATCH_KINDS, ExtendedWatchEventModifier.FILE_TREE);
		registeredWatches.put(path, key);
	}

	private static void handleChange(WatchEvent<?> evt) {
		System.out.println(evt.kind() + "\t" + evt.count() + '\t' + evt.context().getClass().getSimpleName() + '\t' +
		                   evt.context());
	}

	@Override
	public void run() {
		try (WatchService watchService = FileSystems.getDefault().newWatchService()) {
			this.watchService = watchService;

			while (true) {
				WatchKey key = watchService.take();
				for (WatchEvent<?> evt : key.pollEvents())
					handleChange(evt);

				boolean valid = key.reset();
			}
		} catch (InterruptedException e) {
			e.printStackTrace();
		} catch (IOException e) {
			e.printStackTrace();
		}
	}
}
